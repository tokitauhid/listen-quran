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
import com.listenquran.data.repository.QuranRepository
import com.listenquran.data.repository.ReciterRepository
import com.listenquran.data.repository.StatsRepository
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
) : MediaLibrarySession.Callback {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        const val ROOT_ID = "root"
        const val SURAHS_ID = "root/surahs"
        const val POPULAR_ID = "root/popular"
        const val RECENT_ID = "root/recent"
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
                val items: List<MediaItem> = when (parentId) {
                    ROOT_ID -> listOf(
                        buildFolderItem(SURAHS_ID, "Surahs", "All 114 Chapters of the Holy Quran", MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS),
                        buildFolderItem(POPULAR_ID, "Popular Surahs", "Yasin, Al-Mulk, Ar-Rahman, Al-Kahf", MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
                        buildFolderItem(RECENT_ID, "Recently Listened", "Continue your recitations", MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
                    )
                    SURAHS_ID -> {
                        val surahs = quranRepository.getSurahs().first()
                        surahs.map { surah -> buildSurahMediaItem(surah) }
                    }
                    POPULAR_ID -> {
                        val popularIds = listOf(36, 67, 55, 18, 56, 112, 113, 114)
                        val surahs = quranRepository.getSurahs().first()
                        popularIds.mapNotNull { id -> surahs.find { it.id == id } }
                            .map { surah -> buildSurahMediaItem(surah) }
                    }
                    RECENT_ID -> {
                        val recentIds = statsRepository.getRecentSurahNumbers().first()
                        val surahs = quranRepository.getSurahs().first()
                        val matched = recentIds.mapNotNull { id -> surahs.find { it.id == id } }
                        val finalSurahs = if (matched.isNotEmpty()) matched else surahs.take(6)
                        finalSurahs.map { surah -> buildSurahMediaItem(surah) }
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
                        future.set(LibraryResult.ofItem(buildSurahMediaItem(surah), null))
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
    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>
    ): ListenableFuture<MutableList<MediaItem>> {
        val future = SettableFuture.create<MutableList<MediaItem>>()
        scope.launch {
            try {
                val resolvedList = mutableListOf<MediaItem>()
                val defaultReciter = reciterRepository.getDefaultReciter()
                for (item in mediaItems) {
                    if (item.localConfiguration?.uri != null) {
                        resolvedList.add(item)
                    } else {
                        val surahId = parseSurahId(item.mediaId) ?: 1
                        val surah = quranRepository.getSurahById(surahId)
                        val reciter = defaultReciter
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
                future.set(resolvedList)
            } catch (e: Exception) {
                future.set(mediaItems)
            }
        }
        return future
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

    private fun buildSurahMediaItem(surah: Surah): MediaItem {
        val artworkUri = Uri.parse("android.resource://${context.packageName}/${R.mipmap.ic_launcher}")
        return MediaItem.Builder()
            .setMediaId("surah:${surah.id}")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("${surah.id}. ${surah.nameSimple} (${surah.nameArabic})")
                    .setSubtitle("${surah.translatedName} • ${surah.versesCount} Ayahs")
                    .setDisplayTitle("${surah.id}. ${surah.nameSimple}")
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
}
