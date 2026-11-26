package com.listenquran.domain

import com.listenquran.domain.model.BrowseCategoryCatalog
import com.listenquran.domain.model.CategoryGroup
import com.listenquran.domain.model.CategoryKind
import com.listenquran.domain.model.khatam.QuranAyahMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class BrowseCategoryCatalogTest {

    @Test
    fun catalogHas14UniqueCategoriesAcrossAll4Groups() {
        val categories = BrowseCategoryCatalog.allCategories
        assertEquals(14, categories.size)

        val ids = categories.map { it.id }
        assertTrue(ids.all { it.isNotBlank() })
        assertEquals(ids.size, ids.toSet().size)

        val groups = categories.map { it.group }.toSet()
        assertEquals(CategoryGroup.entries.toSet(), groups)
    }

    @Test
    fun everyCategoryEntryHasValidSurahAndAyahBounds() {
        BrowseCategoryCatalog.allCategories.forEach { category ->
            category.entries.forEach { entry ->
                assertTrue(
                    "Invalid surahId ${entry.surahId} in ${category.id}",
                    entry.surahId in 1..QuranAyahMetadata.TOTAL_SURAHS
                )
                val maxAyah = QuranAyahMetadata.SURAH_VERSE_COUNTS[entry.surahId - 1]
                assertTrue(
                    "Invalid startAyah ${entry.startAyah} for surah ${entry.surahId} in ${category.id}",
                    entry.startAyah in 1..maxAyah
                )
                if (entry.endAyah != null) {
                    assertTrue(
                        "Invalid endAyah ${entry.endAyah} for surah ${entry.surahId} in ${category.id}",
                        entry.endAyah in entry.startAyah..maxAyah
                    )
                }
            }
        }
    }

    @Test
    fun featuredCategorySpotlightRespectsFridayAndTimeOfDay() {
        // Thursday evening 19:00 -> Friday Sunnah
        assertEquals(
            "friday_sunnah",
            BrowseCategoryCatalog.featuredCategoryFor(LocalDateTime.of(2026, 11, 19, 19, 0)).category.id
        )
        // Friday afternoon 14:00 -> Friday Sunnah
        assertEquals(
            "friday_sunnah",
            BrowseCategoryCatalog.featuredCategoryFor(LocalDateTime.of(2026, 11, 20, 14, 0)).category.id
        )
        // Monday 06:30 -> Morning & Evening
        assertEquals(
            "morning_evening_adhkar",
            BrowseCategoryCatalog.featuredCategoryFor(LocalDateTime.of(2026, 11, 23, 6, 30)).category.id
        )
        // Monday 13:00 -> Peace & Sakinah
        assertEquals(
            "peace_sakinah",
            BrowseCategoryCatalog.featuredCategoryFor(LocalDateTime.of(2026, 11, 23, 13, 0)).category.id
        )
        // Monday 19:00 -> Ruqyah & Healing
        assertEquals(
            "ruqyah_healing",
            BrowseCategoryCatalog.featuredCategoryFor(LocalDateTime.of(2026, 11, 23, 19, 0)).category.id
        )
        // Monday 22:30 -> Before Sleep
        assertEquals(
            "before_sleep",
            BrowseCategoryCatalog.featuredCategoryFor(LocalDateTime.of(2026, 11, 23, 22, 30)).category.id
        )
        // Monday 02:30 -> Night & Tahajjud
        assertEquals(
            "night_prayer",
            BrowseCategoryCatalog.featuredCategoryFor(LocalDateTime.of(2026, 11, 23, 2, 30)).category.id
        )
    }

    @Test
    fun homeGridCategoriesReturns6UniqueItemsExcludingFeatured() {
        BrowseCategoryCatalog.allCategories.forEach { featured ->
            val preview = BrowseCategoryCatalog.homeGridCategories(featured.id)
            assertEquals(6, preview.size)
            assertEquals(6, preview.map { it.id }.toSet().size)
            assertFalse(preview.any { it.id == featured.id })
        }
    }

    @Test
    fun allJuzSummariesCover30JuzWithExactStartBoundaries() {
        val summaries = BrowseCategoryCatalog.allJuzSummaries()
        assertEquals(30, summaries.size)

        summaries.forEachIndexed { index, summary ->
            val juz = index + 1
            assertEquals(juz, summary.juzNumber)
            assertTrue(summary.arabicName.isNotBlank())
            assertTrue(summary.surahIds.isNotEmpty())
            assertEquals(summary.startSurahId, summary.surahIds.first())

            val globalStart = QuranAyahMetadata.toGlobalAyah(summary.startSurahId, summary.startAyah)
            assertEquals(QuranAyahMetadata.JUZ_START_GLOBAL_AYAH[juz], globalStart)

            val entries = BrowseCategoryCatalog.juzEntries(juz)
            assertEquals(summary.surahIds.size, entries.size)
            assertEquals(summary.startSurahId, entries.first().surahId)
            assertEquals(summary.startAyah, entries.first().startAyah)
            entries.drop(1).forEach { assertEquals(1, it.startAyah) }
        }
    }

    @Test
    fun allAyahSpecificCategoryEntriesDefineEndAyahBounds() {
        BrowseCategoryCatalog.allCategories
            .filter { it.kind == CategoryKind.CURATED_COLLECTION }
            .forEach { category ->
                category.entries.forEach { entry ->
                    if (entry.startAyah > 1) {
                        assertNotNull(
                            "Entry ${entry.surahId}:${entry.startAyah} in ${category.id} must specify endAyah so it does not play the entire surah",
                            entry.endAyah
                        )
                        val maxAyah = QuranAyahMetadata.SURAH_VERSE_COUNTS[entry.surahId - 1]
                        assertTrue(entry.endAyah!! in entry.startAyah..maxAyah)
                    }
                }
            }

        val expectedSingleAyahKeys = mapOf(
            "peace_sakinah" to listOf(13 to 28, 9 to 40),
            "ruqyah_healing" to listOf(2 to 255, 17 to 82, 10 to 57),
            "mercy_forgiveness" to listOf(39 to 53, 25 to 63),
        )
        expectedSingleAyahKeys.forEach { (categoryId, pairs) ->
            val category = BrowseCategoryCatalog.findById(categoryId)!!
            pairs.forEach { (surahId, ayah) ->
                val match = category.entries.find { it.surahId == surahId && it.startAyah == ayah }
                assertNotNull("Expected $surahId:$ayah in $categoryId", match)
                assertEquals(ayah, match!!.endAyah)
            }
        }

        val ruqyahOpening = BrowseCategoryCatalog.findById("ruqyah_healing")!!
            .entries.find { it.surahId == 2 && it.startAyah == 1 }!!
        assertEquals(5, ruqyahOpening.endAyah)

        // Full-surah entries keep endAyah = null
        val friday = BrowseCategoryCatalog.findById("friday_sunnah")!!
        assertTrue(friday.entries.all { it.startAyah == 1 && it.endAyah == null })

        // Juz entries stay unbounded
        val juz1 = BrowseCategoryCatalog.juzEntries(1)
        assertTrue(juz1.all { it.endAyah == null })
        assertNull(juz1.first().endAyah)
    }
}
