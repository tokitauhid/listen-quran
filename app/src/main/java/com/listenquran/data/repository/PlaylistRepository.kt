package com.listenquran.data.repository

import com.listenquran.data.local.dao.BookmarkDao
import com.listenquran.data.local.dao.KhatmaDao
import com.listenquran.data.local.dao.PlaylistDao
import com.listenquran.data.local.dao.StatsDao
import com.listenquran.data.local.entity.*
import com.listenquran.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepository @Inject constructor(
    private val playlistDao: PlaylistDao,
) {
    suspend fun ensureLikedPlaylistCreated(): Long {
        val existing = playlistDao.getLikedCollection().first()
        return if (existing != null) {
            existing.id
        } else {
            playlistDao.insertPlaylist(
                PlaylistEntity(
                    name = "Liked Surahs",
                    description = "Your favorite recitations and saved surahs",
                    isLikedCollection = true,
                )
            )
        }
    }

    fun getAllPlaylists(): Flow<List<Playlist>> {
        return playlistDao.getAllPlaylists().map { list ->
            list.map { entity ->
                Playlist(
                    id = entity.id,
                    name = entity.name,
                    description = entity.description,
                    isLikedCollection = entity.isLikedCollection,
                    createdAt = entity.createdAt,
                )
            }
        }
    }

    fun getPlaylistItems(playlistId: Long): Flow<List<PlaylistItem>> {
        return playlistDao.getPlaylistItems(playlistId).map { list ->
            list.map { item ->
                PlaylistItem(
                    id = item.id,
                    playlistId = item.playlistId,
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
        }
    }

    suspend fun createPlaylist(name: String, description: String = ""): Long {
        return playlistDao.insertPlaylist(
            PlaylistEntity(name = name, description = description, isLikedCollection = false)
        )
    }

    suspend fun deletePlaylist(playlistId: Long) {
        val entity = playlistDao.getPlaylistById(playlistId)
        if (entity != null && !entity.isLikedCollection) {
            playlistDao.deletePlaylist(entity)
        }
    }

    suspend fun addToPlaylist(
        playlistId: Long,
        surahNumber: Int,
        surahName: String,
        surahArabic: String,
        reciterId: Int,
        reciterName: String,
        startAyah: Int = 1,
        endAyah: Int? = null,
    ) {
        playlistDao.insertPlaylistItem(
            PlaylistItemEntity(
                playlistId = playlistId,
                surahNumber = surahNumber,
                surahName = surahName,
                surahArabic = surahArabic,
                reciterId = reciterId,
                reciterName = reciterName,
                startAyah = startAyah,
                endAyah = endAyah,
            )
        )
    }

    suspend fun toggleLiked(
        surahNumber: Int,
        surahName: String,
        surahArabic: String,
        reciterId: Int,
        reciterName: String,
        isCurrentlyLiked: Boolean,
    ) {
        val likedId = ensureLikedPlaylistCreated()
        if (isCurrentlyLiked) {
            playlistDao.removeLiked(surahNumber, reciterId)
        } else {
            addToPlaylist(
                playlistId = likedId,
                surahNumber = surahNumber,
                surahName = surahName,
                surahArabic = surahArabic,
                reciterId = reciterId,
                reciterName = reciterName,
            )
        }
    }

    fun isLiked(surahNumber: Int, reciterId: Int): Flow<Boolean> {
        return playlistDao.isLiked(surahNumber, reciterId)
    }
}
