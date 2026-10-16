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
}
