package com.listenquran.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "surahs")
data class SurahEntity(
    @PrimaryKey val id: Int,
    val nameSimple: String,
    val nameComplex: String,
    val nameArabic: String,
    val revelationPlace: String,
    val revelationOrder: Int,
    val versesCount: Int,
    val startPage: Int,
    val endPage: Int,
    val translatedName: String,
)

@Entity(tableName = "reciters")
data class ReciterEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val englishName: String,
    val style: String?,
    val qiraah: String,
    val photoUrl: String?,
    val surahsCount: Int,
    val isFavorite: Boolean = false,
    val audioServer: String? = null,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val isLikedCollection: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "playlist_items",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["playlistId"])]
)
data class PlaylistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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

@Entity(
    tableName = "bookmarks",
    indices = [Index(value = ["surahNumber", "ayahNumber"], unique = true)]
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val surahNumber: Int,
    val surahName: String,
    val ayahNumber: Int,
    val verseKey: String,
    val textUthmani: String,
    val translation: String,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "khatma")
data class KhatmaEntity(
    @PrimaryKey val id: Long = 1,
    val title: String = "Daily Khatma",
    val currentPage: Int = 1,
    val totalPages: Int = 604,
    val currentJuz: Int = 1,
    val lastSurahNumber: Int = 1,
    val lastAyahNumber: Int = 1,
    val targetDays: Int = 30,
    val pagesPerDayTarget: Int = 4,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "listening_stats")
data class ListeningStatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val surahNumber: Int,
    val reciterId: Int,
    val durationSeconds: Long,
    val timestamp: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "downloaded_surahs",
    indices = [Index(value = ["surahNumber", "reciterId"], unique = true)]
)
data class DownloadedSurahEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val surahNumber: Int,
    val surahName: String,
    val surahArabic: String,
    val reciterId: Int,
    val reciterName: String,
    val localFilePath: String,
    val audioUrl: String,
    val sizeBytes: Long = 0L,
    val downloadedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "translations",
    primaryKeys = ["surahNumber", "ayahNumber", "translationId"],
    indices = [Index(value = ["surahNumber", "translationId"])]
)
data class TranslationEntity(
    val surahNumber: Int,
    val ayahNumber: Int,
    val verseKey: String,
    val translationId: Int,
    val translationName: String,
    val text: String,
)

