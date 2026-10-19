package com.listenquran.player

import com.listenquran.data.repository.khatam.KhatamTrackerRepository
import com.listenquran.domain.model.SegmentTiming
import com.listenquran.domain.model.VerseTiming
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PlaybackSyncTrackerTest {
    private lateinit var tracker: PlaybackSyncTracker

    @Before
    fun setUp() {
        tracker = PlaybackSyncTracker()
    }

    private fun sampleTimings(): List<VerseTiming> {
        return listOf(
            VerseTiming(
                verseKey = "1:1",
                timestampFrom = 0L,
                timestampTo = 6090L,
                duration = 6090L,
                segments = listOf(
                    SegmentTiming(wordIndex = 1, startMs = 0L, endMs = 580L),
                    SegmentTiming(wordIndex = 2, startMs = 580L, endMs = 1409L),
                    SegmentTiming(wordIndex = 3, startMs = 1409L, endMs = 2502L),
                    SegmentTiming(wordIndex = 4, startMs = 2502L, endMs = 5840L),
                )
            ),
            VerseTiming(
                verseKey = "1:2",
                timestampFrom = 6090L,
                timestampTo = 11680L,
                duration = 5590L,
                segments = listOf(
                    SegmentTiming(wordIndex = 1, startMs = 6025L, endMs = 7025L),
                    SegmentTiming(wordIndex = 2, startMs = 7025L, endMs = 7885L),
                    SegmentTiming(wordIndex = 3, startMs = 7885L, endMs = 8515L),
                    SegmentTiming(wordIndex = 4, startMs = 8515L, endMs = 11550L),
                )
            ),
            VerseTiming(
                verseKey = "1:3",
                timestampFrom = 11680L,
                timestampTo = 16300L,
                duration = 4620L,
                segments = listOf(
                    SegmentTiming(wordIndex = 1, startMs = 11615L, endMs = 12855L),
                    SegmentTiming(wordIndex = 2, startMs = 12855L, endMs = 16180L),
                )
            )
        )
    }

    @Test
    fun testEmptyTimingsReturnsGracefulFallback() {
        val emptyList = emptyList<VerseTiming>()
        assertEquals(-1, tracker.findVerseIndex(500L, emptyList))
        assertEquals(0, tracker.findWordIndex(500L, emptyList()))

        tracker.setTimings(1, 7, emptyList)
        assertFalse(tracker.syncState.value.isSyncAvailable)
        assertFalse(tracker.syncState.value.hasWordTimings)
    }

    @Test
    fun testFirstItemAndIntroSilence() {
        val delayedTimings = listOf(
            VerseTiming(
                verseKey = "1:1",
                timestampFrom = 3500L,
                timestampTo = 7000L,
                duration = 3500L,
                segments = emptyList()
            )
        )
        // Position during introductory silence (before 3500ms)
        val index = tracker.findVerseIndex(1000L, delayedTimings)
        assertEquals(0, index)
    }

    @Test
    fun testLastItemAndPastEnd() {
        val timings = sampleTimings()
        // Position past the final verse timestampTo (16300ms)
        val index = tracker.findVerseIndex(20000L, timings)
        assertEquals(2, index) // last index is 2 (verse 1:3)
    }

    @Test
    fun testExactBoundaries() {
        val timings = sampleTimings()

        // Exact start of Verse 1
        assertEquals(0, tracker.findVerseIndex(0L, timings))

        // Inside Verse 1
        assertEquals(0, tracker.findVerseIndex(3000L, timings))

        // Exact boundary at end of Verse 1 / start of Verse 2
        assertEquals(1, tracker.findVerseIndex(6090L, timings))

        // Inside Verse 2
        assertEquals(1, tracker.findVerseIndex(8000L, timings))

        // Exact boundary at end of Verse 2 / start of Verse 3
        assertEquals(2, tracker.findVerseIndex(11680L, timings))
    }

    @Test
    fun testInterVerseGap() {
        val gapTimings = listOf(
            VerseTiming("1:1", 0L, 5000L, 5000L, emptyList()),
            VerseTiming("1:2", 5500L, 10000L, 4500L, emptyList()) // 500ms gap between 5000 and 5500
        )
        // Position in the 500ms gap
        val index = tracker.findVerseIndex(5250L, gapTimings)
        assertEquals(0, index) // snapped cleanly to preceding verse
    }

    @Test
    fun testWordSegmentBoundariesAndGaps() {
        val segments = listOf(
            SegmentTiming(wordIndex = 1, startMs = 0L, endMs = 580L),
            SegmentTiming(wordIndex = 2, startMs = 650L, endMs = 1400L), // 70ms gap between 580 and 650
        )

        // Exact start
        assertEquals(1, tracker.findWordIndex(0L, segments))
        // Inside word 1
        assertEquals(1, tracker.findWordIndex(300L, segments))
        // Exact end of word 1
        assertEquals(1, tracker.findWordIndex(580L, segments))

        // Silence gap between words -> returns 0
        assertEquals(0, tracker.findWordIndex(600L, segments))

        // Inside word 2
        assertEquals(2, tracker.findWordIndex(800L, segments))

        // Past end of all words -> returns 0
        assertEquals(0, tracker.findWordIndex(2000L, segments))
    }

    @Test
    fun testWordSegmentMissingFallback() {
        // Reciters like 11 with verse timings but no word timings
        val timings = listOf(
            VerseTiming("1:1", 0L, 5000L, 5000L, segments = emptyList())
        )
        tracker.setTimings(1, 11, timings)
        assertTrue(tracker.syncState.value.isSyncAvailable)
        assertFalse(tracker.syncState.value.hasWordTimings)
        assertEquals(1, tracker.syncState.value.currentAyahNumber)
        assertEquals(0, tracker.syncState.value.currentWordIndex)
    }

    @Test
    fun testSeekingForwardAndBackward() {
        val timings = sampleTimings()
        tracker.setTimings(1, 7, timings, initialPosMs = 0L)
        assertEquals(1, tracker.syncState.value.currentAyahNumber)
        assertEquals(1, tracker.syncState.value.currentWordIndex)

        // Seek forward to Ayah 3, Word 2
        tracker.onSeek(13000L)
        assertEquals(3, tracker.syncState.value.currentAyahNumber)
        assertEquals(2, tracker.syncState.value.currentWordIndex)

        // Seek backward to Ayah 2, Word 1
        tracker.onSeek(6500L)
        assertEquals(2, tracker.syncState.value.currentAyahNumber)
        assertEquals(1, tracker.syncState.value.currentWordIndex)
    }

    @Test
    fun testMediaItemTransitionResetsState() {
        val timings = sampleTimings()
        tracker.setTimings(1, 7, timings)
        tracker.onSeek(13000L)
        assertEquals(3, tracker.syncState.value.currentAyahNumber)

        // Transition to Surah 2
        tracker.onMediaItemChanged(surahId = 2, reciterId = 7, startAyah = 1)
        assertFalse(tracker.syncState.value.isSyncAvailable)
        assertEquals(1, tracker.syncState.value.currentAyahNumber)
        assertEquals(0, tracker.syncState.value.currentWordIndex)
        assertEquals(0L, tracker.currentPositionMs.value)
    }
}
