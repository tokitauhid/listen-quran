package com.listenquran

import com.listenquran.domain.model.QueueItem
import com.listenquran.domain.model.Reciter
import com.listenquran.domain.model.Surah
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueLogicTest {

    private val testReciter = Reciter(
        id = 7,
        name = "مشاري راشد العفاسي",
        englishName = "Mishari Rashid al-`Afasy",
        style = "Murattal",
        audioServer = "https://download.quranicaudio.com/qdc/mishari_al_afasy/murattal/"
    )

    private fun makeSurah(id: Int, name: String) = Surah(
        id = id,
        nameSimple = name,
        nameComplex = name,
        nameArabic = "سورة",
        revelationPlace = "makkah",
        revelationOrder = id,
        versesCount = 10,
        pages = listOf(1, 2),
        translatedName = name
    )

    @Test
    fun testQueueOrderAndPlayNextInQueue() {
        val surah1 = makeSurah(1, "Al-Fatihah")
        val surah2 = makeSurah(2, "Al-Baqarah")
        val surah3 = makeSurah(3, "Ali 'Imran")

        var queue = listOf<QueueItem>()

        // 1. Add surah 1 and surah 2 to queue
        queue = queue + QueueItem(surah = surah1, reciter = testReciter)
        queue = queue + QueueItem(surah = surah2, reciter = testReciter)
        assertEquals(2, queue.size)
        assertEquals(1, queue[0].surah.id)
        assertEquals(2, queue[1].surah.id)

        // 2. PlayNextInQueue inserts at head
        val playNextItem = QueueItem(surah = surah3, reciter = testReciter)
        queue = listOf(playNextItem) + queue
        assertEquals(3, queue.size)
        assertEquals(3, queue[0].surah.id)
        assertEquals(1, queue[1].surah.id)
        assertEquals(2, queue[2].surah.id)

        // 3. Play next pops the head
        val nextTrack = queue.first()
        queue = queue.drop(1)
        assertEquals(3, nextTrack.surah.id)
        assertEquals(2, queue.size)
        assertEquals(1, queue.first().surah.id)

        // 4. Remove item
        queue = queue.toMutableList().apply { removeAt(0) }
        assertEquals(1, queue.size)
        assertEquals(2, queue.first().surah.id)

        // 5. Clear queue
        queue = emptyList()
        assertTrue(queue.isEmpty())
    }

    @Test
    fun testHistoryStackBehavior() {
        val history = mutableListOf<QueueItem>()
        val surah1 = makeSurah(1, "Al-Fatihah")
        val surah2 = makeSurah(2, "Al-Baqarah")

        // User plays surah 1, then surah 2
        history.add(QueueItem(surah = surah1, reciter = testReciter))
        assertEquals(1, history.size)

        // Now user presses "Previous"
        val lastPlayed = history.removeAt(history.lastIndex)
        assertEquals(1, lastPlayed.surah.id)
        assertTrue(history.isEmpty())
    }
}
