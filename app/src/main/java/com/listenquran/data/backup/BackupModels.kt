package com.listenquran.data.backup

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class QuranBackup(
    val schemaVersion: Int = 1,
    val appVersion: String = "1.0.0",
    val exportedAt: Long = System.currentTimeMillis(),
    val checksum: String = "",
    val playlists: List<BackupPlaylist> = emptyList(),
    val likedSurahs: List<BackupLikedSurah> = emptyList(),
    val bookmarks: List<BackupBookmark> = emptyList(),
    val repetitionPresets: List<BackupRepetitionPreset> = emptyList(),
    val listeningStats: List<BackupListeningStat> = emptyList(),
    val preferences: BackupPreferences? = null,
)

@JsonClass(generateAdapter = true)
data class BackupPlaylist(
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val items: List<BackupPlaylistItem> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class BackupPlaylistItem(
    val surahNumber: Int,
    val surahName: String,
    val surahArabic: String,
    val reciterId: Int,
    val reciterName: String,
    val startAyah: Int = 1,
    val endAyah: Int? = null,
    val orderIndex: Int = 0,
)

@JsonClass(generateAdapter = true)
data class BackupLikedSurah(
    val surahNumber: Int,
    val surahName: String,
    val surahArabic: String,
    val reciterId: Int,
    val reciterName: String,
    val likedAt: Long = System.currentTimeMillis(),
)

@JsonClass(generateAdapter = true)
data class BackupBookmark(
    val surahNumber: Int,
    val surahName: String,
    val ayahNumber: Int,
    val verseKey: String,
    val textUthmani: String,
    val translation: String,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@JsonClass(generateAdapter = true)
data class BackupRepetitionPreset(
    val surahNumber: Int,
    val startAyah: Int,
    val endAyah: Int,
    val repeatCount: Int,
    val delaySeconds: Int,
    val label: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@JsonClass(generateAdapter = true)
data class BackupListeningStat(
    val surahNumber: Int,
    val reciterId: Int,
    val durationSeconds: Long,
    val timestamp: Long,
    val isCompleted: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class BackupPreferences(
    val themeMode: String? = null,
    val playbackSpeed: Float? = null,
    val defaultReciterId: Int? = null,
    val wordByWordEnabled: Boolean? = null,
    val translationEnabled: Boolean? = null,
    val hifzRepeatCount: Int? = null,
    val hifzDelaySeconds: Int? = null,
    val customSleepTimerMinutes: Int? = null,
)

data class BackupImportResult(
    val addedPlaylists: Int = 0,
    val skippedPlaylists: Int = 0,
    val addedLiked: Int = 0,
    val skippedLiked: Int = 0,
    val addedBookmarks: Int = 0,
    val skippedBookmarks: Int = 0,
    val addedPresets: Int = 0,
    val skippedPresets: Int = 0,
    val addedStats: Int = 0,
    val skippedStats: Int = 0,
    val restoredPreferences: Boolean = false,
) {
    val totalAdded: Int get() = addedPlaylists + addedLiked + addedBookmarks + addedPresets + addedStats
    val totalSkipped: Int get() = skippedPlaylists + skippedLiked + skippedBookmarks + skippedPresets + skippedStats

    fun toSummaryMessage(): String {
        val parts = mutableListOf<String>()
        if (addedPlaylists > 0) parts.add("$addedPlaylists playlists")
        if (addedLiked > 0) parts.add("$addedLiked liked")
        if (addedBookmarks > 0) parts.add("$addedBookmarks bookmarks")
        if (addedStats > 0) parts.add("$addedStats sessions")
        if (addedPresets > 0) parts.add("$addedPresets presets")

        val addedStr = if (parts.isNotEmpty()) "Added ${parts.joinToString(", ")}" else "No new items added"
        return if (totalSkipped > 0) {
            "$addedStr ($totalSkipped duplicate items skipped)"
        } else {
            addedStr
        }
    }
}
