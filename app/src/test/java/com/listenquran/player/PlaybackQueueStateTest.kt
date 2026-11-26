package com.listenquran.player

import com.listenquran.domain.model.PlaylistItem
import com.listenquran.domain.model.QueueItem
import com.listenquran.domain.model.Reciter
import com.listenquran.domain.model.Surah
import com.listenquran.ui.PlaylistQueueResolver
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

    @Test
    fun testPlaylistQueueSourceAndItemsPersistAcrossTrackTransitions() {
        val surah18 = makeSurah(18, "Al-Kahf", 110)
        val surah36 = makeSurah(36, "Yasin", 83)
        val surah67 = makeSurah(67, "Al-Mulk", 30)

        val state = PlaybackQueueState(
            currentItem = QueueItem(id = "p1", surah = surah18, reciter = testReciter, timelineIndex = 0),
            upcomingItems = listOf(
                QueueItem(id = "p2", surah = surah36, reciter = testReciter, timelineIndex = 1),
                QueueItem(id = "p3", surah = surah67, reciter = testReciter, timelineIndex = 2),
            ),
            historyItems = emptyList(),
            sourceTitle = "Friday Favorites",
            isShuffleEnabled = false,
            isRadio = false,
            totalCount = 3
        )

        assertEquals("Friday Favorites", state.sourceTitle)
        assertEquals(3, state.totalCount)
        assertEquals(listOf(18, 36, 67), state.allItems.map { it.surah.id })
    }

    @Test
    fun testPlaylistItemResolutionNeverFallsBackToFullCatalog() {
        val items = listOf(
            PlaylistItem(id = 10L, playlistId = 4L, surahNumber = 18, surahName = "Al-Kahf", surahArabic = "الكهف", reciterId = 7, reciterName = "Mishari", orderIndex = 0),
            PlaylistItem(id = 11L, playlistId = 4L, surahNumber = 36, surahName = "Yasin", surahArabic = "يس", reciterId = 7, reciterName = "Mishari", orderIndex = 1),
            PlaylistItem(id = 12L, playlistId = 4L, surahNumber = 67, surahName = "Al-Mulk", surahArabic = "الملك", reciterId = 7, reciterName = "Mishari", orderIndex = 2),
        )
        val clicked = items[1]

        // 1. Even if UI passes an empty list transiently, store items preserve the 3-track playlist queue
        val resolvedFromStore = PlaylistQueueResolver.resolve(
            clickedItem = clicked,
            explicitItems = emptyList(),
            playlistItemsFromStore = items,
            explicitTitle = null,
            storeTitle = "Friday Favorites",
        )
        assertEquals(3, resolvedFromStore.items.size)
        assertEquals(1, resolvedFromStore.startIndex)
        assertEquals("Friday Favorites", resolvedFromStore.title)
        assertEquals(listOf(18, 36, 67), resolvedFromStore.items.map { it.surahNumber })

        // 2. Even if both explicit and store lists are empty, falls back to the single clicked item (never 114 surahs)
        val resolvedSingle = PlaylistQueueResolver.resolve(
            clickedItem = clicked,
            explicitItems = null,
            playlistItemsFromStore = null,
            explicitTitle = "Friday Favorites",
            storeTitle = null,
        )
        assertEquals(1, resolvedSingle.items.size)
        assertEquals(0, resolvedSingle.startIndex)
        assertEquals(36, resolvedSingle.items.first().surahNumber)
        assertEquals("Friday Favorites", resolvedSingle.title)
    }

    @Test
    fun testBoundedAyahQueueItemsCountClippedVersesAndResolveDistinctRanges() {
        val baqarah = makeSurah(2, "Al-Baqarah", 286)
        val ikhlas = makeSurah(112, "Al-Ikhlas", 4)

        val state = PlaybackQueueState(
            currentItem = QueueItem(surah = baqarah, reciter = testReciter, startAyah = 255, endAyah = 255, timelineIndex = 0),
            upcomingItems = listOf(
                QueueItem(surah = baqarah, reciter = testReciter, startAyah = 285, endAyah = 286, timelineIndex = 1),
                QueueItem(surah = ikhlas, reciter = testReciter, startAyah = 1, endAyah = null, timelineIndex = 2),
            ),
            historyItems = emptyList(),
            sourceTitle = "Before Sleep",
            totalCount = 3,
        )

        // 1 (2:255) + 2 (2:285-286) + 4 (full Al-Ikhlas) = 7
        assertEquals(7, state.totalVersesCount)

        val opening = PlaylistItem(
            id = 0L,
            playlistId = 0L,
            surahNumber = 2,
            surahName = "Opening of Al-Baqarah",
            surahArabic = "البقرة",
            reciterId = 7,
            reciterName = "Mishari",
            startAyah = 1,
            endAyah = 5,
            orderIndex = 0,
        )
        val full = PlaylistItem(
            id = 0L,
            playlistId = 0L,
            surahNumber = 2,
            surahName = "Al-Baqarah",
            surahArabic = "البقرة",
            reciterId = 7,
            reciterName = "Mishari",
            startAyah = 1,
            endAyah = null,
            orderIndex = 1,
        )
        val resolved = PlaylistQueueResolver.resolve(
            clickedItem = full,
            explicitItems = listOf(opening, full),
            playlistItemsFromStore = null,
            explicitTitle = "Test",
            storeTitle = null,
        )
        assertEquals(1, resolved.startIndex)
    }
}
