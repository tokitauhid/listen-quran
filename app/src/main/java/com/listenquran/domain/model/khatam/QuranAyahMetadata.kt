package com.listenquran.domain.model.khatam

/**
 * Immutable zero-allocation Quran Ayah metadata engine.
 * Total ayahs across the 114 Surahs: exactly 6,236.
 * Global Ayah IDs are strictly 1-indexed (1 to 6,236).
 */
object QuranAyahMetadata {

    const val TOTAL_AYAHS = 6236
    const val TOTAL_JUZ = 30
    const val TOTAL_PAGES = 604
    const val TOTAL_SURAHS = 114

    // Exact verse counts for Surahs 1 to 114
    val SURAH_VERSE_COUNTS = intArrayOf(
        7, 286, 200, 176, 120, 165, 206, 75, 129, 109,
        123, 111, 43, 52, 99, 128, 111, 110, 98, 135,
        112, 78, 118, 64, 77, 227, 93, 88, 69, 60,
        34, 30, 73, 54, 45, 83, 182, 88, 75, 85,
        54, 53, 89, 59, 37, 35, 38, 29, 18, 45,
        60, 49, 62, 55, 78, 96, 29, 22, 24, 13,
        14, 11, 11, 18, 12, 12, 30, 52, 52, 44,
        28, 28, 20, 56, 40, 31, 50, 40, 46, 42,
        29, 19, 36, 25, 22, 17, 19, 26, 30, 20,
        15, 21, 11, 8, 8, 19, 5, 8, 8, 11,
        11, 8, 3, 9, 5, 4, 7, 3, 6, 3,
        5, 4, 5, 6
    )

    // Precomputed 1-indexed global start ayah for each surah (index 0 is unused)
    val SURAH_START_GLOBAL_AYAH = IntArray(115).apply {
        this[1] = 1
        for (i in 2..114) {
            this[i] = this[i - 1] + SURAH_VERSE_COUNTS[i - 2]
        }
    }

    // Exact 1-indexed global start ayah for each Juz 1 to 30
    val JUZ_START_GLOBAL_AYAH = intArrayOf(
        0, // 0-index placeholder
        1, 149, 260, 386, 517, 641, 751, 900, 1042, 1201,
        1328, 1479, 1649, 1803, 2030, 2215, 2484, 2674, 2876, 3215,
        3386, 3564, 3733, 4090, 4265, 4511, 4706, 5105, 5242, 5673
    )

    // Arabic display names for the 30 Juz
    val JUZ_NAMES_ARABIC = arrayOf(
        "",
        "الم", "سيقول", "تلك الرسل", "لن تنالوا", "والمحصنات",
        "لا يحب الله", "وإذا سمعوا", "ولو أننا", "قال الملأ", "واعلموا",
        "يعتذرون", "وما من دابة", "وما أبرئ", "ربما", "سبحان الذي",
        "قال ألم", "اقترب للناس", "قد أفلح", "وقال الذين", "أمن خلق",
        "اتل ما أوحي", "ومن يقنت", "وما لي", "فمن أظلم", "إليه يرد",
        "حم", "قال فما خطبكم", "قد سمع الله", "تبارك الذي", "عمّ يتساءلون"
    )

    val surahAyahCounts: List<Int> get() = SURAH_VERSE_COUNTS.toList()
    val juzStartAyahs: List<Int> get() = JUZ_START_GLOBAL_AYAH.drop(1)
    val juzNamesArabic: Array<String> get() = JUZ_NAMES_ARABIC
    fun juzArabicName(juz: Int): String = JUZ_NAMES_ARABIC.getOrElse(juz.coerceIn(1, 30)) { "" }

