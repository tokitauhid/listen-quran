package com.listenquran.data.repository

import com.listenquran.data.local.dao.SurahDao
import com.listenquran.data.local.entity.SurahEntity
import com.listenquran.data.remote.QuranApiService
import com.listenquran.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuranRepository @Inject constructor(
    private val apiService: QuranApiService,
    private val surahDao: SurahDao,
) {
    fun getSurahs(): Flow<List<Surah>> = flow {
        // Emit from database first
        surahDao.getAllSurahs().collect { entities ->
            if (entities.isNotEmpty()) {
                emit(entities.map { it.toDomain() })
            } else {
                // Fetch from network and store in Room
                try {
                    val response = apiService.getChapters()
                    val newEntities = response.chapters.map { chapter ->
                        SurahEntity(
                            id = chapter.id,
                            nameSimple = chapter.nameSimple,
                            nameComplex = chapter.nameComplex,
                            nameArabic = chapter.nameArabic,
                            revelationPlace = chapter.revelationPlace,
                            revelationOrder = chapter.revelationOrder,
                            versesCount = chapter.versesCount,
                            startPage = chapter.pages?.firstOrNull() ?: 1,
                            endPage = chapter.pages?.lastOrNull() ?: 1,
                            translatedName = chapter.translatedName.name,
                        )
                    }
                    surahDao.insertSurahs(newEntities)
                    emit(newEntities.map { it.toDomain() })
                } catch (e: Exception) {
                    emit(emptyList())
                }
            }
        }
    }

    suspend fun getSurahById(id: Int): Surah? {
        val entity = surahDao.getSurahById(id)
        return entity?.toDomain()
    }

    suspend fun getVerses(surahNumber: Int): List<Ayah> {
        return try {
            val response = apiService.getVersesByChapter(chapterId = surahNumber)
            response.verses.map { v ->
                val words = v.words?.map { w ->
                    AyahWord(
                        id = w.id,
                        position = w.position,
                        audioUrl = w.audioUrl,
                        textUthmani = w.textUthmani ?: "",
                        transliteration = w.transliteration?.text ?: "",
                        translation = w.translation?.text ?: "",
                    )
                } ?: emptyList()

                Ayah(
                    id = v.id,
                    surahNumber = surahNumber,
                    ayahNumber = v.verseNumber,
                    verseKey = v.verseKey,
                    textUthmani = v.textUthmani ?: "",
                    translation = v.translations?.firstOrNull()?.text ?: "",
                    pageNumber = v.pageNumber ?: 1,
                    juzNumber = v.juzNumber ?: 1,
                    hizbNumber = v.hizbNumber ?: 1,
                    rubElHizb = v.rubElHizbNumber ?: 1,
                    words = words,
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getChapterAudio(reciterId: Int, chapterId: Int): ChapterAudio? {
        return try {
            val response = apiService.getChapterRecitation(reciterId = reciterId, chapterId = chapterId)
            val audio = response.audioFile
            val timings = audio.timestamps?.map { t ->
                val segs = t.segments?.mapNotNull { seg ->
                    if (seg.size >= 3) {
                        SegmentTiming(
                            wordIndex = seg[0].toInt(),
                            startMs = seg[1],
                            endMs = seg[2],
                        )
                    } else null
                } ?: emptyList()

                VerseTiming(
                    verseKey = t.verseKey,
                    timestampFrom = t.timestampFrom,
                    timestampTo = t.timestampTo,
                    duration = t.duration,
                    segments = segs,
                )
            } ?: emptyList()

            ChapterAudio(
                chapterId = audio.chapterId,
                reciterId = reciterId,
                audioUrl = audio.audioUrl,
                format = audio.format ?: "mp3",
                timestamps = timings,
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun SurahEntity.toDomain() = Surah(
        id = id,
        nameSimple = nameSimple,
        nameComplex = nameComplex,
        nameArabic = nameArabic,
        revelationPlace = revelationPlace,
        revelationOrder = revelationOrder,
        versesCount = versesCount,
        pages = listOf(startPage, endPage),
        translatedName = translatedName,
    )
}
