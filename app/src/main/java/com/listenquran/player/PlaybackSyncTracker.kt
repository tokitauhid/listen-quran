package com.listenquran.player

import androidx.compose.runtime.Immutable
import androidx.media3.common.Player
import com.listenquran.data.repository.khatam.KhatamTrackerRepository
import com.listenquran.domain.model.SegmentTiming
import com.listenquran.domain.model.VerseTiming
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Immutable
data class PlaybackSyncState(
    val surahId: Int? = null,
    val reciterId: Int? = null,
    val currentVerseKey: String? = null,
    val currentAyahNumber: Int = 1,
    val currentWordIndex: Int = 0,
    val isSyncAvailable: Boolean = false,
    val hasWordTimings: Boolean = false,
)

@Singleton
class PlaybackSyncTracker internal constructor(
    private val khatamTrackerRepository: KhatamTrackerRepository?,
    @Suppress("UNUSED_PARAMETER") marker: Any? = null,
) {
    @Inject
    constructor(khatamTrackerRepository: KhatamTrackerRepository) : this(khatamTrackerRepository, null)

    // Constructor for testing without Hilt/Android dependencies
    constructor() : this(null, null)

    var onAyahProgressRecorded: ((surahId: Int, ayahNumber: Int) -> Unit)? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _syncState = MutableStateFlow(PlaybackSyncState())
    val syncState: StateFlow<PlaybackSyncState> = _syncState.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _currentWordIndex = MutableStateFlow(0)
    val currentWordIndex: StateFlow<Int> = _currentWordIndex.asStateFlow()

    private var pollJob: Job? = null
    private var sortedTimings: List<VerseTiming> = emptyList()

    // Khatam 80% listening threshold state
    private var trackedSurahId: Int? = null
    private var trackedAyahNumber: Int? = null
    private var trackedAyahAccumulatedMs: Long = 0L
    private var trackedAyahLastPos: Long = -1L
    private var trackedAyahRecorded: Boolean = false

    var isHifzActive: Boolean = false
    var isRadio: Boolean = false

    /**
     * Resets timing data immediately when a media item transition starts,
     * ensuring stale timings from the previous Surah are never matched.
     */
    fun onMediaItemChanged(surahId: Int, reciterId: Int, startAyah: Int = 1) {
        sortedTimings = emptyList()
        resetKhatamTracking()
        _currentPositionMs.value = 0L
        _currentWordIndex.value = 0
        _syncState.value = PlaybackSyncState(
            surahId = surahId,
            reciterId = reciterId,
            currentVerseKey = "$surahId:$startAyah",
            currentAyahNumber = startAyah,
            currentWordIndex = 0,
            isSyncAvailable = false,
            hasWordTimings = false,
        )
    }

    /**
     * Supplies newly resolved timings for the active Surah & Reciter.
     * Sorts both verse boundaries and word segments for safe binary searching.
     */
    fun setTimings(surahId: Int, reciterId: Int, timings: List<VerseTiming>, initialPosMs: Long = 0L) {
        if (timings.isEmpty()) {
            sortedTimings = emptyList()
            _syncState.update { current ->
                current.copy(
                    surahId = surahId,
                    reciterId = reciterId,
                    isSyncAvailable = false,
                    hasWordTimings = false,
                )
            }
            return
        }

        // Ensure verse timings and word segments are strictly sorted by start timestamp
        val prepared = timings.map { verse ->
            if (verse.segments.isNotEmpty()) {
                verse.copy(segments = verse.segments.sortedBy { it.startMs })
            } else {
                verse
            }
        }.sortedBy { it.timestampFrom }

        sortedTimings = prepared
        val hasWordTimings = prepared.any { it.segments.isNotEmpty() }

        val (matchedAyah, matchedWord, matchedKey) = matchPosition(initialPosMs, prepared)

        _currentWordIndex.value = matchedWord
        _syncState.update { current ->
            current.copy(
                surahId = surahId,
                reciterId = reciterId,
                currentVerseKey = matchedKey ?: "$surahId:$matchedAyah",
                currentAyahNumber = matchedAyah,
                currentWordIndex = matchedWord,
                isSyncAvailable = true,
                hasWordTimings = hasWordTimings,
            )
        }
    }

    /**
     * Starts or stops the efficient 60-80ms polling loop tied to playback state.
     */
    fun onPlayStateChanged(isPlaying: Boolean, playerProvider: () -> Player?) {
        pollJob?.cancel()
        pollJob = null

        if (isPlaying) {
            pollJob = scope.launch {
                while (isActive) {
                    val player = playerProvider()
                    if (player != null && player.isPlaying) {
                        val pos = player.currentPosition
                        _currentPositionMs.value = pos
                        processPosition(pos)
                    }
                    delay(75L) // Poll every 75ms for responsive word-by-word sync
                }
            }
        }
    }

    /**
     * Immediately evaluates position upon seeking (scrubber or verse click).
     */
    fun onSeek(newPositionMs: Long) {
        _currentPositionMs.value = newPositionMs
        // Reset delta jump tracking for Khatam to prevent counting seeks as listened
        trackedAyahLastPos = newPositionMs
        processPosition(newPositionMs)
    }

    private fun processPosition(pos: Long) {
        val timings = sortedTimings
        if (timings.isEmpty()) {
            return
        }

        val (ayahNum, wordIdx, verseKey) = matchPosition(pos, timings)

        if (_currentWordIndex.value != wordIdx) {
            _currentWordIndex.value = wordIdx
        }

        val state = _syncState.value
        if (state.currentAyahNumber != ayahNum || state.currentWordIndex != wordIdx || state.currentVerseKey != verseKey) {
            _syncState.update {
                it.copy(
                    currentAyahNumber = ayahNum,
                    currentWordIndex = wordIdx,
                    currentVerseKey = verseKey,
                    isSyncAvailable = true,
                )
            }
        }

        checkKhatamTracking(pos, timings)
    }

    private fun matchPosition(pos: Long, timings: List<VerseTiming>): Triple<Int, Int, String?> {
        val verseIndex = findVerseIndex(pos, timings)
        if (verseIndex < 0 || verseIndex >= timings.size) {
            val fallbackAyah = 1
            return Triple(fallbackAyah, 0, null)
        }

        val timing = timings[verseIndex]
        val ayahNum = timing.verseKey.substringAfter(":").toIntOrNull() ?: (verseIndex + 1)
        val wordIdx = findWordIndex(pos, timing.segments)

        return Triple(ayahNum, wordIdx, timing.verseKey)
    }

    /**
     * O(log N) binary search for the verse at the given position in milliseconds.
     */
    fun findVerseIndex(posMs: Long, timings: List<VerseTiming>): Int {
        if (timings.isEmpty()) return -1
        if (posMs < timings.first().timestampFrom) return 0
        if (posMs >= timings.last().timestampTo) return timings.lastIndex

        var low = 0
        var high = timings.size - 1

        while (low <= high) {
            val mid = (low + high).ushr(1)
            val timing = timings[mid]

            if (posMs < timing.timestampFrom) {
                high = mid - 1
            } else if (posMs >= timing.timestampTo) {
                low = mid + 1
            } else {
                return mid
            }
        }

        // Falls in inter-verse gap between high and low -> snap to high
        return high.coerceIn(0, timings.lastIndex)
    }

    /**
     * O(log M) binary search for the word segment at the given position in milliseconds.
     */
    fun findWordIndex(posMs: Long, segments: List<SegmentTiming>): Int {
        if (segments.isEmpty()) return 0
        if (posMs < segments.first().startMs) return 0
        if (posMs > segments.last().endMs) return 0

        var low = 0
        var high = segments.size - 1

        while (low <= high) {
            val mid = (low + high).ushr(1)
            val seg = segments[mid]

            if (posMs < seg.startMs) {
                high = mid - 1
            } else if (posMs > seg.endMs) {
                low = mid + 1
            } else {
                return seg.wordIndex
            }
        }

        // In a silence gap between words
        return 0
    }

    private fun checkKhatamTracking(pos: Long, timings: List<VerseTiming>) {
        if (isRadio) {
            trackedAyahLastPos = pos
            return
        }

        val surahId = _syncState.value.surahId ?: return
        val verseIndex = findVerseIndex(pos, timings)
        if (verseIndex < 0 || verseIndex >= timings.size) return

        val timing = timings[verseIndex]
        val ayahNum = timing.verseKey.substringAfter(":").toIntOrNull() ?: (verseIndex + 1)
        val verseDuration = timing.timestampTo - timing.timestampFrom

        if (trackedSurahId != surahId || trackedAyahNumber != ayahNum) {
            trackedSurahId = surahId
            trackedAyahNumber = ayahNum
            trackedAyahAccumulatedMs = 0L
            trackedAyahLastPos = pos
            trackedAyahRecorded = false
        } else {
            if (trackedAyahLastPos >= 0L) {
                val delta = pos - trackedAyahLastPos
                if (delta in 0..1000) {
                    trackedAyahAccumulatedMs += delta
                } else {
                    // Seek occurred
                    trackedAyahAccumulatedMs = 0L
                }
            }
            trackedAyahLastPos = pos

            // 80% duration threshold of the Ayah listened
            if (!trackedAyahRecorded && verseDuration > 0 && trackedAyahAccumulatedMs >= (verseDuration * 0.8)) {
                trackedAyahRecorded = true
                onAyahProgressRecorded?.invoke(surahId, ayahNum)
                khatamTrackerRepository?.let { repo ->
                    scope.launch(Dispatchers.IO) {
                        repo.recordPlaybackAyah(surahId, ayahNum)
                    }
                }
            }
        }
    }

    private fun resetKhatamTracking() {
        trackedSurahId = null
        trackedAyahNumber = null
        trackedAyahAccumulatedMs = 0L
        trackedAyahLastPos = -1L
        trackedAyahRecorded = false
    }

    fun stopTracking() {
        pollJob?.cancel()
        pollJob = null
    }
}
