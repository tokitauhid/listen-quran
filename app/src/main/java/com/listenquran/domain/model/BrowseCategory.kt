package com.listenquran.domain.model

import androidx.compose.runtime.Immutable
import com.listenquran.domain.model.khatam.QuranAyahMetadata
import java.time.DayOfWeek
import java.time.LocalDateTime

enum class CategoryGroup(val label: String) {
    DAILY_SUNNAH("Daily Sunnah"),
    SPIRITUAL_MOOD("Peace & Mood"),
    STORIES_AND_DUAS("Stories & Duas"),
    STRUCTURE_AND_STUDY("Structure & Study"),
}

enum class CategoryTone {
    PRIMARY,
    SECONDARY,
    TERTIARY,
    SURFACE_HIGH,
}

enum class CategoryKind {
    CURATED_COLLECTION,
    JUZ_BROWSER,
    LENGTH_AND_REVELATION,
}

@Immutable
data class CategoryEntry(
    val surahId: Int,
    val startAyah: Int = 1,
    val endAyah: Int? = null,
    val label: String? = null,
    val subtitle: String? = null,
)

@Immutable
data class BrowseCategory(
    val id: String,
    val title: String,
    val arabicLabel: String,
    val description: String,
    val group: CategoryGroup,
    val tone: CategoryTone,
    val kind: CategoryKind = CategoryKind.CURATED_COLLECTION,
    val entries: List<CategoryEntry> = emptyList(),
    val showSleepTimerShortcut: Boolean = false,
) {
    val itemCountLabel: String
        get() = when (kind) {
            CategoryKind.JUZ_BROWSER -> "30 Juz"
            CategoryKind.LENGTH_AND_REVELATION -> "114 Surahs"
            CategoryKind.CURATED_COLLECTION -> when {
                id == "quranic_duas" -> "${entries.size} Duas"
                entries.any { it.startAyah > 1 } -> "${entries.size} Recitations"
                else -> "${entries.size} Surahs"
            }
        }
}

@Immutable
data class FeaturedCategorySpotlight(
    val category: BrowseCategory,
    val badgeLabel: String,
    val contextSubtitle: String,
)

@Immutable
data class JuzSummary(
    val juzNumber: Int,
    val arabicName: String,
    val startSurahId: Int,
    val startAyah: Int,
    val surahIds: List<Int>,
)

object BrowseCategoryCatalog {

