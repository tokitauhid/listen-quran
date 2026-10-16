package com.listenquran.data.repository

import com.listenquran.data.local.dao.BookmarkDao
import com.listenquran.data.local.dao.KhatmaDao
import com.listenquran.data.local.dao.StatsDao
import com.listenquran.data.local.entity.BookmarkEntity
import com.listenquran.data.local.entity.KhatmaEntity
import com.listenquran.data.local.entity.ListeningStatEntity
import com.listenquran.domain.model.*
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookmarkRepository @Inject constructor(
    private val bookmarkDao: BookmarkDao,
) {
    fun getAllBookmarks(): Flow<List<Bookmark>> {
        return bookmarkDao.getAllBookmarks().map { list ->
            list.map {
                Bookmark(
                    id = it.id,
                    surahNumber = it.surahNumber,
                    surahName = it.surahName,
                    ayahNumber = it.ayahNumber,
                    verseKey = it.verseKey,
                    textUthmani = it.textUthmani,
                    translation = it.translation,
                    note = it.note,
                    createdAt = it.createdAt,
                )
            }
        }
    }

    suspend fun saveBookmark(
        surahNumber: Int,
        surahName: String,
        ayahNumber: Int,
        verseKey: String,
        textUthmani: String,
        translation: String,
        note: String = "",
    ): Long {
        return bookmarkDao.insertBookmark(
            BookmarkEntity(
                surahNumber = surahNumber,
                surahName = surahName,
                ayahNumber = ayahNumber,
                verseKey = verseKey,
                textUthmani = textUthmani,
                translation = translation,
                note = note,
            )
        )
    }

    suspend fun updateNote(bookmarkId: Long, note: String) {
        bookmarkDao.updateNote(bookmarkId, note)
    }

    suspend fun deleteBookmark(surahNumber: Int, ayahNumber: Int) {
        bookmarkDao.deleteBookmark(surahNumber, ayahNumber)
    }
}

