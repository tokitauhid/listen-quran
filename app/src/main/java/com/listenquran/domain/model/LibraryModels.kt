package com.listenquran.domain.model

data class Playlist(
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val isLikedCollection: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val items: List<PlaylistItem> = emptyList(),
)

data class PlaylistItem(
    val id: Long = 0,
    val playlistId: Long,
    val surahNumber: Int,
    val surahName: String,
    val surahArabic: String,
    val reciterId: Int,
    val reciterName: String,
    val startAyah: Int = 1,
    val endAyah: Int? = null,
    val orderIndex: Int = 0,
)

data class Bookmark(
    val id: Long = 0,
    val surahNumber: Int,
    val surahName: String,
    val ayahNumber: Int,
    val verseKey: String,
    val textUthmani: String,
    val translation: String,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

data class KhatmaProgress(
    val id: Long = 1,
    val title: String = "Daily Khatma",
    val currentPage: Int = 1,
    val totalPages: Int = 604,
    val currentJuz: Int = 1,
    val lastSurahNumber: Int = 1,
    val lastAyahNumber: Int = 1,
    val targetDays: Int = 30,
    val pagesPerDayTarget: Int = 4,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val progressFraction: Float get() = (currentPage.toFloat() / totalPages.toFloat()).coerceIn(0f, 1f)
    val progressPercentage: Int get() = (progressFraction * 100).toInt()
}

data class HifzLoopConfig(
    val surahNumber: Int = 1,
    val startAyah: Int = 1,
    val endAyah: Int = 7,
    val repeatCount: Int = 3, // 0 for continuous (infinity)
    val delaySeconds: Int = 3,
)

data class ListeningSession(
    val id: Long = 0,
    val surahNumber: Int,
    val reciterId: Int,
    val durationSeconds: Long,
    val timestamp: Long = System.currentTimeMillis(),
)

data class ListeningStatsSummary(
    val totalSecondsListened: Long = 0,
    val totalSurahsCompleted: Int = 0,
    val currentDayStreak: Int = 0,
    val topReciterName: String = "",
)

enum class ThemeMode {
    SYSTEM,
    DARK,
    LIGHT,
    AMOLED,
}

enum class AudioQuality {
    STANDARD, // 128 kbps
    HIGH,     // 192 kbps
}