    val allCategories: List<BrowseCategory> = listOf(
        // Pillar A — Daily & Weekly Sunnah Routines
        BrowseCategory(
            id = "friday_sunnah",
            title = "Friday Sunnah",
            arabicLabel = "الجمعة",
            description = "Al-Kahf & Jumu'ah recitations",
            group = CategoryGroup.DAILY_SUNNAH,
            tone = CategoryTone.TERTIARY,
            entries = listOf(
                CategoryEntry(18, 1, subtitle = "Light between the two Fridays"),
                CategoryEntry(32, 1, subtitle = "Friday Fajr Sunnah"),
                CategoryEntry(76, 1, subtitle = "Friday Fajr Sunnah"),
                CategoryEntry(62, 1, subtitle = "The Congregation"),
                CategoryEntry(87, 1, subtitle = "Recited in Jumu'ah prayer"),
                CategoryEntry(88, 1, subtitle = "Recited in Jumu'ah prayer"),
            ),
        ),
        BrowseCategory(
            id = "morning_evening_adhkar",
            title = "Morning & Evening",
            arabicLabel = "أذكار",
            description = "Daily protection & 3 Quls",
            group = CategoryGroup.DAILY_SUNNAH,
            tone = CategoryTone.PRIMARY,
            entries = listOf(
                CategoryEntry(1, 1, label = "Al-Fatihah", subtitle = "The Opening"),
                CategoryEntry(2, 255, 255, label = "Ayatul Kursi", subtitle = "Al-Baqarah · 2:255"),
                CategoryEntry(2, 285, 286, label = "Last 2 Ayahs of Al-Baqarah", subtitle = "Al-Baqarah · 2:285–286"),
                CategoryEntry(112, 1, label = "Al-Ikhlas", subtitle = "Sincerity"),
                CategoryEntry(113, 1, label = "Al-Falaq", subtitle = "The Daybreak"),
                CategoryEntry(114, 1, label = "An-Nas", subtitle = "Mankind"),
                CategoryEntry(109, 1, label = "Al-Kafirun", subtitle = "Protection from shirk"),
            ),
        ),
        BrowseCategory(
            id = "before_sleep",
            title = "Before Sleep",
            arabicLabel = "النوم",
            description = "Bedtime Sunnah recitations",
            group = CategoryGroup.DAILY_SUNNAH,
            tone = CategoryTone.SURFACE_HIGH,
            showSleepTimerShortcut = true,
            entries = listOf(
                CategoryEntry(67, 1, subtitle = "Protection in the grave"),
                CategoryEntry(32, 1, subtitle = "As-Sajdah before sleep"),
                CategoryEntry(2, 285, 286, label = "Amanar-Rasul", subtitle = "Al-Baqarah · 2:285–286"),
                CategoryEntry(2, 255, 255, label = "Ayatul Kursi", subtitle = "Al-Baqarah · 2:255"),
                CategoryEntry(112, 1),
                CategoryEntry(113, 1),
                CategoryEntry(114, 1),
                CategoryEntry(17, 1),
                CategoryEntry(39, 1),
            ),
        ),
        BrowseCategory(
            id = "night_prayer",
            title = "Night & Tahajjud",
            arabicLabel = "قيام الليل",
            description = "Deep Qiyam & contemplation",
            group = CategoryGroup.DAILY_SUNNAH,
            tone = CategoryTone.SURFACE_HIGH,
            showSleepTimerShortcut = true,
            entries = listOf(
                CategoryEntry(73, 1),
                CategoryEntry(55, 1),
                CategoryEntry(56, 1),
                CategoryEntry(75, 1),
                CategoryEntry(76, 1),
                CategoryEntry(50, 1),
            ),
        ),

        // Pillar B — Spiritual & Emotional Themes
        BrowseCategory(
            id = "peace_sakinah",
            title = "Peace & Sakinah",
            arabicLabel = "سكينة",
            description = "Comfort for worried hearts",
            group = CategoryGroup.SPIRITUAL_MOOD,
            tone = CategoryTone.PRIMARY,
            entries = listOf(
                CategoryEntry(93, 1, subtitle = "Your Lord has not forsaken you"),
                CategoryEntry(94, 1, subtitle = "With hardship comes ease"),
                CategoryEntry(13, 28, label = "Hearts Find Rest", subtitle = "Ar-Ra'd · 13:28"),
                CategoryEntry(20, 1),
                CategoryEntry(12, 1),
                CategoryEntry(55, 1),
                CategoryEntry(9, 40, label = "Do Not Grieve", subtitle = "At-Tawbah · 9:40"),
            ),
        ),
        BrowseCategory(
            id = "ruqyah_healing",
            title = "Ruqyah & Healing",
            arabicLabel = "رقية",
            description = "Authentic Shifa & protection",
            group = CategoryGroup.SPIRITUAL_MOOD,
            tone = CategoryTone.SECONDARY,
            entries = listOf(
                CategoryEntry(1, 1, label = "Al-Fatihah", subtitle = "Ash-Shafiyah — The Cure"),
                CategoryEntry(2, 1, 5, label = "Opening of Al-Baqarah", subtitle = "Al-Baqarah · 2:1–5"),
                CategoryEntry(2, 255, 255, label = "Ayatul Kursi", subtitle = "Al-Baqarah · 2:255"),
                CategoryEntry(2, 285, 286, label = "Closing of Al-Baqarah", subtitle = "Al-Baqarah · 2:285–286"),
                CategoryEntry(17, 82, label = "Healing & Mercy", subtitle = "Al-Isra · 17:82"),
                CategoryEntry(10, 57, label = "Healing for the Chests", subtitle = "Yunus · 10:57"),
                CategoryEntry(112, 1),
                CategoryEntry(113, 1),
                CategoryEntry(114, 1),
            ),
        ),
        BrowseCategory(
            id = "mercy_forgiveness",
            title = "Mercy & Hope",
            arabicLabel = "رحمة",
            description = "Tawbah & Allah's vast mercy",
            group = CategoryGroup.SPIRITUAL_MOOD,
            tone = CategoryTone.PRIMARY,
            entries = listOf(
                CategoryEntry(39, 53, label = "Never Despair of Mercy", subtitle = "Az-Zumar · 39:53"),
                CategoryEntry(25, 63, label = "Servants of Ar-Rahman", subtitle = "Al-Furqan · 25:63"),
                CategoryEntry(19, 1),
                CategoryEntry(57, 1),
                CategoryEntry(110, 1),
                CategoryEntry(71, 1),
            ),
        ),
        BrowseCategory(
            id = "gratitude_barakah",
            title = "Gratitude & Barakah",
            arabicLabel = "رزق وشكر",
            description = "Thankfulness & sustenance",
            group = CategoryGroup.SPIRITUAL_MOOD,
            tone = CategoryTone.TERTIARY,
            entries = listOf(
                CategoryEntry(55, 1),
                CategoryEntry(56, 1),
                CategoryEntry(14, 1),
                CategoryEntry(31, 1),
                CategoryEntry(65, 2, 3, label = "Allah Makes a Way Out", subtitle = "At-Talaq · 65:2–3"),
                CategoryEntry(93, 1),
            ),
        ),

        // Pillar C — Stories, Duas & Reflection
        BrowseCategory(
            id = "quranic_duas",
            title = "Quranic Duas",
            arabicLabel = "دعاء",
            description = "Iconic Rabbana supplications",
            group = CategoryGroup.STORIES_AND_DUAS,
            tone = CategoryTone.TERTIARY,
            entries = listOf(
                CategoryEntry(2, 201, 201, label = "Good in Both Worlds", subtitle = "Al-Baqarah · 2:201"),
                CategoryEntry(2, 250, 250, label = "Patience & Steadfastness", subtitle = "Al-Baqarah · 2:250"),
                CategoryEntry(2, 286, 286, label = "Ease & Pardon", subtitle = "Al-Baqarah · 2:286"),
                CategoryEntry(3, 8, 8, label = "Keep Our Hearts Firm", subtitle = "Ali 'Imran · 3:8"),
                CategoryEntry(3, 191, 194, label = "Remembrance & Reflection", subtitle = "Ali 'Imran · 3:191–194"),
                CategoryEntry(7, 23, 23, label = "Dua of Prophet Adam", subtitle = "Al-A'raf · 7:23"),
                CategoryEntry(14, 40, 41, label = "Dua of Prophet Ibrahim", subtitle = "Ibrahim · 14:40–41"),
                CategoryEntry(17, 24, 24, label = "Mercy for Parents", subtitle = "Al-Isra · 17:24"),
                CategoryEntry(20, 25, 28, label = "Dua of Prophet Musa", subtitle = "Ta-Ha · 20:25–28"),
                CategoryEntry(21, 87, 87, label = "Dua of Prophet Yunus", subtitle = "Al-Anbya · 21:87"),
                CategoryEntry(25, 74, 74, label = "Comfort of Our Eyes", subtitle = "Al-Furqan · 25:74"),
                CategoryEntry(28, 24, 24, label = "Seeking Goodness", subtitle = "Al-Qasas · 28:24"),
            ),
        ),
        BrowseCategory(
            id = "stories_prophets",
            title = "Stories of Prophets",
            arabicLabel = "قصص الأنبياء",
            description = "Timeless prophetic journeys",
            group = CategoryGroup.STORIES_AND_DUAS,
            tone = CategoryTone.TERTIARY,
            entries = listOf(
                CategoryEntry(12, 1),
                CategoryEntry(19, 1),
                CategoryEntry(20, 1),
                CategoryEntry(21, 1),
                CategoryEntry(28, 1),
                CategoryEntry(18, 1),
                CategoryEntry(14, 1),
                CategoryEntry(27, 1),
                CategoryEntry(71, 1),
            ),
        ),
        BrowseCategory(
            id = "akhirah_reflection",
            title = "Heart & Akhirah",
            arabicLabel = "الآخرة",
            description = "Awakening & remembrance",
            group = CategoryGroup.STORIES_AND_DUAS,
            tone = CategoryTone.SECONDARY,
            entries = listOf(
                CategoryEntry(50, 1),
                CategoryEntry(69, 1),
                CategoryEntry(75, 1),
                CategoryEntry(77, 1),
                CategoryEntry(78, 1),
                CategoryEntry(81, 1),
                CategoryEntry(82, 1),
                CategoryEntry(89, 1),
            ),
        ),

        // Pillar D — Quran Structure & Practical Listening
        BrowseCategory(
            id = "browse_juz",
            title = "Browse by Juz",
            arabicLabel = "الأجزاء",
            description = "All 30 Juz section by section",
            group = CategoryGroup.STRUCTURE_AND_STUDY,
            tone = CategoryTone.PRIMARY,
            kind = CategoryKind.JUZ_BROWSER,
        ),
        BrowseCategory(
            id = "juz_amma",
            title = "Juz 'Amma & Short",
            arabicLabel = "جزء عمّ",
            description = "Quick listens & Hifz (78–114)",
            group = CategoryGroup.STRUCTURE_AND_STUDY,
            tone = CategoryTone.SECONDARY,
            entries = (78..114).map { CategoryEntry(it) },
        ),
        BrowseCategory(
            id = "length_revelation",
            title = "Length & Revelation",
            arabicLabel = "مكي ومدني",
            description = "Short, long, Makki & Madani",
            group = CategoryGroup.STRUCTURE_AND_STUDY,
            tone = CategoryTone.SURFACE_HIGH,
            kind = CategoryKind.LENGTH_AND_REVELATION,
        ),
    )

