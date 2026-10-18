package com.listenquran.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.listenquran.R
import com.listenquran.data.repository.KhatmaRepository
import com.listenquran.data.repository.PlaylistRepository
import com.listenquran.data.repository.QuranRepository
import com.listenquran.data.repository.ReciterRepository
import com.listenquran.data.repository.StatsRepository
import com.listenquran.domain.model.PlaylistItem
import com.listenquran.domain.model.Reciter
import com.listenquran.domain.model.Surah
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuranLibrarySessionCallback @Inject constructor(
    @ApplicationContext private val context: Context,
    private val quranRepository: QuranRepository,
    private val reciterRepository: ReciterRepository,
    private val statsRepository: StatsRepository,
    private val playlistRepository: PlaylistRepository,
    private val khatmaRepository: KhatmaRepository,
) : MediaLibrarySession.Callback {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        const val ROOT_ID = "root"
        const val RECITERS_ID = "root/reciters"
        const val PLAYLISTS_ID = "root/playlists"
        const val RECENT_ID = "root/recent"
        const val KHATMA_ID = "root/khatma"
    }

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ): MediaSession.ConnectionResult {
        val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
            .build()
        val playerCommands = session.player.availableCommands.buildUpon()
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .build()
        return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            .setAvailableSessionCommands(sessionCommands)
            .setAvailablePlayerCommands(playerCommands)
            .build()
    }

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> {
        val rootItem = MediaItem.Builder()
            .setMediaId(ROOT_ID)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Listen Quran")
                    .setIsPlayable(false)
                    .setIsBrowsable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .build()
            )
            .build()
        return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
    }

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
        scope.launch {
            try {
                val items: List<MediaItem> = when {
                    parentId == ROOT_ID -> listOf(
                        buildFolderItem(RECITERS_ID, "Reciters", "Browse 250+ Quran reciters", MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS),
                        buildFolderItem(PLAYLISTS_ID, "Playlists", "Custom collections & Liked Surahs", MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
                        buildFolderItem(RECENT_ID, "Recently Listened", "Quickly resume your listening", MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
                        buildFolderItem(KHATMA_ID, "Continue Khatma", "Resume your Quran completion goal", MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
                    )
                    parentId == RECITERS_ID -> {
                        val reciters = reciterRepository.getReciters().first()
                        reciters.map { reciter ->
                            buildFolderItem(
                                "root/reciters/${reciter.id}",
                                reciter.englishName,
                                "${reciter.style ?: "Murattal"} • ${reciter.qiraah}",
                                MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS
                            )
                        }
                    }
                    parentId.startsWith("root/reciters/") -> {
                        val reciterId = parentId.removePrefix("root/reciters/").toIntOrNull() ?: 7
                        val reciter = reciterRepository.getReciterById(reciterId) ?: reciterRepository.getDefaultReciter()
                        val surahs = quranRepository.getSurahs().first()
                        surahs.map { surah -> buildSurahMediaItem(surah, reciter) }
                    }
                    parentId == PLAYLISTS_ID -> {
                        val playlists = playlistRepository.getAllPlaylists().first()
                        playlists.map { playlist ->
                            buildFolderItem(
                                "root/playlists/${playlist.id}",
                                playlist.name,
                                "${playlist.items.size} Recitations",
                                MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
                            )
                        }
                    }
                    parentId.startsWith("root/playlists/") -> {
                        val playlistId = parentId.removePrefix("root/playlists/").toLongOrNull() ?: 0L
                        val playlistItems = playlistRepository.getPlaylistItems(playlistId).first()
                        playlistItems.map { item -> buildPlaylistItemMediaItem(item) }
                    }
                    parentId == RECENT_ID -> {
                        val recentIds = statsRepository.getRecentSurahNumbers().first()
                        val surahs = quranRepository.getSurahs().first()
                        val defaultReciter = reciterRepository.getDefaultReciter()
                        val matched = recentIds.mapNotNull { id -> surahs.find { it.id == id } }
                        val finalSurahs = if (matched.isNotEmpty()) matched else surahs.take(6)
                        finalSurahs.map { surah -> buildSurahMediaItem(surah, defaultReciter) }
                    }
                    parentId == KHATMA_ID -> {
                        val khatma = khatmaRepository.getKhatmaProgress().first()
                        val surahs = quranRepository.getSurahs().first()
                        val surah = surahs.find { it.id == khatma.lastSurahNumber } ?: surahs.first()
                        val defaultReciter = reciterRepository.getDefaultReciter()
                        listOf(
                            MediaItem.Builder()
                                .setMediaId("surah:${surah.id}:reciter:${defaultReciter.id}")
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setTitle("Khatma: Page ${khatma.currentPage} (${surah.nameSimple})")
                                        .setSubtitle("Juz ${khatma.currentJuz} • ${khatma.progressPercentage}% Complete")
                                        .setDisplayTitle("Resume Daily Khatma")
                                        .setIsPlayable(true)
                                        .setIsBrowsable(false)
                                        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                                        .build()
                                )
                                .build()
                        )
                    }
                    else -> emptyList()
                }
                future.set(LibraryResult.ofItemList(items, params))
            } catch (e: Exception) {
                future.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_UNKNOWN))
            }
        }
        return future
    }

    override fun onSearch(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<Void>> {
        session.notifySearchResultChanged(browser, query, 10, params)
        return Futures.immediateFuture(LibraryResult.ofVoid())
    }

    override fun onGetSearchResult(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
        scope.launch {
            try {
                val surahs = quranRepository.getSurahs().first()
                val matched = surahs.filter {
                    it.nameSimple.contains(query, ignoreCase = true) ||
                    it.translatedName.contains(query, ignoreCase = true) ||
                    it.id.toString() == query
                }.take(pageSize)
                val defaultReciter = reciterRepository.getDefaultReciter()
                val items = matched.map { buildSurahMediaItem(it, defaultReciter) }
                future.set(LibraryResult.ofItemList(items, params))
            } catch (e: Exception) {
                future.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_UNKNOWN))
            }
        }
        return future
    }

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String
    ): ListenableFuture<LibraryResult<MediaItem>> {
        val future = SettableFuture.create<LibraryResult<MediaItem>>()
        scope.launch {
            try {
                val surahId = parseSurahId(mediaId)
                if (surahId != null) {
                    val surah = quranRepository.getSurahById(surahId)
                    if (surah != null) {
                        val reciterId = parseReciterId(mediaId) ?: 7
                        val reciter = reciterRepository.getReciterById(reciterId) ?: reciterRepository.getDefaultReciter()
                        future.set(LibraryResult.ofItem(buildSurahMediaItem(surah, reciter), null))
                        return@launch
                    }
                }
                future.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
            } catch (e: Exception) {
                future.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_UNKNOWN))
            }
        }
        return future
    }

    @OptIn(UnstableApi::class)
    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
        scope.launch {
            try {
                val resolved = resolveMediaItemList(mediaItems)
                future.set(
                    MediaSession.MediaItemsWithStartPosition(
                        resolved,
                        startIndex.coerceIn(0, (resolved.size - 1).coerceAtLeast(0)),
                        startPositionMs.coerceAtLeast(0L)
                    )
                )
            } catch (e: Exception) {
                future.set(MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs))
            }
        }
        return future
    }

    @OptIn(UnstableApi::class)
    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>
    ): ListenableFuture<MutableList<MediaItem>> {
        val future = SettableFuture.create<MutableList<MediaItem>>()
        scope.launch {
            try {
                future.set(resolveMediaItemList(mediaItems))
            } catch (e: Exception) {
                future.set(mediaItems)
            }
        }
        return future
    }

    private suspend fun resolveMediaItemList(mediaItems: List<MediaItem>): MutableList<MediaItem> {
        val resolvedList = mutableListOf<MediaItem>()
        val defaultReciter = reciterRepository.getDefaultReciter()
        for (item in mediaItems) {
            if (item.localConfiguration?.uri != null) {
                resolvedList.add(item)
            } else if (item.mediaId.startsWith("radio:")) {
                val parts = item.mediaId.split(":")
                val surahId = parts.getOrNull(3)?.toIntOrNull() ?: 1
                val reciterId = parts.getOrNull(4)?.toIntOrNull() ?: defaultReciter.id
                val audioData = quranRepository.getChapterAudio(reciterId, surahId)
                if (audioData != null) {
                    resolvedList.add(item.buildUpon().setUri(audioData.audioUrl).build())
                } else {
                    resolvedList.add(item)
                }
            } else {
                val surahId = parseSurahId(item.mediaId) ?: 1
                val reciterId = parseReciterId(item.mediaId) ?: defaultReciter.id
                val surah = quranRepository.getSurahById(surahId)
                val reciter = reciterRepository.getReciterById(reciterId) ?: defaultReciter
                val audioData = quranRepository.getChapterAudio(reciter.id, surahId)
                if (audioData != null && surah != null) {
                    val artworkUri = Uri.parse("android.resource://${context.packageName}/${R.mipmap.ic_launcher}")
                    val resolvedItem = MediaItem.Builder()
                        .setMediaId("surah:${surah.id}:reciter:${reciter.id}")
                        .setUri(audioData.audioUrl)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle("${surah.id}. ${surah.nameSimple} (${surah.nameArabic})")
                                .setArtist(reciter.englishName)
                                .setAlbumTitle("The Holy Quran")
                                .setAlbumArtist(reciter.englishName)
                                .setDisplayTitle("${surah.id}. ${surah.nameSimple}")
                                .setSubtitle(reciter.englishName)
                                .setDescription("${surah.translatedName} • ${surah.revelationPlace.replaceFirstChar { it.uppercase() }} • ${surah.versesCount} Verses")
                                .setArtworkUri(artworkUri)
                                .setIsPlayable(true)
                                .setIsBrowsable(false)
                                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                                .build()
                        )
                        .build()
                    resolvedList.add(resolvedItem)
                } else {
                    resolvedList.add(item)
                }
            }
        }
        return resolvedList
    }

    @OptIn(UnstableApi::class)
    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val currentItem = mediaSession.player.currentMediaItem
        return if (currentItem != null) {
            val startPositionMs = mediaSession.player.currentPosition
            val startIndex = mediaSession.player.currentMediaItemIndex
            Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(
                    listOf(currentItem),
                    startIndex,
                    startPositionMs
                )
            )
        } else {
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            scope.launch {
                val defaultReciter = reciterRepository.getDefaultReciter()
                val audioData = quranRepository.getChapterAudio(defaultReciter.id, 1)
                val surah = quranRepository.getSurahById(1)
                if (audioData != null && surah != null) {
                    val artworkUri = Uri.parse("android.resource://${context.packageName}/${R.mipmap.ic_launcher}")
                    val item = MediaItem.Builder()
                        .setMediaId("surah:1:reciter:${defaultReciter.id}")
                        .setUri(audioData.audioUrl)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle("1. Al-Fatihah (الفاتحة)")
                                .setArtist(defaultReciter.englishName)
                                .setArtworkUri(artworkUri)
                                .setIsPlayable(true)
                                .build()
                        )
                        .build()
                    future.set(MediaSession.MediaItemsWithStartPosition(listOf(item), 0, 0L))
                } else {
                    future.setException(UnsupportedOperationException("No media to resume"))
                }
            }
            future
        }
    }

    private fun buildFolderItem(id: String, title: String, subtitle: String, mediaType: Int): MediaItem {
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setIsPlayable(false)
                    .setIsBrowsable(true)
                    .setMediaType(mediaType)
                    .build()
            )
            .build()
    }

    private fun buildSurahMediaItem(surah: Surah, reciter: Reciter): MediaItem {
        val artworkUri = Uri.parse("android.resource://${context.packageName}/${R.mipmap.ic_launcher}")
        return MediaItem.Builder()
            .setMediaId("surah:${surah.id}:reciter:${reciter.id}")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("${surah.id}. ${surah.nameSimple} (${surah.nameArabic})")
                    .setSubtitle("${reciter.englishName} • ${surah.versesCount} Ayahs")
                    .setDisplayTitle("${surah.id}. ${surah.nameSimple}")
                    .setArtworkUri(artworkUri)
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .build()
            )
            .build()
    }

    private fun buildPlaylistItemMediaItem(item: PlaylistItem): MediaItem {
        val artworkUri = Uri.parse("android.resource://${context.packageName}/${R.mipmap.ic_launcher}")
        return MediaItem.Builder()
            .setMediaId("surah:${item.surahNumber}:reciter:${item.reciterId}")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("${item.surahNumber}. ${item.surahName} (${item.surahArabic})")
                    .setSubtitle(item.reciterName)
                    .setDisplayTitle(item.surahName)
                    .setArtworkUri(artworkUri)
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .build()
            )
            .build()
    }

    private fun parseSurahId(mediaId: String): Int? {
        if (mediaId.startsWith("surah:")) {
            val remainder = mediaId.removePrefix("surah:")
            return remainder.substringBefore(":").toIntOrNull()
        }
        return mediaId.toIntOrNull()
    }

    private fun parseReciterId(mediaId: String): Int? {
        if (mediaId.contains(":reciter:")) {
            val remainder = mediaId.substringAfter(":reciter:")
            return remainder.substringBefore(":").toIntOrNull()
        }
        return null
    }
}
