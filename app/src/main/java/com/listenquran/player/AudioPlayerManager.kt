package com.listenquran.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaBrowser
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.listenquran.R
import com.listenquran.data.repository.QuranRepository
import com.listenquran.data.repository.ReciterRepository
import com.listenquran.data.repository.StatsRepository
import com.listenquran.domain.model.*
import com.listenquran.player.cast.OutputDevice
import com.listenquran.player.cast.OutputDeviceManager
import com.listenquran.player.cast.OutputRouteState
import com.listenquran.player.cast.RemoteTrackDescriptor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@androidx.compose.runtime.Immutable
data class PlaybackState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentSurah: Surah? = null,
    val currentReciter: Reciter? = null,
    val currentAyahNumber: Int = 1,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val verseTimings: List<VerseTiming> = emptyList(),
    val isSyncAvailable: Boolean = false,
    val hasWordTimings: Boolean = false,
    val isHifzActive: Boolean = false,
    val hifzConfig: HifzLoopConfig? = null,
    val hifzCurrentLoop: Int = 0,
    val sleepTimerRemainingSeconds: Long? = null,
    val stopAtEndOfSurah: Boolean = false,
    val sleepTimerMode: SleepTimerMode = SleepTimerMode.Off,
    val queue: List<QueueItem> = emptyList(),
    val isShuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val isRadio: Boolean = false,
    val currentRadioStation: RadioStation? = null,
    val radioError: String? = null,
    val lastSurahBeforeRadio: Surah? = null,
    val lastReciterBeforeRadio: Reciter? = null,
    val activeStartAyah: Int? = null,
    val activeEndAyah: Int? = null,
)

