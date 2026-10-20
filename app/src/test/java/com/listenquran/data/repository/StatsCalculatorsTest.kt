package com.listenquran.data.repository

import com.listenquran.data.local.dao.ReciterDurationShare
import com.listenquran.data.local.entity.ListeningStatEntity
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class StatsCalculatorsTest {

    private val zoneId = ZoneId.systemDefault()

    private fun dateToTimestamp(date: LocalDate, hour: Int = 12): Long {
        return date.atTime(hour, 0).atZone(zoneId).toInstant().toEpochMilli()
    }

    @Test
    fun `calculateStreak returns 0 for empty list`() {
        val streak = StatsCalculators.calculateStreak(emptyList<ListeningStatEntity>())
        assertEquals(0, streak)
    }

    @Test
    fun `calculateStreak ignores sessions under 5 seconds`() {
        val today = LocalDate.now(zoneId)
        val sessions = listOf(
            ListeningStatEntity(
                surahNumber = 1,
                reciterId = 7,
                durationSeconds = 4, // under 5s threshold
                timestamp = dateToTimestamp(today)
            )
        )
        val streak = StatsCalculators.calculateStreak(sessions)
        assertEquals(0, streak)
    }

    @Test
    fun `calculateStreak returns 1 when listened only today`() {
        val today = LocalDate.now(zoneId)
        val sessions = listOf(
            ListeningStatEntity(
                surahNumber = 1,
                reciterId = 7,
                durationSeconds = 60,
                timestamp = dateToTimestamp(today)
            )
        )
        val streak = StatsCalculators.calculateStreak(sessions)
        assertEquals(1, streak)
    }

    @Test
    fun `calculateStreak returns 1 when listened only yesterday`() {
        val yesterday = LocalDate.now(zoneId).minusDays(1)
        val sessions = listOf(
            ListeningStatEntity(
                surahNumber = 1,
                reciterId = 7,
                durationSeconds = 120,
                timestamp = dateToTimestamp(yesterday)
            )
        )
        val streak = StatsCalculators.calculateStreak(sessions)
        assertEquals(1, streak)
    }

    @Test
    fun `calculateStreak returns 0 when last session was 2 days ago`() {
        val twoDaysAgo = LocalDate.now(zoneId).minusDays(2)
        val sessions = listOf(
            ListeningStatEntity(
                surahNumber = 1,
                reciterId = 7,
                durationSeconds = 300,
                timestamp = dateToTimestamp(twoDaysAgo)
            )
        )
        val streak = StatsCalculators.calculateStreak(sessions)
        assertEquals(0, streak)
    }

    @Test
    fun `calculateStreak counts consecutive days backwards from today`() {
        val today = LocalDate.now(zoneId)
        val sessions = (0..4).map { daysAgo ->
            ListeningStatEntity(
                surahNumber = 1,
                reciterId = 7,
                durationSeconds = 100,
                timestamp = dateToTimestamp(today.minusDays(daysAgo.toLong()))
            )
        }
        val streak = StatsCalculators.calculateStreak(sessions)
        assertEquals(5, streak)
    }

    @Test
    fun `calculateStreak stops at gap in history`() {
        val today = LocalDate.now(zoneId)
        val sessions = listOf(
            // Today
            ListeningStatEntity(surahNumber = 1, reciterId = 7, durationSeconds = 100, timestamp = dateToTimestamp(today)),
            // 2 days ago (yesterday is missing!)
            ListeningStatEntity(surahNumber = 1, reciterId = 7, durationSeconds = 100, timestamp = dateToTimestamp(today.minusDays(2))),
            // 3 days ago
            ListeningStatEntity(surahNumber = 1, reciterId = 7, durationSeconds = 100, timestamp = dateToTimestamp(today.minusDays(3)))
        )
        val streak = StatsCalculators.calculateStreak(sessions)
        assertEquals(1, streak)
    }

    @Test
    fun `calculateStreak dedupes multiple sessions on same day`() {
        val today = LocalDate.now(zoneId)
        val yesterday = today.minusDays(1)
        val sessions = listOf(
            ListeningStatEntity(surahNumber = 1, reciterId = 7, durationSeconds = 10, timestamp = dateToTimestamp(today, 8)),
            ListeningStatEntity(surahNumber = 2, reciterId = 7, durationSeconds = 20, timestamp = dateToTimestamp(today, 14)),
            ListeningStatEntity(surahNumber = 3, reciterId = 7, durationSeconds = 30, timestamp = dateToTimestamp(today, 20)),
            ListeningStatEntity(surahNumber = 1, reciterId = 7, durationSeconds = 40, timestamp = dateToTimestamp(yesterday, 10))
        )
        val streak = StatsCalculators.calculateStreak(sessions)
        assertEquals(2, streak)
    }

    @Test
    fun `calculateWeeklyActivity returns exactly 7 days ending with today`() {
        val today = LocalDate.now(zoneId)
        val sessions = listOf(
            ListeningStatEntity(
                surahNumber = 1,
                reciterId = 7,
                durationSeconds = 120, // 2 minutes
                timestamp = dateToTimestamp(today)
            )
        )

        val weekly = StatsCalculators.calculateWeeklyActivity(sessions)
        assertEquals(7, weekly.size)

        // Last item is today
        val lastItem = weekly.last()
        assertEquals(2, lastItem.minutes)

        // Total minutes sum matches 2
        val totalMinutes = weekly.sumOf { it.minutes }
        assertEquals(2, totalMinutes)
    }

    @Test
    fun `calculateReciterShares computes correct percentages and reciter names`() {
        val rawShares = listOf(
            ReciterDurationShare(reciterId = 7, totalSeconds = 700),
            ReciterDurationShare(reciterId = 1, totalSeconds = 300)
        )
        val reciterMap = mapOf(
            7 to "Mishari Rashid al-`Afasy",
            1 to "AbdulBaset AbdulSamad"
        )

        val result = StatsCalculators.calculateReciterShares(rawShares, reciterMap)
        assertEquals(2, result.size)

        assertEquals("Mishari Rashid al-`Afasy", result[0].reciterName)
        assertEquals(0.70f, result[0].percentage, 0.05f)
        assertEquals(700L, result[0].seconds)

        assertEquals("AbdulBaset AbdulSamad", result[1].reciterName)
        assertEquals(0.30f, result[1].percentage, 0.05f)
        assertEquals(300L, result[1].seconds)
    }

    @Test
    fun `calculateReciterShares groups beyond top 5 into Other bucket`() {
        val rawShares = (1..8).map { id ->
            ReciterDurationShare(reciterId = id, totalSeconds = 100L)
        }
        val reciterMap = (1..8).associateWith { "Reciter $it" }

        val result = StatsCalculators.calculateReciterShares(rawShares, reciterMap)
        assertEquals(6, result.size) // Top 5 + Other

        val other = result.last()
        assertEquals("Other", other.reciterName)
        assertEquals(-1, other.reciterId)
        assertEquals(300L, other.seconds)
    }
}
