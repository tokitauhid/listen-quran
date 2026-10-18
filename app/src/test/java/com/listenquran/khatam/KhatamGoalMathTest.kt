package com.listenquran.khatam

import com.listenquran.data.local.khatam.KhatamEntity
import com.listenquran.domain.model.khatam.AyahRangeSet
import com.listenquran.domain.model.khatam.QuranAyahMetadata
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.ceil
import kotlin.math.max

class KhatamGoalMathTest {

    @Test
    fun goalPace_newKhatam30Days_calculatesDailyTarget() {
        val totalAyahs = QuranAyahMetadata.TOTAL_AYAHS // 6236
        val targetDays = 30
        val daysRemaining = 30
        val completed = 0

        val dailyTarget = ceil((totalAyahs - completed).toDouble() / daysRemaining).toInt()
        assertEquals(208, dailyTarget) // 6236 / 30 = 207.86 -> 208
    }

    @Test
    fun goalPace_midwayAheadOfSchedule_detectsOnTrack() {
        val targetDays = 30
        val daysElapsed = 10
        val daysRemaining = 20
        // Expected pace at day 10: (10 / 30) * 6236 = 2078 ayahs
        // Actual completed: 2500 ayahs (ahead of schedule)
        val completed = 2500
        val remaining = 6236 - completed
        val newDailyTarget = ceil(remaining.toDouble() / daysRemaining).toInt()

        val expectedPace = ((daysElapsed.toFloat() / targetDays.toFloat()) * 6236).toInt()
        val isOnTrack = completed >= expectedPace

        assertTrue(isOnTrack)
        assertEquals(187, newDailyTarget) // 3736 / 20 = 186.8 -> 187
    }

    @Test
    fun goalPace_midwayBehindSchedule_detectsGentlePaceAdjustment() {
        val targetDays = 30
        val daysElapsed = 15
        val daysRemaining = 15
        // Expected at day 15: 3118 ayahs
        // Actual: 2000 ayahs
        val completed = 2000
        val remaining = 6236 - completed
        val newDailyTarget = ceil(remaining.toDouble() / daysRemaining).toInt()

        val expectedPace = ((daysElapsed.toFloat() / targetDays.toFloat()) * 6236).toInt()
        val isOnTrack = completed >= expectedPace

        assertFalse(isOnTrack)
        assertEquals(283, newDailyTarget) // 4236 / 15 = 282.4 -> 283
    }
}