    private val categoriesById: Map<String, BrowseCategory> = allCategories.associateBy { it.id }

    fun findById(id: String): BrowseCategory? = categoriesById[id]

    fun featuredCategoryFor(dateTime: LocalDateTime = LocalDateTime.now()): FeaturedCategorySpotlight {
        val day = dateTime.dayOfWeek
        val hour = dateTime.hour
        val minute = dateTime.minute

        val isFridayWindow = (day == DayOfWeek.THURSDAY && hour >= 18) ||
            (day == DayOfWeek.FRIDAY && hour < 18)

        return when {
            isFridayWindow -> FeaturedCategorySpotlight(
                category = findById("friday_sunnah")!!,
                badgeLabel = "Jumu'ah Sunnah",
                contextSubtitle = "Recite Surah Al-Kahf & Friday classics",
            )
            hour in 5..10 || (hour == 4 && minute >= 30) -> FeaturedCategorySpotlight(
                category = findById("morning_evening_adhkar")!!,
                badgeLabel = "Morning Sunnah",
                contextSubtitle = "Start your day with protection & peace",
            )
            hour in 11..16 -> FeaturedCategorySpotlight(
                category = findById("peace_sakinah")!!,
                badgeLabel = "Midday Calm",
                contextSubtitle = "Pause for comfort and tranquility",
            )
            hour in 17..20 -> FeaturedCategorySpotlight(
                category = findById("ruqyah_healing")!!,
                badgeLabel = "Evening Protection",
                contextSubtitle = "Calming protection & remembrance",
            )
            hour in 21..23 || hour == 0 -> FeaturedCategorySpotlight(
                category = findById("before_sleep")!!,
                badgeLabel = "Before Sleep",
                contextSubtitle = "Al-Mulk & bedtime Sunnah with sleep timer",
            )
            else -> FeaturedCategorySpotlight(
                category = findById("night_prayer")!!,
                badgeLabel = "Qiyam & Tahajjud",
                contextSubtitle = "Quiet recitations for the last third of night",
            )
        }
    }

