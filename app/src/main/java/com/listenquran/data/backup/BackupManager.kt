package com.listenquran.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.listenquran.BuildConfig
import com.listenquran.data.local.ListenQuranDatabase
import com.listenquran.data.local.entity.*
import com.listenquran.data.local.preferences.UserPreferencesRepository
import com.listenquran.domain.model.ThemeMode
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: ListenQuranDatabase,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val moshi: Moshi,
) {
    private val playlistDao = database.playlistDao()
    private val likedDao = database.likedDao()
    private val bookmarkDao = database.bookmarkDao()
    private val repetitionPresetDao = database.repetitionPresetDao()
    private val statsDao = database.statsDao()

    private val backupAdapter by lazy {
        moshi.adapter(QuranBackup::class.java)
    }

    suspend fun createBackupPayload(): QuranBackup = withContext(Dispatchers.IO) {
        val playlists = playlistDao.getAllPlaylists().first()
            .filter { !it.isLikedCollection } // Never export Liked collection as a standard playlist
            .map { pl ->
                val items = playlistDao.getPlaylistItems(pl.id).first().map { item ->
                    BackupPlaylistItem(
                        surahNumber = item.surahNumber,
                        surahName = item.surahName,
                        surahArabic = item.surahArabic,
                        reciterId = item.reciterId,
                        reciterName = item.reciterName,
                        startAyah = item.startAyah,
                        endAyah = item.endAyah,
                        orderIndex = item.orderIndex,
                    )
                }
                BackupPlaylist(
                    name = pl.name,
                    description = pl.description,
                    createdAt = pl.createdAt,
                    items = items,
                )
            }

        val liked = likedDao.getAllLiked().first().map {
            BackupLikedSurah(
                surahNumber = it.surahNumber,
                surahName = it.surahName,
                surahArabic = it.surahArabic,
                reciterId = it.reciterId,
                reciterName = it.reciterName,
                likedAt = it.likedAt,
            )
        }

        val bookmarks = bookmarkDao.getAllBookmarks().first().map {
            BackupBookmark(
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

        val presets = repetitionPresetDao.getAllPresets().first().map {
            BackupRepetitionPreset(
                surahNumber = it.surahNumber,
                startAyah = it.startAyah,
                endAyah = it.endAyah,
                repeatCount = it.repeatCount,
                delaySeconds = it.delaySeconds,
                label = it.label,
                createdAt = it.createdAt,
            )
        }

        val stats = statsDao.getAllSessions().first().map {
            BackupListeningStat(
                surahNumber = it.surahNumber,
                reciterId = it.reciterId,
                durationSeconds = it.durationSeconds,
                timestamp = it.timestamp,
                isCompleted = it.isCompleted,
            )
        }

        val themeMode = userPreferencesRepository.themeMode.first()
        val speed = userPreferencesRepository.playbackSpeed.first()
        val reciterId = userPreferencesRepository.lastReciterId.first()
        val wbw = userPreferencesRepository.wordByWordEnabled.first()
        val translation = userPreferencesRepository.translationEnabled.first()
        val repeat = userPreferencesRepository.hifzRepeatCount.first()
        val delay = userPreferencesRepository.hifzDelaySeconds.first()
        val sleepTimer = userPreferencesRepository.customSleepTimerMinutes.first()

        val prefs = BackupPreferences(
            themeMode = themeMode.name,
            playbackSpeed = speed,
            defaultReciterId = reciterId,
            wordByWordEnabled = wbw,
            translationEnabled = translation,
            hifzRepeatCount = repeat,
            hifzDelaySeconds = delay,
            customSleepTimerMinutes = sleepTimer,
        )

        val unchecksummed = QuranBackup(
            schemaVersion = 1,
            appVersion = BuildConfig.VERSION_NAME,
            exportedAt = System.currentTimeMillis(),
            checksum = "",
            playlists = playlists,
            likedSurahs = liked,
            bookmarks = bookmarks,
            repetitionPresets = presets,
            listeningStats = stats,
            preferences = prefs,
        )

        val jsonString = backupAdapter.toJson(unchecksummed)
        val checksum = computeSha256(jsonString)

        unchecksummed.copy(checksum = checksum)
    }

    suspend fun exportBackupToUri(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val backup = createBackupPayload()
            val json = backupAdapter.indent("  ").toJson(backup)
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(json.toByteArray(Charsets.UTF_8))
                stream.flush()
            } ?: return@withContext Result.failure(Exception("Cannot open destination stream"))

            userPreferencesRepository.setLastBackupTimestamp(System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importBackupFromUri(uri: Uri, replaceExisting: Boolean = false): Result<BackupImportResult> = withContext(Dispatchers.IO) {
        try {
            // Guard against unbounded file reading (max 20 MB)
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(Exception("Cannot open input stream"))

            val json = inputStream.use { stream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytes = 0
                val out = java.io.ByteArrayOutputStream()
                while (stream.read(buffer).also { bytesRead = it } != -1) {
                    totalBytes += bytesRead
                    if (totalBytes > 20 * 1024 * 1024) {
                        return@withContext Result.failure(Exception("File exceeds maximum allowed size (20 MB)"))
                    }
                    out.write(buffer, 0, bytesRead)
                }
                out.toString(Charsets.UTF_8.name())
            }

            val backup = try {
                backupAdapter.fromJson(json)
            } catch (e: Exception) {
                return@withContext Result.failure(Exception("Invalid backup JSON format: ${e.message}"))
            } ?: return@withContext Result.failure(Exception("Empty or malformed backup file"))

            // Verify checksum if present
            if (backup.checksum.isNotBlank()) {
                val candidateJson = backupAdapter.toJson(backup.copy(checksum = ""))
                val calculatedHash = computeSha256(candidateJson)
                if (!calculatedHash.equals(backup.checksum, ignoreCase = true)) {
                    return@withContext Result.failure(Exception("Backup checksum mismatch: file may be corrupted"))
                }
            }

            // Execute atomic Room transaction
            val result = database.withTransaction {
                if (replaceExisting) {
                    // Wipe existing tables (except downloaded files)
                    database.openHelper.writableDatabase.execSQL("DELETE FROM playlists WHERE isLikedCollection = 0")
                    database.openHelper.writableDatabase.execSQL("DELETE FROM playlist_items")
                    database.openHelper.writableDatabase.execSQL("DELETE FROM liked_surahs")
                    database.openHelper.writableDatabase.execSQL("DELETE FROM bookmarks")
                    database.openHelper.writableDatabase.execSQL("DELETE FROM repetition_presets")
                    database.openHelper.writableDatabase.execSQL("DELETE FROM listening_stats")
                }

                // 1. Restore Liked
                var addedLiked = 0
                var skippedLiked = 0
                for (item in backup.likedSurahs) {
                    val alreadyLiked = likedDao.isLikedDirect(item.surahNumber, item.reciterId)
                    if (!alreadyLiked) {
                        likedDao.insertLiked(
                            LikedSurahEntity(
                                surahNumber = item.surahNumber,
                                surahName = item.surahName,
                                surahArabic = item.surahArabic,
                                reciterId = item.reciterId,
                                reciterName = item.reciterName,
                                likedAt = item.likedAt,
                            )
                        )
                        addedLiked++
                    } else {
                        skippedLiked++
                    }
                }

                // 2. Restore Playlists (deduplicate by name)
                var addedPlaylists = 0
                var skippedPlaylists = 0
                val existingPlaylists = playlistDao.getAllPlaylists().first()

                for (pl in backup.playlists) {
                    val duplicate = existingPlaylists.find { it.name.trim().equals(pl.name.trim(), ignoreCase = true) }
                    if (duplicate != null && !replaceExisting) {
                        skippedPlaylists++
                    } else {
                        val newId = playlistDao.insertPlaylist(
                            PlaylistEntity(
                                name = pl.name,
                                description = pl.description,
                                isLikedCollection = false, // Strictly false!
                                createdAt = pl.createdAt,
                            )
                        )
                        if (pl.items.isNotEmpty()) {
                            val entities = pl.items.mapIndexed { idx, item ->
                                PlaylistItemEntity(
                                    playlistId = newId,
                                    surahNumber = item.surahNumber,
                                    surahName = item.surahName,
                                    surahArabic = item.surahArabic,
                                    reciterId = item.reciterId,
                                    reciterName = item.reciterName,
                                    startAyah = item.startAyah,
                                    endAyah = item.endAyah,
                                    orderIndex = item.orderIndex.takeIf { it > 0 } ?: idx,
                                )
                            }
                            playlistDao.insertPlaylistItems(entities)
                        }
                        addedPlaylists++
                    }
                }

                // 3. Restore Bookmarks (deduplicate by surahNumber + ayahNumber)
                var addedBookmarks = 0
                var skippedBookmarks = 0
                val existingBookmarks = bookmarkDao.getAllBookmarks().first()

                for (b in backup.bookmarks) {
                    val exists = existingBookmarks.any { it.surahNumber == b.surahNumber && it.ayahNumber == b.ayahNumber }
                    if (exists && !replaceExisting) {
                        skippedBookmarks++
                    } else {
                        bookmarkDao.insertBookmark(
                            BookmarkEntity(
                                surahNumber = b.surahNumber,
                                surahName = b.surahName,
                                ayahNumber = b.ayahNumber,
                                verseKey = b.verseKey,
                                textUthmani = b.textUthmani,
                                translation = b.translation,
                                note = b.note,
                                createdAt = b.createdAt,
                            )
                        )
                        addedBookmarks++
                    }
                }

                // 4. Restore Repetition Presets
                var addedPresets = 0
                var skippedPresets = 0
                val existingPresets = repetitionPresetDao.getAllPresets().first()

                for (p in backup.repetitionPresets) {
                    val exists = existingPresets.any {
                        it.surahNumber == p.surahNumber && it.startAyah == p.startAyah && it.endAyah == p.endAyah && it.repeatCount == p.repeatCount && it.delaySeconds == p.delaySeconds
                    }
                    if (exists && !replaceExisting) {
                        skippedPresets++
                    } else {
                        repetitionPresetDao.insertPreset(
                            RepetitionPresetEntity(
                                surahNumber = p.surahNumber,
                                startAyah = p.startAyah,
                                endAyah = p.endAyah,
                                repeatCount = p.repeatCount,
                                delaySeconds = p.delaySeconds,
                                label = p.label,
                                createdAt = p.createdAt,
                            )
                        )
                        addedPresets++
                    }
                }

                // 5. Restore Stats (deduplicate by timestamp + surahNumber + reciterId)
                var addedStats = 0
                var skippedStats = 0
                val existingStats = statsDao.getAllSessions().first()

                val newStats = mutableListOf<ListeningStatEntity>()
                for (s in backup.listeningStats) {
                    val exists = existingStats.any { it.timestamp == s.timestamp && it.surahNumber == s.surahNumber && it.reciterId == s.reciterId }
                    if (exists && !replaceExisting) {
                        skippedStats++
                    } else {
                        newStats.add(
                            ListeningStatEntity(
                                surahNumber = s.surahNumber,
                                reciterId = s.reciterId,
                                durationSeconds = s.durationSeconds,
                                timestamp = s.timestamp,
                                isCompleted = s.isCompleted,
                            )
                        )
                        addedStats++
                    }
                }
                if (newStats.isNotEmpty()) {
                    statsDao.insertSessions(newStats)
                }

                BackupImportResult(
                    addedPlaylists = addedPlaylists,
                    skippedPlaylists = skippedPlaylists,
                    addedLiked = addedLiked,
                    skippedLiked = skippedLiked,
                    addedBookmarks = addedBookmarks,
                    skippedBookmarks = skippedBookmarks,
                    addedPresets = addedPresets,
                    skippedPresets = skippedPresets,
                    addedStats = addedStats,
                    skippedStats = skippedStats,
                    restoredPreferences = backup.preferences != null,
                )
            }

            // 6. Restore Preferences if available
            backup.preferences?.let { prefs ->
                prefs.themeMode?.let {
                    try {
                        userPreferencesRepository.setThemeMode(ThemeMode.valueOf(it))
                    } catch (_: Exception) {}
                }
                prefs.playbackSpeed?.let { userPreferencesRepository.setPlaybackSpeed(it) }
                prefs.defaultReciterId?.let { userPreferencesRepository.setLastReciterId(it) }
                prefs.wordByWordEnabled?.let { userPreferencesRepository.setWordByWordEnabled(it) }
                prefs.translationEnabled?.let { userPreferencesRepository.setTranslationEnabled(it) }
                if (prefs.hifzRepeatCount != null && prefs.hifzDelaySeconds != null) {
                    userPreferencesRepository.setHifzLoop(prefs.hifzRepeatCount, prefs.hifzDelaySeconds)
                }
                prefs.customSleepTimerMinutes?.let { userPreferencesRepository.setCustomSleepTimerMinutes(it) }
            }

            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun computeSha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}
