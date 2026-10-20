package com.listenquran.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class Playlist(
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val isLikedCollection: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val items: List<PlaylistItem> = emptyList(),
)

@Immutable
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

@Immutable
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

@Immutable
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

@Immutable
data class HifzLoopConfig(
    val surahNumber: Int = 1,
    val startAyah: Int = 1,
    val endAyah: Int = 7,
    val repeatCount: Int = 3, // 0 for continuous (infinity)
    val delaySeconds: Int = 3,
)

@Immutable
data class RepetitionPreset(
    val id: Long = 0,
    val surahNumber: Int,
    val surahName: String = "",
    val startAyah: Int,
    val endAyah: Int,
    val repeatCount: Int, // 1..100, 0 for continuous (infinite)
    val delaySeconds: Int = 3,
    val label: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Immutable
data class ListeningSession(
    val id: Long = 0,
    val surahNumber: Int,
    val reciterId: Int,
    val durationSeconds: Long,
    val timestamp: Long = System.currentTimeMillis(),
)

@Immutable
data class DailyListeningActivity(
    val dayLabel: String,
    val minutes: Int,
    val isFriday: Boolean = false,
    val dateEpochDays: Long = 0,
)

@Immutable
data class ReciterListeningShare(
    val reciterId: Int,
    val reciterName: String,
    val seconds: Long,
    val percentage: Float,
)

@Immutable
data class ListeningStatsSummary(
    val totalSecondsListened: Long = 0,
    val totalSurahsCompleted: Int = 0,
    val currentDayStreak: Int = 0,
    val topReciterName: String = "",
    val weeklyActivity: List<DailyListeningActivity> = emptyList(),
    val reciterShares: List<ReciterListeningShare> = emptyList(),
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

@Immutable
data class LikedSurah(
    val id: Long = 0,
    val surahNumber: Int,
    val surahName: String,
    val surahArabic: String,
    val reciterId: Int,
    val reciterName: String,
    val likedAt: Long = System.currentTimeMillis(),
) {
    fun toPlaylistItem(playlistId: Long = 0): PlaylistItem {
        return PlaylistItem(
            id = id,
            playlistId = playlistId,
            surahNumber = surahNumber,
            surahName = surahName,
            surahArabic = surahArabic,
            reciterId = reciterId,
            reciterName = reciterName,
            startAyah = 1,
            endAyah = null,
            orderIndex = 0,
        )
    }
}

object RepetitionRangeValidator {
    data class ValidationResult(
        val isValid: Boolean,
        val parsedStartAyah: Int? = null,
        val parsedEndAyah: Int? = null,
        val parsedRepeatCount: Int? = null,
        val startError: String? = null,
        val endError: String? = null,
        val repeatCountError: String? = null,
    )

    fun validate(
        startText: String,
        endText: String,
        repeatCountText: String,
        maxAyahs: Int,
        isInfiniteRepeat: Boolean = false,
    ): ValidationResult {
        val startNum = startText.trim().toIntOrNull()
        val startError = when {
            startText.isBlank() -> "Enter a start ayah"
            startNum == null || startNum < 1 -> "Must be at least 1"
            startNum > maxAyahs -> "Exceeds surah limit ($maxAyahs)"
            else -> null
        }

        // Blank end text means single ayah repetition (endAyah = startAyah)
        val endNum = if (endText.isBlank()) startNum else endText.trim().toIntOrNull()
        val endError = when {
            endText.isNotBlank() && endNum == null -> "Invalid number"
            endText.isNotBlank() && endNum != null && endNum > maxAyahs -> "Exceeds surah limit ($maxAyahs)"
            startNum != null && endNum != null && endNum < startNum -> "End must be ≥ start ($startNum)"
            else -> null
        }

        val repeatNum = if (isInfiniteRepeat) 0 else repeatCountText.trim().toIntOrNull()
        val repeatError = when {
            isInfiniteRepeat -> null
            repeatCountText.isBlank() -> "Enter repeat count"
            repeatNum == null || repeatNum < 1 -> "Must be at least 1"
            repeatNum > 100 -> "Maximum is 100"
            else -> null
        }

        val isValid = startError == null && endError == null && repeatError == null &&
                startNum != null && endNum != null && (isInfiniteRepeat || repeatNum != null)

        return ValidationResult(
            isValid = isValid,
            parsedStartAyah = if (isValid) startNum else null,
            parsedEndAyah = if (isValid) endNum else null,
            parsedRepeatCount = if (isValid) (if (isInfiniteRepeat) 0 else repeatNum) else null,
            startError = startError,
            endError = endError,
            repeatCountError = repeatError,
        )
    }
}