    private val homeGridPriorityIds = listOf(
        "browse_juz",
        "ruqyah_healing",
        "before_sleep",
        "quranic_duas",
        "peace_sakinah",
        "stories_prophets",
        "morning_evening_adhkar",
        "juz_amma",
        "friday_sunnah",
    )

    fun homeGridCategories(featuredId: String): List<BrowseCategory> {
        return homeGridPriorityIds
            .asSequence()
            .filter { it != featuredId }
            .mapNotNull { findById(it) }
            .take(6)
            .toList()
    }

    fun allJuzSummaries(): List<JuzSummary> {
        return (1..QuranAyahMetadata.TOTAL_JUZ).map { juz ->
            val range = QuranAyahMetadata.juzRange(juz)
            val (startSurah, startAyah) = QuranAyahMetadata.toSurahAyah(range.first)
            val (endSurah, _) = QuranAyahMetadata.toSurahAyah(range.last)
            JuzSummary(
                juzNumber = juz,
                arabicName = QuranAyahMetadata.juzArabicName(juz),
                startSurahId = startSurah,
                startAyah = startAyah,
                surahIds = (startSurah..endSurah).toList(),
            )
        }
    }

    fun juzEntries(juzNumber: Int): List<CategoryEntry> {
        val juz = juzNumber.coerceIn(1, QuranAyahMetadata.TOTAL_JUZ)
        val range = QuranAyahMetadata.juzRange(juz)
        val (startSurah, startAyah) = QuranAyahMetadata.toSurahAyah(range.first)
        val (endSurah, _) = QuranAyahMetadata.toSurahAyah(range.last)
        return (startSurah..endSurah).mapIndexed { index, surahId ->
            CategoryEntry(
                surahId = surahId,
                startAyah = if (index == 0) startAyah else 1,
            )
        }
    }
}
