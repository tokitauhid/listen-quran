package com.listenquran.data.local.dao

import androidx.room.*
import com.listenquran.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SurahDao {
    @Query("SELECT * FROM surahs ORDER BY id ASC")
    fun getAllSurahs(): Flow<List<SurahEntity>>

    @Query("SELECT * FROM surahs WHERE id = :id LIMIT 1")
    suspend fun getSurahById(id: Int): SurahEntity?

    @Query("SELECT * FROM surahs WHERE nameSimple LIKE '%' || :query || '%' OR nameComplex LIKE '%' || :query || '%' OR translatedName LIKE '%' || :query || '%' ORDER BY id ASC")
    fun searchSurahs(query: String): Flow<List<SurahEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSurahs(surahs: List<SurahEntity>)
}

@Dao
interface ReciterDao {
    @Query("SELECT * FROM reciters ORDER BY englishName ASC")
    fun getAllReciters(): Flow<List<ReciterEntity>>

    @Query("SELECT * FROM reciters WHERE isFavorite = 1 ORDER BY englishName ASC")
    fun getFavoriteReciters(): Flow<List<ReciterEntity>>

    @Query("SELECT * FROM reciters WHERE id = :id LIMIT 1")
    suspend fun getReciterById(id: Int): ReciterEntity?

    @Query("SELECT * FROM reciters WHERE englishName LIKE '%' || :query || '%' OR name LIKE '%' || :query || '%'")
    fun searchReciters(query: String): Flow<List<ReciterEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReciters(reciters: List<ReciterEntity>)

    @Query("UPDATE reciters SET isFavorite = :isFavorite WHERE id = :reciterId")
    suspend fun updateFavorite(reciterId: Int, isFavorite: Boolean)
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY isLikedCollection DESC, createdAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE isLikedCollection = 1 LIMIT 1")
    fun getLikedCollection(): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
    suspend fun getPlaylistById(playlistId: Long): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY orderIndex ASC")
    fun getPlaylistItems(playlistId: Long): Flow<List<PlaylistItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistItem(item: PlaylistItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistItems(items: List<PlaylistItemEntity>)

    @Query("DELETE FROM playlist_items WHERE id = :itemId")
    suspend fun deletePlaylistItem(itemId: Long)

    @Query("UPDATE playlist_items SET orderIndex = :orderIndex WHERE id = :itemId")
    suspend fun updatePlaylistItemOrder(itemId: Long, orderIndex: Int)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun clearPlaylistItems(playlistId: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM playlist_items pi INNER JOIN playlists p ON pi.playlistId = p.id WHERE p.isLikedCollection = 1 AND pi.surahNumber = :surahNumber AND pi.reciterId = :reciterId)")
    fun isLiked(surahNumber: Int, reciterId: Int): Flow<Boolean>

    @Query("DELETE FROM playlist_items WHERE playlistId IN (SELECT id FROM playlists WHERE isLikedCollection = 1) AND surahNumber = :surahNumber AND reciterId = :reciterId")
    suspend fun removeLiked(surahNumber: Int, reciterId: Int)
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun getAllBookmarks(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE surahNumber = :surahNumber AND ayahNumber = :ayahNumber LIMIT 1")
    fun getBookmark(surahNumber: Int, ayahNumber: Int): Flow<BookmarkEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity): Long

    @Query("UPDATE bookmarks SET note = :note WHERE id = :id")
    suspend fun updateNote(id: Long, note: String)

    @Query("DELETE FROM bookmarks WHERE surahNumber = :surahNumber AND ayahNumber = :ayahNumber")
    suspend fun deleteBookmark(surahNumber: Int, ayahNumber: Int)

    @Delete
    suspend fun deleteBookmark(bookmark: BookmarkEntity)
}

@Dao
interface KhatmaDao {
    @Query("SELECT * FROM khatma WHERE id = 1 LIMIT 1")
    fun getKhatmaProgress(): Flow<KhatmaEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateKhatma(khatma: KhatmaEntity)
}

@Dao
interface StatsDao {
    @Query("SELECT * FROM listening_stats ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<ListeningStatEntity>>

    @Query("SELECT SUM(durationSeconds) FROM listening_stats")
    fun getTotalDurationSeconds(): Flow<Long?>

    @Insert
    suspend fun insertSession(session: ListeningStatEntity)

    @Query("SELECT DISTINCT surahNumber FROM listening_stats ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentSurahNumbers(limit: Int = 8): Flow<List<Int>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<ListeningStatEntity>)

    @Query("DELETE FROM listening_stats")
    suspend fun clearAllStats()
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloaded_surahs ORDER BY downloadedAt DESC")
    fun getAllDownloadedSurahs(): Flow<List<DownloadedSurahEntity>>

    @Query("SELECT * FROM downloaded_surahs WHERE surahNumber = :surahNumber AND reciterId = :reciterId LIMIT 1")
    suspend fun getDownloadedSurah(surahNumber: Int, reciterId: Int): DownloadedSurahEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM downloaded_surahs WHERE surahNumber = :surahNumber AND reciterId = :reciterId)")
    fun isDownloaded(surahNumber: Int, reciterId: Int): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownloadedSurah(entity: DownloadedSurahEntity): Long

    @Query("DELETE FROM downloaded_surahs WHERE surahNumber = :surahNumber AND reciterId = :reciterId")
    suspend fun deleteDownloadedSurah(surahNumber: Int, reciterId: Int)

    @Query("SELECT SUM(sizeBytes) FROM downloaded_surahs")
    fun getTotalDownloadedBytes(): Flow<Long?>

    @Query("DELETE FROM downloaded_surahs")
    suspend fun clearAllDownloads()
}

