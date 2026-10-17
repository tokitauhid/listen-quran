package com.listenquran.domain.model

import androidx.compose.runtime.Immutable

@Immutable
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
    val englishName: String,
    val style: String?,
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

@Immutable
data class RadioStation(
    val id: String,
    val name: String,
    val description: String,
    val streamUrl: String,
    val city: String = "Live",
)

val CuratedRadioStations = listOf(
    RadioStation(
        id = "makkah",
        name = "Makkah Live Radio",
        description = "Live Holy Quran recitation from Makkah",
        streamUrl = "https://backup.qurango.net/radio/makkah",
        city = "Makkah"
    ),
    RadioStation(
        id = "cairo",
        name = "Cairo Quran Radio",
        description = "Official Egypt Radio Holy Quran broadcast",
        streamUrl = "https://stream.radiojar.com/8s5u8tpdt7uvv",
        city = "Cairo"
    ),
    RadioStation(
        id = "alafasi",
        name = "Mishary Al-Afasy Radio",
        description = "24/7 continuous recitations by Sheikh Mishary",
        streamUrl = "https://backup.qurango.net/radio/mishary_alafasi",
        city = "Kuwait"
    ),
    RadioStation(
        id = "abdulbasit",
        name = "Abdulbasit Abdulsamad Radio",
        description = "Legendary Mojawwad recitations 24/7",
        streamUrl = "https://backup.qurango.net/radio/abdulbasit_abdulsamad_mojawwad",
        city = "Cairo"
    ),
    RadioStation(
        id = "hussary",
        name = "Mahmoud Khalil Al-Husary",
        description = "Masterful Murattal tajweed instruction",
        streamUrl = "https://backup.qurango.net/radio/mahmoud_khalil_alhussary",
        city = "Egypt"
    ),
    RadioStation(
        id = "ghamdi",
        name = "Saad Al-Ghamdi Radio",
        description = "Heart-softening continuous recitation",
        streamUrl = "https://backup.qurango.net/radio/saad_alghamdi",
        city = "Dammam"
    ),
    RadioStation(
        id = "shatri",
        name = "Abu Bakr Al-Shatri Radio",
        description = "Revered emotional Quran recitation",
        streamUrl = "https://backup.qurango.net/radio/abu_bakr_alshatri",
        city = "Jeddah"
    ),
)

