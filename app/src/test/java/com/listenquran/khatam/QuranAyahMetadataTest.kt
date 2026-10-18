package com.listenquran.khatam

import com.listenquran.domain.model.khatam.QuranAyahMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuranAyahMetadataTest {

    @Test
    fun totalAyahs_exactly6236() {
        assertEquals(6236, QuranAyahMetadata.TOTAL_AYAHS)
        assertEquals(114, QuranAyahMetadata.surahAyahCounts.size)
        assertEquals(6236, QuranAyahMetadata.surahAyahCounts.sum())
    }

    @Test
    fun juzBoundaries_coverEntireQuranWithoutGapsOrOverlaps() {
        assertEquals(30, QuranAyahMetadata.juzStartAyahs.size)
        assertEquals(1, QuranAyahMetadata.juzStartAyahs[0])

        for (juz in 1..30) {
            val range = QuranAyahMetadata.juzRange(juz)
            assertTrue("Juz $juz range start ${range.first} must be <= end ${range.last}", range.first <= range.last)
            if (juz < 30) {
                val nextStart = QuranAyahMetadata.juzStartAyahs[juz]
                assertEquals("Juz $juz must end immediately before Juz ${juz + 1}", nextStart - 1, range.last)
            } else {
                assertEquals("Juz 30 must end at 6236", 6236, range.last)
            }
        }
    }

    @Test
    fun ayahToSurahConversions_areBiDirectional() {
        // Test Al-Fatihah
        assertEquals(1, QuranAyahMetadata.toGlobalAyah(1, 1))
        assertEquals(7, QuranAyahMetadata.toGlobalAyah(1, 7))
        assertEquals(1 to 1, QuranAyahMetadata.toSurahAyah(1))
        assertEquals(1 to 7, QuranAyahMetadata.toSurahAyah(7))

        // Test Al-Baqarah start and end
        assertEquals(8, QuranAyahMetadata.toGlobalAyah(2, 1))
        assertEquals(286 + 7, QuranAyahMetadata.toGlobalAyah(2, 286))
        assertEquals(2 to 1, QuranAyahMetadata.toSurahAyah(8))
        assertEquals(2 to 286, QuranAyahMetadata.toSurahAyah(293))

        // Test An-Nas
        assertEquals(6236, QuranAyahMetadata.toGlobalAyah(114, 6))
        assertEquals(114 to 6, QuranAyahMetadata.toSurahAyah(6236))

        // Full Quran roundtrip check
        for (globalAyah in 1..6236) {
            val (surah, ayah) = QuranAyahMetadata.toSurahAyah(globalAyah)
            val computedGlobal = QuranAyahMetadata.toGlobalAyah(surah, ayah)
            assertEquals("Roundtrip failed for global ayah $globalAyah", globalAyah, computedGlobal)
        }
    }

    @Test
    fun juzLookup_returnsCorrectJuz() {
        assertEquals(1, QuranAyahMetadata.toJuz(1))
        assertEquals(1, QuranAyahMetadata.toJuz(148))
        assertEquals(2, QuranAyahMetadata.toJuz(149))
        assertEquals(30, QuranAyahMetadata.toJuz(5673))
        assertEquals(30, QuranAyahMetadata.toJuz(6236))
    }
}
