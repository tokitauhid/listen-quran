package com.listenquran.data.repository

import com.listenquran.data.local.dao.BookmarkDao
import com.listenquran.data.local.dao.KhatmaDao
import com.listenquran.data.local.dao.LikedDao
import com.listenquran.data.local.dao.PlaylistDao
import com.listenquran.data.local.dao.StatsDao
import com.listenquran.data.local.entity.*
import com.listenquran.domain.model.*
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepository @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val likedDao: LikedDao,
    private val moshi: Moshi,
) {
    private val likedMutex = Mutex()

    private fun PlaylistItemEntity.toDomain() = PlaylistItem(
        id = id,
        playlistId = playlistId,
        surahNumber = surahNumber,
        surahName = surahName,
        surahArabic = surahArabic,
        reciterId = reciterId,
        reciterName = reciterName,
        startAyah = startAyah,
        endAyah = endAyah,
        orderIndex = orderIndex,
    )

    private fun LikedSurahEntity.toLikedPlaylistItem(playlistId: Long, index: Int) = PlaylistItem(
        id = id,
        playlistId = playlistId,
        surahNumber = surahNumber,
        surahName = surahName,
        surahArabic = surahArabic,
        reciterId = reciterId,
        reciterName = reciterName,
        startAyah = 1,
        endAyah = null,
        orderIndex = index,
    )

    private fun mergeLikedDomainItems(
        playlistId: Long,
        likedEntities: List<LikedSurahEntity>,
        legacyItems: List<PlaylistItemEntity>,
    ): List<PlaylistItem> {
        val primary = likedEntities.mapIndexed { idx, liked -> liked.toLikedPlaylistItem(playlistId, idx) }
        if (legacyItems.isEmpty()) return primary
        val seenKeys = likedEntities.mapTo(HashSet()) { it.surahNumber to it.reciterId }
        val extras = legacyItems
            .filter { (it.surahNumber to it.reciterId) !in seenKeys }
            .map { it.toDomain() }
        return primary + extras
    }

    suspend fun ensureLikedPlaylistCreated(): Long = likedMutex.withLock {
        ensureLikedPlaylistCreatedInternal()
    }

    private suspend fun ensureLikedPlaylistCreatedInternal(): Long {
        playlistDao.removeDuplicatePlaylistItems()
        val likedCollections = playlistDao.getAllPlaylists().first().filter { it.isLikedCollection }
        val primaryId = if (likedCollections.isNotEmpty()) {
            likedCollections.first().id
        } else {
            playlistDao.insertPlaylist(
                PlaylistEntity(
                    name = "Liked Surahs",
                    description = "Your favorite recitations and saved surahs",
                    isLikedCollection = true,
                )
            )
        }

        // Reconcile any legacy liked rows stored in playlist_items into liked_surahs
        for (col in likedCollections) {
            val legacyItems = playlistDao.getPlaylistItemsDirect(col.id)
            for (legacy in legacyItems) {
                if (!likedDao.isLikedDirect(legacy.surahNumber, legacy.reciterId)) {
                    likedDao.insertLiked(
                        LikedSurahEntity(
                            surahNumber = legacy.surahNumber,
                            surahName = legacy.surahName,
                            surahArabic = legacy.surahArabic,
                            reciterId = legacy.reciterId,
                            reciterName = legacy.reciterName,
                            likedAt = col.createdAt,
                        )
                    )
                }
            }
            if (legacyItems.isNotEmpty()) {
                playlistDao.clearPlaylistItems(col.id)
            }
            if (col.id != primaryId) {
                playlistDao.deletePlaylist(col)
            }
        }
        return primaryId
    }

    fun getAllPlaylists(): Flow<List<Playlist>> {
        return combine(
            playlistDao.getAllPlaylists(),
            playlistDao.getAllPlaylistItems(),
            likedDao.getAllLiked(),
        ) { playlists, allItems, likedEntities ->
            val itemsByPlaylistId = allItems.groupBy { it.playlistId }
            playlists.map { entity ->
                val domainItems = if (entity.isLikedCollection) {
                    mergeLikedDomainItems(entity.id, likedEntities, itemsByPlaylistId[entity.id].orEmpty())
                } else {
                    itemsByPlaylistId[entity.id].orEmpty().map { it.toDomain() }
                }
                Playlist(
                    id = entity.id,
                    name = entity.name,
                    description = entity.description,
                    isLikedCollection = entity.isLikedCollection,
                    createdAt = entity.createdAt,
                    items = domainItems,
                )
            }
        }
    }

    suspend fun getPlaylistById(playlistId: Long): Playlist? {
        val entity = playlistDao.getPlaylistById(playlistId) ?: return null
        val domainItems = if (entity.isLikedCollection) {
            mergeLikedDomainItems(
                playlistId = entity.id,
                likedEntities = likedDao.getAllLiked().first(),
                legacyItems = playlistDao.getPlaylistItemsDirect(entity.id),
            )
        } else {
            playlistDao.getPlaylistItemsDirect(entity.id).map { it.toDomain() }
        }
        return Playlist(
            id = entity.id,
            name = entity.name,
            description = entity.description,
            isLikedCollection = entity.isLikedCollection,
            createdAt = entity.createdAt,
            items = domainItems,
        )
    }

    fun getPlaylistItems(playlistId: Long): Flow<List<PlaylistItem>> {
        return playlistDao.getPlaylistItems(playlistId).map { list ->
            list.map { it.toDomain() }
        }
    }

    suspend fun createPlaylist(name: String, description: String = ""): Long {
        return playlistDao.insertPlaylist(
            PlaylistEntity(name = name, description = description, isLikedCollection = false)
        )
    }

    suspend fun createPlaylistWithEntries(
        name: String,
        description: String = "",
        entries: List<PlaylistItem>,
    ): Long {
        val playlistId = createPlaylist(name, description)
        if (entries.isEmpty()) return playlistId

        val seen = HashSet<List<Int?>>(entries.size)
        val entities = ArrayList<PlaylistItemEntity>(entries.size)
        var orderIndex = 0
        for (entry in entries) {
            val key = listOf(entry.surahNumber, entry.startAyah, entry.endAyah, entry.reciterId)
            if (seen.add(key)) {
                entities.add(
                    PlaylistItemEntity(
                        playlistId = playlistId,
                        surahNumber = entry.surahNumber,
                        surahName = entry.surahName,
                        surahArabic = entry.surahArabic,
                        reciterId = entry.reciterId,
                        reciterName = entry.reciterName,
                        startAyah = entry.startAyah,
                        endAyah = entry.endAyah,
                        orderIndex = orderIndex++,
                    )
                )
            }
        }
        if (entities.isNotEmpty()) {
            playlistDao.insertPlaylistItems(entities)
        }
        return playlistId
    }

    suspend fun deletePlaylist(playlistId: Long) {
        val entity = playlistDao.getPlaylistById(playlistId)
        if (entity != null && !entity.isLikedCollection) {
            playlistDao.deletePlaylist(entity)
        }
    }

    suspend fun renamePlaylist(playlistId: Long, newName: String) {
        val entity = playlistDao.getPlaylistById(playlistId)
        if (entity != null && !entity.isLikedCollection && newName.isNotBlank()) {
            playlistDao.updatePlaylistName(playlistId, newName.trim())
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
    ): Boolean {
        if (playlistDao.isItemInPlaylist(playlistId, surahNumber, reciterId)) {
            return false
        }
        val nextOrderIndex = playlistDao.getPlaylistItemsDirect(playlistId).size
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
                orderIndex = nextOrderIndex,
            )
        )
        return true
    }

    suspend fun toggleLiked(
        surahNumber: Int,
        surahName: String,
        surahArabic: String,
        reciterId: Int,
        reciterName: String,
    ): Boolean = likedMutex.withLock {
        ensureLikedPlaylistCreatedInternal()
        val isCurrentlyLiked = likedDao.isLikedDirect(surahNumber, reciterId)
        if (isCurrentlyLiked) {
            likedDao.removeLiked(surahNumber, reciterId)
            false
        } else {
            likedDao.insertLiked(
                LikedSurahEntity(
                    surahNumber = surahNumber,
                    surahName = surahName,
                    surahArabic = surahArabic,
                    reciterId = reciterId,
                    reciterName = reciterName,
                    likedAt = System.currentTimeMillis(),
                )
            )
            true
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
        return likedDao.isLiked(surahNumber, reciterId)
    }

    suspend fun exportPlaylistsJson(): String {
        val allPlaylists = playlistDao.getAllPlaylists().first()
        val exportList = mutableListOf<Map<String, Any>>()
        for (pl in allPlaylists) {
            val items = if (pl.isLikedCollection) {
                likedDao.getAllLiked().first().mapIndexed { idx, liked -> liked.toLikedPlaylistItem(pl.id, idx) }
            } else {
                playlistDao.getPlaylistItems(pl.id).first().map { it.toDomain() }
            }
            exportList.add(
                mapOf(
                    "name" to pl.name,
                    "description" to pl.description,
                    "isLiked" to pl.isLikedCollection,
                    "items" to items.map {
                        buildMap<String, Any> {
                            put("surahNumber", it.surahNumber)
                            put("surahName", it.surahName)
                            put("surahArabic", it.surahArabic)
                            put("reciterId", it.reciterId)
                            put("reciterName", it.reciterName)
                            put("startAyah", it.startAyah)
                            it.endAyah?.let { end -> put("endAyah", end) }
                        }
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
                val itemsRaw = plMap["items"] as? List<Map<String, Any>> ?: emptyList()

                if (isLiked) {
                    ensureLikedPlaylistCreated()
                    for (itemMap in itemsRaw) {
                        val surahNum = (itemMap["surahNumber"] as? Number)?.toInt() ?: continue
                        val surahName = itemMap["surahName"] as? String ?: "Surah"
                        val surahArabic = itemMap["surahArabic"] as? String ?: ""
                        val reciterId = (itemMap["reciterId"] as? Number)?.toInt() ?: 7
                        val reciterName = itemMap["reciterName"] as? String ?: "Mishary Rashid Al-Afasy"
                        if (!likedDao.isLikedDirect(surahNum, reciterId)) {
                            likedDao.insertLiked(
                                LikedSurahEntity(
                                    surahNumber = surahNum,
                                    surahName = surahName,
                                    surahArabic = surahArabic,
                                    reciterId = reciterId,
                                    reciterName = reciterName,
                                )
                            )
                        }
                    }
                } else {
                    val newPlId = playlistDao.insertPlaylist(
                        PlaylistEntity(name = name, description = desc, isLikedCollection = false)
                    )
                    val items = itemsRaw.mapIndexedNotNull { idx, itemMap ->
                        val surahNum = (itemMap["surahNumber"] as? Number)?.toInt() ?: return@mapIndexedNotNull null
                        val surahName = itemMap["surahName"] as? String ?: "Surah"
                        val surahArabic = itemMap["surahArabic"] as? String ?: ""
                        val reciterId = (itemMap["reciterId"] as? Number)?.toInt() ?: 7
                        val reciterName = itemMap["reciterName"] as? String ?: "Mishary Rashid Al-Afasy"
                        val startAyah = (itemMap["startAyah"] as? Number)?.toInt() ?: 1
                        val endAyah = (itemMap["endAyah"] as? Number)?.toInt()
                        PlaylistItemEntity(
                            playlistId = newPlId,
                            surahNumber = surahNum,
                            surahName = surahName,
                            surahArabic = surahArabic,
                            reciterId = reciterId,
                            reciterName = reciterName,
                            startAyah = startAyah,
                            endAyah = endAyah,
                            orderIndex = idx,
                        )
                    }
                    if (items.isNotEmpty()) {
                        playlistDao.insertPlaylistItems(items)
                    }
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
