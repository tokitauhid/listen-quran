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

    private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `downloaded_surahs` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `surahNumber` INTEGER NOT NULL,
                    `surahName` TEXT NOT NULL,
                    `surahArabic` TEXT NOT NULL,
                    `reciterId` INTEGER NOT NULL,
                    `reciterName` TEXT NOT NULL,
                    `localFilePath` TEXT NOT NULL,
                    `audioUrl` TEXT NOT NULL,
                    `sizeBytes` INTEGER NOT NULL,
                    `downloadedAt` INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_downloaded_surahs_surahNumber_reciterId` ON `downloaded_surahs` (`surahNumber`, `reciterId`)")
        }
    }

    val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `translations` (
                    `surahNumber` INTEGER NOT NULL,
                    `ayahNumber` INTEGER NOT NULL,
                    `verseKey` TEXT NOT NULL,
                    `translationId` INTEGER NOT NULL,
                    `translationName` TEXT NOT NULL,
                    `text` TEXT NOT NULL,
                    PRIMARY KEY(`surahNumber`, `ayahNumber`, `translationId`)
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_translations_surahNumber_translationId` ON `translations` (`surahNumber`, `translationId`)")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ListenQuranDatabase {
        return Room.databaseBuilder(
            context,
            ListenQuranDatabase::class.java,
            "listen_quran_db"
        )
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
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
    fun provideTranslationDao(db: ListenQuranDatabase): TranslationDao = db.translationDao()

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
