package com.listenquran.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ChaptersResponse(
    @Json(name = "chapters") val chapters: List<ChapterDto>,
)

@JsonClass(generateAdapter = true)
data class ChapterDto(
    @Json(name = "id") val id: Int,
    @Json(name = "revelation_place") val revelationPlace: String,
    @Json(name = "revelation_order") val revelationOrder: Int,
    @Json(name = "bismillah_pre") val bismillahPre: Boolean,
    @Json(name = "name_simple") val nameSimple: String,
    @Json(name = "name_complex") val nameComplex: String,
    @Json(name = "name_arabic") val nameArabic: String,
    @Json(name = "verses_count") val versesCount: Int,
    @Json(name = "pages") val pages: List<Int>?,
    @Json(name = "translated_name") val translatedName: TranslatedNameDto,
)

@JsonClass(generateAdapter = true)
data class TranslatedNameDto(
    @Json(name = "language_name") val languageName: String,
    @Json(name = "name") val name: String,
)

@JsonClass(generateAdapter = true)
data class RecitationsResponse(
    @Json(name = "recitations") val recitations: List<RecitationDto>,
)

@JsonClass(generateAdapter = true)
data class RecitationDto(
    @Json(name = "id") val id: Int,
    @Json(name = "reciter_name") val reciterName: String,
    @Json(name = "style") val style: String?,
    @Json(name = "translated_name") val translatedName: TranslatedNameDto?,
)

@JsonClass(generateAdapter = true)
data class ChapterAudioResponse(
    @Json(name = "audio_file") val audioFile: AudioFileDto,
)

@JsonClass(generateAdapter = true)
data class AudioFileDto(
    @Json(name = "id") val id: Int,
    @Json(name = "chapter_id") val chapterId: Int,
    @Json(name = "file_size") val fileSize: Double?,
    @Json(name = "format") val format: String?,
    @Json(name = "audio_url") val audioUrl: String,
    @Json(name = "timestamps") val timestamps: List<VerseTimestampDto>?,
)

@JsonClass(generateAdapter = true)
data class VerseTimestampDto(
    @Json(name = "verse_key") val verseKey: String,
    @Json(name = "timestamp_from") val timestampFrom: Long,
    @Json(name = "timestamp_to") val timestampTo: Long,
    @Json(name = "duration") val duration: Long,
    @Json(name = "segments") val segments: List<List<Long>>?, // [word_index, start_ms, end_ms]
)

@JsonClass(generateAdapter = true)
data class VersesResponse(
    @Json(name = "verses") val verses: List<VerseDto>,
)

@JsonClass(generateAdapter = true)
data class VerseDto(
    @Json(name = "id") val id: Int,
    @Json(name = "verse_number") val verseNumber: Int,
    @Json(name = "verse_key") val verseKey: String,
    @Json(name = "text_uthmani") val textUthmani: String?,
    @Json(name = "page_number") val pageNumber: Int?,
    @Json(name = "juz_number") val juzNumber: Int?,
    @Json(name = "hizb_number") val hizbNumber: Int?,
    @Json(name = "rub_el_hizb_number") val rubElHizbNumber: Int?,
    @Json(name = "translations") val translations: List<VerseTranslationDto>?,
    @Json(name = "words") val words: List<WordDto>?,
)

@JsonClass(generateAdapter = true)
data class VerseTranslationDto(
    @Json(name = "id") val id: Int?,
    @Json(name = "text") val text: String,
)

@JsonClass(generateAdapter = true)
data class WordDto(
    @Json(name = "id") val id: Int,
    @Json(name = "position") val position: Int,
    @Json(name = "audio_url") val audioUrl: String?,
    @Json(name = "text_uthmani") val textUthmani: String?,
    @Json(name = "transliteration") val transliteration: WordTextDto?,
    @Json(name = "translation") val translation: WordTextDto?,
)

@JsonClass(generateAdapter = true)
data class WordTextDto(
    @Json(name = "text") val text: String?,
)
