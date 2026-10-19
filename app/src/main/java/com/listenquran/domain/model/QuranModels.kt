package com.listenquran.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class Surah(
    val id: Int,
    val nameSimple: String,
    val nameComplex: String = "",
    val nameArabic: String = "",
    val revelationPlace: String = "makkah",
    val revelationOrder: Int = 1,
    val versesCount: Int = 0,
    val pages: List<Int> = emptyList(),
    val translatedName: String = "",
) {
    val isMakki: Boolean get() = revelationPlace.equals("makkah", ignoreCase = true)
}

@Immutable
data class Ayah(
    val id: Int,
    val surahNumber: Int,
    val ayahNumber: Int,
    val verseKey: String,
    val textUthmani: String,
    val translation: String,
    val pageNumber: Int = 1,
    val juzNumber: Int = 1,
    val hizbNumber: Int = 1,
    val rubElHizb: Int = 1,
    val words: List<AyahWord> = emptyList(),
)

@Immutable
data class AyahWord(
    val id: Int,
    val position: Int,
    val audioUrl: String? = null,
    val textUthmani: String,
    val transliteration: String,
    val translation: String,
)

@Immutable
data class Reciter(
    val id: Int,
    val name: String,
    val englishName: String = name,
    val style: String? = null,
    val qiraah: String = "Hafs 'an 'Asim",
    val photoUrl: String? = null,
    val surahsCount: Int = 114,
    val isFavorite: Boolean = false,
    val audioServer: String? = null,
)

@Immutable
data class QueueItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val surah: Surah,
    val reciter: Reciter,
    val startAyah: Int = 1,
    val timelineIndex: Int = 0,
)


@Immutable
data class VerseTiming(
    val verseKey: String,
    val timestampFrom: Long,
    val timestampTo: Long,
    val duration: Long,
    val segments: List<SegmentTiming> = emptyList(),
)

@Immutable
data class SegmentTiming(
    val wordIndex: Int,
    val startMs: Long,
    val endMs: Long,
)

@Immutable
data class ChapterAudio(
    val chapterId: Int,
    val reciterId: Int,
    val audioUrl: String,
    val format: String = "mp3",
    val durationSeconds: Long = 0,
    val timestamps: List<VerseTiming> = emptyList(),
)

enum class RadioStationType {
    CURATED,
    RECITER,
    LIVE_STREAM
}

@Immutable
data class RadioStation(
    val id: String,
    val name: String,
    val description: String,
    val type: RadioStationType = RadioStationType.CURATED,
    val bannerUrl: String? = null,
    val reciterId: Int? = null,
    val reciterName: String? = null,
    val targetSurahs: List<Int> = emptyList(),
    val poolReciterIds: List<Int> = emptyList(),
    val streamUrl: String? = null,
    val isLive: Boolean = false,
    val city: String = "Quran.com",
)

val CuratedRadioStations = listOf(
    RadioStation(
        id = "curated_1",
        name = "Popular Recitations",
        description = "Daily curated feed of recitations",
        type = RadioStationType.CURATED,
        bannerUrl = "https://quran.com/images/stations/1.jpeg",
        poolReciterIds = listOf(7, 3, 10, 4), // Mishary, Sudais, Shuraym, Shatri
        targetSurahs = (1..114).toList(),
        city = "Curated"
    ),
    RadioStation(
        id = "curated_2",
        name = "Yaseen, Al-Waqiah, Al-Mulk",
        description = "The Surahs from a curation of reciters",
        type = RadioStationType.CURATED,
        bannerUrl = "https://quran.com/images/stations/2.jpg",
        poolReciterIds = listOf(7),
        targetSurahs = listOf(36, 56, 67),
        city = "Curated"
    ),
    RadioStation(
        id = "curated_3",
        name = "Surah Al-Kahf",
        description = "Listen to Surah Alkahf on repeat",
        type = RadioStationType.CURATED,
        bannerUrl = "https://quran.com/images/stations/3.jpeg",
        poolReciterIds = listOf(7, 3, 10, 4),
        targetSurahs = listOf(18),
        city = "Curated"
    ),
    RadioStation(
        id = "curated_4",
        name = "Juz Amma",
        description = "Listen to the final Juz of the Quran",
        type = RadioStationType.CURATED,
        bannerUrl = "https://quran.com/images/stations/4.jpeg",
        poolReciterIds = listOf(7, 3, 10, 4),
        targetSurahs = (78..114).toList(),
        city = "Curated"
    )
)