    val surahEnglishNames = listOf(
        "Al-Fatihah", "Al-Baqarah", "Ali 'Imran", "An-Nisa", "Al-Ma'idah",
        "Al-An'am", "Al-A'raf", "Al-Anfal", "At-Tawbah", "Yunus",
        "Hud", "Yusuf", "Ar-Ra'd", "Ibrahim", "Al-Hijr",
        "An-Nahl", "Al-Isra", "Al-Kahf", "Maryam", "Ta-Ha",
        "Al-Anbya", "Al-Hajj", "Al-Mu'minun", "An-Nur", "Al-Furqan",
        "Ash-Shu'ara", "An-Naml", "Al-Qasas", "Al-'Ankabut", "Ar-Rum",
        "Luqman", "As-Sajdah", "Al-Ahzab", "Saba", "Fatir",
        "Ya-Sin", "As-Saffat", "Sad", "Az-Zumar", "Ghafir",
        "Fussilat", "Ash-Shuraa", "Az-Zukhruf", "Ad-Dukhan", "Al-Jathiyah",
        "Al-Ahqaf", "Muhammad", "Al-Fath", "Al-Hujurat", "Qaf",
        "Adh-Dhariyat", "At-Tur", "An-Najm", "Al-Qamar", "Ar-Rahman",
        "Al-Waqi'ah", "Al-Hadid", "Al-Mujadila", "Al-Hashr", "Al-Mumtahanah",
        "As-Saf", "Al-Jumu'ah", "Al-Munafiqun", "At-Taghabun", "At-Talaq",
        "At-Tahrim", "Al-Mulk", "Al-Qalam", "Al-Haqqah", "Al-Ma'arij",
        "Nuh", "Al-Jinn", "Al-Muzzammil", "Al-Muddaththir", "Al-Qiyamah",
        "Al-Insan", "Al-Mursalat", "An-Naba", "An-Nazi'at", "'Abasa",
        "At-Takwir", "Al-Infitar", "Al-Mutaffifin", "Al-Inshiqaq", "Al-Buruj",
        "At-Tariq", "Al-A'la", "Al-Ghashiyah", "Al-Fajr", "Al-Balad",
        "Ash-Shams", "Al-Layl", "Ad-Duhaa", "Ash-Sharh", "At-Tin",
        "Al-'Alaq", "Al-Qadr", "Al-Bayyinah", "Az-Zalzalah", "Al-'Adiyat",
        "Al-Qari'ah", "At-Takathur", "Al-'Asr", "Al-Humazah", "Al-Fil",
        "Quraysh", "Al-Ma'un", "Al-Kawthar", "Al-Kafirun", "An-Nasr",
        "Al-Masad", "Al-Ikhlas", "Al-Falaq", "An-Nas"
    )

    /**
     * Converts a (surahId, ayahNumber) pair to a 1-indexed global ayah ID (1..6236).
     */
    fun toGlobalAyah(surahId: Int, ayahNumber: Int): Int {
        val s = surahId.coerceIn(1, 114)
        val maxAyah = SURAH_VERSE_COUNTS[s - 1]
        val a = ayahNumber.coerceIn(1, maxAyah)
        return SURAH_START_GLOBAL_AYAH[s] + (a - 1)
    }

    /**
     * Converts a global ayah ID (1..6236) to Pair(surahId, ayahNumber).
     */
    fun toSurahAyah(globalAyah: Int): Pair<Int, Int> {
        val g = globalAyah.coerceIn(1, TOTAL_AYAHS)
        // Binary search or linear search over the 114 surahs
        var low = 1
        var high = 114
        var surahId = 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (SURAH_START_GLOBAL_AYAH[mid] <= g) {
                surahId = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        val ayahNumber = g - SURAH_START_GLOBAL_AYAH[surahId] + 1
        return Pair(surahId, ayahNumber)
    }

    /**
     * Returns the 1-indexed Juz (1..30) containing the global ayah.
     */
    fun toJuz(globalAyah: Int): Int {
        val g = globalAyah.coerceIn(1, TOTAL_AYAHS)
        var low = 1
        var high = 30
        var juz = 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (JUZ_START_GLOBAL_AYAH[mid] <= g) {
                juz = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return juz
    }

    /**
     * Returns the global ayah range for a given Juz (1..30).
     */
    fun juzRange(juz: Int): IntRange {
        val j = juz.coerceIn(1, 30)
        val start = JUZ_START_GLOBAL_AYAH[j]
        val end = if (j < 30) JUZ_START_GLOBAL_AYAH[j + 1] - 1 else TOTAL_AYAHS
        return start..end
    }

    /**
     * Returns the global ayah range for a given Surah (1..114).
     */
    fun surahRange(surahId: Int): IntRange {
        val s = surahId.coerceIn(1, 114)
        val start = SURAH_START_GLOBAL_AYAH[s]
        val end = start + SURAH_VERSE_COUNTS[s - 1] - 1
        return start..end
    }

    /**
     * Returns an approximate page (1..604) for a given global ayah based on Quran standard layout.
     */
    fun toPage(globalAyah: Int): Int {
        val g = globalAyah.coerceIn(1, TOTAL_AYAHS)
        // Linear mapping across 604 pages as standard view projection
        val page = ((g - 1) * TOTAL_PAGES / TOTAL_AYAHS) + 1
        return page.coerceIn(1, TOTAL_PAGES)
    }

    /**
     * Returns the approximate global ayah range for a given page (1..604).
     */
    fun pageRange(page: Int): IntRange {
        val p = page.coerceIn(1, TOTAL_PAGES)
        val start = ((p - 1) * TOTAL_AYAHS / TOTAL_PAGES) + 1
        val end = if (p < TOTAL_PAGES) (p * TOTAL_AYAHS / TOTAL_PAGES) else TOTAL_AYAHS
        return start..end
    }
}
