package com.listenquran.player

import android.content.Context
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.listenquran.data.repository.QuranRepository
import com.listenquran.data.repository.ReciterRepository
import com.listenquran.data.repository.StatsRepository
import com.listenquran.domain.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

data class PlaybackState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentSurah: Surah? = null,
    val currentReciter: Reciter? = null,
    val currentAyahNumber: Int = 1,
    val currentWordIndex: Int = 0,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val verseTimings: List<VerseTiming> = emptyList(),
    val isHifzActive: Boolean = false,
    val hifzConfig: HifzLoopConfig? = null,
    val hifzCurrentLoop: Int = 0,
    val sleepTimerRemainingSeconds: Long? = null,
    val stopAtEndOfSurah: Boolean = false,
    val isRadio: Boolean = false,
    val radioStation: RadioStation? = null,
    val queue: List<QueueItem> = emptyList(),
    val isShuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
)

@Singleton
class AudioPlayerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    val player: ExoPlayer,
    private val quranRepository: QuranRepository,
    private val reciterRepository: ReciterRepository,
    private val statsRepository: StatsRepository,
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var sessionStartTime: Long = 0L
    private val history = mutableListOf<QueueItem>()

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playbackState.value = _playbackState.value.copy(isPlaying = isPlaying)
                if (isPlaying) {
                    sessionStartTime = System.currentTimeMillis()
                    startProgressTracker()
                } else {
                    recordSessionDuration()
                    stopProgressTracker()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val isBuffering = playbackState == Player.STATE_BUFFERING
                val duration = if (player.duration > 0) player.duration else 0L
                _playbackState.value = _playbackState.value.copy(
                    isBuffering = isBuffering,
                    durationMs = duration
                )
                if (playbackState == Player.STATE_ENDED) {
                    if (_playbackState.value.stopAtEndOfSurah) {
                        _playbackState.value = _playbackState.value.copy(stopAtEndOfSurah = false)
                        player.pause()
                    } else if (_playbackState.value.repeatMode == Player.REPEAT_MODE_ONE) {
                        player.seekTo(0)
                        player.play()
                    } else {
                        playNext()
                    }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.e("AudioPlayerManager", "ExoPlayer playback error: ${error.errorCodeName}", error)
                _playbackState.value = _playbackState.value.copy(isBuffering = false)
                if (_playbackState.value.queue.isNotEmpty()) {
                    scope.launch {
                        delay(1200)
                        playNext()
                    }
                }
            }
        })
    }

    private fun ensureServiceRunning() {
        try {
            val intent = Intent(context, ListenQuranMediaService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            android.util.Log.e("AudioPlayerManager", "Failed to start media service", e)
        }
    }

    fun playSurah(surah: Surah, reciter: Reciter, startAyah: Int = 1) {
        val prevSurah = _playbackState.value.currentSurah
        val prevReciter = _playbackState.value.currentReciter
        if (prevSurah != null && prevReciter != null && (prevSurah.id != surah.id || prevReciter.id != reciter.id)) {
            history.add(QueueItem(surah = prevSurah, reciter = prevReciter))
            if (history.size > 50) history.removeAt(0)
        }

        ensureServiceRunning()
        _currentPositionMs.value = 0L
        scope.launch {
            _playbackState.value = _playbackState.value.copy(
                currentSurah = surah,
                currentReciter = reciter,
                isRadio = false,
                radioStation = null,
                isBuffering = true,
                currentAyahNumber = startAyah,
            )

            val audioData = quranRepository.getChapterAudio(reciterId = reciter.id, chapterId = surah.id)
            if (audioData != null) {
                val artworkUri = android.net.Uri.parse("android.resource://${context.packageName}/${com.listenquran.R.mipmap.ic_launcher}")
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
                    .setUri(audioData.audioUrl)
                    .setMediaMetadata(mediaMetadata)
                    .build()

                player.setMediaItem(mediaItem)
                player.prepare()

                _playbackState.value = _playbackState.value.copy(
                    verseTimings = audioData.timestamps,
                )

                if (startAyah > 1) {
                    val targetTiming = audioData.timestamps.find { it.verseKey == "${surah.id}:$startAyah" }
                    if (targetTiming != null) {
                        player.seekTo(targetTiming.timestampFrom)
                        _currentPositionMs.value = targetTiming.timestampFrom
                    }
                }

                player.play()
            }
        }
    }

    fun playRadio(station: RadioStation) {
        ensureServiceRunning()
        _currentPositionMs.value = 0L
        scope.launch {
            _playbackState.value = _playbackState.value.copy(
                isRadio = true,
                radioStation = station,
                currentSurah = null,
                currentReciter = null,
                verseTimings = emptyList(),
                isBuffering = true,
            )

            val artworkUri = android.net.Uri.parse("android.resource://${context.packageName}/${com.listenquran.R.mipmap.ic_launcher}")
            val mediaMetadata = MediaMetadata.Builder()
                .setTitle(station.name)
                .setArtist(station.reciterName ?: "Quran Radio")
                .setAlbumTitle("Quran Radio Live")
                .setAlbumArtist(station.reciterName ?: "Quran Radio")
                .setDisplayTitle(station.name)
                .setSubtitle(station.reciterName ?: "24/7 Live Stream")
                .setDescription("24/7 Live Quran Stream")
                .setArtworkUri(artworkUri)
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
                .build()

            val mediaItem = MediaItem.Builder()
                .setUri(station.streamUrl)
                .setMediaMetadata(mediaMetadata)
                .build()

            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()
        }
    }

    fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
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
        player.playbackParameters = PlaybackParameters(speed)
        _playbackState.value = _playbackState.value.copy(playbackSpeed = speed)
    }

    fun addToQueue(surah: Surah, reciter: Reciter? = null, startAyah: Int = 1) {
        val targetReciter = reciter ?: _playbackState.value.currentReciter ?: return
        val item = QueueItem(surah = surah, reciter = targetReciter, startAyah = startAyah)
        _playbackState.value = _playbackState.value.copy(queue = _playbackState.value.queue + item)
    }

    fun playNextInQueue(surah: Surah, reciter: Reciter? = null, startAyah: Int = 1) {
        val targetReciter = reciter ?: _playbackState.value.currentReciter ?: return
        val item = QueueItem(surah = surah, reciter = targetReciter, startAyah = startAyah)
        _playbackState.value = _playbackState.value.copy(queue = listOf(item) + _playbackState.value.queue)
    }

    fun playQueueItem(index: Int) {
        val currentQueue = _playbackState.value.queue
        if (index in currentQueue.indices) {
            val target = currentQueue[index]
            val remaining = currentQueue.toMutableList().apply { removeAt(index) }
            _playbackState.value = _playbackState.value.copy(queue = remaining)
            playSurah(target.surah, target.reciter, target.startAyah)
        }
    }

    fun removeFromQueue(index: Int) {
        val currentQueue = _playbackState.value.queue.toMutableList()
        if (index in currentQueue.indices) {
            currentQueue.removeAt(index)
            _playbackState.value = _playbackState.value.copy(queue = currentQueue)
        }
    }

    fun clearQueue() {
        _playbackState.value = _playbackState.value.copy(queue = emptyList())
    }

    fun toggleShuffle() {
        val newShuffle = !_playbackState.value.isShuffleEnabled
        _playbackState.value = _playbackState.value.copy(isShuffleEnabled = newShuffle)
    }

    fun toggleRepeat() {
        val nextRepeat = when (_playbackState.value.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        player.repeatMode = nextRepeat
        _playbackState.value = _playbackState.value.copy(repeatMode = nextRepeat)
    }

    fun playNext() {
        val q = _playbackState.value.queue
        if (q.isNotEmpty()) {
            val nextItem = q.first()
            _playbackState.value = _playbackState.value.copy(queue = q.drop(1))
            playSurah(nextItem.surah, nextItem.reciter, nextItem.startAyah)
            return
        }

        val reciter = _playbackState.value.currentReciter ?: return
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
        // Standard player behavior: restart current track if already played > 3 seconds
        if (player.currentPosition > 3000L) {
            seekTo(0L)
            return
        }

        // Return to last played item from history if available
        if (history.isNotEmpty()) {
            val prevItem = history.removeAt(history.lastIndex)
            playSurah(prevItem.surah, prevItem.reciter, prevItem.startAyah)
            return
        }

        // Fallback to sequential previous Surah
        val current = _playbackState.value.currentSurah ?: return
        val prevId = if (current.id > 1) current.id - 1 else 114
        val reciter = _playbackState.value.currentReciter ?: return
        scope.launch {
            val prevSurah = quranRepository.getSurahById(prevId)
            if (prevSurah != null) {
                playSurah(prevSurah, reciter)
            }
        }
    }

    fun startHifzLoop(config: HifzLoopConfig) {
        _playbackState.value = _playbackState.value.copy(
            isHifzActive = true,
            hifzConfig = config,
            hifzCurrentLoop = 1,
        )
        seekToAyah(config.startAyah)
        if (!player.isPlaying) {
            player.play()
        }
    }

    fun stopHifzLoop() {
        _playbackState.value = _playbackState.value.copy(
            isHifzActive = false,
            hifzConfig = null,
            hifzCurrentLoop = 0,
        )
    }

    fun setStopAtEndOfSurah(enable: Boolean) {
        _playbackState.value = _playbackState.value.copy(
            stopAtEndOfSurah = enable,
            sleepTimerRemainingSeconds = null
        )
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        _playbackState.value = _playbackState.value.copy(stopAtEndOfSurah = false)

        if (minutes <= 0) {
            _playbackState.value = _playbackState.value.copy(sleepTimerRemainingSeconds = null)
            return
        }

        val totalSeconds = minutes * 60L
        _playbackState.value = _playbackState.value.copy(sleepTimerRemainingSeconds = totalSeconds)

        sleepTimerJob = scope.launch {
            var remaining = totalSeconds
            while (remaining > 0) {
                delay(1000L)
                remaining--
                _playbackState.value = _playbackState.value.copy(sleepTimerRemainingSeconds = remaining)
            }
            player.pause()
            _playbackState.value = _playbackState.value.copy(sleepTimerRemainingSeconds = null)
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
                val pos = player.currentPosition
                val dur = if (player.duration > 0) player.duration else _playbackState.value.durationMs

                // High-frequency position update decoupled from main PlaybackState
                _currentPositionMs.value = pos

                // Only update duration if it transitioned from 0 or changed significantly
                if (_playbackState.value.durationMs != dur && dur > 0) {
                    _playbackState.value = _playbackState.value.copy(durationMs = dur)
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
            // CRITICAL: Only emit state update when ayah or word index actually changes!
            // Previously this assigned _playbackState.value 10-20 times per second unconditionally,
            // triggering a recomposition storm across the entire app tree!
            if (_playbackState.value.currentAyahNumber != ayahNum || _playbackState.value.currentWordIndex != wordIdx) {
                _playbackState.value = _playbackState.value.copy(
                    currentAyahNumber = ayahNum,
                    currentWordIndex = wordIdx,
                )
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
                    player.pause()
                    if (config.delaySeconds > 0) {
                        delay(config.delaySeconds * 1000L)
                    }
                    _playbackState.value = _playbackState.value.copy(hifzCurrentLoop = currentLoop + 1)
                    seekToAyah(config.startAyah)
                    player.play()
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
}
