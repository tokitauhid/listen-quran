package com.listenquran.player

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSessionAndProgressTest {

    @Test
    fun `SurahProgressRules ignores under 30s, keeps partial, clears at 95 percent, and caps at 50`() {
        var list = emptyList<SurahResumeProgress>()

        // Under 30s -> ignored
        list = SurahProgressRules.applyUpdate(list, surahId = 2, reciterId = 7, positionMs = 20_000L, durationMs = 1_000_000L, lastAyahNumber = 3, nowMs = 100L)
        assertTrue(list.isEmpty())

        // 45s of 1000s -> saved
        list = SurahProgressRules.applyUpdate(list, surahId = 2, reciterId = 7, positionMs = 45_000L, durationMs = 1_000_000L, lastAyahNumber = 5, nowMs = 200L)
        assertEquals(1, list.size)
        assertEquals(45_000L, list.first().positionMs)
        assertEquals(5, list.first().lastAyahNumber)

        // >= 95% -> cleared
        list = SurahProgressRules.applyUpdate(list, surahId = 2, reciterId = 7, positionMs = 955_000L, durationMs = 1_000_000L, lastAyahNumber = 280, nowMs = 300L)
        assertTrue(list.isEmpty())

        // Cap at 50 entries newest-first
        for (i in 1..60) {
            list = SurahProgressRules.applyUpdate(
                list,
                surahId = i,
                reciterId = 7,
                positionMs = 60_000L,
                durationMs = 300_000L,
                lastAyahNumber = 2,
                nowMs = i.toLong(),
            )
        }
        assertEquals(50, list.size)
        assertEquals(60, list.first().surahId)
        assertEquals(11, list.last().surahId)
    }

    @Test
    fun `SavedPlaybackSession Moshi JSON round-trip preserves queue, shuffle, speed, and clip bounds`() {
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        val adapter = moshi.adapter(SavedPlaybackSession::class.java)

        val original = SavedPlaybackSession(
            items = listOf(
                SavedQueueItemDto(
                    mediaId = "surah:18:reciter:7:ayah:1",
                    uri = "https://example.com/18.mp3",
                    title = "18. Al-Kahf",
                    subtitle = "Mishari",
                    artist = "Mishari",
                    displayTitle = "18. Al-Kahf",
                    clipStartMs = 0L,
                    clipEndMs = -1L,
                ),
                SavedQueueItemDto(
                    mediaId = "surah:2:reciter:7:ayah:255:end:255",
                    uri = "https://example.com/2.mp3",
                    title = "2. Al-Baqarah",
                    clipStartMs = 123_000L,
                    clipEndMs = 175_000L,
                ),
            ),
            currentIndex = 1,
            positionMs = 18_400L,
            isShuffleEnabled = true,
            shuffledIndices = listOf(1, 0),
            repeatMode = 2,
            playbackSpeed = 1.25f,
            sourceTitle = "Friday Mix",
            lastRadioStationId = null,
            savedAt = 1_700_000_000_000L,
        )

        val json = adapter.toJson(original)
        val decoded = adapter.fromJson(json)
        assertNotNull(decoded)
        assertEquals(original, decoded)
    }
}
