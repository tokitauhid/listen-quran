package com.listenquran.data.repository

import com.listenquran.data.local.dao.BookmarkDao
import com.listenquran.data.local.dao.KhatmaDao
import com.listenquran.data.local.dao.LikedDao
import com.listenquran.data.local.dao.RepetitionPresetDao
import com.listenquran.data.local.dao.StatsDao
import com.listenquran.data.local.entity.BookmarkEntity
import com.listenquran.data.local.entity.KhatmaEntity
import com.listenquran.data.local.entity.LikedSurahEntity
import com.listenquran.data.local.entity.ListeningStatEntity
import com.listenquran.data.local.entity.RepetitionPresetEntity
import com.listenquran.domain.model.*
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    suspend fun deleteBookmarkById(id: Long) {
        bookmarkDao.deleteBookmarkById(id)
    }

    suspend fun deleteBookmark(bookmark: Bookmark) {
        bookmarkDao.deleteBookmark(bookmark.surahNumber, bookmark.ayahNumber)
    }
}

@Singleton
class RepetitionPresetRepository @Inject constructor(
    private val repetitionPresetDao: RepetitionPresetDao,
) {
    fun getAllPresets(): Flow<List<RepetitionPreset>> {
        return repetitionPresetDao.getAllPresets().map { list ->
            list.map {
                RepetitionPreset(
                    id = it.id,
                    surahNumber = it.surahNumber,
                    startAyah = it.startAyah,
                    endAyah = it.endAyah,
                    repeatCount = it.repeatCount,
                    delaySeconds = it.delaySeconds,
                    label = it.label,
                    createdAt = it.createdAt,
                )
            }
        }
    }

    suspend fun savePreset(preset: RepetitionPreset): Long {
        return repetitionPresetDao.insertPreset(
            RepetitionPresetEntity(
                id = preset.id,
                surahNumber = preset.surahNumber,
                startAyah = preset.startAyah,
                endAyah = preset.endAyah,
                repeatCount = preset.repeatCount,
                delaySeconds = preset.delaySeconds,
                label = preset.label,
                createdAt = preset.createdAt,
            )
        )
    }

    suspend fun updatePreset(preset: RepetitionPreset) {
        repetitionPresetDao.updatePreset(
            RepetitionPresetEntity(
                id = preset.id,
                surahNumber = preset.surahNumber,
                startAyah = preset.startAyah,
                endAyah = preset.endAyah,
                repeatCount = preset.repeatCount,
                delaySeconds = preset.delaySeconds,
                label = preset.label,
                createdAt = preset.createdAt,
            )
        )
    }

    suspend fun deletePreset(id: Long) {
        repetitionPresetDao.deletePreset(id)
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

@Singleton
class LikedRepository @Inject constructor(
    private val likedDao: LikedDao,
) {
    private val likedMutex = Mutex()

    fun getAllLiked(): Flow<List<LikedSurah>> {
        return likedDao.getAllLiked().map { list ->
            list.map {
                LikedSurah(
                    id = it.id,
                    surahNumber = it.surahNumber,
                    surahName = it.surahName,
                    surahArabic = it.surahArabic,
                    reciterId = it.reciterId,
                    reciterName = it.reciterName,
                    likedAt = it.likedAt,
                )
            }
        }
    }

    fun isLiked(surahNumber: Int, reciterId: Int): Flow<Boolean> {
        return likedDao.isLiked(surahNumber, reciterId)
    }

    suspend fun isLikedDirect(surahNumber: Int, reciterId: Int): Boolean {
        return likedDao.isLikedDirect(surahNumber, reciterId)
    }

    suspend fun toggleLike(
        surahNumber: Int,
        surahName: String,
        surahArabic: String,
        reciterId: Int,
        reciterName: String,
    ): Boolean = likedMutex.withLock {
        val currentlyLiked = likedDao.isLikedDirect(surahNumber, reciterId)
        if (currentlyLiked) {
            likedDao.removeLiked(surahNumber, reciterId)
            false
        } else {
            likedDao.insertLiked(
                LikedSurahEntity(
                    surahNumber = surahNumber,
                    surahName = surahName,
                    surahArabic = surahArabic,
                    reciterId = reciterId,
                    reciterName = reciterName,
                    likedAt = System.currentTimeMillis(),
                )
            )
            true
        }
    }

    suspend fun addLike(
        surahNumber: Int,
        surahName: String,
        surahArabic: String,
        reciterId: Int,
        reciterName: String,
    ): Long = likedMutex.withLock {
        likedDao.insertLiked(
            LikedSurahEntity(
                surahNumber = surahNumber,
                surahName = surahName,
                surahArabic = surahArabic,
                reciterId = reciterId,
                reciterName = reciterName,
                likedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun removeLike(surahNumber: Int, reciterId: Int) = likedMutex.withLock {
        likedDao.removeLiked(surahNumber, reciterId)
    }

    suspend fun deleteById(id: Long) = likedMutex.withLock {
        likedDao.deleteById(id)
    }

    suspend fun getCount(): Int {
        return likedDao.getCount()
    }
}

