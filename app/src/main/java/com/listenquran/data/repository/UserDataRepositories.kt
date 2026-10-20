package com.listenquran.data.repository

import com.listenquran.data.local.dao.BookmarkDao
import com.listenquran.data.local.dao.KhatmaDao
import com.listenquran.data.local.dao.LikedDao
import com.listenquran.data.local.dao.RepetitionPresetDao
import com.listenquran.data.local.dao.StatsDao
import com.listenquran.data.local.entity.BookmarkEntity
import com.listenquran.data.local.entity.KhatmaEntity
import com.listenquran.data.local.dao.ReciterDao
import com.listenquran.data.local.dao.ReciterDurationShare
import com.listenquran.data.local.entity.LikedSurahEntity
import com.listenquran.data.local.entity.ListeningStatEntity
import com.listenquran.data.local.entity.RepetitionPresetEntity
import com.listenquran.domain.model.*
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
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

/**
 * METRIC DEFINITIONS & SPECIFICATIONS:
 * 1. Total Time: Exact sum of durationSeconds from listening_stats table for all qualifying playback sessions (>= 5s).
 * 2. Current Streak: Consecutive local calendar days (in user's active timezone) with at least one qualifying
 *    session (>= 5s), ending either today or yesterday. If no sessions exist for today or yesterday, streak is 0.
 * 3. Surahs Completed: Count of distinct Surahs completed, recorded when playback reaches the end of the
 *    recitation (Player.STATE_ENDED or auto-transition to next track, or >= 90% of duration was listened).
 * 4. Weekly Activity: Real minutes per day for the last 7 calendar days ending today in the user's local timezone,
 *    with localized weekday labels in locale order, and Friday marked as the Sunnah Kahf peak.
 * 5. Reciter Breakdown: Real listening duration percentage per reciter, displaying the top 4 reciters
 *    with names resolved from ReciterDao, plus an "Other Reciters" aggregate bucket if more than 4 reciters exist.
 */
