package com.listenquran.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaBrowser
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.listenquran.R
import com.listenquran.data.repository.QuranRepository
import com.listenquran.data.repository.ReciterRepository
import com.listenquran.data.repository.StatsRepository
import com.listenquran.domain.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Calendar
import javax.inject.Inject
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
    val isHifzActive: Boolean = false,
    val hifzConfig: HifzLoopConfig? = null,
    val hifzCurrentLoop: Int = 0,
    val sleepTimerRemainingSeconds: Long? = null,
    val stopAtEndOfSurah: Boolean = false,
    val queue: List<QueueItem> = emptyList(),
    val isShuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val isRadio: Boolean = false,
    val currentRadioStation: RadioStation? = null,
    val radioError: String? = null,
    val lastSurahBeforeRadio: Surah? = null,
    val lastReciterBeforeRadio: Reciter? = null,
)

@Singleton
class AudioPlayerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val quranRepository: QuranRepository,
    private val reciterRepository: ReciterRepository,
    private val statsRepository: StatsRepository,
    private val downloadRepository: com.listenquran.data.repository.QuranDownloadRepository,
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _currentWordIndex = MutableStateFlow(0)
    val currentWordIndex: StateFlow<Int> = _currentWordIndex.asStateFlow()

    private var browserFuture: ListenableFuture<MediaBrowser>? = null
    private var browser: MediaBrowser? = null

    val mediaBrowser: MediaBrowser?
        get() = browser

    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var radioWatchdogJob: Job? = null
    private var sessionStartTime: Long = 0L
    private val history = mutableListOf<QueueItem>()

    init {
        connectBrowser()
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

    private fun withController(action: (MediaBrowser) -> Unit) {
        val b = browser
        if (b != null) {
            action(b)
        } else {
            ensureServiceRunning()
            browserFuture?.addListener({
                try {
                    browserFuture?.get()?.let(action)
                } catch (e: Exception) {
                    android.util.Log.e("AudioPlayerManager", "Failed to obtain MediaBrowser", e)
                }
            }, ContextCompat.getMainExecutor(context))
        }
    }

    private fun setupControllerListener(controller: MediaBrowser) {
        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playbackState.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying) {
                    radioWatchdogJob?.cancel()
                    _playbackState.update { it.copy(radioError = null, isBuffering = false) }
                    sessionStartTime = System.currentTimeMillis()
                    startProgressTracker()
                } else {
                    recordSessionDuration()
                    stopProgressTracker()
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                val isBuffering = state == Player.STATE_BUFFERING
                val duration = if (controller.duration > 0) controller.duration else 0L
                _playbackState.update {
                    it.copy(
                        isBuffering = isBuffering,
                        durationMs = duration
                    )
                }
                if (state == Player.STATE_READY && _playbackState.value.isRadio) {
                    radioWatchdogJob?.cancel()
                    _playbackState.update { it.copy(radioError = null, isBuffering = false) }
                }
                if (state == Player.STATE_ENDED) {
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

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem?.let {
                    if (it.mediaId.startsWith("radio:")) {
                        syncFromRadioMediaItem(it)
                        extendRadioQueueIfNeeded()
                    } else {
                        syncFromMediaItem(it)
                    }
                }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _playbackState.update { it.copy(repeatMode = repeatMode) }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _playbackState.update { it.copy(isShuffleEnabled = shuffleModeEnabled) }
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
            startProgressTracker()
        }
        controller.currentMediaItem?.let { syncFromMediaItem(it) }
    }

    private fun syncFromMediaItem(mediaItem: MediaItem) {
        val mediaId = mediaItem.mediaId
        if (mediaId.startsWith("radio:")) {
            syncFromRadioMediaItem(mediaItem)
            return
        }
        val surahId = parseSurahId(mediaId) ?: return
        val reciterId = parseReciterId(mediaId)

        if (_playbackState.value.currentSurah?.id != surahId ||
            (reciterId != null && _playbackState.value.currentReciter?.id != reciterId)) {
            scope.launch {
                val surah = quranRepository.getSurahById(surahId) ?: return@launch
                val reciter = if (reciterId != null) {
                    reciterRepository.getReciterById(reciterId) ?: reciterRepository.getDefaultReciter()
                } else {
                    _playbackState.value.currentReciter ?: reciterRepository.getDefaultReciter()
                }
                val audioData = quranRepository.getChapterAudio(reciter.id, surah.id)
                _playbackState.update {
                    it.copy(
                        isRadio = false,
                        currentRadioStation = null,
                        currentSurah = surah,
                        currentReciter = reciter,
                        verseTimings = audioData?.timestamps ?: emptyList(),
                        currentAyahNumber = 1
                    )
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

            _playbackState.update { current ->
                val station = current.currentRadioStation ?: if (stationId != null) {
                    CuratedRadioStations.find { it.id == stationId }
                } else null

                current.copy(
                    isRadio = true,
                    currentRadioStation = station ?: current.currentRadioStation,
                    currentSurah = surah ?: current.currentSurah,
                    currentReciter = reciter,
                    verseTimings = audioData?.timestamps ?: emptyList(),
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

    fun playSurah(surah: Surah, reciter: Reciter, startAyah: Int = 1) {
        radioWatchdogJob?.cancel()
        val prevSurah = _playbackState.value.currentSurah
        val prevReciter = _playbackState.value.currentReciter
        if (prevSurah != null && prevReciter != null && (prevSurah.id != surah.id || prevReciter.id != reciter.id)) {
            history.add(QueueItem(surah = prevSurah, reciter = prevReciter))
            if (history.size > 50) history.removeAt(0)
        }

        ensureServiceRunning()
        _currentPositionMs.value = 0L
        scope.launch {
            _playbackState.update {
                it.copy(
                    currentSurah = surah,
                    currentReciter = reciter,
                    isBuffering = true,
                    currentAyahNumber = startAyah,
                    isRadio = false,
                    currentRadioStation = null,
                    radioError = null,
                )
            }

            val downloaded = downloadRepository.getDownloadedSurah(surah.id, reciter.id)
            val audioData = quranRepository.getChapterAudio(reciterId = reciter.id, chapterId = surah.id)
            val effectiveUri = when {
                downloaded != null && java.io.File(downloaded.localFilePath).exists() -> Uri.fromFile(java.io.File(downloaded.localFilePath))
                audioData != null -> Uri.parse(audioData.audioUrl)
                else -> null
            }

            if (effectiveUri != null) {
                val artworkUri = Uri.parse("android.resource://${context.packageName}/${R.mipmap.ic_launcher}")
                val mediaMetadata = MediaMetadata.Builder()
                    .setTitle("${surah.id}. ${surah.nameSimple} (${surah.nameArabic})")
                    .setArtist(reciter.englishName)
                    .setAlbumTitle("The Holy Quran")
                    .setAlbumArtist(reciter.englishName)
                    .setDisplayTitle("${surah.id}. ${surah.nameSimple}")
                    .setSubtitle("${reciter.englishName} • Ayah $startAyah")
                    .setDescription("${surah.translatedName} • ${surah.revelationPlace.replaceFirstChar { it.uppercase() }} • ${surah.versesCount} Verses")
                    .setArtworkUri(artworkUri)
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .build()

                val mediaItem = MediaItem.Builder()
                    .setMediaId("surah:${surah.id}:reciter:${reciter.id}")
                    .setUri(effectiveUri)
                    .setMediaMetadata(mediaMetadata)
                    .build()

                _playbackState.update {
                    it.copy(verseTimings = audioData?.timestamps ?: emptyList())
                }

                var startPositionMs = 0L
                if (startAyah > 1 && audioData != null) {
                    val targetTiming = audioData.timestamps.find { it.verseKey == "${surah.id}:$startAyah" }
                    if (targetTiming != null) {
                        startPositionMs = targetTiming.timestampFrom
                        _currentPositionMs.value = startPositionMs
                    }
                }

                withController { controller ->
                    controller.setMediaItem(mediaItem, startPositionMs)
                    controller.prepare()
                    controller.play()
                }
            }
        }
    }

    fun togglePlayPause() {
        withController { controller ->
            if (controller.isPlaying) {
                controller.pause()
            } else {
                controller.play()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        withController { controller ->
            controller.seekTo(positionMs)
        }
        _currentPositionMs.value = positionMs
        updateAyahAtPosition(positionMs)
    }

    fun seekToAyah(ayahNumber: Int) {
        val surahId = _playbackState.value.currentSurah?.id ?: return
        val targetTiming = _playbackState.value.verseTimings.find { it.verseKey == "$surahId:$ayahNumber" }
        if (targetTiming != null) {
            seekTo(targetTiming.timestampFrom)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        withController { controller ->
            controller.playbackParameters = PlaybackParameters(speed)
        }
        _playbackState.update { it.copy(playbackSpeed = speed) }
    }

    fun addToQueue(surah: Surah, reciter: Reciter? = null, startAyah: Int = 1) {
        val targetReciter = reciter ?: _playbackState.value.currentReciter ?: return
        val item = QueueItem(surah = surah, reciter = targetReciter, startAyah = startAyah)
        _playbackState.update { it.copy(queue = it.queue + item) }
    }

    fun playNextInQueue(surah: Surah, reciter: Reciter? = null, startAyah: Int = 1) {
        val targetReciter = reciter ?: _playbackState.value.currentReciter ?: return
        val item = QueueItem(surah = surah, reciter = targetReciter, startAyah = startAyah)
        _playbackState.update { it.copy(queue = listOf(item) + it.queue) }
    }

    fun playQueueItem(index: Int) {
        val currentQueue = _playbackState.value.queue
        if (index in currentQueue.indices) {
            val target = currentQueue[index]
            val remaining = currentQueue.toMutableList().apply { removeAt(index) }
            _playbackState.update { it.copy(queue = remaining) }
            playSurah(target.surah, target.reciter, target.startAyah)
        }
    }

    fun removeFromQueue(index: Int) {
        val currentQueue = _playbackState.value.queue.toMutableList()
        if (index in currentQueue.indices) {
            currentQueue.removeAt(index)
            _playbackState.update { it.copy(queue = currentQueue) }
        }
    }

    fun clearQueue() {
        _playbackState.update { it.copy(queue = emptyList()) }
    }

    fun reorderQueue(newQueue: List<QueueItem>) {
        _playbackState.update { it.copy(queue = newQueue) }
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
            ?: Uri.parse("android.resource://${context.packageName}/${R.mipmap.ic_launcher}")

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
                controller.play()
            }
        }
    }

    fun stopRadio() {
        radioWatchdogJob?.cancel()
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
        withController { controller ->
            val newShuffle = !controller.shuffleModeEnabled
            controller.shuffleModeEnabled = newShuffle
            _playbackState.update { it.copy(isShuffleEnabled = newShuffle) }
        }
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

        val q = _playbackState.value.queue
        if (q.isNotEmpty()) {
            val nextItem = q.first()
            _playbackState.update { it.copy(queue = q.drop(1)) }
            playSurah(nextItem.surah, nextItem.reciter, nextItem.startAyah)
            return
        }

        val reciter = _playbackState.value.currentReciter ?: runBlocking { reciterRepository.getDefaultReciter() }
        val current = _playbackState.value.currentSurah ?: return
        val nextId = if (_playbackState.value.isShuffleEnabled) {
            (1..114).random()
        } else {
            if (current.id < 114) current.id + 1 else 1
        }

        scope.launch {
            val nextSurah = quranRepository.getSurahById(nextId)
            if (nextSurah != null) {
                playSurah(nextSurah, reciter)
            }
        }
    }

    fun playPrevious() {
        if (_playbackState.value.isRadio) {
            seekTo(0L)
            return
        }

        val pos = browser?.currentPosition ?: _currentPositionMs.value
        if (pos > 3000L) {
            seekTo(0L)
            return
        }

        if (history.isNotEmpty()) {
            val prevItem = history.removeAt(history.lastIndex)
            playSurah(prevItem.surah, prevItem.reciter, prevItem.startAyah)
            return
        }

        val current = _playbackState.value.currentSurah ?: return
        val prevId = if (current.id > 1) current.id - 1 else 114
        val reciter = _playbackState.value.currentReciter ?: runBlocking { reciterRepository.getDefaultReciter() }
        scope.launch {
            val prevSurah = quranRepository.getSurahById(prevId)
            if (prevSurah != null) {
                playSurah(prevSurah, reciter)
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
        withController { controller ->
            if (!controller.isPlaying) {
                controller.play()
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

    fun setStopAtEndOfSurah(enable: Boolean) {
        _playbackState.update {
            it.copy(
                stopAtEndOfSurah = enable,
                sleepTimerRemainingSeconds = null
            )
        }
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        _playbackState.update { it.copy(stopAtEndOfSurah = false) }

        if (minutes <= 0) {
            _playbackState.update { it.copy(sleepTimerRemainingSeconds = null) }
            return
        }

        val totalSeconds = minutes * 60L
        _playbackState.update { it.copy(sleepTimerRemainingSeconds = totalSeconds) }

        sleepTimerJob = scope.launch {
            var remaining = totalSeconds
            while (remaining > 0) {
                delay(1000L)
                remaining--
                _playbackState.update { it.copy(sleepTimerRemainingSeconds = remaining) }
            }
            fadeOutAndPause()
            _playbackState.update { it.copy(sleepTimerRemainingSeconds = null) }
        }
    }

    private suspend fun fadeOutAndPause() {
        val steps = 30
        val stepDelayMs = 100L // 30 * 100ms = 3.0 seconds
        for (i in steps downTo 0) {
            val vol = i / steps.toFloat()
            withController { it.volume = vol }
            delay(stepDelayMs)
        }
        withController {
            it.pause()
            it.volume = 1.0f
        }
    }

    fun setSleepTimerUntilTime(targetHour: Int, targetMinute: Int) {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, targetMinute)
            set(Calendar.SECOND, 0)
            if (before(now)) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        val diffMinutes = ((target.timeInMillis - now.timeInMillis) / 60000L).toInt().coerceAtLeast(1)
        setSleepTimer(diffMinutes)
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val pos = browser?.currentPosition ?: _currentPositionMs.value
                val dur = browser?.duration?.takeIf { it > 0 } ?: _playbackState.value.durationMs

                _currentPositionMs.value = pos

                if (_playbackState.value.durationMs != dur && dur > 0) {
                    _playbackState.update { it.copy(durationMs = dur) }
                }

                updateAyahAtPosition(pos)
                checkHifzLoop(pos)
                delay(200L)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun updateAyahAtPosition(pos: Long) {
        val timings = _playbackState.value.verseTimings
        if (timings.isEmpty()) return

        val matchedTiming = timings.find { pos in it.timestampFrom until it.timestampTo }
            ?: timings.lastOrNull { pos >= it.timestampFrom }

        if (matchedTiming != null) {
            val ayahNum = matchedTiming.verseKey.split(":").getOrNull(1)?.toIntOrNull() ?: 1
            var wordIdx = 0
            for (seg in matchedTiming.segments) {
                if (pos in seg.startMs..seg.endMs) {
                    wordIdx = seg.wordIndex
                    break
                }
            }
            if (_currentWordIndex.value != wordIdx) {
                _currentWordIndex.value = wordIdx
            }
            if (_playbackState.value.currentAyahNumber != ayahNum) {
                _playbackState.update {
                    it.copy(currentAyahNumber = ayahNum)
                }
            }
        }
    }

    private fun checkHifzLoop(pos: Long) {
        val state = _playbackState.value
        if (!state.isHifzActive || state.hifzConfig == null) return

        val config = state.hifzConfig
        val surahId = state.currentSurah?.id ?: return
        val endTiming = state.verseTimings.find { it.verseKey == "$surahId:${config.endAyah}" } ?: return

        if (pos >= endTiming.timestampTo) {
            val currentLoop = state.hifzCurrentLoop
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
            } else {
                stopHifzLoop()
            }
        }
    }

    private fun recordSessionDuration() {
        val start = sessionStartTime
        if (start > 0) {
            val elapsedSecs = (System.currentTimeMillis() - start) / 1000
            val surah = _playbackState.value.currentSurah?.id ?: return
            val reciter = _playbackState.value.currentReciter?.id ?: return
            scope.launch {
                statsRepository.recordSession(surah, reciter, elapsedSecs)
            }
            sessionStartTime = 0L
        }
    }

    fun release() {
        browserFuture?.let { MediaController.releaseFuture(it) }
        browser = null
        browserFuture = null
        stopProgressTracker()
        sleepTimerJob?.cancel()
    }
}
