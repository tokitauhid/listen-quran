package com.listenquran.di

import android.content.Context
import androidx.room.Room
import com.listenquran.data.local.ListenQuranDatabase
import com.listenquran.data.local.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ListenQuranDatabase {
        return Room.databaseBuilder(
            context,
            ListenQuranDatabase::class.java,
            "listen_quran_db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideSurahDao(db: ListenQuranDatabase): SurahDao = db.surahDao()

    @Provides
    fun provideReciterDao(db: ListenQuranDatabase): ReciterDao = db.reciterDao()

    @Provides
    fun providePlaylistDao(db: ListenQuranDatabase): PlaylistDao = db.playlistDao()

    @Provides
    fun provideBookmarkDao(db: ListenQuranDatabase): BookmarkDao = db.bookmarkDao()

    @Provides
    fun provideKhatmaDao(db: ListenQuranDatabase): KhatmaDao = db.khatmaDao()

    @Provides
    fun provideStatsDao(db: ListenQuranDatabase): StatsDao = db.statsDao()

    @Provides
    fun provideDownloadDao(db: ListenQuranDatabase): DownloadDao = db.downloadDao()

    @Provides
    @Singleton
    fun provideKhatamDatabase(@ApplicationContext context: Context): com.listenquran.data.local.khatam.KhatamDatabase {
        return Room.databaseBuilder(
            context,
            com.listenquran.data.local.khatam.KhatamDatabase::class.java,
            "khatam.db"
        )
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .addCallback(object : androidx.room.RoomDatabase.Callback() {
                override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    super.onOpen(db)
                    // Durability requirement: synchronous = FULL ensures writes survive sudden power loss
                    db.query("PRAGMA synchronous = FULL;").close()
                }
            })
            .build()
    }

    @Provides
    fun provideKhatamDao(db: com.listenquran.data.local.khatam.KhatamDatabase): com.listenquran.data.local.khatam.KhatamDao = db.khatamDao()
}
