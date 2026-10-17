package com.listenquran.data.repository

import com.listenquran.data.local.dao.BookmarkDao
import com.listenquran.data.local.dao.KhatmaDao
import com.listenquran.data.local.dao.PlaylistDao
import com.listenquran.data.local.dao.StatsDao
import com.listenquran.data.local.entity.*
import com.listenquran.domain.model.*
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepository @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val moshi: Moshi,
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

    suspend fun deletePlaylistItem(itemId: Long) {
        playlistDao.deletePlaylistItem(itemId)
    }

    suspend fun reorderPlaylistItems(items: List<PlaylistItem>) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        items.forEachIndexed { index, item ->
            playlistDao.updatePlaylistItemOrder(item.id, index)
        }
    }

    fun isLiked(surahNumber: Int, reciterId: Int): Flow<Boolean> {
        return playlistDao.isLiked(surahNumber, reciterId)
    }

    suspend fun exportPlaylistsJson(): String {
        val allPlaylists = playlistDao.getAllPlaylists().first()
        val exportList = mutableListOf<Map<String, Any>>()
        for (pl in allPlaylists) {
            val items = playlistDao.getPlaylistItems(pl.id).first()
            exportList.add(
                mapOf(
                    "name" to pl.name,
                    "description" to pl.description,
                    "isLiked" to pl.isLikedCollection,
                    "items" to items.map {
                        mapOf(
                            "surahNumber" to it.surahNumber,
                            "surahName" to it.surahName,
                            "surahArabic" to it.surahArabic,
                            "reciterId" to it.reciterId,
                            "reciterName" to it.reciterName,
                            "startAyah" to it.startAyah,
                        )
                    }
                )
            )
        }
        val adapter = moshi.adapter(List::class.java)
        return adapter.toJson(exportList)
    }

    suspend fun importPlaylistsJson(json: String): Boolean {
        return try {
            val adapter = moshi.adapter(List::class.java)
            val list = adapter.fromJson(json) as? List<Map<String, Any>> ?: return false
            for (plMap in list) {
                val name = plMap["name"] as? String ?: continue
                val desc = plMap["description"] as? String ?: ""
                val isLiked = plMap["isLiked"] as? Boolean ?: false
                val newPlId = playlistDao.insertPlaylist(
                    PlaylistEntity(name = name, description = desc, isLikedCollection = isLiked)
                )
                val itemsRaw = plMap["items"] as? List<Map<String, Any>> ?: emptyList()
                val items = itemsRaw.mapNotNull { itemMap ->
                    val surahNum = (itemMap["surahNumber"] as? Number)?.toInt() ?: return@mapNotNull null
                    val surahName = itemMap["surahName"] as? String ?: "Surah"
                    val surahArabic = itemMap["surahArabic"] as? String ?: ""
                    val reciterId = (itemMap["reciterId"] as? Number)?.toInt() ?: 7
                    val reciterName = itemMap["reciterName"] as? String ?: "Mishary Rashid Al-Afasy"
                    val startAyah = (itemMap["startAyah"] as? Number)?.toInt() ?: 1
                    PlaylistItemEntity(
                        playlistId = newPlId,
                        surahNumber = surahNum,
                        surahName = surahName,
                        surahArabic = surahArabic,
                        reciterId = reciterId,
                        reciterName = reciterName,
                        startAyah = startAyah
                    )
                }
                if (items.isNotEmpty()) {
                    playlistDao.insertPlaylistItems(items)
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
