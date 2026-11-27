package com.listenquran.player.cast

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages DLNA speaker discovery (background M-SEARCH + passive SSDP multicast + DataStore cache),
 * live PCM/WAV/L16 streaming sessions over [LocalMediaHttpServer] + [PcmStreamHub],
 * remote volume control, and renderer transport monitoring with latency estimation.
 */
@Singleton
class OutputDeviceManager internal constructor(
    private val discoveryClient: DeviceDiscoveryClient,
    private val controlClient: RemoteControlClient,
    private val mediaServer: LocalMediaHttpServer,
    private val pcmStreamHub: PcmStreamHub,
    private val deviceCache: CastDeviceCache? = null,
    private val appContext: Context? = null,
    private val pollIntervalMs: Long = 2000L,
    private val connectPollIntervalMs: Long = 200L,
    private val connectTimeoutMs: Long = 10_000L,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    @Suppress("UNUSED_PARAMETER") testMarker: Any? = null,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        discoveryClient: DeviceDiscoveryClient,
        controlClient: RemoteControlClient,
        mediaServer: LocalMediaHttpServer,
        pcmStreamHub: PcmStreamHub,
        deviceCache: CastDeviceCache,
    ) : this(
        discoveryClient = discoveryClient,
        controlClient = controlClient,
        mediaServer = mediaServer,
        pcmStreamHub = pcmStreamHub,
        deviceCache = deviceCache,
        appContext = context,
        pollIntervalMs = 2000L,
        connectPollIntervalMs = 200L,
        connectTimeoutMs = 10_000L,
        ioDispatcher = Dispatchers.IO,
        testMarker = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val opLock = Any()
    private val pollGeneration = AtomicLong(0L)
    private val sessionGeneration = AtomicLong(0L)

    private val _routeState = MutableStateFlow(OutputRouteState())
    val routeState: StateFlow<OutputRouteState> = _routeState.asStateFlow()

    val isCasting: Boolean
        get() = _routeState.value.isCasting

    val speakerDelayMs: Long
        get() = _routeState.value.speakerDelayMs

    var onRendererConnected: (() -> Unit)? = null
    var onRemoteConnectionLost: ((deviceName: String, reason: String) -> Unit)? = null
    var onRemotePlayPauseRequested: ((isPlaying: Boolean) -> Unit)? = null

    private val preferredFormats = ConcurrentHashMap<String, LiveStreamFormat>()
    private val cacheLoaded = AtomicBoolean(false)

    @Volatile
    private var activeOpJob: Job? = null

    @Volatile
    private var pollJob: Job? = null

    @Volatile
    private var discoveryLoopJob: Job? = null

    @Volatile
    private var passiveListenerJob: Job? = null

    @Volatile
    private var scanJob: Job? = null

    @Volatile
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    @Volatile
    private var intentionalPause: Boolean = false

    @Volatile
    private var currentLiveUrl: String? = null

    @Volatile
    private var currentDidlXml: String? = null

    @Volatile
    private var expectedFormat: LiveStreamFormat = LiveStreamFormat.WAV

    @Volatile
    private var lastMetadata: LiveTrackMetadata = LiveTrackMetadata(
        title = "Listen Quran",
        artist = "Quran Recitation",
    )

    init {
        mediaServer.onRendererConnected = {
            onRendererConnected?.invoke()
        }
    }

    fun startBackgroundDiscovery() {
        scope.launch {
            ensureCacheLoaded()
        }
        synchronized(opLock) {
            if (discoveryLoopJob?.isActive == true) return
            discoveryLoopJob = scope.launch {
                while (isActive) {
                    scanForDevicesInternal(timeoutMs = 3000L)
                    delay(30_000L)
                }
            }
            if (passiveListenerJob?.isActive != true) {
                passiveListenerJob = scope.launch {
                    discoveryClient.passiveDiscoveryFlow().collect { event ->
                        when (event) {
                            is SsdpPassiveEvent.Alive -> mergeDiscoveredDevice(event.device)
                            is SsdpPassiveEvent.ByeBye -> removeDiscoveredDeviceByKey(event.locationOrUsn)
                        }
                    }
                }
            }
            registerWifiCallbackLocked()
        }
    }

    fun stopBackgroundDiscovery() {
        synchronized(opLock) {
            discoveryLoopJob?.cancel()
            discoveryLoopJob = null
            passiveListenerJob?.cancel()
            passiveListenerJob = null
            scanJob?.cancel()
            scanJob = null
            unregisterWifiCallbackLocked()
        }
    }

    suspend fun scanForDevices() {
        ensureCacheLoaded()
        scanForDevicesInternal(timeoutMs = 3000L)
    }

    private suspend fun scanForDevicesInternal(timeoutMs: Long) {
        _routeState.update { it.copy(isScanning = true, errorMessage = null) }
        runCatching {
            discoveryClient.discoverDevicesFlow(timeoutMs).collect { device ->
                mergeDiscoveredDevice(device)
            }
        }
        _routeState.update { it.copy(isScanning = false) }
        deviceCache?.save(_routeState.value.discoveredDevices, preferredFormats)
    }

    private suspend fun ensureCacheLoaded() {
        if (!cacheLoaded.compareAndSet(false, true)) return
        val cache = deviceCache ?: return
        val (cachedDevices, cachedFormats) = cache.load()
        preferredFormats.putAll(cachedFormats)
        if (cachedDevices.isEmpty()) return

        _routeState.update { state ->
            val merged = (state.discoveredDevices + cachedDevices)
                .distinctBy { it.id }
                .sortedBy { it.name.lowercase() }
            state.copy(discoveredDevices = merged)
        }

        scope.launch {
            val verified = cachedDevices.map { dev ->
                async {
                    val loc = dev.locationUrl
                    if (loc.isNullOrBlank()) dev else discoveryClient.verifyDevice(loc)
                }
            }.awaitAll().filterNotNull()
            val verifiedIds = verified.map { it.id }.toSet()
            _routeState.update { state ->
                val updated = state.discoveredDevices.filter { existing ->
                    val wasCachedOnly = cachedDevices.any { it.id == existing.id }
                    !wasCachedOnly || existing.id in verifiedIds || existing.id == state.activeDevice.id
                }.let { list ->
                    (list + verified).distinctBy { it.id }.sortedBy { it.name.lowercase() }
                }
                state.copy(discoveredDevices = updated)
            }
        }
    }

    private fun mergeDiscoveredDevice(device: OutputDevice) {
        if (device.protocol != CastProtocol.DLNA) return
        _routeState.update { state ->
            val filtered = state.discoveredDevices.filterNot { it.id == device.id }
            val updated = (filtered + device).sortedBy { it.name.lowercase() }
            val updatedActive = if (state.activeDevice.id == device.id) device else state.activeDevice
            state.copy(
                activeDevice = updatedActive,
                discoveredDevices = updated,
            )
        }
    }

    private fun removeDiscoveredDeviceByKey(key: String) {
        _routeState.update { state ->
            val remaining = state.discoveredDevices.filterNot { dev ->
                (dev.id == key || dev.locationUrl == key) && dev.id != state.activeDevice.id
            }
            state.copy(discoveredDevices = remaining)
        }
    }

    private fun registerWifiCallbackLocked() {
        val ctx = appContext ?: return
        if (networkCallback != null) return
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                scanJob?.cancel()
                scanJob = scope.launch {
                    delay(400L)
                    scanForDevicesInternal(3000L)
                }
            }

            override fun onLost(network: Network) {
                scanJob?.cancel()
                scanJob = scope.launch {
                    scanForDevicesInternal(1500L)
                }
            }
        }
        runCatching {
            cm.registerNetworkCallback(request, cb)
            networkCallback = cb
        }
    }

    private fun unregisterWifiCallbackLocked() {
        val ctx = appContext ?: return
        val cb = networkCallback ?: return
        networkCallback = null
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        runCatching { cm.unregisterNetworkCallback(cb) }
    }

    suspend fun connectToDevice(
        device: OutputDevice,
        metadata: LiveTrackMetadata? = null,
        autoPlay: Boolean = true,
    ): Result<Unit> {
        if (device.protocol == CastProtocol.LOCAL) {
            disconnect()
            return Result.success(Unit)
        }

        val callerJob = currentCoroutineContext()[Job]
        val myGen: Long
        synchronized(opLock) {
            myGen = sessionGeneration.incrementAndGet()
            activeOpJob?.cancel()
            activeOpJob = callerJob
            stopPollingLocked()
        }

        val lanIp = discoveryClient.getLocalLanIpv4Address()
        if (lanIp == null) {
            val msg = "Connect to Wi-Fi to play on network speakers"
            if (sessionGeneration.get() == myGen) {
                _routeState.update {
                    it.copy(
                        activeDevice = OutputDevice.LOCAL_PHONE,
                        connectionState = CastConnectionState.ERROR,
                        errorMessage = msg,
                    )
                }
            }
            return Result.failure(IllegalStateException(msg))
        }

        if (metadata != null) {
            lastMetadata = metadata
        }
        intentionalPause = !autoPlay

        _routeState.update {
            it.copy(
                activeDevice = device,
                connectionState = CastConnectionState.CONNECTING,
                errorMessage = null,
                speakerDelayMs = 0L,
            )
        }

        val token = pcmStreamHub.openSession()
        pcmStreamHub.paused = false

        try {
            mediaServer.start(lanIp, forceRestart = true)
            mediaServer.setSessionToken(token)
        } catch (e: Exception) {
            pcmStreamHub.closeSession()
            if (sessionGeneration.get() == myGen) {
                val msg = "Failed to start audio stream server: ${e.message}"
                _routeState.update {
                    it.copy(
                        activeDevice = OutputDevice.LOCAL_PHONE,
                        connectionState = CastConnectionState.ERROR,
                        errorMessage = msg,
                    )
                }
            }
            return Result.failure(e)
        }

        val initialFormat = preferredFormats[device.id] ?: LiveStreamFormat.WAV

        try {
            var res = startStreamOnRenderer(device, token, initialFormat, myGen)
            if (res.isFailure && initialFormat == LiveStreamFormat.WAV && isOperationCurrent(myGen)) {
                val msg = res.exceptionOrNull()?.message.orEmpty()
                val isFormatReject = msg.contains("714") ||
                    msg.contains("Illegal MIME", ignoreCase = true) ||
                    msg.contains("Timed out", ignoreCase = true)
                if (isFormatReject) {
                    pcmStreamHub.clearBuffer()
                    res = startStreamOnRenderer(device, token, LiveStreamFormat.L16, myGen)
                    if (res.isSuccess) {
                        preferredFormats[device.id] = LiveStreamFormat.L16
                        deviceCache?.save(_routeState.value.discoveredDevices, preferredFormats)
                    }
                }
            } else if (res.isSuccess) {
                preferredFormats[device.id] = initialFormat
            }

            if (!isOperationCurrent(myGen)) {
                synchronized(opLock) {
                    if (activeOpJob == callerJob) activeOpJob = null
                }
                return Result.failure(CancellationException("Cast connection superseded or cancelled"))
            }

            if (res.isFailure) {
                pcmStreamHub.closeSession()
                mediaServer.stop()
                val errMsg = res.exceptionOrNull()?.message ?: "Could not connect to ${device.name}"
                synchronized(opLock) {
                    if (activeOpJob == callerJob) activeOpJob = null
                }
                _routeState.update {
                    it.copy(
                        activeDevice = OutputDevice.LOCAL_PHONE,
                        connectionState = CastConnectionState.ERROR,
                        errorMessage = errMsg,
                    )
                }
                return res
            }

            val initialVolume = controlClient.getVolume(device).getOrNull() ?: _routeState.value.remoteVolume
            if (!isOperationCurrent(myGen)) {
                synchronized(opLock) {
                    if (activeOpJob == callerJob) activeOpJob = null
                }
                return Result.failure(CancellationException("Cast connection cancelled"))
            }

            _routeState.update { state ->
                val known = (state.discoveredDevices + device).distinctBy { it.id }.sortedBy { it.name.lowercase() }
                state.copy(
                    activeDevice = device,
                    discoveredDevices = known,
                    connectionState = CastConnectionState.CONNECTED,
                    remoteVolume = initialVolume,
                    errorMessage = null,
                )
            }

            synchronized(opLock) {
                if (sessionGeneration.get() == myGen) {
                    activeOpJob = null
                    startPollingLocked(device, myGen)
                }
            }
            return Result.success(Unit)
        } catch (ce: CancellationException) {
            synchronized(opLock) {
                if (activeOpJob == callerJob) activeOpJob = null
            }
            if (sessionGeneration.get() == myGen) {
                pcmStreamHub.closeSession()
                mediaServer.stop()
                _routeState.update {
                    it.copy(
                        activeDevice = OutputDevice.LOCAL_PHONE,
                        connectionState = CastConnectionState.DISCONNECTED,
                        errorMessage = null,
                    )
                }
            }
            throw ce
        }
    }

    private suspend fun startStreamOnRenderer(
        device: OutputDevice,
        token: String,
        format: LiveStreamFormat,
        expectedGen: Long,
    ): Result<Unit> {
        val streamUrl = mediaServer.buildLiveStreamUrl(token, format)
        val didl = DlnaXmlParser.buildLiveDidlLiteMetadata(lastMetadata, streamUrl, format)
        expectedFormat = format
        currentLiveUrl = streamUrl
        currentDidlXml = didl

        val outcome = withTimeoutOrNull(connectTimeoutMs) {
            if (!isOperationCurrent(expectedGen)) return@withTimeoutOrNull Result.failure<Unit>(CancellationException())

            controlClient.stop(device)
            if (!isOperationCurrent(expectedGen)) return@withTimeoutOrNull Result.failure<Unit>(CancellationException())

            val setUriRes = controlClient.setTransportUri(device, streamUrl, didl)
            if (setUriRes.isFailure) return@withTimeoutOrNull setUriRes
            if (!isOperationCurrent(expectedGen)) return@withTimeoutOrNull Result.failure<Unit>(CancellationException())

            val playRes = controlClient.play(device)
            if (playRes.isFailure) return@withTimeoutOrNull playRes

            while (isOperationCurrent(expectedGen)) {
                val state = controlClient.getTransportInfo(device).getOrNull()
                if (state == RemoteTransportState.PLAYING) {
                    return@withTimeoutOrNull Result.success(Unit)
                }
                delay(connectPollIntervalMs)
            }
            Result.failure(CancellationException())
        }

        return outcome ?: Result.failure(IllegalStateException("Timed out connecting to ${device.name}"))
    }

    suspend fun playRemote(): Result<Unit> {
        val device = activeRemoteDeviceOrNull() ?: return Result.failure(IllegalStateException("No remote device"))
        intentionalPause = false
        pcmStreamHub.paused = false
        val res = controlClient.play(device)
        if (res.isFailure) {
            _routeState.update { it.copy(errorMessage = res.exceptionOrNull()?.message) }
        }
        return res
    }

    suspend fun pauseRemote(): Result<Unit> {
        val device = activeRemoteDeviceOrNull() ?: return Result.failure(IllegalStateException("No remote device"))
        intentionalPause = true
        pcmStreamHub.paused = true
        val res = controlClient.pause(device)
        if (res.isFailure) {
            _routeState.update { it.copy(errorMessage = res.exceptionOrNull()?.message) }
        }
        return res
    }

    /**
     * Synchronizes transport pause/play when playback state changes outside [togglePlayPause]
     * (e.g. notification shade or lockscreen controls).
     */
    suspend fun syncPlaybackState(isPlaying: Boolean) {
        if (!isCasting) return
        val shouldPause = !isPlaying
        pcmStreamHub.paused = shouldPause
        if (intentionalPause != shouldPause) {
            if (shouldPause) pauseRemote() else playRemote()
        }
    }

    suspend fun setRemoteVolume(volume: Int): Result<Unit> {
        val device = activeRemoteDeviceOrNull() ?: return Result.failure(IllegalStateException("No remote device"))
        val clamped = volume.coerceIn(0, 100)
        _routeState.update { it.copy(remoteVolume = clamped, isRemoteMuted = clamped == 0) }
        return controlClient.setVolume(device, clamped)
    }

    suspend fun setRemoteMuted(mute: Boolean): Result<Unit> {
        val device = activeRemoteDeviceOrNull() ?: return Result.failure(IllegalStateException("No remote device"))
        _routeState.update { it.copy(isRemoteMuted = mute) }
        return controlClient.setMute(device, mute)
    }

    suspend fun toggleRemoteMute(): Result<Unit> {
        return setRemoteMuted(!_routeState.value.isRemoteMuted)
    }

    /**
     * Immediately stops streaming, shuts down the HTTP bridge, resets routing to the local device,
     * sends Stop asynchronously, and returns the estimated [speakerDelayMs] so local playback
     * can seek back to match what the listener actually heard on the speaker.
     */
    suspend fun disconnect(sendStop: Boolean = true): Long {
        val delayEstimate = _routeState.value.speakerDelayMs
        val prevDevice = _routeState.value.activeDevice
        synchronized(opLock) {
            sessionGeneration.incrementAndGet()
            stopPollingLocked()
            activeOpJob?.cancel()
            activeOpJob = null
        }

        intentionalPause = false
        currentLiveUrl = null
        currentDidlXml = null
        pcmStreamHub.closeSession()
        mediaServer.stop()

        _routeState.update { state ->
            state.copy(
                activeDevice = OutputDevice.LOCAL_PHONE,
                connectionState = CastConnectionState.DISCONNECTED,
                isRemoteMuted = false,
                errorMessage = null,
                speakerDelayMs = 0L,
            )
        }

        if (sendStop && prevDevice.protocol != CastProtocol.LOCAL) {
            scope.launch {
                withTimeoutOrNull(1500L) {
                    controlClient.stop(prevDevice)
                }
            }
        }
        return delayEstimate
    }

    suspend fun disconnectAndAwaitStop() {
        val prevDevice = _routeState.value.activeDevice
        disconnect(sendStop = false)
        if (prevDevice.protocol != CastProtocol.LOCAL) {
            withTimeoutOrNull(1400L) {
                controlClient.stop(prevDevice)
            }
        }
    }

    private fun isOperationCurrent(expectedSessionGen: Long): Boolean {
        return sessionGeneration.get() == expectedSessionGen
    }

    private fun stopPollingLocked() {
        pollGeneration.incrementAndGet()
        pollJob?.cancel()
        pollJob = null
    }

    private fun startPollingLocked(device: OutputDevice, expectedSessionGen: Long) {
        val gen = pollGeneration.incrementAndGet()
        pollJob = scope.launch {
            var consecutiveFailures = 0
            var recoveryAttempted = false
            while (isActive && pollGeneration.get() == gen && sessionGeneration.get() == expectedSessionGen) {
                delay(pollIntervalMs)
                if (pollGeneration.get() != gen || sessionGeneration.get() != expectedSessionGen) break

                val transportRes = controlClient.getTransportInfo(device)
                if (pollGeneration.get() != gen || sessionGeneration.get() != expectedSessionGen) break

                if (transportRes.isFailure) {
                    consecutiveFailures++
                    if (consecutiveFailures >= 3) {
                        val reason = transportRes.exceptionOrNull()?.message ?: "Connection unreachable"
                        handleConnectionLost(device.name, reason, expectedSessionGen)
                        break
                    }
                    continue
                }

                consecutiveFailures = 0
                val transport = transportRes.getOrNull() ?: RemoteTransportState.UNKNOWN

                val posRes = controlClient.getPositionInfo(device)
                if (pollGeneration.get() != gen || sessionGeneration.get() != expectedSessionGen) break
                val pos = posRes.getOrNull()

                when (transport) {
                    RemoteTransportState.PLAYING -> {
                        recoveryAttempted = false
                        if (pos != null && pos.relTimeMs > 0L) {
                            val writtenMs = pcmStreamHub.streamMsWritten
                            if (writtenMs >= pos.relTimeMs) {
                                val sampleDelay = (writtenMs - pos.relTimeMs).coerceIn(0L, 5000L)
                                _routeState.update { state ->
                                    val prev = state.speakerDelayMs
                                    val ema = if (prev <= 0L) sampleDelay else ((prev * 7 + sampleDelay * 3) / 10)
                                    state.copy(speakerDelayMs = ema.coerceIn(0L, 5000L))
                                }
                            }
                        }
                    }
                    RemoteTransportState.PAUSED -> {
                        if (!intentionalPause) {
                            intentionalPause = true
                            pcmStreamHub.paused = true
                            onRemotePlayPauseRequested?.invoke(false)
                        }
                    }
                    RemoteTransportState.STOPPED, RemoteTransportState.NO_MEDIA -> {
                        if (!intentionalPause) {
                            if (!recoveryAttempted) {
                                recoveryAttempted = true
                                val url = currentLiveUrl
                                val didl = currentDidlXml
                                if (url != null && didl != null) {
                                    val setRes = controlClient.setTransportUri(device, url, didl)
                                    val playRes = if (setRes.isSuccess) controlClient.play(device) else setRes
                                    if (playRes.isFailure) {
                                        handleConnectionLost(device.name, "Playback stopped on speaker", expectedSessionGen)
                                        break
                                    }
                                }
                            } else {
                                handleConnectionLost(device.name, "Speaker stopped playback", expectedSessionGen)
                                break
                            }
                        }
                    }
                    RemoteTransportState.TRANSITIONING, RemoteTransportState.UNKNOWN -> Unit
                }
            }
        }
    }

    private fun handleConnectionLost(deviceName: String, reason: String, expectedSessionGen: Long) {
        synchronized(opLock) {
            if (sessionGeneration.get() != expectedSessionGen) return
            sessionGeneration.incrementAndGet()
            stopPollingLocked()
            activeOpJob = null
        }
        intentionalPause = false
        currentLiveUrl = null
        currentDidlXml = null
        pcmStreamHub.closeSession()
        mediaServer.stop()
        _routeState.update { state ->
            state.copy(
                activeDevice = OutputDevice.LOCAL_PHONE,
                connectionState = CastConnectionState.DISCONNECTED,
                isRemoteMuted = false,
                errorMessage = "Lost connection to $deviceName",
                speakerDelayMs = 0L,
            )
        }
        onRemoteConnectionLost?.invoke(deviceName, reason)
    }

    private fun activeRemoteDeviceOrNull(): OutputDevice? {
        val state = _routeState.value
        return if (state.isCasting && state.activeDevice.protocol != CastProtocol.LOCAL) {
            state.activeDevice
        } else {
            null
        }
    }
}
