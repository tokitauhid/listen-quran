package com.listenquran.player.cast

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    @Suppress("UNUSED_PARAMETER") testMarker: Any? = null,
) {
    @Inject
    constructor(
        discoveryClient: DeviceDiscoveryClient,
        controlClient: RemoteControlClient,
        mediaServer: LocalMediaHttpServer,
    ) : this(discoveryClient, controlClient, mediaServer, 500L, 350L, Dispatchers.IO, null)

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val stateMutex = Mutex()
    private val pollGeneration = AtomicLong(0L)

    private val _routeState = MutableStateFlow(OutputRouteState())
    val routeState: StateFlow<OutputRouteState> = _routeState.asStateFlow()

    val isCasting: Boolean
        get() = _routeState.value.isCasting

    var onRemotePositionUpdate: ((positionMs: Long, durationMs: Long) -> Unit)? = null
    var onRemotePlayStateChanged: ((isPlaying: Boolean, isBuffering: Boolean) -> Unit)? = null
    var onRemoteTrackEnded: (() -> Unit)? = null
    var onRemoteConnectionLost: ((deviceName: String, reason: String) -> Unit)? = null

    @Volatile
    private var pollJob: Job? = null

    @Volatile
    private var lastKnownPositionMs: Long = 0L

    @Volatile
    private var currentServedUri: String? = null

    @Volatile
    private var nextServedUri: String? = null

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
    ): Result<Unit> = stateMutex.withLock {
        if (device.protocol == CastProtocol.LOCAL) {
            disconnectLocked()
            return Result.success(Unit)
        }

        stopPollingLocked()
        val lanIp = discoveryClient.getLocalLanIpv4Address()
        if (lanIp == null) {
            val msg = "Connect to Wi-Fi to cast to network speakers"
            _routeState.update {
                it.copy(
                    connectionState = CastConnectionState.ERROR,
                    errorMessage = msg,
                )
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
            mediaServer.start(lanIp)
            mediaServer.clearMedia()
        } catch (e: Exception) {
            val msg = "Failed to start local cast stream server: ${e.message}"
            _routeState.update {
                it.copy(
                    activeDevice = OutputDevice.LOCAL_PHONE,
                    connectionState = CastConnectionState.ERROR,
                    errorMessage = msg,
                )
            }
            return Result.failure(e)
        }

        lastKnownPositionMs = currentTrack?.startPositionMs?.coerceAtLeast(0L) ?: 0L
        hasObservedPlayingForCurrentTrack = false
        currentServedUri = null
        nextServedUri = null

        if (currentTrack != null) {
            val loadRes = pushTrackInternal(device, currentTrack, nextTrack, autoPlay)
            if (loadRes.isFailure) {
                mediaServer.stop()
                val errMsg = loadRes.exceptionOrNull()?.message ?: "Failed to start playback on ${device.name}"
                _routeState.update {
                    it.copy(
                        activeDevice = OutputDevice.LOCAL_PHONE,
                        connectionState = CastConnectionState.ERROR,
                        errorMessage = errMsg,
                    )
                }
                return loadRes
            }
        }

        val initialVolume = controlClient.getVolume(device).getOrNull() ?: _routeState.value.remoteVolume
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

        startPollingLocked(device)
        return Result.success(Unit)
    }

    suspend fun loadTrackOnActiveDevice(
        currentTrack: RemoteTrackDescriptor,
        nextTrack: RemoteTrackDescriptor? = null,
        autoPlay: Boolean = true,
    ): Result<Unit> = stateMutex.withLock {
        val state = _routeState.value
        val device = state.activeDevice
        if (!state.isCasting || device.protocol == CastProtocol.LOCAL) {
            return Result.failure(IllegalStateException("Not connected to a remote output device"))
        }

        stopPollingLocked()
        mediaServer.clearMedia()
        hasObservedPlayingForCurrentTrack = false
        lastKnownPositionMs = currentTrack.startPositionMs.coerceAtLeast(0L)

        val res = pushTrackInternal(device, currentTrack, nextTrack, autoPlay)
        if (res.isSuccess) {
            startPollingLocked(device)
        } else {
            _routeState.update { it.copy(errorMessage = res.exceptionOrNull()?.message) }
        }
        return res
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

    suspend fun disconnect(): Long = stateMutex.withLock {
        disconnectLocked()
    }

    private suspend fun disconnectLocked(): Long {
        val lastPos = lastKnownPositionMs
        stopPollingLocked()
        val device = _routeState.value.activeDevice
        if (device.protocol != CastProtocol.LOCAL) {
            withTimeoutOrNull(1500L) {
                controlClient.stop(device)
            }
        }
        mediaServer.stop()
        currentServedUri = null
        nextServedUri = null
        hasObservedPlayingForCurrentTrack = false
        _routeState.update { state ->
            state.copy(
                activeDevice = OutputDevice.LOCAL_PHONE,
                connectionState = CastConnectionState.DISCONNECTED,
                isRemoteMuted = false,
                errorMessage = null,
            )
        }
        return lastPos
    }

    private suspend fun pushTrackInternal(
        device: OutputDevice,
        currentTrack: RemoteTrackDescriptor,
        nextTrack: RemoteTrackDescriptor?,
        autoPlay: Boolean,
    ): Result<Unit> {
        val resolvedCurrent = resolveAndRegister(currentTrack)
            ?: return Result.failure(IllegalStateException("No playable audio source for ${currentTrack.title}"))

        currentServedUri = resolvedCurrent.first
        val setUriRes = controlClient.setTransportUri(device, resolvedCurrent.first, resolvedCurrent.second)
        if (setUriRes.isFailure) return setUriRes

        if (autoPlay) {
            val playRes = controlClient.play(device)
            if (playRes.isFailure) return playRes

            if (currentTrack.startPositionMs > 1500L) {
                repeat(6) {
                    if (seekDelayMs > 0L) delay(seekDelayMs)
                    val tState = controlClient.getTransportInfo(device).getOrNull()
                    if (tState == RemoteTransportState.PLAYING) {
                        controlClient.seek(device, currentTrack.startPositionMs)
                        return@repeat
                    }
                }
                // Best-effort seek even if renderer did not report PLAYING within initial window
                controlClient.seek(device, currentTrack.startPositionMs)
            }
        }

        if (nextTrack != null) {
            val resolvedNext = resolveAndRegister(nextTrack)
            if (resolvedNext != null) {
                nextServedUri = resolvedNext.first
                controlClient.setNextTransportUri(device, resolvedNext.first, resolvedNext.second)
            }
        } else {
            nextServedUri = null
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

    private fun startPollingLocked(device: OutputDevice) {
        val gen = pollGeneration.incrementAndGet()
        pollJob = scope.launch {
            var consecutiveFailures = 0
            while (isActive && pollGeneration.get() == gen) {
                delay(pollIntervalMs)
                if (pollGeneration.get() != gen) break

                val transportRes = controlClient.getTransportInfo(device)
                val posRes = controlClient.getPositionInfo(device)

                if (pollGeneration.get() != gen) break

                if (transportRes.isFailure && posRes.isFailure) {
                    consecutiveFailures++
                    if (consecutiveFailures >= 3) {
                        val reason = transportRes.exceptionOrNull()?.message
                            ?: posRes.exceptionOrNull()?.message
                            ?: "Connection unreachable"
                        handleConnectionLost(device.name, reason)
                        break
                    }
                    continue
                }
                consecutiveFailures = 0

                val transport = transportRes.getOrNull() ?: RemoteTransportState.UNKNOWN
                val pos = posRes.getOrNull()

                val expectedNextUri = nextServedUri
                if (expectedNextUri != null && pos?.trackUri == expectedNextUri) {
                    currentServedUri = expectedNextUri
                    nextServedUri = null
                    hasObservedPlayingForCurrentTrack = true
                    onRemoteTrackEnded?.invoke()
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
                            onRemoteTrackEnded?.invoke()
                        }
                    }
                    RemoteTransportState.UNKNOWN -> Unit
                }
            }
        }
    }

    private suspend fun handleConnectionLost(deviceName: String, reason: String) {
        stateMutex.withLock {
            stopPollingLocked()
            mediaServer.stop()
            currentServedUri = null
            nextServedUri = null
            hasObservedPlayingForCurrentTrack = false
            _routeState.update { state ->
                state.copy(
                    activeDevice = OutputDevice.LOCAL_PHONE,
                    connectionState = CastConnectionState.DISCONNECTED,
                    isRemoteMuted = false,
                    errorMessage = "Connection to $deviceName lost",
                )
            }
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
