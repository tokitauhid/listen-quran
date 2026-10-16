package com.listenquran.domain.model

data class Surah(
    val id: Int,
    val nameSimple: String,
    val nameComplex: String,
    val nameArabic: String,
    val revelationPlace: String,
    val revelationOrder: Int,
    val versesCount: Int,
    val pages: List<Int> = emptyList(),
    val translatedName: String,
) {
    val isMakki: Boolean get() = revelationPlace.equals("makkah", ignoreCase = true)
}

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

data class AyahWord(
    val id: Int,
    val position: Int,
    val audioUrl: String? = null,
    val textUthmani: String,
    val transliteration: String,
    val translation: String,
)

data class Reciter(
    val id: Int,
    val name: String,
    val englishName: String,
    val style: String?,
    val qiraah: String = "Hafs 'an 'Asim",
    val photoUrl: String? = null,
    val surahsCount: Int = 114,
    val isFavorite: Boolean = false,
    val audioServer: String? = null,
)

data class QueueItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val surah: Surah,
    val reciter: Reciter,
    val startAyah: Int = 1,
)

data class VerseTiming(
    val verseKey: String,
    val timestampFrom: Long,
    val timestampTo: Long,
    val duration: Long,
    val segments: List<SegmentTiming> = emptyList(),
)

data class SegmentTiming(
    val wordIndex: Int,
    val startMs: Long,
    val endMs: Long,
)

data class ChapterAudio(
    val chapterId: Int,
    val reciterId: Int,
    val audioUrl: String,
    val format: String = "mp3",
    val durationSeconds: Long = 0,
    val timestamps: List<VerseTiming> = emptyList(),
)

data class RadioStation(
    val id: String,
    val name: String,
    val streamUrl: String,
    val description: String,
    val reciterName: String? = null,
    val isLive: Boolean = true,
)
