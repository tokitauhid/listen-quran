package com.listenquran.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.listenquran.data.local.dao.*
import com.listenquran.data.local.entity.*

@Database(
    entities = [
        SurahEntity::class,
        ReciterEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
        BookmarkEntity::class,
        KhatmaEntity::class,
        ListeningStatEntity::class,
        DownloadedSurahEntity::class,
        TranslationEntity::class,
    ],
    version = 4,
    exportSchema = false
)
abstract class ListenQuranDatabase : RoomDatabase() {
    abstract fun surahDao(): SurahDao
    abstract fun reciterDao(): ReciterDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun khatmaDao(): KhatmaDao
    abstract fun statsDao(): StatsDao
    abstract fun downloadDao(): DownloadDao
    abstract fun translationDao(): TranslationDao
}