object StatsCalculators {
    fun calculateStreak(
        timestamps: List<Long>,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Int {
        if (timestamps.isEmpty()) return 0
        val days = timestamps.map {
            Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate()
        }.distinct().sortedDescending()

        if (days.isEmpty()) return 0

        val today = LocalDate.now(zoneId)
        val yesterday = today.minusDays(1)

        val mostRecent = days.first()
        if (mostRecent != today && mostRecent != yesterday) {
            return 0
        }

        var streak = 0
        var expectedDay = mostRecent
        for (day in days) {
            if (day == expectedDay) {
                streak++
                expectedDay = expectedDay.minusDays(1)
            } else if (day.isBefore(expectedDay)) {
                break
            }
        }
        return streak
    }

    @JvmName("calculateStreakFromSessions")
    fun calculateStreak(
        sessions: List<ListeningStatEntity>,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Int {
        val qualifying = sessions.filter { it.durationSeconds >= 5 }.map { it.timestamp }
        return calculateStreak(qualifying, zoneId)
    }

    fun calculateWeeklyActivity(
        sessions: List<ListeningStatEntity>,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<DailyListeningActivity> {
        val today = LocalDate.now(zoneId)
        val dayBuckets = (6 downTo 0).map { today.minusDays(it.toLong()) }

        val secondsByDate = mutableMapOf<LocalDate, Long>()
        for (session in sessions) {
            val date = Instant.ofEpochMilli(session.timestamp).atZone(zoneId).toLocalDate()
            secondsByDate[date] = (secondsByDate[date] ?: 0L) + session.durationSeconds
        }

        val dayFormatter = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())

        return dayBuckets.map { date ->
            val totalSecs = secondsByDate[date] ?: 0L
            DailyListeningActivity(
                dayLabel = date.format(dayFormatter),
                minutes = (totalSecs / 60).toInt(),
                isFriday = date.dayOfWeek == DayOfWeek.FRIDAY,
                dateEpochDays = date.toEpochDay()
            )
        }
    }

    fun calculateReciterShares(
        shares: List<ReciterDurationShare>,
        reciterMap: Map<Int, String>
    ): List<ReciterListeningShare> {
        val totalSecs = shares.sumOf { it.totalSeconds }
        if (totalSecs <= 0 || shares.isEmpty()) return emptyList()

        val topShares = shares.take(5)
        val otherShares = shares.drop(5)

        val result = mutableListOf<ReciterListeningShare>()
        for (share in topShares) {
            val name = reciterMap[share.reciterId] ?: "Reciter #${share.reciterId}"
            val pct = (share.totalSeconds.toDouble() / totalSecs.toDouble()).toFloat().coerceIn(0f, 1f)
            result.add(
                ReciterListeningShare(
                    reciterId = share.reciterId,
                    reciterName = name,
                    seconds = share.totalSeconds,
                    percentage = pct,
                )
            )
        }

        if (otherShares.isNotEmpty()) {
            val otherSecs = otherShares.sumOf { it.totalSeconds }
            val otherPct = (otherSecs.toDouble() / totalSecs.toDouble()).toFloat().coerceIn(0f, 1f)
            result.add(
                ReciterListeningShare(
                    reciterId = -1,
                    reciterName = "Other",
                    seconds = otherSecs,
                    percentage = otherPct,
                )
            )
        }

        return result
    }

    suspend fun calculateReciterShares(
        shares: List<ReciterDurationShare>,
        totalSecs: Long,
        resolveReciterName: suspend (Int) -> String
    ): List<ReciterListeningShare> {
        if (totalSecs <= 0 || shares.isEmpty()) return emptyList()

        val topShares = shares.take(4)
        val otherShares = shares.drop(4)

        val result = mutableListOf<ReciterListeningShare>()
        for (share in topShares) {
            val name = resolveReciterName(share.reciterId)
            val pct = (share.totalSeconds.toDouble() / totalSecs.toDouble()).toFloat().coerceIn(0f, 1f)
            result.add(
                ReciterListeningShare(
                    reciterId = share.reciterId,
                    reciterName = name,
                    seconds = share.totalSeconds,
                    percentage = pct,
                )
            )
        }

        if (otherShares.isNotEmpty()) {
            val otherSecs = otherShares.sumOf { it.totalSeconds }
            val otherPct = (otherSecs.toDouble() / totalSecs.toDouble()).toFloat().coerceIn(0f, 1f)
            result.add(
                ReciterListeningShare(
                    reciterId = -1,
                    reciterName = "Other Reciters",
                    seconds = otherSecs,
                    percentage = otherPct,
                )
            )
        }

        return result
    }
}

private data class BasicStatsAggregate(
    val totalSecs: Long,
    val completedCount: Int,
    val qualifyingTimestamps: List<Long>,
    val topReciterId: Int?
)

@Singleton
class StatsRepository @Inject constructor(
    private val statsDao: StatsDao,
    private val reciterDao: ReciterDao,
    private val moshi: Moshi,
) {
    fun getSummary(zoneId: ZoneId = ZoneId.systemDefault()): Flow<ListeningStatsSummary> {
        val sevenDaysAgoMillis = LocalDate.now(zoneId).minusDays(6).atStartOfDay(zoneId).toInstant().toEpochMilli()

        val basicFlow = combine(
            statsDao.getTotalDurationSeconds(),
            statsDao.getCompletedSurahsCount(),
            statsDao.getQualifyingTimestamps(),
            statsDao.getTopReciterId()
        ) { totalSecsRaw, completedCount, qualifyingTimestamps, topReciterId ->
            BasicStatsAggregate(
                totalSecs = totalSecsRaw ?: 0L,
                completedCount = completedCount,
                qualifyingTimestamps = qualifyingTimestamps,
                topReciterId = topReciterId
            )
        }

        return combine(
            basicFlow,
            statsDao.getReciterDurationShares(),
            statsDao.getSessionsSince(sevenDaysAgoMillis),
        ) { basic, reciterSharesRaw, recentSessions ->
            val streak = StatsCalculators.calculateStreak(basic.qualifyingTimestamps, zoneId)

            val topReciterName = if (basic.topReciterId != null && basic.topReciterId > 0) {
                val entity = reciterDao.getReciterById(basic.topReciterId)
                entity?.englishName ?: "Reciter #${basic.topReciterId}"
            } else ""

            val weeklyActivity = StatsCalculators.calculateWeeklyActivity(recentSessions, zoneId)

            val reciterShares = StatsCalculators.calculateReciterShares(
                shares = reciterSharesRaw,
                totalSecs = basic.totalSecs,
                resolveReciterName = { id ->
                    val entity = reciterDao.getReciterById(id)
                    entity?.englishName ?: "Reciter #$id"
                }
            )

            ListeningStatsSummary(
                totalSecondsListened = basic.totalSecs,
                totalSurahsCompleted = basic.completedCount,
                currentDayStreak = streak,
                topReciterName = topReciterName,
                weeklyActivity = weeklyActivity,
                reciterShares = reciterShares,
            )
        }
    }

    suspend fun recordSession(surahNumber: Int, reciterId: Int, durationSeconds: Long, isCompleted: Boolean = false) {
        if (durationSeconds >= 5) {
            statsDao.insertSession(
                ListeningStatEntity(
                    surahNumber = surahNumber,
                    reciterId = reciterId,
                    durationSeconds = durationSeconds,
                    timestamp = System.currentTimeMillis(),
                    isCompleted = isCompleted,
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
        return adapter.toJson(sessions.map { mapOf("surah" to it.surahNumber, "reciter" to it.reciterId, "duration" to it.durationSeconds, "timestamp" to it.timestamp, "completed" to it.isCompleted) })
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
                val isCompleted = (map["completed"] as? Boolean) ?: false
                ListeningStatEntity(
                    surahNumber = surah,
                    reciterId = reciter,
                    durationSeconds = duration,
                    timestamp = timestamp,
                    isCompleted = isCompleted,
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

