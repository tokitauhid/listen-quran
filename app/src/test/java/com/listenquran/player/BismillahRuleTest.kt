package com.listenquran.player

import com.listenquran.domain.model.SegmentTiming
import com.listenquran.domain.model.VerseTiming
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BismillahRuleTest {

    @Test
    fun `never plays Bismillah before Surah 1 or Surah 9`() {
        val timingsStartingAtZero = listOf(
            VerseTiming("1:1", 0L, 6000L, 6000L, listOf(SegmentTiming(1, 0L, 1200L)))
        )
        assertFalse(BismillahRule.shouldPlayBismillah(1, timingsStartingAtZero))
        assertFalse(BismillahRule.shouldPlayBismillah(9, timingsStartingAtZero))
    }

    @Test
    fun `skips Bismillah when timings are empty`() {
        assertFalse(BismillahRule.shouldPlayBismillah(2, emptyList()))
    }

    @Test
    fun `plays Bismillah when Ayah 1 starts near zero (no preamble in file)`() {
        val timings = listOf(
            VerseTiming("67:1", 0L, 10790L, 10790L, listOf(SegmentTiming(1, 0L, 1040L), SegmentTiming(2, 1040L, 1990L)))
        )
        assertTrue(BismillahRule.shouldPlayBismillah(67, timings))
    }

    @Test
    fun `skips Bismillah when Ayah 1 first word starts at or after 3000ms (preamble already present)`() {
        val timingsWithPreamble = listOf(
            VerseTiming("67:1", 0L, 14000L, 14000L, listOf(SegmentTiming(1, 3500L, 4600L)))
        )
        assertFalse(BismillahRule.shouldPlayBismillah(67, timingsWithPreamble))

        val verseOnlyWithOffset = listOf(
            VerseTiming("67:1", 4200L, 14000L, 9800L, emptyList())
        )
        assertFalse(BismillahRule.shouldPlayBismillah(67, verseOnlyWithOffset))
    }
}
