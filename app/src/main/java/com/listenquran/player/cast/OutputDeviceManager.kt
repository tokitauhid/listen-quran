package com.listenquran.player.cast

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OutputDeviceManager internal constructor(
    private val discoveryClient: DeviceDiscoveryClient,
    private val controlClient: RemoteControlClient,
    private val mediaServer: LocalMediaHttpServer,
    private val pollIntervalMs: Long = 500L,
    private val seekDelayMs: Long = 350L,
    private val connectTimeoutMs: Long = 10_000L,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    @Suppress("UNUSED_PARAMETER") testMarker: Any? = null,
) {
    @Inject
    constructor(
        discoveryClient: DeviceDiscoveryClient,
        controlClient: RemoteControlClient,
        mediaServer: LocalMediaHttpServer,
    ) : this(discoveryClient, controlClient, mediaServer, 500L, 350L, 10_000L, Dispatchers.IO, null)

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val opLock = Any()
    private val pollGeneration = AtomicLong(0L)
    private val sessionGeneration = AtomicLong(0L)
    private val trackGeneration = AtomicLong(0L)

    private val _routeState = MutableStateFlow(OutputRouteState())
    val routeState: StateFlow<OutputRouteState> = _routeState.asStateFlow()

    val isCasting: Boolean
        get() = _routeState.value.isCasting

    var onRemotePositionUpdate: ((positionMs: Long, durationMs: Long) -> Unit)? = null
    var onRemotePlayStateChanged: ((isPlaying: Boolean, isBuffering: Boolean) -> Unit)? = null
    var onRemoteTrackEnded: ((advancedToNextUri: Boolean) -> Unit)? = null
    var onRemoteConnectionLost: ((deviceName: String, reason: String) -> Unit)? = null

    @Volatile
    private var activeOpJob: Job? = null

    @Volatile
    private var pollJob: Job? = null

    @Volatile
    private var lastKnownPositionMs: Long = 0L

    @Volatile
    private var currentServedUri: String? = null

    @Volatile
    private var currentMediaKey: String? = null

    @Volatile
    private var nextServedUri: String? = null

    @Volatile
    private var nextMediaKey: String? = null

    @Volatile
    private var hasObservedPlayingForCurrentTrack: Boolean = false

    suspend fun scanForDevices() {
        _routeState.update { it.copy(isScanning = true, errorMessage = null) }
        val discovered = runCatching { discoveryClient.discoverDevices(3000L) }.getOrDefault(emptyList())
        _routeState.update { state ->
            val merged = if (state.activeDevice.protocol != CastProtocol.LOCAL &&
                discovered.none { it.id == state.activeDevice.id }
            ) {
                (listOf(state.activeDevice) + discovered).distinctBy { it.id }
            } else {
                discovered
            }
            state.copy(
                discoveredDevices = merged.sortedBy { it.name.lowercase() },
                isScanning = false,
            )
        }
    }

    suspend fun connectToDevice(
        device: OutputDevice,
        currentTrack: RemoteTrackDescriptor?,
        nextTrack: RemoteTrackDescriptor? = null,
        autoPlay: Boolean = true,
    ): Result<Unit> {
        if (device.protocol == CastProtocol.LOCAL) {
            disconnect()
            return Result.success(Unit)
        }

        val callerJob = currentCoroutineContext()[Job]
        val myGen: Long
        val myTrackGen: Long
        synchronized(opLock) {
            myGen = sessionGeneration.incrementAndGet()
            myTrackGen = trackGeneration.incrementAndGet()
            activeOpJob?.cancel()
            activeOpJob = callerJob
            stopPollingLocked()
        }

        val lanIp = discoveryClient.getLocalLanIpv4Address()
        if (lanIp == null) {
            val msg = "Connect to Wi-Fi to cast to network speakers"
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

        _routeState.update {
            it.copy(
                activeDevice = device,
                connectionState = CastConnectionState.CONNECTING,
                errorMessage = null,
            )
        }

        try {
            mediaServer.start(lanIp, forceRestart = true)
            mediaServer.clearMedia()
        } catch (e: Exception) {
            if (sessionGeneration.get() == myGen) {
                val msg = "Failed to start local cast stream server: ${e.message}"
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

        lastKnownPositionMs = currentTrack?.startPositionMs?.coerceAtLeast(0L) ?: 0L
        hasObservedPlayingForCurrentTrack = false
        currentServedUri = null
        currentMediaKey = null
        nextServedUri = null
        nextMediaKey = null

        try {
            val handshakeOk: Result<Unit>? = withTimeoutOrNull(connectTimeoutMs) {
                if (currentTrack != null) {
                    val loadRes = pushTrackInternal(device, currentTrack, nextTrack, autoPlay, myGen, myTrackGen)
                    if (loadRes.isFailure) return@withTimeoutOrNull loadRes
                }
                Result.success(Unit)
            }

            if (!isOperationCurrent(myGen, myTrackGen)) {
                synchronized(opLock) {
                    if (activeOpJob == callerJob) activeOpJob = null
                }
                return Result.failure(CancellationException("Cast connection superseded or cancelled"))
            }

            if (handshakeOk == null) {
                mediaServer.stop()
                val timeoutMsg = "Timed out connecting to ${device.name}"
                synchronized(opLock) {
                    if (activeOpJob == callerJob) activeOpJob = null
                }
                _routeState.update {
                    it.copy(
                        activeDevice = OutputDevice.LOCAL_PHONE,
                        connectionState = CastConnectionState.ERROR,
                        errorMessage = timeoutMsg,
                    )
                }
                return Result.failure(IllegalStateException(timeoutMsg))
            }

            if (handshakeOk.isFailure) {
                mediaServer.stop()
                val errMsg = handshakeOk.exceptionOrNull()?.message ?: "Failed to start playback on ${device.name}"
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
                return handshakeOk
            }

            val initialVolume = controlClient.getVolume(device).getOrNull() ?: _routeState.value.remoteVolume
            if (!isOperationCurrent(myGen, myTrackGen)) {
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

    suspend fun loadTrackOnActiveDevice(
        currentTrack: RemoteTrackDescriptor,
        nextTrack: RemoteTrackDescriptor? = null,
        autoPlay: Boolean = true,
    ): Result<Unit> {
        val callerJob = currentCoroutineContext()[Job]
        val device: OutputDevice
        val myGen: Long
        val myTrackGen: Long
        synchronized(opLock) {
            val state = _routeState.value
            if (!state.isCasting || state.activeDevice.protocol == CastProtocol.LOCAL) {
                return Result.failure(IllegalStateException("Not connected to a remote output device"))
            }
            device = state.activeDevice
            myGen = sessionGeneration.get()
            myTrackGen = trackGeneration.incrementAndGet()
            activeOpJob?.cancel()
            activeOpJob = callerJob
            stopPollingLocked()
        }

        hasObservedPlayingForCurrentTrack = false
        lastKnownPositionMs = currentTrack.startPositionMs.coerceAtLeast(0L)

        return try {
            val res = withTimeoutOrNull(connectTimeoutMs) {
                pushTrackInternal(device, currentTrack, nextTrack, autoPlay, myGen, myTrackGen)
            } ?: Result.failure(IllegalStateException("Timed out loading track on ${device.name}"))

            if (!isOperationCurrent(myGen, myTrackGen)) {
                return Result.failure(CancellationException("Cast session or track superseded"))
            }

            if (res.isSuccess) {
                synchronized(opLock) {
                    if (isOperationCurrent(myGen, myTrackGen)) {
                        if (activeOpJob == callerJob) activeOpJob = null
                        startPollingLocked(device, myGen)
                    }
                }
            } else {
                synchronized(opLock) {
                    if (activeOpJob == callerJob) activeOpJob = null
                }
                _routeState.update { it.copy(errorMessage = res.exceptionOrNull()?.message) }
            }
            res
        } catch (ce: CancellationException) {
            synchronized(opLock) {
                if (activeOpJob == callerJob) activeOpJob = null
            }
            throw ce
        }
    }

    suspend fun updateNextTrackOnActiveDevice(nextTrack: RemoteTrackDescriptor?): Result<Unit> {
        val device = activeRemoteDeviceOrNull()
            ?: return Result.failure(IllegalStateException("Not connected to a remote output device"))
        if (nextTrack == null) {
            nextServedUri = null
            nextMediaKey = null
            currentMediaKey?.let { mediaServer.retainOnly(setOf(it)) }
            return Result.success(Unit)
        }
        val resolvedNext = resolveAndRegister(nextTrack)
            ?: return Result.failure(IllegalStateException("No playable audio source for ${nextTrack.title}"))
        val keepKeys = buildSet {
            currentMediaKey?.let { add(it) }
            add(nextTrack.mediaKey)
        }
        mediaServer.retainOnly(keepKeys)
        nextServedUri = resolvedNext.first
        nextMediaKey = nextTrack.mediaKey
        return controlClient.setNextTransportUri(device, resolvedNext.first, resolvedNext.second)
    }

    suspend fun playRemote(): Result<Unit> {
        val device = activeRemoteDeviceOrNull() ?: return Result.failure(IllegalStateException("No remote device"))
        val res = controlClient.play(device)
        if (res.isSuccess) {
            onRemotePlayStateChanged?.invoke(true, false)
        } else {
            _routeState.update { it.copy(errorMessage = res.exceptionOrNull()?.message) }
        }
        return res
    }

    suspend fun pauseRemote(): Result<Unit> {
        val device = activeRemoteDeviceOrNull() ?: return Result.failure(IllegalStateException("No remote device"))
        val res = controlClient.pause(device)
        if (res.isSuccess) {
            onRemotePlayStateChanged?.invoke(false, false)
        } else {
            _routeState.update { it.copy(errorMessage = res.exceptionOrNull()?.message) }
        }
        return res
    }

    suspend fun seekRemote(positionMs: Long): Result<Unit> {
        val device = activeRemoteDeviceOrNull() ?: return Result.failure(IllegalStateException("No remote device"))
        val clamped = positionMs.coerceAtLeast(0L)
        lastKnownPositionMs = clamped
        val res = controlClient.seek(device, clamped)
        if (res.isFailure) {
            _routeState.update { it.copy(errorMessage = res.exceptionOrNull()?.message) }
        }
        return res
    }

    suspend fun setRemoteVolume(volume: Int): Result<Unit> {
        val device = activeRemoteDeviceOrNull() ?: return Result.failure(IllegalStateException("No remote device"))
        val clamped = volume.coerceIn(0, 100)
        val res = controlClient.setVolume(device, clamped)
        if (res.isSuccess) {
            _routeState.update { it.copy(remoteVolume = clamped, isRemoteMuted = clamped == 0) }
        }
        return res
    }

    suspend fun toggleRemoteMute(): Result<Unit> {
        val device = activeRemoteDeviceOrNull() ?: return Result.failure(IllegalStateException("No remote device"))
        val targetMute = !_routeState.value.isRemoteMuted
        val res = controlClient.setMute(device, targetMute)
        if (res.isSuccess) {
            _routeState.update { it.copy(isRemoteMuted = targetMute) }
        }
        return res
    }

    /**
     * Immediately cancels any pending connect/poll work, shuts down the local HTTP bridge,
     * resets routing state to the phone, and sends Stop asynchronously to avoid freezing UI.
     */
    suspend fun disconnect(): Long {
        val lastPos = lastKnownPositionMs
        val prevDevice = _routeState.value.activeDevice
        synchronized(opLock) {
            sessionGeneration.incrementAndGet()
            stopPollingLocked()
            activeOpJob?.cancel()
            activeOpJob = null
        }

        mediaServer.stop()
        currentServedUri = null
        currentMediaKey = null
        nextServedUri = null
        nextMediaKey = null
        hasObservedPlayingForCurrentTrack = false

        _routeState.update { state ->
            state.copy(
                activeDevice = OutputDevice.LOCAL_PHONE,
                connectionState = CastConnectionState.DISCONNECTED,
                isRemoteMuted = false,
                errorMessage = null,
            )
        }

        if (prevDevice.protocol != CastProtocol.LOCAL) {
            scope.launch {
                withTimeoutOrNull(1500L) {
                    controlClient.stop(prevDevice)
                }
            }
        }
        return lastPos
    }

    private fun isOperationCurrent(expectedSessionGen: Long, expectedTrackGen: Long): Boolean {
        return sessionGeneration.get() == expectedSessionGen && trackGeneration.get() == expectedTrackGen
    }

    private suspend fun pushTrackInternal(
        device: OutputDevice,
        currentTrack: RemoteTrackDescriptor,
        nextTrack: RemoteTrackDescriptor?,
        autoPlay: Boolean,
        expectedSessionGen: Long,
        expectedTrackGen: Long,
    ): Result<Unit> {
        val resolvedCurrent = resolveAndRegister(currentTrack)
            ?: return Result.failure(IllegalStateException("No playable audio source for ${currentTrack.title}"))

        if (!isOperationCurrent(expectedSessionGen, expectedTrackGen)) {
            return Result.failure(CancellationException("Cast session cancelled"))
        }

        // Reset renderer pipeline to STOPPED before setting a new URI so UPnP renderers
        // (notably gmediarender) never treat Play() as a no-op or resume a paused/stalled stream.
        // Ignore errors because idle renderers in NO_MEDIA_PRESENT legally return UPnP fault 701.
        controlClient.stop(device)

        if (!isOperationCurrent(expectedSessionGen, expectedTrackGen)) {
            return Result.failure(CancellationException("Cast session cancelled"))
        }

        val setUriRes = controlClient.setTransportUri(device, resolvedCurrent.first, resolvedCurrent.second)
        if (setUriRes.isFailure) return setUriRes

        if (!isOperationCurrent(expectedSessionGen, expectedTrackGen)) {
            return Result.failure(CancellationException("Cast session cancelled"))
        }

        currentServedUri = resolvedCurrent.first
        currentMediaKey = currentTrack.mediaKey
        nextServedUri = null
        nextMediaKey = null
        mediaServer.retainOnly(setOf(currentTrack.mediaKey))

        if (autoPlay) {
            val shouldSeek = currentTrack.startPositionMs > 1500L &&
                (currentTrack.durationMs <= 0L ||
                    currentTrack.durationMs <= currentTrack.startPositionMs ||
                    currentTrack.startPositionMs < currentTrack.durationMs - 2000L)
            val prePlayPos = if (shouldSeek) controlClient.getPositionInfo(device).getOrNull() else null

            val playRes = controlClient.play(device)
            if (playRes.isFailure) return playRes

            if (shouldSeek) {
                val hasStalePrePlay = prePlayPos != null &&
                    (prePlayPos.trackDurationMs > 0L || prePlayPos.relTimeMs > 0L)
                var sought = false
                for (attempt in 0 until 12) {
                    if (!isOperationCurrent(expectedSessionGen, expectedTrackGen)) {
                        return Result.failure(CancellationException("Cast session cancelled"))
                    }
                    if (seekDelayMs > 0L) delay(seekDelayMs)
                    if (!isOperationCurrent(expectedSessionGen, expectedTrackGen)) {
                        return Result.failure(CancellationException("Cast session cancelled"))
                    }
                    val tState = controlClient.getTransportInfo(device).getOrNull()
                    if (tState == RemoteTransportState.PLAYING) {
                        val pos = controlClient.getPositionInfo(device).getOrNull()
                        val readyForSeek = pos != null &&
                            (pos.trackDurationMs > 0L || pos.relTimeMs > 0L) &&
                            (!hasStalePrePlay || pos.relTimeMs != prePlayPos?.relTimeMs || pos.trackDurationMs != prePlayPos?.trackDurationMs)
                        if (readyForSeek) {
                            controlClient.seek(device, currentTrack.startPositionMs)
                            sought = true
                            break
                        }
                    }
                }
                if (!sought && isOperationCurrent(expectedSessionGen, expectedTrackGen)) {
                    controlClient.seek(device, currentTrack.startPositionMs)
                }
            }
        }

        if (!isOperationCurrent(expectedSessionGen, expectedTrackGen)) {
            return Result.failure(CancellationException("Cast session cancelled"))
        }

        if (nextTrack != null) {
            val resolvedNext = resolveAndRegister(nextTrack)
            if (resolvedNext != null) {
                nextServedUri = resolvedNext.first
                nextMediaKey = nextTrack.mediaKey
                controlClient.setNextTransportUri(device, resolvedNext.first, resolvedNext.second)
            }
        } else {
            nextServedUri = null
            nextMediaKey = null
        }

        return Result.success(Unit)
    }

    private fun resolveAndRegister(track: RemoteTrackDescriptor): Pair<String, String>? {
        val localFile = track.localFilePath?.let { File(it) }?.takeIf { it.exists() && it.isFile }
        val source: CastMediaSource = when {
            localFile != null -> CastMediaSource.LocalFile(localFile)
            !track.streamUrl.isNullOrBlank() && track.streamUrl.startsWith("file://") -> {
                val f = File(track.streamUrl.removePrefix("file://"))
                if (f.exists()) CastMediaSource.LocalFile(f) else return null
            }
            !track.streamUrl.isNullOrBlank() -> CastMediaSource.RemoteHttpProxy(track.streamUrl)
            else -> return null
        }

        val servedUrl = mediaServer.registerMedia(track.mediaKey, source)
        val didl = DlnaXmlParser.buildDidlLiteMetadata(
            title = track.title,
            artist = track.artist,
            album = track.album,
            streamUrl = servedUrl,
            durationMs = track.durationMs.takeIf { it > 0L },
            artworkUrl = track.artworkUrl,
        )
        return servedUrl to didl
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

                val posRes = controlClient.getPositionInfo(device)
                if (pollGeneration.get() != gen || sessionGeneration.get() != expectedSessionGen) break

                consecutiveFailures = 0
                val transport = transportRes.getOrNull() ?: RemoteTransportState.UNKNOWN
                val pos = posRes.getOrNull()

                val expectedNextUri = nextServedUri
                if (expectedNextUri != null && pos?.trackUri == expectedNextUri) {
                    currentServedUri = expectedNextUri
                    currentMediaKey = nextMediaKey
                    nextServedUri = null
                    nextMediaKey = null
                    hasObservedPlayingForCurrentTrack = true
                    onRemoteTrackEnded?.invoke(true)
                    continue
                }

                when (transport) {
                    RemoteTransportState.PLAYING -> {
                        hasObservedPlayingForCurrentTrack = true
                        onRemotePlayStateChanged?.invoke(true, false)
                        if (pos != null) {
                            lastKnownPositionMs = pos.relTimeMs
                            onRemotePositionUpdate?.invoke(pos.relTimeMs, pos.trackDurationMs)
                        }
                    }
                    RemoteTransportState.TRANSITIONING -> {
                        onRemotePlayStateChanged?.invoke(true, true)
                    }
                    RemoteTransportState.PAUSED -> {
                        onRemotePlayStateChanged?.invoke(false, false)
                        if (pos != null) {
                            lastKnownPositionMs = pos.relTimeMs
                            onRemotePositionUpdate?.invoke(pos.relTimeMs, pos.trackDurationMs)
                        }
                    }
                    RemoteTransportState.STOPPED, RemoteTransportState.NO_MEDIA -> {
                        onRemotePlayStateChanged?.invoke(false, false)
                        val dur = pos?.trackDurationMs ?: 0L
                        val rel = pos?.relTimeMs ?: lastKnownPositionMs
                        val reachedEnd = hasObservedPlayingForCurrentTrack &&
                            (dur <= 0L || rel >= (dur - 3000L).coerceAtLeast(0L) || rel == 0L)
                        if (reachedEnd) {
                            hasObservedPlayingForCurrentTrack = false
                            onRemoteTrackEnded?.invoke(false)
                        }
                    }
                    RemoteTransportState.UNKNOWN -> Unit
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
        mediaServer.stop()
        currentServedUri = null
        currentMediaKey = null
        nextServedUri = null
        nextMediaKey = null
        hasObservedPlayingForCurrentTrack = false
        _routeState.update { state ->
            state.copy(
                activeDevice = OutputDevice.LOCAL_PHONE,
                connectionState = CastConnectionState.DISCONNECTED,
                isRemoteMuted = false,
                errorMessage = "Connection to $deviceName lost",
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