@Singleton
class AudioPlayerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val quranRepository: QuranRepository,
    private val reciterRepository: ReciterRepository,
    private val statsRepository: StatsRepository,
    private val downloadRepository: com.listenquran.data.repository.QuranDownloadRepository,
    private val khatamTrackerRepository: com.listenquran.data.repository.khatam.KhatamTrackerRepository,
    val queueManager: PlaybackQueueManager,
    val syncTracker: PlaybackSyncTracker,
    val outputDeviceManager: OutputDeviceManager,
    private val exoPlayerProvider: Provider<ExoPlayer>,
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    val outputRouteState: StateFlow<OutputRouteState> = outputDeviceManager.routeState

    private val _castMessageEvent = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val castMessageEvent: SharedFlow<String> = _castMessageEvent.asSharedFlow()

    private val _sleepTimerState = MutableStateFlow(SleepTimerState())
    val sleepTimerState: StateFlow<SleepTimerState> = _sleepTimerState.asStateFlow()

    val syncState: StateFlow<PlaybackSyncState> = syncTracker.syncState
    val queueState: StateFlow<PlaybackQueueState> = queueManager.queueState

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    val currentWordIndex: StateFlow<Int> = syncTracker.currentWordIndex

    private var browserFuture: ListenableFuture<MediaBrowser>? = null
    private var browser: MediaBrowser? = null

    val mediaBrowser: MediaBrowser?
        get() = browser

    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var radioWatchdogJob: Job? = null
    private var sessionStartTime: Long = 0L
    private var lastSyncedMediaId: String? = null
    private val history = mutableListOf<QueueItem>()

    @Volatile
    private var pendingRemoteSeekMs: Long? = null

    @Volatile
    private var skipNextRemotePush: Boolean = false

    @Volatile
    private var isRestartingHifzLoop: Boolean = false

    @Volatile
    private var activeClipBounds: AyahClipBounds? = null

    @Volatile
    private var unclippedEndAyahMs: Long? = null

    @Volatile
    private var isAdvancingClipEnd: Boolean = false

    @Volatile
    private var clipRemoteHasEnteredWindow: Boolean = false

    @Volatile
    private var clipRemoteOutOfWindowPolls: Int = 0

    @Volatile
    private var clipRemoteSeekRetries: Int = 0

    private fun resetRemoteClipTracking() {
        clipRemoteHasEnteredWindow = false
        clipRemoteOutOfWindowPolls = 0
        clipRemoteSeekRetries = 0
    }

    private var castConnectJob: Job? = null

    private val isCastingOrConnecting: Boolean
        get() = outputDeviceManager.isCasting || outputDeviceManager.routeState.value.isConnecting

    init {
        connectBrowser()
        wireCastCallbacks()
        scope.launch {
            syncTracker.syncState.collect { sync ->
                _playbackState.update { current ->
                    current.copy(
                        currentAyahNumber = sync.currentAyahNumber,
                        isSyncAvailable = sync.isSyncAvailable,
                        hasWordTimings = sync.hasWordTimings,
                    )
                }
            }
        }
    }

    private fun wireCastCallbacks() {
        outputDeviceManager.onRemotePositionUpdate = { posMs, durationMs ->
            if (outputDeviceManager.isCasting) {
                scope.launch {
                    if (!outputDeviceManager.isCasting) return@launch
                    val clip = activeClipBounds
                    if (clip != null) {
                        val inWindow = posMs in (clip.startMs - 3_000L)..(clip.endMs + 2_000L)
                        if (inWindow) {
                            clipRemoteHasEnteredWindow = true
                            clipRemoteOutOfWindowPolls = 0
                        } else if (!clipRemoteHasEnteredWindow) {
                            clipRemoteOutOfWindowPolls++
                            if (clipRemoteOutOfWindowPolls >= 3 && clipRemoteSeekRetries < 3 && clip.startMs > 1_500L) {
                                clipRemoteOutOfWindowPolls = 0
                                clipRemoteSeekRetries++
                                scope.launch {
                                    outputDeviceManager.seekRemote(clip.startMs)
                                }
                            }
                            return@launch
                        }
                        val relPos = (posMs - clip.startMs).coerceIn(0L, clip.durationMs)
                        _currentPositionMs.value = relPos
                        if (clip.durationMs > 0L) {
                            _playbackState.update { it.copy(durationMs = clip.durationMs) }
                        }
                        syncTracker.updateExternalPosition(posMs)
                        checkHifzLoop(posMs)
                        if (clipRemoteHasEnteredWindow && posMs >= clip.endMs && !isAdvancingClipEnd) {
                            isAdvancingClipEnd = true
                            handleRemoteTrackEnded(advancedToNextUri = false)
                        }
                    } else {
                        _currentPositionMs.value = posMs
                        if (durationMs > 0L && (_playbackState.value.durationMs <= 0L || _playbackState.value.verseTimings.isEmpty())) {
                            _playbackState.update { it.copy(durationMs = durationMs) }
                        }
                        syncTracker.updateExternalPosition(posMs)
                        checkHifzLoop(posMs)
                        val unclippedEnd = unclippedEndAyahMs
                        if (unclippedEnd != null && posMs >= unclippedEnd && !isAdvancingClipEnd) {
                            isAdvancingClipEnd = true
                            handleRemoteTrackEnded(advancedToNextUri = false)
                        }
                    }
                }
            }
        }
        outputDeviceManager.onRemotePlayStateChanged = { isPlaying, isBuffering ->
            if (outputDeviceManager.isCasting) {
                scope.launch {
                    if (!outputDeviceManager.isCasting) return@launch
                    val wasPlaying = _playbackState.value.isPlaying
                    if (isPlaying && !wasPlaying) {
                        radioWatchdogJob?.cancel()
                        sessionStartTime = System.currentTimeMillis()
                    } else if (!isPlaying && wasPlaying) {
                        recordSessionDuration()
                    }
                    _playbackState.update {
                        it.copy(
                            isPlaying = isPlaying,
                            isBuffering = isBuffering,
                            radioError = if (isPlaying) null else it.radioError,
                        )
                    }
                }
            }
        }
        outputDeviceManager.onRemoteTrackEnded = { advancedToNextUri ->
            scope.launch {
                if (outputDeviceManager.isCasting) {
                    handleRemoteTrackEnded(advancedToNextUri)
                }
            }
        }
        outputDeviceManager.onRemoteConnectionLost = { deviceName, _ ->
            scope.launch {
                recordSessionDuration()
                _playbackState.update { it.copy(isPlaying = false, isBuffering = false) }
                _castMessageEvent.emit("Connection to $deviceName lost. Playback paused on phone.")
            }
        }
    }

    private fun connectBrowser() {
        if (browser != null) return
        try {
            val sessionToken = SessionToken(
                context,
                ComponentName(context, ListenQuranMediaService::class.java)
            )
            val future = MediaBrowser.Builder(context, sessionToken)
                .buildAsync()
            browserFuture = future
            future.addListener({
                try {
                    val controller = future.get()
                    browser = controller
                    setupControllerListener(controller)
                } catch (e: Exception) {
                    android.util.Log.e("AudioPlayerManager", "Failed to connect to MediaLibraryService", e)
                }
            }, ContextCompat.getMainExecutor(context))
        } catch (e: Exception) {
            android.util.Log.e("AudioPlayerManager", "Error creating MediaBrowser", e)
        }
    }

    internal fun withController(action: (MediaBrowser) -> Unit) {
        val b = browser
        if (b != null && b.isConnected) {
            action(b)
        } else {
            if (b != null && !b.isConnected) {
                browser = null
                browserFuture = null
            }
            ensureServiceRunning()
            browserFuture?.addListener({
                try {
                    val controller = browserFuture?.get()
                    if (controller != null && controller.isConnected) {
                        action(controller)
                    } else {
                        android.util.Log.w("AudioPlayerManager", "MediaBrowser not connected; reconnecting")
                        browser = null
                        browserFuture = null
                        connectBrowser()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AudioPlayerManager", "Failed to obtain MediaBrowser", e)
                }
            }, ContextCompat.getMainExecutor(context))
        }
    }

    private fun setupControllerListener(controller: MediaBrowser) {
        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (outputDeviceManager.isCasting || outputDeviceManager.routeState.value.isConnecting) {
                    if (isPlaying) {
                        controller.pause()
                    }
                    return
                }
                _playbackState.update { it.copy(isPlaying = isPlaying) }
                syncTracker.onPlayStateChanged(isPlaying) { browser }
                if (isPlaying) {
                    radioWatchdogJob?.cancel()
                    _playbackState.update { it.copy(radioError = null, isBuffering = false) }
                    sessionStartTime = System.currentTimeMillis()
                    startProgressTracker()

                    // Resilient fetch: If cold-start network lag caused timings to be empty initially,
                    // re-fetch them in background now that the player has buffered and network is live.
                    if (_playbackState.value.verseTimings.isEmpty() && !_playbackState.value.isRadio) {
                        val surah = _playbackState.value.currentSurah
                        val reciter = _playbackState.value.currentReciter
                        if (surah != null && reciter != null) {
                            scope.launch {
                                val audioData = quranRepository.getChapterAudio(reciter.id, surah.id)
                                val timings = audioData?.timestamps ?: emptyList()
                                if (timings.isNotEmpty()) {
                                    val clip = activeClipBounds
                                    syncTracker.setTimings(
                                        surahId = surah.id,
                                        reciterId = reciter.id,
                                        timings = timings,
                                        initialPosMs = browser?.currentPosition ?: 0L,
                                        positionOffsetMs = clip?.startMs ?: 0L,
                                    )
                                    if (clip == null && _playbackState.value.activeEndAyah != null) {
                                        val startAyahBound = _playbackState.value.activeStartAyah ?: 1
                                        unclippedEndAyahMs = resolveAyahClipBounds(
                                            surah.id,
                                            startAyahBound,
                                            _playbackState.value.activeEndAyah,
                                            timings,
                                        )?.endMs
                                    }
                                    _playbackState.update {
                                        it.copy(
                                            verseTimings = timings,
                                            isSyncAvailable = true,
                                            hasWordTimings = timings.any { t -> t.segments.isNotEmpty() }
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    recordSessionDuration()
                    stopProgressTracker()
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (outputDeviceManager.isCasting || outputDeviceManager.routeState.value.isConnecting) {
                    return
                }
                if (state == Player.STATE_ENDED) {
                    recordSessionDuration(isCompleted = true)
                }
                val isBuffering = state == Player.STATE_BUFFERING
                val rawDuration = controller.duration
                val duration = if (rawDuration > 0) {
                    rawDuration
                } else {
                    activeClipBounds?.durationMs?.takeIf { it > 0 }
                        ?: _playbackState.value.durationMs.takeIf { it > 0 }
                        ?: _playbackState.value.verseTimings.lastOrNull()?.timestampTo
                        ?: 0L
                }
                _playbackState.update {
                    it.copy(
                        isBuffering = isBuffering,
                        durationMs = duration
                    )
                }
                if (state == Player.STATE_READY) {
                    if (_playbackState.value.isRadio) {
                        radioWatchdogJob?.cancel()
                    }
                    _playbackState.update { it.copy(radioError = null, isBuffering = false) }
                }
                if (state == Player.STATE_ENDED) {
                    val playback = _playbackState.value
                    if (playback.isHifzActive && playback.hifzConfig != null) {
                        val config = playback.hifzConfig
                        val currentLoop = playback.hifzCurrentLoop
                        if (config.repeatCount == 0 || currentLoop < config.repeatCount) {
                            scope.launch {
                                withController { it.pause() }
                                if (config.delaySeconds > 0) {
                                    delay(config.delaySeconds * 1000L)
                                }
                                _playbackState.update { it.copy(hifzCurrentLoop = currentLoop + 1) }
                                seekToAyah(config.startAyah)
                                withController { it.play() }
                            }
                            return
                        } else {
                            stopHifzLoop()
                        }
                    }
                    if (_sleepTimerState.value.isRunning) {
                        val mode = _sleepTimerState.value.mode
                        if (mode is SleepTimerMode.EndOfSurah || mode is SleepTimerMode.EndOfPlaylist) {
                            syncTracker.flushKhatamProgress()
                            controller.pause()
                            cancelSleepTimer()
                            return
                        }
                    }
                    if (_playbackState.value.stopAtEndOfSurah) {
                        _playbackState.update { it.copy(stopAtEndOfSurah = false) }
                        controller.pause()
                    } else if (_playbackState.value.repeatMode == Player.REPEAT_MODE_ONE) {
                        controller.seekTo(0)
                        controller.play()
                    } else {
                        playNext()
                    }
                }
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                queueManager.rebuildQueueState(controller)
                rearmSleepTimerForNewQueue()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val isCompleted = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
                recordSessionDuration(isCompleted = isCompleted)
                if (controller.isPlaying) {
                    sessionStartTime = System.currentTimeMillis()
                }
                mediaItem?.let {
                    if (it.mediaId.startsWith("radio:")) {
                        syncFromRadioMediaItem(it)
                        extendRadioQueueIfNeeded()
                    } else {
                        syncFromMediaItem(
                            mediaItem = it,
                            seekToEmbeddedAyah = reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED
                        )
                    }
                }
                queueManager.rebuildQueueState(controller)

                if (outputDeviceManager.isCasting) {
                    if (skipNextRemotePush) {
                        skipNextRemotePush = false
                        val currentUnbounded = mediaItem != null &&
                            parseEndAyah(mediaItem.mediaId) == null &&
                            parseStartAyah(mediaItem.mediaId) <= 1
                        val upcomingNext = getNextMediaItemInTimeline(controller)
                        if (currentUnbounded && upcomingNext != null &&
                            parseStartAyah(upcomingNext.mediaId) <= 1 &&
                            parseEndAyah(upcomingNext.mediaId) == null
                        ) {
                            scope.launch {
                                val nextDesc = buildRemoteDescriptorForMediaItem(upcomingNext, 0L, 0L)
                                outputDeviceManager.updateNextTrackOnActiveDevice(nextDesc)
                            }
                        } else {
                            scope.launch { outputDeviceManager.updateNextTrackOnActiveDevice(null) }
                        }
                    } else if (mediaItem != null) {
                        resetRemoteClipTracking()
                        val explicitSeek = pendingRemoteSeekMs
                        pendingRemoteSeekMs = null
                        val clipConfig = mediaItem.clippingConfiguration
                        val isClipped = clipConfig.endPositionMs != C.TIME_END_OF_SOURCE
                        val clipStart = if (isClipped) clipConfig.startPositionMs.coerceAtLeast(0L) else 0L
                        val initialStartPos = explicitSeek ?: when {
                            isClipped && reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED ->
                                clipStart + controller.currentPosition.coerceAtLeast(0L)
                            isClipped -> clipStart
                            reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED ->
                                controller.currentPosition.coerceAtLeast(0L)
                            else -> 0L
                        }
                        val nextItem = getNextMediaItemInTimeline(controller)
                        scope.launch {
                            var resolvedStartPos = initialStartPos
                            if (resolvedStartPos == 0L && !isClipped) {
                                val embeddedStartAyah = parseStartAyah(mediaItem.mediaId)
                                val sId = parseSurahId(mediaItem.mediaId)
                                val rId = parseReciterId(mediaItem.mediaId) ?: _playbackState.value.currentReciter?.id ?: 7
                                if (embeddedStartAyah > 1 && sId != null) {
                                    val audioData = quranRepository.getChapterAudio(rId, sId)
                                    resolvedStartPos = audioData?.timestamps
                                        ?.find { t -> t.verseKey == "$sId:$embeddedStartAyah" }
                                        ?.timestampFrom ?: 0L
                                }
                            }
                            val remoteDurationHint = resolveFullSurahDurationMs(mediaItem)
                            pushCurrentMediaItemToRemote(
                                currentItem = mediaItem,
                                nextItem = nextItem,
                                startPosMs = resolvedStartPos,
                                durationHintMs = remoteDurationHint,
                            )
                        }
                    }
                }

                if (_sleepTimerState.value.isRunning) {
                    when (_sleepTimerState.value.mode) {
                        is SleepTimerMode.EndOfSurah -> {
                            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                                syncTracker.flushKhatamProgress()
                                val prevIndex = (controller.currentMediaItemIndex - 1).coerceAtLeast(0)
                                controller.pause()
                                if (controller.mediaItemCount > prevIndex) {
                                    controller.seekTo(prevIndex, 0L)
                                }
                                cancelSleepTimer()
                            }
                        }
                        is SleepTimerMode.EndOfPlaylist -> {
                            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                                syncTracker.flushKhatamProgress()
                                controller.pause()
                                cancelSleepTimer()
                            } else {
                                if (queueManager.queueState.value.upcomingItems.isEmpty()) {
                                    setPauseAtEndOfMediaItemsInternal(true)
                                }
                            }
                        }
                        else -> { /* duration timers handled by countdown job */ }
                    }
                }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _playbackState.update { it.copy(repeatMode = repeatMode) }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _playbackState.update { it.copy(isShuffleEnabled = shuffleModeEnabled) }
                queueManager.rebuildQueueState(controller)
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                syncTracker.onSeek(newPosition.positionMs)
                if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION || reason == Player.DISCONTINUITY_REASON_SEEK) {
                    queueManager.rebuildQueueState(controller)
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.e("AudioPlayerManager", "MediaController playback error: ${error.errorCodeName}", error)
                _playbackState.update { it.copy(isBuffering = false) }
                if (_playbackState.value.isRadio) {
                    _playbackState.update {
                        it.copy(
                            radioError = "Playback error: ${error.errorCodeName}. Retrying..."
                        )
                    }
                    scope.launch {
                        delay(2000)
                        if (_playbackState.value.isRadio) {
                            val station = _playbackState.value.currentRadioStation
                            if (station != null) {
                                playRadio(station)
                            }
                        }
                    }
                } else if (_playbackState.value.queue.isNotEmpty()) {
                    scope.launch {
                        delay(1200)
                        playNext()
                    }
                }
            }
        })

        // Synchronize initial state from controller if it was already playing
        if (controller.isPlaying) {
            _playbackState.update { it.copy(isPlaying = true) }
            syncTracker.onPlayStateChanged(true) { browser }
            startProgressTracker()
        }
        controller.currentMediaItem?.let { syncFromMediaItem(it) }
        queueManager.rebuildQueueState(controller)
    }

    private fun syncFromMediaItem(mediaItem: MediaItem, seekToEmbeddedAyah: Boolean = false) {
        val mediaId = mediaItem.mediaId
        if (mediaId.startsWith("radio:")) {
            syncFromRadioMediaItem(mediaItem)
            return
        }
        val surahId = parseSurahId(mediaId) ?: return
        val reciterId = parseReciterId(mediaId)
        val startAyah = parseStartAyah(mediaId)
        val endAyah = parseEndAyah(mediaId)
        val clipConfig = mediaItem.clippingConfiguration
        val isItemClipped = clipConfig.endPositionMs != C.TIME_END_OF_SOURCE

        if (lastSyncedMediaId != mediaId ||
            _playbackState.value.currentSurah?.id != surahId ||
            (reciterId != null && _playbackState.value.currentReciter?.id != reciterId)) {
            lastSyncedMediaId = mediaId
            isAdvancingClipEnd = false
            resetRemoteClipTracking()
            val targetReciterId = reciterId ?: _playbackState.value.currentReciter?.id ?: 7
            if (isItemClipped) {
                activeClipBounds = AyahClipBounds(
                    startAyah = startAyah,
                    endAyah = endAyah ?: startAyah,
                    startMs = clipConfig.startPositionMs.coerceAtLeast(0L),
                    endMs = clipConfig.endPositionMs,
                )
                unclippedEndAyahMs = null
            } else {
                activeClipBounds = null
                unclippedEndAyahMs = null
            }
            syncTracker.onMediaItemChanged(surahId, targetReciterId, startAyah)

            scope.launch {
                val surah = quranRepository.getSurahById(surahId) ?: return@launch
                val reciter = if (reciterId != null) {
                    reciterRepository.getReciterById(reciterId) ?: reciterRepository.getDefaultReciter()
                } else {
                    _playbackState.value.currentReciter ?: reciterRepository.getDefaultReciter()
                }
                val audioData = quranRepository.getChapterAudio(reciter.id, surah.id)
                val timings = audioData?.timestamps ?: emptyList()

                if (isItemClipped) {
                    val clipStart = clipConfig.startPositionMs.coerceAtLeast(0L)
                    val clipEnd = clipConfig.endPositionMs
                    val bounds = AyahClipBounds(
                        startAyah = startAyah,
                        endAyah = endAyah ?: startAyah,
                        startMs = clipStart,
                        endMs = clipEnd,
                    )
                    activeClipBounds = bounds
                    unclippedEndAyahMs = null
                    val dur = browser?.duration?.takeIf { it > 0 } ?: bounds.durationMs
                    val effectivePosMs = browser?.currentPosition?.coerceAtLeast(0L) ?: 0L
                    _currentPositionMs.value = effectivePosMs
                    syncTracker.setTimings(
                        surahId = surah.id,
                        reciterId = reciter.id,
                        timings = timings,
                        initialPosMs = effectivePosMs,
                        positionOffsetMs = clipStart,
                    )
                    val syncedAyah = if (timings.isNotEmpty()) syncTracker.syncState.value.currentAyahNumber else startAyah
                    _playbackState.update {
                        it.copy(
                            isRadio = false,
                            currentRadioStation = null,
                            currentSurah = surah,
                            currentReciter = reciter,
                            durationMs = dur,
                            verseTimings = timings,
                            isSyncAvailable = timings.isNotEmpty(),
                            hasWordTimings = timings.any { t -> t.segments.isNotEmpty() },
                            currentAyahNumber = syncedAyah,
                            activeStartAyah = bounds.startAyah,
                            activeEndAyah = bounds.endAyah,
                        )
                    }
                } else {
                    val surahDuration = timings.lastOrNull()?.timestampTo ?: 0L
                    val dur = browser?.duration?.takeIf { it > 0 } ?: surahDuration
                    var effectivePosMs = browser?.currentPosition?.coerceAtLeast(0L) ?: 0L

                    val targetTiming = if (startAyah > 1) {
                        timings.find { it.verseKey == "${surah.id}:$startAyah" }
                    } else null

                    if (!outputDeviceManager.isCasting && seekToEmbeddedAyah && targetTiming != null) {
                        val controller = browser
                        if (controller != null &&
                            controller.currentMediaItem?.mediaId == mediaId &&
                            controller.currentPosition < targetTiming.timestampFrom - 1500L
                        ) {
                            controller.seekTo(targetTiming.timestampFrom)
                            effectivePosMs = targetTiming.timestampFrom
                        }
                    } else if (startAyah > 1 && targetTiming != null && effectivePosMs == 0L) {
                        effectivePosMs = targetTiming.timestampFrom
                    }

                    unclippedEndAyahMs = if (endAyah != null) {
                        resolveAyahClipBounds(surah.id, startAyah, endAyah, timings)?.endMs
                    } else null

                    _currentPositionMs.value = effectivePosMs
                    syncTracker.setTimings(surah.id, reciter.id, timings, effectivePosMs)
                    _playbackState.update {
                        it.copy(
                            isRadio = false,
                            currentRadioStation = null,
                            currentSurah = surah,
                            currentReciter = reciter,
                            durationMs = dur,
                            verseTimings = timings,
                            isSyncAvailable = timings.isNotEmpty(),
                            hasWordTimings = timings.any { t -> t.segments.isNotEmpty() },
                            currentAyahNumber = startAyah,
                            activeStartAyah = if (endAyah != null) startAyah else null,
                            activeEndAyah = endAyah,
                        )
                    }
                }
            }
        }
    }

    private fun syncFromRadioMediaItem(mediaItem: MediaItem) {
        val parts = mediaItem.mediaId.split(":")
        // Format: radio:{stationId}:{timestamp}:{surahId}:{reciterId}
        val stationId = parts.getOrNull(1)
        val surahId = parts.getOrNull(3)?.toIntOrNull()
        val reciterId = parts.getOrNull(4)?.toIntOrNull()

        scope.launch {
            val surah = surahId?.let { quranRepository.getSurahById(it) }
            val reciter = reciterId?.let { reciterRepository.getReciterById(it) } ?: reciterRepository.getDefaultReciter()
            val audioData = if (surah != null) quranRepository.getChapterAudio(reciter.id, surah.id) else null
            val timings = audioData?.timestamps ?: emptyList()

            syncTracker.isRadio = true
            if (surah != null) {
                syncTracker.setTimings(surah.id, reciter.id, timings, browser?.currentPosition ?: 0L)
            }

            _playbackState.update { current ->
                val station = current.currentRadioStation ?: if (stationId != null) {
                    CuratedRadioStations.find { it.id == stationId }
                } else null

                current.copy(
                    isRadio = true,
                    currentRadioStation = station ?: current.currentRadioStation,
                    currentSurah = surah ?: current.currentSurah,
                    currentReciter = reciter,
                    verseTimings = timings,
                    isSyncAvailable = timings.isNotEmpty(),
                    hasWordTimings = timings.any { t -> t.segments.isNotEmpty() },
                    currentAyahNumber = 1,
                    radioError = null,
                    isBuffering = false
                )
            }
        }
    }

    private fun parseSurahId(mediaId: String): Int? {
        if (mediaId.startsWith("surah:")) {
            val remainder = mediaId.removePrefix("surah:")
            return remainder.substringBefore(":").toIntOrNull()
        }
        return mediaId.toIntOrNull()
    }

    private fun parseReciterId(mediaId: String): Int? {
        if (mediaId.contains(":reciter:")) {
            return mediaId.substringAfter(":reciter:").substringBefore(":").toIntOrNull()
        }
        return null
    }

    private fun parseStartAyah(mediaId: String): Int = PlaybackQueueManager.parseStartAyah(mediaId)

    private fun parseEndAyah(mediaId: String): Int? = PlaybackQueueManager.parseEndAyah(mediaId)

    private fun ensureServiceRunning() {
        try {
            val intent = Intent(context, ListenQuranMediaService::class.java)
            ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            android.util.Log.e("AudioPlayerManager", "Failed to start media service", e)
        }
        if (browser == null) {
            connectBrowser()
        }
    }

    fun playSurah(
        surah: Surah,
        reciter: Reciter,
        startAyah: Int = 1,
        queueTitle: String? = null
    ) {
        radioWatchdogJob?.cancel()
        activeClipBounds = null
        unclippedEndAyahMs = null
        isAdvancingClipEnd = false
        resetRemoteClipTracking()
        syncTracker.isRadio = false
        syncTracker.onMediaItemChanged(surah.id, reciter.id, startAyah)
        ensureServiceRunning()
        _currentPositionMs.value = 0L

        scope.launch {
            val audioData = quranRepository.getChapterAudio(reciterId = reciter.id, chapterId = surah.id)
            val timings = audioData?.timestamps ?: emptyList()
            val surahDuration = timings.lastOrNull()?.timestampTo ?: 0L
            syncTracker.setTimings(surah.id, reciter.id, timings, 0L)
            _playbackState.update {
                it.copy(
                    currentSurah = surah,
                    currentReciter = reciter,
                    isBuffering = true,
                    durationMs = surahDuration,
                    currentAyahNumber = startAyah,
                    verseTimings = timings,
                    isSyncAvailable = timings.isNotEmpty(),
                    hasWordTimings = timings.any { t -> t.segments.isNotEmpty() },
                    isRadio = false,
                    currentRadioStation = null,
                    radioError = null,
                    activeStartAyah = null,
                    activeEndAyah = null,
                )
            }

            val allSurahs = queueManager.getAllSurahs()
            val targetSurahs = if (allSurahs.isNotEmpty()) allSurahs else listOf(surah)
            val startIndex = targetSurahs.indexOfFirst { it.id == surah.id }.coerceAtLeast(0)

            queueManager.playQueue(
                surahs = targetSurahs,
                reciter = reciter,
                startIndex = startIndex,
                startAyah = startAyah,
                queueTitle = queueTitle ?: "The Holy Quran",
                autoPlay = !isCastingOrConnecting,
                withController = ::withController
            )
        }
    }

    fun playSurahs(
        surahs: List<Surah>,
        reciter: Reciter,
        startIndex: Int = 0,
        startAyah: Int = 1,
        queueTitle: String? = null
    ) {
        if (surahs.isEmpty()) return
        radioWatchdogJob?.cancel()
        activeClipBounds = null
        unclippedEndAyahMs = null
        isAdvancingClipEnd = false
        resetRemoteClipTracking()
        syncTracker.isRadio = false
        ensureServiceRunning()
        _currentPositionMs.value = 0L

        val targetSurah = surahs.getOrNull(startIndex) ?: surahs.first()
        syncTracker.onMediaItemChanged(targetSurah.id, reciter.id, startAyah)

        scope.launch {
            val audioData = quranRepository.getChapterAudio(reciterId = reciter.id, chapterId = targetSurah.id)
            val timings = audioData?.timestamps ?: emptyList()
            val surahDuration = timings.lastOrNull()?.timestampTo ?: 0L
            syncTracker.setTimings(targetSurah.id, reciter.id, timings, 0L)
            _playbackState.update {
                it.copy(
                    currentSurah = targetSurah,
                    currentReciter = reciter,
                    isBuffering = true,
                    durationMs = surahDuration,
                    currentAyahNumber = startAyah,
                    verseTimings = timings,
                    isSyncAvailable = timings.isNotEmpty(),
                    hasWordTimings = timings.any { t -> t.segments.isNotEmpty() },
                    isRadio = false,
                    currentRadioStation = null,
                    radioError = null,
                    activeStartAyah = null,
                    activeEndAyah = null,
                )
            }

            queueManager.playQueue(
                surahs = surahs,
                reciter = reciter,
                startIndex = startIndex,
                startAyah = startAyah,
                queueTitle = queueTitle,
                autoPlay = !isCastingOrConnecting,
                withController = ::withController
            )
        }
    }

    fun playPlaylist(
        items: List<PlaylistItem>,
        allSurahs: List<Surah>,
        allReciters: List<Reciter>,
        startIndex: Int = 0,
        queueTitle: String? = null,
        shuffle: Boolean = false
    ) {
        if (items.isEmpty()) return
        radioWatchdogJob?.cancel()
        activeClipBounds = null
        unclippedEndAyahMs = null
        isAdvancingClipEnd = false
        resetRemoteClipTracking()
        ensureServiceRunning()
        _currentPositionMs.value = 0L

        val clamped = startIndex.coerceIn(0, items.size - 1)
        val targetItem = items[clamped]
        val targetSurah = allSurahs.find { it.id == targetItem.surahNumber }
        val targetReciter = allReciters.find { it.id == targetItem.reciterId } ?: reciterRepository.getDefaultReciter()

        if (targetSurah != null) {
            syncTracker.isRadio = false
            syncTracker.onMediaItemChanged(targetSurah.id, targetReciter.id, targetItem.startAyah)
        }

        scope.launch {
            var effectiveReciter = targetReciter
            var timings = if (targetSurah != null) {
                quranRepository.getVerseTimings(targetReciter.id, targetSurah.id)
            } else emptyList()

            var clipBounds = if (targetSurah != null && targetItem.endAyah != null) {
                resolveAyahClipBounds(targetSurah.id, targetItem.startAyah, targetItem.endAyah, timings)
            } else null

            if (targetSurah != null && targetItem.endAyah != null && clipBounds == null) {
                val defaultRec = reciterRepository.getDefaultReciter()
                if (defaultRec.id != targetReciter.id) {
                    val fbTimings = quranRepository.getVerseTimings(defaultRec.id, targetSurah.id)
                    val fbBounds = resolveAyahClipBounds(targetSurah.id, targetItem.startAyah, targetItem.endAyah, fbTimings)
                    if (fbBounds != null) {
                        effectiveReciter = defaultRec
                        timings = fbTimings
                        clipBounds = fbBounds
                    }
                }
            }

            if (clipBounds != null && targetSurah != null) {
                activeClipBounds = clipBounds
                unclippedEndAyahMs = null
                pendingRemoteSeekMs = clipBounds.startMs
                syncTracker.setTimings(
                    surahId = targetSurah.id,
                    reciterId = effectiveReciter.id,
                    timings = timings,
                    initialPosMs = 0L,
                    positionOffsetMs = clipBounds.startMs,
                )
            } else if (targetSurah != null) {
                activeClipBounds = null
                unclippedEndAyahMs = null
                val startPos = computeInitialStartPositionMs(
                    isClipped = false,
                    surahId = targetSurah.id,
                    startAyah = targetItem.startAyah,
                    timings = timings,
                )
                if (startPos > 0L) {
                    pendingRemoteSeekMs = startPos
                }
                syncTracker.setTimings(targetSurah.id, effectiveReciter.id, timings, startPos)
            }

            val initialDurationMs = clipBounds?.durationMs
                ?: timings.lastOrNull()?.timestampTo
                ?: 0L

            _playbackState.update {
                it.copy(
                    currentSurah = targetSurah ?: it.currentSurah,
                    currentReciter = effectiveReciter,
                    isBuffering = true,
                    durationMs = initialDurationMs,
                    currentAyahNumber = targetItem.startAyah,
                    verseTimings = timings,
                    isSyncAvailable = timings.isNotEmpty(),
                    hasWordTimings = timings.any { t -> t.segments.isNotEmpty() },
                    isRadio = false,
                    currentRadioStation = null,
                    radioError = null,
                    activeStartAyah = clipBounds?.startAyah ?: targetItem.endAyah?.let { targetItem.startAyah },
                    activeEndAyah = clipBounds?.endAyah ?: targetItem.endAyah,
                )
            }

            queueManager.playPlaylist(
                items = items,
                allSurahs = allSurahs,
                allReciters = allReciters,
                startIndex = clamped,
                queueTitle = queueTitle,
                shuffle = shuffle,
                autoPlay = !isCastingOrConnecting,
                withController = ::withController
            )
        }
    }

    fun switchReciterPreservingQueue(reciter: Reciter) {
        val currentSurah = _playbackState.value.currentSurah ?: return
        if (_playbackState.value.isRadio) return
        val currentAyah = _playbackState.value.currentAyahNumber.coerceAtLeast(1)
        val activeStartBound = _playbackState.value.activeStartAyah
        val activeEndBound = _playbackState.value.activeEndAyah
        val wasPlaying = _playbackState.value.isPlaying
        syncTracker.onMediaItemChanged(currentSurah.id, reciter.id, currentAyah)
        ensureServiceRunning()

        scope.launch {
            var effectiveReciter = reciter
            var timings = quranRepository.getVerseTimings(reciter.id, currentSurah.id)
            var clipBounds = if (activeEndBound != null) {
                resolveAyahClipBounds(currentSurah.id, activeStartBound ?: 1, activeEndBound, timings)
            } else null

            if (activeEndBound != null && clipBounds == null) {
                val defaultRec = reciterRepository.getDefaultReciter()
                if (defaultRec.id != reciter.id) {
                    val fbTimings = quranRepository.getVerseTimings(defaultRec.id, currentSurah.id)
                    val fbBounds = resolveAyahClipBounds(currentSurah.id, activeStartBound ?: 1, activeEndBound, fbTimings)
                    if (fbBounds != null) {
                        effectiveReciter = defaultRec
                        timings = fbTimings
                        clipBounds = fbBounds
                    }
                }
            }

            activeClipBounds = clipBounds
            unclippedEndAyahMs = null
            isAdvancingClipEnd = false
            resetRemoteClipTracking()

            val surahDuration = timings.lastOrNull()?.timestampTo ?: 0L
            if (clipBounds != null) {
                val startAyahBound = clipBounds.startAyah
                val targetAbsPos = if (currentAyah in startAyahBound..clipBounds.endAyah && currentAyah > startAyahBound) {
                    timings.find { it.verseKey == "${currentSurah.id}:$currentAyah" }?.timestampFrom ?: clipBounds.startMs
                } else {
                    clipBounds.startMs
                }
                val relPos = (targetAbsPos - clipBounds.startMs).coerceIn(0L, clipBounds.durationMs)
                _currentPositionMs.value = relPos
                pendingRemoteSeekMs = targetAbsPos
                syncTracker.setTimings(
                    surahId = currentSurah.id,
                    reciterId = effectiveReciter.id,
                    timings = timings,
                    initialPosMs = relPos,
                    positionOffsetMs = clipBounds.startMs,
                )
                _playbackState.update {
                    it.copy(
                        currentReciter = effectiveReciter,
                        isBuffering = true,
                        durationMs = clipBounds.durationMs,
                        verseTimings = timings,
                        isSyncAvailable = timings.isNotEmpty(),
                        hasWordTimings = timings.any { t -> t.segments.isNotEmpty() },
                        activeStartAyah = clipBounds.startAyah,
                        activeEndAyah = clipBounds.endAyah,
                    )
                }
            } else {
                val targetPos = if (currentAyah > 1) {
                    timings.find { it.verseKey == "${currentSurah.id}:$currentAyah" }?.timestampFrom ?: 0L
                } else 0L
                _currentPositionMs.value = targetPos
                pendingRemoteSeekMs = targetPos
                syncTracker.setTimings(currentSurah.id, effectiveReciter.id, timings, targetPos)
                _playbackState.update {
                    it.copy(
                        currentReciter = effectiveReciter,
                        isBuffering = true,
                        durationMs = surahDuration,
                        verseTimings = timings,
                        isSyncAvailable = timings.isNotEmpty(),
                        hasWordTimings = timings.any { t -> t.segments.isNotEmpty() },
                    )
                }
            }
            queueManager.switchReciterPreservingQueue(
                reciter = effectiveReciter,
                currentAyahNumber = currentAyah,
                wasPlayingOverride = wasPlaying,
                autoPlay = !isCastingOrConnecting,
                withController = ::withController
            )
        }
    }

    fun togglePlayPause() {
        if (outputDeviceManager.isCasting) {
            scope.launch {
                if (_playbackState.value.isPlaying) {
                    outputDeviceManager.pauseRemote()
                } else {
                    outputDeviceManager.playRemote()
                }
            }
            return
        }
        withController { controller ->
            if (controller.isPlaying) {
                controller.pause()
            } else {
                controller.play()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val clampedPos = positionMs.coerceAtLeast(0L)
        _currentPositionMs.value = clampedPos
        syncTracker.onSeek(clampedPos)
        if (outputDeviceManager.isCasting) {
            val remoteTarget = (activeClipBounds?.startMs ?: 0L) + clampedPos
            scope.launch {
                outputDeviceManager.seekRemote(remoteTarget)
            }
            return
        }
        withController { controller ->
            controller.seekTo(clampedPos)
        }
    }

    fun seekToAyah(ayahNumber: Int) {
        val surahId = _playbackState.value.currentSurah?.id ?: return
        val clip = activeClipBounds
        if (clip != null && ayahNumber !in clip.startAyah..clip.endAyah) return
        val targetTiming = _playbackState.value.verseTimings.find { it.verseKey == "$surahId:$ayahNumber" }
        if (targetTiming != null) {
            val targetPos = if (clip != null) {
                (targetTiming.timestampFrom - clip.startMs).coerceIn(0L, clip.durationMs)
            } else {
                targetTiming.timestampFrom
            }
            seekTo(targetPos)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        withController { controller ->
            controller.playbackParameters = PlaybackParameters(speed)
        }
        _playbackState.update { it.copy(playbackSpeed = speed) }
    }

    fun addToQueue(surah: Surah, reciter: Reciter? = null, startAyah: Int = 1) {
        val targetReciter = reciter ?: _playbackState.value.currentReciter ?: runBlocking { reciterRepository.getDefaultReciter() }
        queueManager.addToQueue(surah, targetReciter, ::withController)
    }

    fun playNextInQueue(surah: Surah, reciter: Reciter? = null, startAyah: Int = 1) {
        val targetReciter = reciter ?: _playbackState.value.currentReciter ?: runBlocking { reciterRepository.getDefaultReciter() }
        queueManager.playNext(surah, targetReciter, ::withController)
    }

    fun playQueueItem(timelineIndex: Int) {
        queueManager.seekToTimelineIndex(
            timelineIndex = timelineIndex,
            autoPlay = !isCastingOrConnecting,
            withController = ::withController
        )
    }

    fun removeFromQueue(timelineIndex: Int) {
        queueManager.removeFromQueue(timelineIndex, ::withController)
    }

    fun clearQueue() {
        queueManager.clearUpcoming(::withController)
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        queueManager.moveQueueItem(fromIndex, toIndex, ::withController)
    }

    fun reorderQueue(newQueue: List<QueueItem>) {
        // Handled via moveQueueItem in PlaybackQueueManager
    }

    private data class RadioTrack(
        val surah: Surah,
        val reciter: Reciter,
        val audioUrl: String,
        val timestamps: List<VerseTiming>
    )

    private suspend fun buildRadioTrack(station: RadioStation): RadioTrack? {
        val targetSurahs = if (station.targetSurahs.isNotEmpty()) station.targetSurahs else (1..114).toList()
        val poolReciters = if (station.poolReciterIds.isNotEmpty()) {
            station.poolReciterIds
        } else {
            listOf(station.reciterId ?: 7)
        }

        val surahId = targetSurahs.random()
        val reciterId = poolReciters.random()
        val surah = quranRepository.getSurahById(surahId) ?: return null
        val reciter = reciterRepository.getReciterById(reciterId) ?: reciterRepository.getDefaultReciter()
        val audioData = quranRepository.getChapterAudio(reciter.id, surah.id) ?: return null
        return RadioTrack(surah, reciter, audioData.audioUrl, audioData.timestamps)
    }

    private fun buildRadioMediaItem(station: RadioStation, track: RadioTrack): MediaItem {
        val artworkUri = station.bannerUrl?.let { Uri.parse(it) }
            ?: Uri.parse("android.resource://${context.packageName}/${R.drawable.ic_app_logo}")

        return MediaItem.Builder()
            .setMediaId("radio:${station.id}:${System.nanoTime()}:${track.surah.id}:${track.reciter.id}")
            .setUri(track.audioUrl)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("${track.surah.id}. ${track.surah.nameSimple} (${track.surah.nameArabic})")
                    .setSubtitle("Radio · ${station.name}")
                    .setArtist("${track.reciter.englishName} • Radio · ${station.name}")
                    .setAlbumTitle("Quran.com Radio")
                    .setAlbumArtist(track.reciter.englishName)
                    .setDisplayTitle("${track.surah.id}. ${track.surah.nameSimple}")
                    .setDescription("${track.surah.translatedName} • ${track.reciter.englishName}")
                    .setArtworkUri(artworkUri)
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
                    .build()
            )
            .build()
    }

    private fun extendRadioQueueIfNeeded() {
        val station = _playbackState.value.currentRadioStation ?: return
        withController { controller ->
            val remainingCount = controller.mediaItemCount - (controller.currentMediaItemIndex + 1)
            if (remainingCount < 3) {
                scope.launch {
                    val nextTrack = buildRadioTrack(station) ?: return@launch
                    val nextItem = buildRadioMediaItem(station, nextTrack)
                    withController { c ->
                        c.addMediaItem(nextItem)
                    }
                }
            }
        }
    }

    fun playRadio(station: RadioStation) {
        ensureServiceRunning()
        radioWatchdogJob?.cancel()
        activeClipBounds = null
        unclippedEndAyahMs = null
        isAdvancingClipEnd = false
        resetRemoteClipTracking()
        _currentPositionMs.value = 0L

        val prevSurah = if (!_playbackState.value.isRadio) _playbackState.value.currentSurah else _playbackState.value.lastSurahBeforeRadio
        val prevReciter = if (!_playbackState.value.isRadio) _playbackState.value.currentReciter else _playbackState.value.lastReciterBeforeRadio

        _playbackState.update {
            it.copy(
                isRadio = true,
                currentRadioStation = station,
                isBuffering = true,
                radioError = null,
                lastSurahBeforeRadio = prevSurah,
                lastReciterBeforeRadio = prevReciter,
                queue = emptyList(),
                activeStartAyah = null,
                activeEndAyah = null,
            )
        }

        // 10-second watchdog for initial buffering timeout
        radioWatchdogJob = scope.launch {
            delay(10000)
            if (_playbackState.value.isRadio && _playbackState.value.isBuffering && !_playbackState.value.isPlaying) {
                _playbackState.update {
                    it.copy(
                        isBuffering = false,
                        radioError = "Playback connection timed out. Tap to retry."
                    )
                }
            }
        }

        scope.launch {
            val initialTracks = mutableListOf<RadioTrack>()
            for (i in 0 until 3) {
                val track = buildRadioTrack(station)
                if (track != null) {
                    initialTracks.add(track)
                }
            }

            if (initialTracks.isEmpty()) {
                radioWatchdogJob?.cancel()
                _playbackState.update {
                    it.copy(
                        isBuffering = false,
                        radioError = "Failed to load station tracks. Please check connection."
                    )
                }
                return@launch
            }

            val firstTrack = initialTracks.first()
            _playbackState.update {
                it.copy(
                    currentSurah = firstTrack.surah,
                    currentReciter = firstTrack.reciter,
                    currentAyahNumber = 1,
                    verseTimings = firstTrack.timestamps,
                )
            }

            val mediaItems = initialTracks.map { buildRadioMediaItem(station, it) }

            withController { controller ->
                controller.setMediaItems(mediaItems, 0, 0L)
                controller.prepare()
                if (!isCastingOrConnecting) {
                    controller.play()
                }
            }
        }
    }

    fun stopRadio() {
        radioWatchdogJob?.cancel()
        activeClipBounds = null
        unclippedEndAyahMs = null
        isAdvancingClipEnd = false
        resetRemoteClipTracking()
        if (outputDeviceManager.isCasting) {
            scope.launch { outputDeviceManager.pauseRemote() }
        }
        withController { controller ->
            controller.pause()
            controller.clearMediaItems()
        }
        _playbackState.update {
            it.copy(
                isRadio = false,
                currentRadioStation = null,
                isPlaying = false,
                isBuffering = false,
                radioError = null,
                activeStartAyah = null,
                activeEndAyah = null,
            )
        }
    }

    fun resumeSurahAfterRadio() {
        val surah = _playbackState.value.lastSurahBeforeRadio
        val reciter = _playbackState.value.lastReciterBeforeRadio ?: runBlocking { reciterRepository.getDefaultReciter() }
        if (surah != null) {
            stopRadio()
            playSurah(surah, reciter)
        }
    }

    fun toggleShuffle() {
        queueManager.toggleShuffle(::withController)
    }

    fun toggleRepeat() {
        withController { controller ->
            val nextRepeat = when (controller.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            controller.repeatMode = nextRepeat
            _playbackState.update { it.copy(repeatMode = nextRepeat) }
        }
    }

    fun playNext() {
        if (_playbackState.value.isRadio) {
            withController { controller ->
                if (controller.hasNextMediaItem()) {
                    controller.seekToNextMediaItem()
                } else {
                    val station = _playbackState.value.currentRadioStation
                    if (station != null) {
                        scope.launch {
                            val nextTrack = buildRadioTrack(station) ?: return@launch
                            val nextItem = buildRadioMediaItem(station, nextTrack)
                            withController { c ->
                                c.addMediaItem(nextItem)
                                c.seekToNextMediaItem()
                            }
                        }
                    }
                }
            }
            return
        }

        withController { controller ->
            if (controller.hasNextMediaItem()) {
                controller.seekToNextMediaItem()
            } else if (controller.mediaItemCount > 0) {
                if (outputDeviceManager.isCasting && controller.mediaItemCount == 1) {
                    val clipStart = activeClipBounds?.startMs ?: 0L
                    isAdvancingClipEnd = false
                    resetRemoteClipTracking()
                    scope.launch {
                        outputDeviceManager.seekRemote(clipStart)
                        outputDeviceManager.playRemote()
                    }
                } else {
                    controller.seekTo(0, 0L)
                }
            }
        }
    }

    fun playPrevious() {
        if (_playbackState.value.isRadio) {
            seekTo(0L)
            return
        }

        if (outputDeviceManager.isCasting && _currentPositionMs.value > 3000L) {
            seekTo(0L)
            return
        }

        withController { controller ->
            val pos = if (outputDeviceManager.isCasting) _currentPositionMs.value else controller.currentPosition
            if (pos > 3000L) {
                controller.seekTo(0L)
            } else if (controller.hasPreviousMediaItem()) {
                controller.seekToPreviousMediaItem()
            } else if (controller.mediaItemCount > 0) {
                if (outputDeviceManager.isCasting && controller.mediaItemCount == 1) {
                    val clipStart = activeClipBounds?.startMs ?: 0L
                    isAdvancingClipEnd = false
                    resetRemoteClipTracking()
                    scope.launch {
                        outputDeviceManager.seekRemote(clipStart)
                        outputDeviceManager.playRemote()
                    }
                } else {
                    controller.seekTo(controller.mediaItemCount - 1, 0L)
                }
            }
        }
    }

    fun startHifzLoop(config: HifzLoopConfig) {
        _playbackState.update {
            it.copy(
                isHifzActive = true,
                hifzConfig = config,
                hifzCurrentLoop = 1,
            )
        }
        seekToAyah(config.startAyah)
        if (outputDeviceManager.isCasting) {
            scope.launch {
                if (!_playbackState.value.isPlaying) {
                    outputDeviceManager.playRemote()
                }
            }
        } else {
            withController { controller ->
                if (!controller.isPlaying) {
                    controller.play()
                }
            }
        }
    }

    fun stopHifzLoop() {
        _playbackState.update {
            it.copy(
                isHifzActive = false,
                hifzConfig = null,
                hifzCurrentLoop = 0,
            )
        }
    }

    fun setSleepTimerMode(mode: SleepTimerMode) {
        if (mode is SleepTimerMode.Off) {
            cancelSleepTimer()
            return
        }

        // Tapping active option again turns the timer off
        if (_sleepTimerState.value.isRunning && isSameSleepTimerMode(_sleepTimerState.value.mode, mode)) {
            cancelSleepTimer()
            return
        }

        sleepTimerJob?.cancel()
        sleepTimerJob = null
        setPauseAtEndOfMediaItemsInternal(false)

        when (mode) {
            is SleepTimerMode.Duration -> startDurationTimer(mode, mode.totalMinutes)
            is SleepTimerMode.Custom -> startDurationTimer(mode, mode.totalMinutes)
            is SleepTimerMode.EndOfSurah -> startEndOfSurahTimer()
            is SleepTimerMode.EndOfPlaylist -> startEndOfPlaylistTimer()
            is SleepTimerMode.Off -> cancelSleepTimer()
        }
    }

    private fun isSameSleepTimerMode(current: SleepTimerMode, requested: SleepTimerMode): Boolean {
        return when {
            current is SleepTimerMode.Off && requested is SleepTimerMode.Off -> true
            current is SleepTimerMode.EndOfSurah && requested is SleepTimerMode.EndOfSurah -> true
            current is SleepTimerMode.EndOfPlaylist && requested is SleepTimerMode.EndOfPlaylist -> true
            current is SleepTimerMode.Duration && requested is SleepTimerMode.Duration -> current.totalMinutes == requested.totalMinutes
            current is SleepTimerMode.Custom && requested is SleepTimerMode.Custom -> true
            else -> false
        }
    }

    private fun startDurationTimer(mode: SleepTimerMode, minutes: Int) {
        if (minutes <= 0) {
            cancelSleepTimer()
            return
        }
        val totalSeconds = minutes * 60L
        _sleepTimerState.value = SleepTimerState(
            mode = mode,
            remainingSeconds = totalSeconds,
            isRunning = true
        )
        _playbackState.update {
            it.copy(
                sleepTimerRemainingSeconds = totalSeconds,
                stopAtEndOfSurah = false,
                sleepTimerMode = mode
            )
        }

        sleepTimerJob = scope.launch {
            var remaining = totalSeconds
            while (remaining > 0) {
                delay(1000L)
                remaining--
                _sleepTimerState.update { it.copy(remainingSeconds = remaining) }
                _playbackState.update { it.copy(sleepTimerRemainingSeconds = remaining) }
            }
            fadeOutAndPause()
            _sleepTimerState.value = SleepTimerState(mode = SleepTimerMode.Off, isRunning = false)
            _playbackState.update {
                it.copy(
                    sleepTimerRemainingSeconds = null,
                    sleepTimerMode = SleepTimerMode.Off
                )
            }
        }
    }

    private fun startEndOfSurahTimer() {
        setPauseAtEndOfMediaItemsInternal(true)
        _sleepTimerState.value = SleepTimerState(
            mode = SleepTimerMode.EndOfSurah,
            remainingSeconds = null,
            isRunning = true
        )
        _playbackState.update {
            it.copy(
                stopAtEndOfSurah = true,
                sleepTimerRemainingSeconds = null,
                sleepTimerMode = SleepTimerMode.EndOfSurah
            )
        }
    }

    private fun startEndOfPlaylistTimer() {
        if (queueManager.queueState.value.upcomingItems.isEmpty()) {
            setPauseAtEndOfMediaItemsInternal(true)
        } else {
            setPauseAtEndOfMediaItemsInternal(false)
        }
        _sleepTimerState.value = SleepTimerState(
            mode = SleepTimerMode.EndOfPlaylist,
            remainingSeconds = null,
            isRunning = true
        )
        _playbackState.update {
            it.copy(
                stopAtEndOfSurah = false,
                sleepTimerRemainingSeconds = null,
                sleepTimerMode = SleepTimerMode.EndOfPlaylist
            )
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        setPauseAtEndOfMediaItemsInternal(false)
        withController { it.volume = 1.0f }
        _sleepTimerState.value = SleepTimerState(mode = SleepTimerMode.Off, isRunning = false)
        _playbackState.update {
            it.copy(
                stopAtEndOfSurah = false,
                sleepTimerRemainingSeconds = null,
                sleepTimerMode = SleepTimerMode.Off
            )
        }
    }

    fun setStopAtEndOfSurah(enable: Boolean) {
        if (enable) {
            setSleepTimerMode(SleepTimerMode.EndOfSurah)
        } else {
            cancelSleepTimer()
        }
    }

    fun setSleepTimer(minutes: Int) {
        if (minutes <= 0) {
            cancelSleepTimer()
        } else {
            setSleepTimerMode(SleepTimerMode.Duration(minutes))
        }
    }

    private suspend fun fadeOutAndPause() {
        if (outputDeviceManager.isCasting) {
            syncTracker.flushKhatamProgress()
            outputDeviceManager.pauseRemote()
            return
        }
        val steps = 30
        val stepDelayMs = 100L // 30 * 100ms = 3.0 seconds
        for (i in steps downTo 0) {
            val vol = i / steps.toFloat()
            withController { it.volume = vol }
            delay(stepDelayMs)
        }
        syncTracker.flushKhatamProgress()
        withController {
            it.pause()
            it.volume = 1.0f
        }
    }

    private fun setPauseAtEndOfMediaItemsInternal(pause: Boolean) {
        try {
            exoPlayerProvider.get().pauseAtEndOfMediaItems = pause
        } catch (e: Exception) {
            android.util.Log.w("AudioPlayerManager", "Failed to set pauseAtEndOfMediaItems", e)
        }
    }

    private fun rearmSleepTimerForNewQueue() {
        if (!_sleepTimerState.value.isRunning) return
        when (_sleepTimerState.value.mode) {
            is SleepTimerMode.EndOfSurah -> {
                setPauseAtEndOfMediaItemsInternal(true)
            }
            is SleepTimerMode.EndOfPlaylist -> {
                if (queueManager.queueState.value.upcomingItems.isEmpty()) {
                    setPauseAtEndOfMediaItemsInternal(true)
                } else {
                    setPauseAtEndOfMediaItemsInternal(false)
                }
            }
            else -> { /* duration timers continue running */ }
        }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val isBuffering = browser?.playbackState == Player.STATE_BUFFERING
                val pos = browser?.currentPosition ?: _currentPositionMs.value
                val dur = browser?.duration?.takeIf { it > 0 }
                    ?: activeClipBounds?.durationMs?.takeIf { it > 0 }
                    ?: _playbackState.value.verseTimings.lastOrNull()?.timestampTo
                    ?: _playbackState.value.durationMs

                if (!isBuffering || kotlin.math.abs(pos - _currentPositionMs.value) < 1000) {
                    _currentPositionMs.value = pos
                }

                if (_playbackState.value.durationMs != dur && dur > 0) {
                    _playbackState.update { it.copy(durationMs = dur) }
                }

                checkHifzLoop(pos)
                checkUnclippedEndAyah(pos)
                delay(200L)
            }
        }
    }

    private fun checkUnclippedEndAyah(pos: Long) {
        if (activeClipBounds != null || isAdvancingClipEnd) return
        val endMs = unclippedEndAyahMs ?: return
        if (pos < endMs) return

        isAdvancingClipEnd = true
        if (_sleepTimerState.value.isRunning) {
            val mode = _sleepTimerState.value.mode
            if (mode is SleepTimerMode.EndOfSurah ||
                (mode is SleepTimerMode.EndOfPlaylist && queueManager.queueState.value.upcomingItems.isEmpty())
            ) {
                syncTracker.flushKhatamProgress()
                withController { it.pause() }
                cancelSleepTimer()
                return
            }
        }
        if (_playbackState.value.stopAtEndOfSurah) {
            _playbackState.update { it.copy(stopAtEndOfSurah = false) }
            withController { it.pause() }
        } else if (_playbackState.value.repeatMode == Player.REPEAT_MODE_ONE) {
            isAdvancingClipEnd = false
            seekToAyah(_playbackState.value.activeStartAyah ?: 1)
        } else {
            playNext()
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun checkHifzLoop(pos: Long) {
        if (isRestartingHifzLoop) return
        val state = _playbackState.value
        if (!state.isHifzActive || state.hifzConfig == null) return

        val config = state.hifzConfig
        val surahId = state.currentSurah?.id ?: return
        val endTiming = state.verseTimings.find { it.verseKey == "$surahId:${config.endAyah}" } ?: return

        if (pos >= endTiming.timestampTo) {
            val currentLoop = state.hifzCurrentLoop
            if (config.repeatCount == 0 || currentLoop < config.repeatCount) {
                isRestartingHifzLoop = true
                scope.launch {
                    try {
                        if (outputDeviceManager.isCasting) {
                            outputDeviceManager.pauseRemote()
                            if (config.delaySeconds > 0) {
                                delay(config.delaySeconds * 1000L)
                            }
                            _playbackState.update { it.copy(hifzCurrentLoop = currentLoop + 1) }
                            val startTiming = state.verseTimings.find { it.verseKey == "$surahId:${config.startAyah}" }
                            val startMs = startTiming?.timestampFrom ?: 0L
                            _currentPositionMs.value = startMs
                            syncTracker.onSeek(startMs)
                            outputDeviceManager.seekRemote(startMs)
                            outputDeviceManager.playRemote()
                        } else {
                            withController { it.pause() }
                            if (config.delaySeconds > 0) {
                                delay(config.delaySeconds * 1000L)
                            }
                            _playbackState.update { it.copy(hifzCurrentLoop = currentLoop + 1) }
                            seekToAyah(config.startAyah)
                            withController { it.play() }
                        }
                    } finally {
                        isRestartingHifzLoop = false
                    }
                }
            } else {
                stopHifzLoop()
            }
        }
    }

    private fun recordSessionDuration(isCompleted: Boolean = false) {
        val start = sessionStartTime
        if (start > 0) {
            val now = System.currentTimeMillis()
            val elapsedSecs = (now - start) / 1000
            sessionStartTime = 0L // reset immediately to prevent double counting

            if (_playbackState.value.isRadio) {
                return
            }

            val surah = _playbackState.value.currentSurah?.id ?: return
            val reciter = _playbackState.value.currentReciter?.id ?: return

            if (elapsedSecs >= 5) {
                val durationMs = _playbackState.value.durationMs
                val posMs = browser?.currentPosition ?: 0L
                val completed = isCompleted || (durationMs > 0 && posMs >= (durationMs * 0.90).toLong())
                scope.launch {
                    statsRepository.recordSession(surah, reciter, elapsedSecs, completed)
                }
            }
        }
    }

    fun scanOutputDevices() {
        scope.launch {
            outputDeviceManager.scanForDevices()
        }
    }

    fun connectToOutputDevice(device: OutputDevice) {
        if (device.protocol == com.listenquran.player.cast.CastProtocol.LOCAL) {
            disconnectOutputDevice(resumeLocal = true)
            return
        }
        ensureServiceRunning()
        castConnectJob?.cancel()
        resetRemoteClipTracking()
        val state = _playbackState.value
        val wasPlaying = state.isPlaying
        val localPosMs = if (!outputDeviceManager.isCasting) {
            (browser?.takeIf { it.isConnected }?.currentPosition?.takeIf { it > 0L } ?: _currentPositionMs.value).coerceAtLeast(0L)
        } else {
            _currentPositionMs.value.coerceAtLeast(0L)
        }

        // Pause local audio output immediately so phone and remote speaker never overlap
        browser?.takeIf { it.isConnected }?.pause()
        _playbackState.update {
            it.copy(
                isPlaying = false,
                isBuffering = wasPlaying || state.currentSurah != null,
            )
        }

        withController { controller ->
            val currentItem = controller.currentMediaItem
            val nextItem = getNextMediaItemInTimeline(controller)
            val clipStart = activeClipBounds?.startMs
                ?: currentItem?.clippingConfiguration
                    ?.takeIf { it.endPositionMs != C.TIME_END_OF_SOURCE }
                    ?.startPositionMs
                    ?.coerceAtLeast(0L)
                ?: 0L
            val remoteStartPosMs = clipStart + localPosMs
            castConnectJob = scope.launch {
                try {
                    val remoteDurationHint = if (currentItem != null) {
                        resolveFullSurahDurationMs(currentItem)
                    } else {
                        state.verseTimings.lastOrNull()?.timestampTo ?: state.durationMs
                    }
                    val currentDesc = if (currentItem != null) {
                        buildRemoteDescriptorForMediaItem(currentItem, remoteStartPosMs, remoteDurationHint)
                    } else if (state.currentSurah != null && state.currentReciter != null) {
                        buildRemoteDescriptorForSurah(state.currentSurah, state.currentReciter, remoteStartPosMs, remoteDurationHint)
                    } else null

                    val currentIsUnbounded = currentItem == null ||
                        (parseEndAyah(currentItem.mediaId) == null && parseStartAyah(currentItem.mediaId) <= 1)
                    val nextDesc = if (currentIsUnbounded) {
                        nextItem
                            ?.takeIf { parseStartAyah(it.mediaId) <= 1 && parseEndAyah(it.mediaId) == null }
                            ?.let { buildRemoteDescriptorForMediaItem(it, 0L, 0L) }
                    } else null
                    val autoPlay = wasPlaying || currentDesc != null
                    val res = outputDeviceManager.connectToDevice(
                        device = device,
                        currentTrack = currentDesc,
                        nextTrack = nextDesc,
                        autoPlay = autoPlay,
                    )
                    if (res.isFailure) {
                        _playbackState.update { it.copy(isPlaying = false, isBuffering = false) }
                        _castMessageEvent.emit(res.exceptionOrNull()?.message ?: "Could not connect to ${device.name}")
                    } else if (currentDesc != null) {
                        _playbackState.update { it.copy(isPlaying = autoPlay, isBuffering = false) }
                    } else {
                        _playbackState.update { it.copy(isBuffering = false) }
                    }
                } catch (ce: CancellationException) {
                    _playbackState.update { it.copy(isBuffering = false) }
                    throw ce
                }
            }
        }
    }

    fun disconnectOutputDevice(resumeLocal: Boolean = true) {
        val wasPlaying = _playbackState.value.isPlaying
        castConnectJob?.cancel()
        castConnectJob = null
        scope.launch {
            val resumePosMs = outputDeviceManager.disconnect()
            val clip = activeClipBounds
            val localResumePosMs = if (clip != null) {
                (resumePosMs - clip.startMs).coerceIn(0L, clip.durationMs)
            } else {
                resumePosMs
            }
            _currentPositionMs.value = localResumePosMs
            syncTracker.onSeek(localResumePosMs)
            _playbackState.update { it.copy(isBuffering = false) }
            if (resumeLocal) {
                withController { controller ->
                    if (localResumePosMs > 0L) {
                        controller.seekTo(localResumePosMs)
                    }
                    if (wasPlaying) {
                        controller.play()
                    } else {
                        controller.pause()
                    }
                }
            }
        }
    }

    fun setRemoteVolume(volume: Int) {
        scope.launch {
            outputDeviceManager.setRemoteVolume(volume)
        }
    }

    fun toggleRemoteMute() {
        scope.launch {
            outputDeviceManager.toggleRemoteMute()
        }
    }

    private suspend fun handleRemoteTrackEnded(advancedToNextUri: Boolean = false) {
        recordSessionDuration(isCompleted = true)
        val playback = _playbackState.value
        if (playback.isHifzActive && playback.hifzConfig != null) {
            val config = playback.hifzConfig
            val currentLoop = playback.hifzCurrentLoop
            if (config.repeatCount == 0 || currentLoop < config.repeatCount) {
                outputDeviceManager.pauseRemote()
                if (config.delaySeconds > 0) {
                    delay(config.delaySeconds * 1000L)
                }
                _playbackState.update { it.copy(hifzCurrentLoop = currentLoop + 1) }
                seekToAyah(config.startAyah)
                outputDeviceManager.playRemote()
                return
            } else {
                stopHifzLoop()
            }
        }
        if (_sleepTimerState.value.isRunning) {
            val mode = _sleepTimerState.value.mode
            if (mode is SleepTimerMode.EndOfSurah ||
                (mode is SleepTimerMode.EndOfPlaylist && queueManager.queueState.value.upcomingItems.isEmpty())
            ) {
                syncTracker.flushKhatamProgress()
                outputDeviceManager.pauseRemote()
                cancelSleepTimer()
                return
            }
        }
        if (_playbackState.value.stopAtEndOfSurah) {
            _playbackState.update { it.copy(stopAtEndOfSurah = false) }
            outputDeviceManager.pauseRemote()
        } else if (_playbackState.value.repeatMode == Player.REPEAT_MODE_ONE) {
            val clipStart = activeClipBounds?.startMs ?: 0L
            isAdvancingClipEnd = false
            resetRemoteClipTracking()
            outputDeviceManager.seekRemote(clipStart)
            outputDeviceManager.playRemote()
        } else {
            if (advancedToNextUri) {
                skipNextRemotePush = true
            }
            playNext()
        }
    }

    private suspend fun resolveFullSurahDurationMs(mediaItem: MediaItem): Long {
        val sId = parseSurahId(mediaItem.mediaId) ?: return 0L
        val rId = parseReciterId(mediaItem.mediaId) ?: _playbackState.value.currentReciter?.id ?: 7
        return quranRepository.getVerseTimings(rId, sId).lastOrNull()?.timestampTo ?: 0L
    }

    private suspend fun pushCurrentMediaItemToRemote(
        currentItem: MediaItem,
        nextItem: MediaItem?,
        startPosMs: Long,
        durationHintMs: Long,
    ) {
        if (!outputDeviceManager.isCasting) return
        val currentDesc = buildRemoteDescriptorForMediaItem(currentItem, startPosMs, durationHintMs) ?: return
        val currentIsUnbounded = parseEndAyah(currentItem.mediaId) == null && parseStartAyah(currentItem.mediaId) <= 1
        val nextDesc = if (currentIsUnbounded) {
            nextItem
                ?.takeIf { parseStartAyah(it.mediaId) <= 1 && parseEndAyah(it.mediaId) == null }
                ?.let { buildRemoteDescriptorForMediaItem(it, 0L, 0L) }
        } else null
        val res = outputDeviceManager.loadTrackOnActiveDevice(currentDesc, nextDesc, autoPlay = true)
        if (res.isFailure) {
            _castMessageEvent.emit(res.exceptionOrNull()?.message ?: "Failed to cast track")
        }
    }

    private fun getNextMediaItemInTimeline(controller: MediaBrowser): MediaItem? {
        val nextIndex = controller.nextMediaItemIndex
        return if (nextIndex != androidx.media3.common.C.INDEX_UNSET && nextIndex in 0 until controller.mediaItemCount) {
            controller.getMediaItemAt(nextIndex)
        } else null
    }

    private suspend fun buildRemoteDescriptorForMediaItem(
        item: MediaItem,
        startPosMs: Long,
        durationHintMs: Long,
    ): RemoteTrackDescriptor? {
        val uri = item.localConfiguration?.uri
        if (uri != null) {
            val uriStr = uri.toString()
            val localPath = if (uri.scheme == "file") uri.path else null
            return RemoteTrackDescriptor(
                mediaKey = item.mediaId.ifBlank { "track_${System.nanoTime()}" },
                title = item.mediaMetadata.displayTitle?.toString()
                    ?: item.mediaMetadata.title?.toString()
                    ?: "Quran Recitation",
                artist = item.mediaMetadata.artist?.toString() ?: "Listen Quran",
                album = item.mediaMetadata.albumTitle?.toString() ?: "The Holy Quran",
                streamUrl = if (localPath == null) uriStr else null,
                localFilePath = localPath,
                durationMs = durationHintMs,
                startPositionMs = startPosMs,
            )
        }
        if (item.mediaId.startsWith("radio:")) {
            val parts = item.mediaId.split(":")
            val radioSurahId = parts.getOrNull(3)?.toIntOrNull() ?: return null
            val radioReciterId = parts.getOrNull(4)?.toIntOrNull() ?: _playbackState.value.currentReciter?.id ?: 7
            val surah = quranRepository.getSurahById(radioSurahId) ?: return null
            val reciter = reciterRepository.getReciterById(radioReciterId) ?: return null
            return buildRemoteDescriptorForSurah(surah, reciter, startPosMs, durationHintMs)
        }
        val surahId = parseSurahId(item.mediaId) ?: return null
        val reciterId = parseReciterId(item.mediaId) ?: _playbackState.value.currentReciter?.id ?: 7
        val surah = quranRepository.getSurahById(surahId) ?: return null
        val reciter = reciterRepository.getReciterById(reciterId) ?: return null
        return buildRemoteDescriptorForSurah(surah, reciter, startPosMs, durationHintMs)
    }

    private suspend fun buildRemoteDescriptorForSurah(
        surah: Surah,
        reciter: Reciter,
        startPosMs: Long,
        durationHintMs: Long,
    ): RemoteTrackDescriptor? {
        val downloaded = downloadRepository.getDownloadedSurah(surah.id, reciter.id)
        val localFile = downloaded?.localFilePath?.takeIf { java.io.File(it).exists() }
        val streamUrl = if (localFile == null) {
            quranRepository.getChapterAudio(reciter.id, surah.id)?.audioUrl
        } else null
        if (localFile == null && streamUrl.isNullOrBlank()) return null

        return RemoteTrackDescriptor(
            mediaKey = "s${surah.id}_r${reciter.id}",
            title = "${surah.id}. ${surah.nameSimple}",
            artist = reciter.englishName,
            album = "The Holy Quran",
            streamUrl = streamUrl,
            localFilePath = localFile,
            durationMs = durationHintMs,
            startPositionMs = startPosMs,
        )
    }

    fun release() {
        CoroutineScope(Dispatchers.IO).launch {
            outputDeviceManager.disconnect()
        }
        browserFuture?.let { MediaController.releaseFuture(it) }
        browser = null
        browserFuture = null
        stopProgressTracker()
        sleepTimerJob?.cancel()
    }
}
