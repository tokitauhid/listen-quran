package com.listenquran.player

import com.listenquran.domain.model.VerseTiming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaIdStartAyahParsingTest {

    private val sampleAlBaqarahTimings = listOf(
        VerseTiming(verseKey = "2:1", timestampFrom = 4500L, timestampTo = 8200L, duration = 3700L),
        VerseTiming(verseKey = "2:2", timestampFrom = 8200L, timestampTo = 14000L, duration = 5800L),
        VerseTiming(verseKey = "2:5", timestampFrom = 25000L, timestampTo = 32000L, duration = 7000L),
        VerseTiming(verseKey = "2:255", timestampFrom = 2400000L, timestampTo = 2452000L, duration = 52000L),
        VerseTiming(verseKey = "2:285", timestampFrom = 2700000L, timestampTo = 2735000L, duration = 35000L),
        VerseTiming(verseKey = "2:286", timestampFrom = 2735000L, timestampTo = 2790000L, duration = 55000L),
    )

    @Test
    fun standardMediaIdWithoutAyahDefaultsTo1() {
        assertEquals(1, PlaybackQueueManager.parseStartAyah("surah:2:reciter:7:uid:abc-123"))
        assertEquals(1, PlaybackQueueManager.parseStartAyah("surah:114:reciter:3:uid:xyz"))
        assertNull(PlaybackQueueManager.parseEndAyah("surah:2:reciter:7:uid:abc-123"))
    }

    @Test
    fun encodedAyahInMediaIdIsParsedAccurately() {
        assertEquals(255, PlaybackQueueManager.parseStartAyah("surah:2:reciter:7:ayah:255:uid:abc-123"))
        assertEquals(285, PlaybackQueueManager.parseStartAyah("surah:2:reciter:7:ayah:285:uid:xyz"))
        assertEquals(87, PlaybackQueueManager.parseStartAyah("surah:21:reciter:2:ayah:87:uid:def"))
    }

    @Test
    fun encodedStartAndEndAyahRoundTripsThroughEncodeAndParse() {
        val singleId = PlaybackQueueManager.encodeSurahMediaId(
            surahId = 2,
            reciterId = 7,
            startAyah = 255,
            endAyah = 255,
            queueId = "q-1",
        )
        assertEquals("surah:2:reciter:7:ayah:255:end:255:uid:q-1", singleId)
        assertEquals(255, PlaybackQueueManager.parseStartAyah(singleId))
        assertEquals(255, PlaybackQueueManager.parseEndAyah(singleId))

        val rangeFromOneId = PlaybackQueueManager.encodeSurahMediaId(
            surahId = 2,
            reciterId = 7,
            startAyah = 1,
            endAyah = 5,
            queueId = "q-2",
        )
        assertEquals("surah:2:reciter:7:ayah:1:end:5:uid:q-2", rangeFromOneId)
        assertEquals(1, PlaybackQueueManager.parseStartAyah(rangeFromOneId))
        assertEquals(5, PlaybackQueueManager.parseEndAyah(rangeFromOneId))

        val fullSurahId = PlaybackQueueManager.encodeSurahMediaId(
            surahId = 18,
            reciterId = 7,
            startAyah = 1,
            endAyah = null,
            queueId = "q-3",
        )
        assertEquals("surah:18:reciter:7:uid:q-3", fullSurahId)
        assertEquals(1, PlaybackQueueManager.parseStartAyah(fullSurahId))
        assertNull(PlaybackQueueManager.parseEndAyah(fullSurahId))
    }

    @Test
    fun invalidOrZeroAyahValuesClampOrFallbackTo1() {
        assertEquals(1, PlaybackQueueManager.parseStartAyah("surah:2:reciter:7:ayah:0:uid:abc"))
        assertEquals(1, PlaybackQueueManager.parseStartAyah("surah:2:reciter:7:ayah:-5:uid:abc"))
        assertEquals(1, PlaybackQueueManager.parseStartAyah("surah:2:reciter:7:ayah:notanumber:uid:abc"))
        assertEquals(1, PlaybackQueueManager.parseStartAyah("radio:curated_1:12345:18:7"))
        assertEquals(1, PlaybackQueueManager.parseStartAyah(""))
        assertNull(PlaybackQueueManager.parseEndAyah("surah:2:reciter:7:ayah:255:end:0:uid:abc"))
        assertNull(PlaybackQueueManager.parseEndAyah("surah:2:reciter:7:ayah:255:end:bad:uid:abc"))
    }

    @Test
    fun resolveAyahClipBoundsHandlesSingleRangeAndUnboundedCorrectly() {
        // Full surah (endAyah = null) -> null (no clipping)
        assertNull(resolveAyahClipBounds(2, 1, null, sampleAlBaqarahTimings))
        // Mid-surah unbounded (e.g. Juz browser) -> null (no clipping)
        assertNull(resolveAyahClipBounds(2, 255, null, sampleAlBaqarahTimings))

        // Single ayah (Ayatul Kursi 2:255)
        val ayatulKursi = resolveAyahClipBounds(2, 255, 255, sampleAlBaqarahTimings)!!
        assertEquals(2400000L, ayatulKursi.startMs)
        assertEquals(2452000L, ayatulKursi.endMs)
        assertEquals(52000L, ayatulKursi.durationMs)

        // Multi-ayah range starting mid-surah (2:285-286)
        val closingBaqarah = resolveAyahClipBounds(2, 285, 286, sampleAlBaqarahTimings)!!
        assertEquals(2700000L, closingBaqarah.startMs)
        assertEquals(2790000L, closingBaqarah.endMs)
        assertEquals(90000L, closingBaqarah.durationMs)

        // Range starting at Ayah 1 (2:1-5) keeps startMs = 0L to include Bismillah
        val openingBaqarah = resolveAyahClipBounds(2, 1, 5, sampleAlBaqarahTimings)!!
        assertEquals(0L, openingBaqarah.startMs)
        assertEquals(32000L, openingBaqarah.endMs)
        assertEquals(32000L, openingBaqarah.durationMs)

        // Invalid bounds or missing timings -> null
        assertNull(resolveAyahClipBounds(2, 286, 285, sampleAlBaqarahTimings))
        assertNull(resolveAyahClipBounds(2, 255, 255, emptyList()))
        assertNull(resolveAyahClipBounds(2, 10, 10, sampleAlBaqarahTimings))
    }

    @Test
    fun computeInitialStartPositionMsNeverDoubleOffsetsClippedItems() {
        assertEquals(
            0L,
            computeInitialStartPositionMs(
                isClipped = true,
                surahId = 2,
                startAyah = 255,
                timings = sampleAlBaqarahTimings,
            )
        )
        assertEquals(
            2400000L,
            computeInitialStartPositionMs(
                isClipped = false,
                surahId = 2,
                startAyah = 255,
                timings = sampleAlBaqarahTimings,
            )
        )
        assertEquals(
            0L,
            computeInitialStartPositionMs(
                isClipped = false,
                surahId = 2,
                startAyah = 1,
                timings = sampleAlBaqarahTimings,
            )
        )
    }
}
