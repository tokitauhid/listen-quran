package com.listenquran.player

import com.listenquran.domain.model.SegmentTiming
import com.listenquran.domain.model.VerseTiming
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SyncAvailabilityTest {

    private lateinit var tracker: PlaybackSyncTracker

    @Before
    fun setUp() {
        tracker = PlaybackSyncTracker()
    }

    @Test
    fun `sync availability is false initially`() {
        assertFalse(tracker.syncState.value.isSyncAvailable)
        assertFalse(tracker.syncState.value.hasWordTimings)
    }

    @Test
    fun `sync availability becomes true when timings with segments are loaded`() {
        val timings = listOf(
            VerseTiming(
                verseKey = "1:1",
                timestampFrom = 0L,
                timestampTo = 5000L,
                duration = 5000L,
                segments = listOf(
                    SegmentTiming(wordIndex = 1, startMs = 0L, endMs = 1000L)
                )
            )
        )
        tracker.setTimings(surahId = 1, reciterId = 7, timings = timings)

        assertTrue(tracker.syncState.value.isSyncAvailable)
        assertTrue(tracker.syncState.value.hasWordTimings)
    }

    @Test
    fun `sync availability is false when reciter has no timing data`() {
        tracker.setTimings(surahId = 1, reciterId = 999, timings = emptyList())

        assertFalse(tracker.syncState.value.isSyncAvailable)
        assertFalse(tracker.syncState.value.hasWordTimings)
    }

    @Test
    fun `changing from sync-enabled reciter to non-synced reciter updates state without stale data`() {
        val timings = listOf(
            VerseTiming(
                verseKey = "1:1",
                timestampFrom = 0L,
                timestampTo = 5000L,
                duration = 5000L,
                segments = emptyList()
            )
        )
        // Mishari has timings
        tracker.setTimings(surahId = 1, reciterId = 7, timings = timings)
        assertTrue(tracker.syncState.value.isSyncAvailable)

        // Switching to external reciter without timings
        tracker.setTimings(surahId = 1, reciterId = 15, timings = emptyList())
        assertFalse(tracker.syncState.value.isSyncAvailable)
        assertFalse(tracker.syncState.value.hasWordTimings)
        assertEquals(1, tracker.syncState.value.currentAyahNumber)
    }

    @Test
    fun `onMediaItemChanged clears sync state`() {
        val timings = listOf(
            VerseTiming(
                verseKey = "1:1",
                timestampFrom = 0L,
                timestampTo = 5000L,
                duration = 5000L,
                segments = listOf(SegmentTiming(1, 0L, 1000L))
            )
        )
        tracker.setTimings(1, 7, timings)
        assertTrue(tracker.syncState.value.isSyncAvailable)

        tracker.onMediaItemChanged(2, 7)
        assertFalse(tracker.syncState.value.isSyncAvailable)
        assertFalse(tracker.syncState.value.hasWordTimings)
    }
}
