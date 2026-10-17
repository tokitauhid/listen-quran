package com.listenquran.data.repository

import android.content.Context
import com.listenquran.data.local.dao.ReciterDao
import com.listenquran.data.local.dao.SurahDao
import com.listenquran.data.local.entity.SurahEntity
import com.listenquran.data.remote.QuranApiService
import com.listenquran.domain.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuranRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiService: QuranApiService,
    private val surahDao: SurahDao,
    private val reciterDao: ReciterDao,
) {
    private var cachedAssetChapters: List<SurahEntity>? = null

    private suspend fun loadChaptersFromAssets(): List<SurahEntity> = withContext(Dispatchers.IO) {
        cachedAssetChapters?.let { return@withContext it }
        try {
            val jsonString = context.assets.open("chapters.json").bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<SurahEntity>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val pagesArray = obj.optJSONArray("pages")
                val startPage = pagesArray?.optInt(0, 1) ?: 1
                val endPage = pagesArray?.optInt(1, startPage) ?: startPage
                val translatedName = obj.optJSONObject("translated_name")?.optString("name", "") ?: ""

                list.add(
                    SurahEntity(
                        id = obj.getInt("id"),
                        nameSimple = obj.getString("name_simple"),
                        nameComplex = obj.optString("name_complex", obj.getString("name_simple")),
                        nameArabic = obj.getString("name_arabic"),
                        revelationPlace = obj.optString("revelation_place", "makkah"),
                        revelationOrder = obj.optInt("revelation_order", 1),
                        versesCount = obj.getInt("verses_count"),
                        startPage = startPage,
                        endPage = endPage,
                        translatedName = translatedName
                    )
                )
            }
            cachedAssetChapters = list
            list
        } catch (e: Exception) {
            android.util.Log.e("QuranRepository", "Error reading chapters.json asset", e)
            emptyList()
        }
    }

    fun getSurahs(): Flow<List<Surah>> = flow {
        // Emit from database first
        surahDao.getAllSurahs().collect { entities ->
            if (entities.isNotEmpty() && entities.size == 114) {
                emit(entities.map { it.toDomain() })
            } else {
                // Seed 114 chapters instantly from bundled assets
                val assetChapters = loadChaptersFromAssets()
                if (assetChapters.isNotEmpty()) {
                    surahDao.insertSurahs(assetChapters)
                    emit(assetChapters.map { it.toDomain() })
                } else {
                    // Fetch from network and store in Room as fallback
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
    }

    suspend fun getSurahById(id: Int): Surah? {
        val entity = surahDao.getSurahById(id)
        if (entity != null) return entity.toDomain()
        val assetChapters = loadChaptersFromAssets()
        if (assetChapters.isNotEmpty()) {
            surahDao.insertSurahs(assetChapters)
            return assetChapters.find { it.id == id }?.toDomain()
        }
        return null
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
        var reciter = reciterDao.getReciterById(reciterId)
        if (reciter == null || reciter.audioServer == null) {
            val defaultMatch = ReciterRepository.defaultReciters.find { it.id == reciterId }
            if (defaultMatch != null) {
                reciter = defaultMatch
            }
        }
        if (reciter?.audioServer != null) {
            val server = reciter.audioServer.trimEnd('/') + "/"
            val audioUrl = if (server.contains("mp3quran.net")) {
                server + String.format("%03d.mp3", chapterId)
            } else {
                server + "$chapterId.mp3"
            }

            var timings = emptyList<VerseTiming>()
            // Attempt to get timestamps from Quran.com API if it's one of the supported recitation IDs
            if (reciterId in 1..12) {
                try {
                    val response = apiService.getChapterRecitation(reciterId = reciterId, chapterId = chapterId)
                    timings = response.audioFile.timestamps?.map { t ->
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
                } catch (_: Exception) {
                    // Non-blocking fallback: direct audio still plays instantly
                }
            }

            return ChapterAudio(
                chapterId = chapterId,
                reciterId = reciterId,
                audioUrl = audioUrl,
                format = "mp3",
                timestamps = timings,
            )
        }

        // Fallback for standard Quran.com API recitations
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
