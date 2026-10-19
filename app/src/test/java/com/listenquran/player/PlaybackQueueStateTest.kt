package com.listenquran.player

import com.listenquran.domain.model.QueueItem
import com.listenquran.domain.model.Reciter
import com.listenquran.domain.model.Surah
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackQueueStateTest {

    private val testReciter = Reciter(
        id = 7,
        name = "Mishari",
        englishName = "Mishari Rashid al-Afasy",
        audioServer = "https://server.com"
    )

    private fun makeSurah(id: Int, name: String, verses: Int) = Surah(
        id = id,
        nameSimple = name,
        nameArabic = "سورة",
        revelationPlace = "makkah",
        versesCount = verses,
        translatedName = name
    )

    @Test
    fun testTotalVersesCountAndEstimatedMinutes() {
        val surah1 = makeSurah(1, "Al-Fatihah", 7)
        val surah2 = makeSurah(112, "Al-Ikhlas", 4)
        val surah3 = makeSurah(113, "Al-Falaq", 5)

        val state = PlaybackQueueState(
            currentItem = QueueItem(surah = surah1, reciter = testReciter, timelineIndex = 0),
            upcomingItems = listOf(
                QueueItem(surah = surah2, reciter = testReciter, timelineIndex = 1),
                QueueItem(surah = surah3, reciter = testReciter, timelineIndex = 2)
            ),
            historyItems = emptyList(),
            sourceTitle = "The 4 Quls",
            isShuffleEnabled = false,
            isRadio = false,
            totalCount = 3
        )

        // Verses count = 7 (current) + 4 + 5 (upcoming) = 16
        assertEquals(16, state.totalVersesCount)
        // 16 verses * 4.5s / 60s = 1.2 min -> 1 minute
        assertEquals(1, state.estimatedRemainingMinutes)
        assertEquals(3, state.allItems.size)
        assertFalse(state.isRadio)
    }

    @Test
    fun testRadioStateHasZeroUpcomingAndRadioTrue() {
        val state = PlaybackQueueState(
            currentItem = null,
            upcomingItems = emptyList(),
            historyItems = emptyList(),
            sourceTitle = "Quran.com Radio",
            isShuffleEnabled = false,
            isRadio = true,
            totalCount = 0
        )

        assertTrue(state.isRadio)
        assertEquals(0, state.totalCount)
        assertTrue(state.upcomingItems.isEmpty())
        assertTrue(state.historyItems.isEmpty())
    }
}