@Singleton
class KhatmaRepository @Inject constructor(
    private val khatmaDao: KhatmaDao,
) {
    fun getKhatmaProgress(): Flow<KhatmaProgress> {
        return khatmaDao.getKhatmaProgress().map { entity ->
            if (entity != null) {
                KhatmaProgress(
                    id = entity.id,
                    title = entity.title,
                    currentPage = entity.currentPage,
                    totalPages = entity.totalPages,
                    currentJuz = entity.currentJuz,
                    lastSurahNumber = entity.lastSurahNumber,
                    lastAyahNumber = entity.lastAyahNumber,
                    targetDays = entity.targetDays,
                    pagesPerDayTarget = entity.pagesPerDayTarget,
                    updatedAt = entity.updatedAt,
                )
            } else {
                KhatmaProgress()
            }
        }
    }

    suspend fun updateProgress(
        page: Int,
        juz: Int,
        surahNumber: Int,
        ayahNumber: Int,
    ) {
        val current = khatmaDao.getKhatmaProgress().first() ?: KhatmaEntity()
        khatmaDao.updateKhatma(
            current.copy(
                currentPage = page.coerceIn(1, 604),
                currentJuz = juz.coerceIn(1, 30),
                lastSurahNumber = surahNumber,
                lastAyahNumber = ayahNumber,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }
}

@Singleton
class RadioRepository @Inject constructor() {
    private val curatedStations = listOf(
        RadioStation(
            id = "cairo_radio",
            name = "Cairo Quran Radio",
            streamUrl = "https://stream.radiojar.com/8s5u8p4e508uv",
            description = "Official Holy Quran Radio broadcast from Cairo, Egypt",
            reciterName = "Various Egyptian Masters",
            isLive = true
        ),
        RadioStation(
            id = "makkah_live",
            name = "Makkah Haram Live",
            streamUrl = "https://video.holymakkah.gov.sa/live/audio.mp3",
            description = "Live 24/7 audio broadcast directly from the Grand Mosque in Makkah",
            reciterName = "Imams of Masjid Al-Haram",
            isLive = true
        ),
        RadioStation(
            id = "reciter_afasy",
            name = "Mishary Al-Afasy 24/7",
            streamUrl = "https://backup.qurango.net/radio/mishary_alafasi",
            description = "Continuous Murattal recitation by Sheikh Mishary Rashid Al-Afasy",
            reciterName = "Mishary Rashid Al-Afasy",
            isLive = true
        ),
        RadioStation(
            id = "reciter_abdulbaset",
            name = "AbdulBaset AbdulSamad Radio",
            streamUrl = "https://backup.qurango.net/radio/abdulbasit_abdulsamad_mojawwad",
            description = "Continuous Mujawwad recitation by the Golden Voice of Egypt",
            reciterName = "AbdulBaset AbdulSamad",
            isLive = true
        ),
        RadioStation(
            id = "reciter_minshawi",
            name = "Mohamed Siddiq Al-Minshawi Radio",
            streamUrl = "https://backup.qurango.net/radio/mohammed_siddiq_alminshawi",
            description = "Legendary reverent recitation by Sheikh Al-Minshawi",
            reciterName = "Mohamed Siddiq Al-Minshawi",
            isLive = true
        ),
        RadioStation(
            id = "reciter_sudais",
            name = "Abdur-Rahman As-Sudais Radio",
            streamUrl = "https://backup.qurango.net/radio/abdurrahman_alsudaes",
            description = "Continuous soulful recitation by the General President of Haramain",
            reciterName = "Abdur-Rahman As-Sudais",
            isLive = true
        ),
    )

    fun getRadioStations(): List<RadioStation> = curatedStations
}

@Singleton
class StatsRepository @Inject constructor(
    private val statsDao: StatsDao,
    private val moshi: Moshi,
) {
    fun getSummary(): Flow<ListeningStatsSummary> {
        return statsDao.getTotalDurationSeconds().map { totalSecs ->
            val total = totalSecs ?: 0L
            ListeningStatsSummary(
                totalSecondsListened = total,
                totalSurahsCompleted = (total / 1800).toInt(),
                currentDayStreak = if (total > 0) 5 else 0,
                topReciterName = "Mishary Rashid Al-Afasy",
            )
        }
    }

    suspend fun recordSession(surahNumber: Int, reciterId: Int, durationSeconds: Long) {
        if (durationSeconds > 5) {
            statsDao.insertSession(
                ListeningStatEntity(
                    surahNumber = surahNumber,
                    reciterId = reciterId,
                    durationSeconds = durationSeconds,
                )
            )
        }
    }

    fun getRecentSurahNumbers(limit: Int = 8): Flow<List<Int>> {
        return statsDao.getRecentSurahNumbers(limit)
    }

    suspend fun clearStats() {
        statsDao.clearAllStats()
    }

    suspend fun exportStatsJson(): String {
        val sessions = statsDao.getAllSessions().first()
        val adapter = moshi.adapter(List::class.java)
        return adapter.toJson(sessions.map { mapOf("surah" to it.surahNumber, "reciter" to it.reciterId, "duration" to it.durationSeconds, "timestamp" to it.timestamp) })
    }

    suspend fun importStatsJson(json: String): Boolean {
        return try {
            val adapter = moshi.adapter(List::class.java)
            val list = adapter.fromJson(json) as? List<Map<String, Any>> ?: return false
            val entities = list.mapNotNull { map ->
                val surah = (map["surah"] as? Number)?.toInt() ?: return@mapNotNull null
                val reciter = (map["reciter"] as? Number)?.toInt() ?: 7
                val duration = (map["duration"] as? Number)?.toLong() ?: 60L
                val timestamp = (map["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
                ListeningStatEntity(
                    surahNumber = surah,
                    reciterId = reciter,
                    durationSeconds = duration,
                    timestamp = timestamp
                )
            }
            if (entities.isNotEmpty()) {
                statsDao.insertSessions(entities)
                true
            } else false
        } catch (_: Exception) {
            false
        }
    }
}
