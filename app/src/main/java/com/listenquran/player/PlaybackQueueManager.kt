package com.listenquran.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaBrowser
import com.listenquran.R
import com.listenquran.data.repository.QuranDownloadRepository
import com.listenquran.data.repository.QuranRepository
import com.listenquran.data.repository.ReciterRepository
import com.listenquran.domain.model.PlaylistItem
import com.listenquran.domain.model.QueueItem
import com.listenquran.domain.model.Reciter
import com.listenquran.domain.model.Surah
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@OptIn(UnstableApi::class)
@Singleton
class PlaybackQueueManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val quranRepository: QuranRepository,
    private val reciterRepository: ReciterRepository,
    private val downloadRepository: QuranDownloadRepository,
    private val exoPlayerProvider: Provider<ExoPlayer>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _queueState = MutableStateFlow(PlaybackQueueState())
    val queueState: StateFlow<PlaybackQueueState> = _queueState.asStateFlow()

    private var cachedSurahs: List<Surah>? = null
    private var defaultReciterCache: Reciter? = null

    var currentShuffleOrder: ControllableShuffleOrder? = null
        private set

    var currentSourceTitle: String? = null
        private set

    suspend fun getAllSurahs(): List<Surah> {
        val cached = cachedSurahs
        if (!cached.isNullOrEmpty()) return cached
        return withContext(Dispatchers.IO) {
            val list = quranRepository.getSurahs().first()
            cachedSurahs = list
            list
        }
    }

    suspend fun getDefaultReciter(): Reciter {
        val cached = defaultReciterCache
        if (cached != null) return cached
        return withContext(Dispatchers.IO) {
            val r = reciterRepository.getDefaultReciter()
            defaultReciterCache = r
            r
        }
    }

    suspend fun buildMediaItem(
        surah: Surah,
        reciter: Reciter,
        startAyah: Int = 1,
        queueId: String = UUID.randomUUID().toString()
    ): MediaItem {
        val downloaded = downloadRepository.getDownloadedSurah(surah.id, reciter.id)
        var effectiveUri = when {
            downloaded != null && File(downloaded.localFilePath).exists() -> Uri.fromFile(File(downloaded.localFilePath))
            reciter.audioServer != null -> {
                val server = reciter.audioServer.trimEnd('/') + "/"
                val url = if (server.contains("mp3quran.net")) {
                    server + String.format("%03d.mp3", surah.id)
                } else {
                    server + "${surah.id}.mp3"
                }
                Uri.parse(url)
            }
            else -> {
                val defaultMatch = ReciterRepository.defaultReciters.find { it.id == reciter.id }
                if (defaultMatch?.audioServer != null) {
                    val server = defaultMatch.audioServer.trimEnd('/') + "/"
                    val url = if (server.contains("mp3quran.net")) {
                        server + String.format("%03d.mp3", surah.id)
                    } else {
                        server + "${surah.id}.mp3"
                    }
                    Uri.parse(url)
                } else null
            }
        }

        if (effectiveUri == null) {
            val audioData = quranRepository.getChapterAudio(reciter.id, surah.id)
            if (audioData != null) {
                effectiveUri = Uri.parse(audioData.audioUrl)
            }
        }

        val artworkUri = Uri.parse("android.resource://${context.packageName}/${R.mipmap.ic_launcher}")
        val mediaMetadata = MediaMetadata.Builder()
            .setTitle("${surah.id}. ${surah.nameSimple} (${surah.nameArabic})")
            .setArtist(reciter.englishName)
            .setAlbumTitle("The Holy Quran")
            .setAlbumArtist(reciter.englishName)
            .setDisplayTitle("${surah.id}. ${surah.nameSimple}")
            .setSubtitle("${reciter.englishName}${if (startAyah > 1) " • Ayah $startAyah" else ""}")
            .setDescription("${surah.translatedName} • ${surah.revelationPlace.replaceFirstChar { it.uppercase() }} • ${surah.versesCount} Verses")
            .setArtworkUri(artworkUri)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .build()

        val builder = MediaItem.Builder()
            .setMediaId("surah:${surah.id}:reciter:${reciter.id}:uid:$queueId")
            .setMediaMetadata(mediaMetadata)

        if (effectiveUri != null) {
            builder.setUri(effectiveUri)
        }

        return builder.build()
    }

    fun playQueue(
        surahs: List<Surah>,
        reciter: Reciter,
        startIndex: Int = 0,
        startAyah: Int = 1,
        queueTitle: String? = null,
        withController: ((MediaBrowser) -> Unit) -> Unit
    ) {
        if (surahs.isEmpty()) return
        val clampedIndex = startIndex.coerceIn(0, surahs.size - 1)
        currentSourceTitle = queueTitle

        scope.launch {
            val mediaItems = ArrayList<MediaItem>(surahs.size)
            for (i in surahs.indices) {
                val s = surahs[i]
                val ayah = if (i == clampedIndex) startAyah else 1
                mediaItems.add(buildMediaItem(s, reciter, ayah))
            }

            var startPosMs = 0L
            if (startAyah > 1) {
                val targetSurah = surahs[clampedIndex]
                val audioData = quranRepository.getChapterAudio(reciter.id, targetSurah.id)
                val targetTiming = audioData?.timestamps?.find { it.verseKey == "${targetSurah.id}:$startAyah" }
                if (targetTiming != null) {
                    startPosMs = targetTiming.timestampFrom
                }
            }

            withController { controller ->
                currentShuffleOrder = null
                controller.shuffleModeEnabled = false

                controller.setMediaItems(mediaItems, clampedIndex, startPosMs)
                controller.prepare()
                controller.play()

                rebuildQueueState(controller)
                persistQueueAsync(surahs, reciter, clampedIndex, queueTitle, false)
            }
        }
    }

    fun playPlaylist(
        items: List<PlaylistItem>,
        allSurahs: List<Surah>,
        allReciters: List<Reciter>,
        startIndex: Int = 0,
        queueTitle: String? = null,
        withController: ((MediaBrowser) -> Unit) -> Unit
    ) {
        if (items.isEmpty()) return
        scope.launch {
            val defaultReciter = getDefaultReciter()
            val mediaItems = ArrayList<MediaItem>(items.size)
            val queuedSurahs = ArrayList<Surah>(items.size)

            for (item in items) {
                val surah = allSurahs.find { it.id == item.surahNumber } ?: allSurahs.first()
                val reciter = allReciters.find { it.id == item.reciterId } ?: defaultReciter
                queuedSurahs.add(surah)
                mediaItems.add(buildMediaItem(surah, reciter, item.startAyah))
            }

            val clampedIndex = startIndex.coerceIn(0, items.size - 1)
            currentSourceTitle = queueTitle

            withController { controller ->
                currentShuffleOrder = null
                controller.shuffleModeEnabled = false

                controller.setMediaItems(mediaItems, clampedIndex, 0L)
                controller.prepare()
                controller.play()

                rebuildQueueState(controller)
                persistQueueAsync(queuedSurahs, allReciters.firstOrNull() ?: defaultReciter, clampedIndex, queueTitle, false)
            }
        }
    }

    fun toggleShuffle(withController: ((MediaBrowser) -> Unit) -> Unit) {
        withController { controller ->
            val newShuffleState = !controller.shuffleModeEnabled
            val itemCount = controller.mediaItemCount
            val currentIndex = controller.currentMediaItemIndex

            if (newShuffleState && itemCount > 1) {
                val order = ControllableShuffleOrder.createWithCurrentFirst(itemCount, currentIndex)
                currentShuffleOrder = order
                try {
                    exoPlayerProvider.get().setShuffleOrder(order)
                } catch (e: Exception) {
                    android.util.Log.e("PlaybackQueueManager", "Failed to set custom shuffle order on ExoPlayer", e)
                }
                controller.shuffleModeEnabled = true
            } else {
                currentShuffleOrder = null
                controller.shuffleModeEnabled = false
            }
            rebuildQueueState(controller)
        }
    }

    fun addToQueue(
        surah: Surah,
        reciter: Reciter,
        withController: ((MediaBrowser) -> Unit) -> Unit
    ) {
        scope.launch {
            val mediaItem = buildMediaItem(surah, reciter)
            withController { controller ->
                val insertionIndex = controller.mediaItemCount
                controller.addMediaItem(mediaItem)

                val order = currentShuffleOrder
                if (controller.shuffleModeEnabled && order != null) {
                    val newOrder = order.cloneAndInsert(insertionIndex, 1) as? ControllableShuffleOrder
                        ?: ControllableShuffleOrder(order.shuffledIndices + insertionIndex)
                    currentShuffleOrder = newOrder
                    try {
                        exoPlayerProvider.get().setShuffleOrder(newOrder)
                    } catch (e: Exception) {
                        android.util.Log.e("PlaybackQueueManager", "Error updating shuffle order on add", e)
                    }
                }
                rebuildQueueState(controller)
            }
        }
    }

    fun playNext(
        surah: Surah,
        reciter: Reciter,
        withController: ((MediaBrowser) -> Unit) -> Unit
    ) {
        scope.launch {
            val mediaItem = buildMediaItem(surah, reciter)
            withController { controller ->
                val insertionIndex = (controller.currentMediaItemIndex + 1).coerceAtMost(controller.mediaItemCount)
                controller.addMediaItem(insertionIndex, mediaItem)

                val order = currentShuffleOrder
                if (controller.shuffleModeEnabled && order != null) {
                    val newOrder = ControllableShuffleOrder.cloneWithPlayNext(order, insertionIndex)
                    currentShuffleOrder = newOrder
                    try {
                        exoPlayerProvider.get().setShuffleOrder(newOrder)
                    } catch (e: Exception) {
                        android.util.Log.e("PlaybackQueueManager", "Error updating shuffle order on playNext", e)
                    }
                }
                rebuildQueueState(controller)
            }
        }
    }

    fun removeFromQueue(
        timelineIndex: Int,
        withController: ((MediaBrowser) -> Unit) -> Unit
    ) {
        withController { controller ->
            if (timelineIndex in 0 until controller.mediaItemCount) {
                controller.removeMediaItem(timelineIndex)
                val order = currentShuffleOrder
                if (controller.shuffleModeEnabled && order != null) {
                    val newOrder = order.cloneAndRemove(timelineIndex, timelineIndex + 1) as? ControllableShuffleOrder
                    currentShuffleOrder = newOrder
                    if (newOrder != null) {
                        try {
                            exoPlayerProvider.get().setShuffleOrder(newOrder)
                        } catch (e: Exception) {
                            android.util.Log.e("PlaybackQueueManager", "Error updating shuffle order on remove", e)
                        }
                    }
                }
                rebuildQueueState(controller)
            }
        }
    }

    fun moveQueueItem(
        fromIndex: Int,
        toIndex: Int,
        withController: ((MediaBrowser) -> Unit) -> Unit
    ) {
        withController { controller ->
            val order = currentShuffleOrder
            if (controller.shuffleModeEnabled && order != null) {
                // Reorder in shuffled space
                val currentIdx = controller.currentMediaItemIndex
                val currentPos = order.shuffledIndices.indexOf(currentIdx)
                val fromShufflePos = currentPos + 1 + fromIndex
                val toShufflePos = currentPos + 1 + toIndex

                if (fromShufflePos in order.shuffledIndices.indices && toShufflePos in order.shuffledIndices.indices) {
                    val list = order.shuffledIndices.toMutableList()
                    val moved = list.removeAt(fromShufflePos)
                    list.add(toShufflePos, moved)
                    val newOrder = ControllableShuffleOrder(list.toIntArray())
                    currentShuffleOrder = newOrder
                    try {
                        exoPlayerProvider.get().setShuffleOrder(newOrder)
                    } catch (e: Exception) {
                        android.util.Log.e("PlaybackQueueManager", "Error updating shuffle order on move", e)
                    }
                    rebuildQueueState(controller)
                }
            } else {
                // Reorder in timeline space
                val currentIdx = controller.currentMediaItemIndex
                val fromTimeline = currentIdx + 1 + fromIndex
                val toTimeline = currentIdx + 1 + toIndex
                if (fromTimeline in 0 until controller.mediaItemCount && toTimeline in 0 until controller.mediaItemCount) {
                    controller.moveMediaItem(fromTimeline, toTimeline)
                    rebuildQueueState(controller)
                }
            }
        }
    }

    fun clearUpcoming(withController: ((MediaBrowser) -> Unit) -> Unit) {
        withController { controller ->
            val count = controller.mediaItemCount
            val current = controller.currentMediaItemIndex
            for (i in (count - 1) downTo (current + 1)) {
                controller.removeMediaItem(i)
            }
            if (currentShuffleOrder != null) {
                currentShuffleOrder = ControllableShuffleOrder(intArrayOf(current))
                try {
                    exoPlayerProvider.get().setShuffleOrder(currentShuffleOrder!!)
                } catch (e: Exception) {
                    android.util.Log.e("PlaybackQueueManager", "Error clearing shuffle order", e)
                }
            }
            rebuildQueueState(controller)
        }
    }

    fun seekToTimelineIndex(
        timelineIndex: Int,
        withController: ((MediaBrowser) -> Unit) -> Unit
    ) {
        withController { controller ->
            if (timelineIndex in 0 until controller.mediaItemCount) {
                controller.seekTo(timelineIndex, 0L)
            }
        }
    }

    fun rebuildQueueState(player: Player) {
        val itemCount = player.mediaItemCount
        val currentIndex = player.currentMediaItemIndex
        val isShuffle = player.shuffleModeEnabled

        if (itemCount == 0) {
            _queueState.value = PlaybackQueueState(
                currentItem = null,
                upcomingItems = emptyList(),
                historyItems = emptyList(),
                sourceTitle = currentSourceTitle,
                isShuffleEnabled = isShuffle,
                isRadio = false,
                totalCount = 0
            )
            return
        }

        val currentMediaItem = player.currentMediaItem
        if (currentMediaItem?.mediaId?.startsWith("radio:") == true) {
            _queueState.value = PlaybackQueueState(
                currentItem = null,
                upcomingItems = emptyList(),
                historyItems = emptyList(),
                sourceTitle = "Quran.com Radio",
                isShuffleEnabled = false,
                isRadio = true,
                totalCount = 0
            )
            return
        }

        val allSurahs = cachedSurahs ?: emptyList()
        val defaultReciter: Reciter = defaultReciterCache ?: ReciterRepository.defaultReciters.first().let {
            Reciter(
                id = it.id,
                name = it.name,
                englishName = it.englishName,
                style = it.style,
                qiraah = it.qiraah,
                photoUrl = it.photoUrl,
                surahsCount = it.surahsCount,
                isFavorite = it.isFavorite,
                audioServer = it.audioServer
            )
        }

        val allItems = ArrayList<QueueItem>(itemCount)
        for (i in 0 until itemCount) {
            val mediaItem = player.getMediaItemAt(i)
            val surahId = parseSurahId(mediaItem.mediaId) ?: 1
            val reciterId = parseReciterId(mediaItem.mediaId) ?: defaultReciter.id
            val queueId = parseQueueUid(mediaItem.mediaId) ?: "queue_$i"

            val surah = allSurahs.find { it.id == surahId } ?: Surah(
                id = surahId,
                nameSimple = mediaItem.mediaMetadata.displayTitle?.toString() ?: "Surah $surahId",
                nameArabic = "",
                versesCount = 0,
                revelationPlace = "makkah",
                translatedName = ""
            )
            val reciterEntity = ReciterRepository.defaultReciters.find { it.id == reciterId }
            val reciter = if (reciterEntity != null) {
                Reciter(
                    id = reciterEntity.id,
                    name = reciterEntity.name,
                    englishName = reciterEntity.englishName,
                    style = reciterEntity.style,
                    qiraah = reciterEntity.qiraah,
                    photoUrl = reciterEntity.photoUrl,
                    surahsCount = reciterEntity.surahsCount,
                    isFavorite = reciterEntity.isFavorite,
                    audioServer = reciterEntity.audioServer
                )
            } else {
                Reciter(
                    id = reciterId,
                    name = mediaItem.mediaMetadata.artist?.toString() ?: "Reciter",
                    englishName = mediaItem.mediaMetadata.artist?.toString() ?: "Reciter"
                )
            }

            allItems.add(
                QueueItem(
                    id = queueId,
                    surah = surah,
                    reciter = reciter,
                    startAyah = 1,
                    timelineIndex = i
                )
            )
        }

        val historyItems = mutableListOf<QueueItem>()
        var nowPlaying: QueueItem? = null
        val upcomingItems = mutableListOf<QueueItem>()

        val shuffleOrder = currentShuffleOrder
        if (isShuffle && shuffleOrder != null && shuffleOrder.shuffledIndices.size == itemCount) {
            val shuffledIndices = shuffleOrder.shuffledIndices
            val currentPosInShuffle = shuffledIndices.indexOf(currentIndex)

            if (currentPosInShuffle >= 0) {
                for (p in 0 until currentPosInShuffle) {
                    val tIdx = shuffledIndices[p]
                    if (tIdx in allItems.indices) historyItems.add(allItems[tIdx])
                }
                if (currentIndex in allItems.indices) {
                    nowPlaying = allItems[currentIndex]
                }
                for (p in (currentPosInShuffle + 1) until shuffledIndices.size) {
                    val tIdx = shuffledIndices[p]
                    if (tIdx in allItems.indices) upcomingItems.add(allItems[tIdx])
                }
            } else {
                if (currentIndex in allItems.indices) nowPlaying = allItems[currentIndex]
            }
        } else {
            for (i in 0 until currentIndex) {
                historyItems.add(allItems[i])
            }
            if (currentIndex in allItems.indices) {
                nowPlaying = allItems[currentIndex]
            }
            for (i in (currentIndex + 1) until itemCount) {
                upcomingItems.add(allItems[i])
            }
        }

        _queueState.value = PlaybackQueueState(
            currentItem = nowPlaying,
            upcomingItems = upcomingItems,
            historyItems = historyItems,
            sourceTitle = currentSourceTitle,
            isShuffleEnabled = isShuffle,
            isRadio = false,
            totalCount = itemCount
        )
    }

    private fun persistQueueAsync(
        items: List<Surah>,
        reciter: Reciter,
        currentIndex: Int,
        queueTitle: String?,
        isShuffle: Boolean
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                val file = File(context.filesDir, "queue_cache.json")
                val json = JSONObject().apply {
                    put("queueTitle", queueTitle ?: "")
                    put("currentIndex", currentIndex)
                    put("reciterId", reciter.id)
                    put("isShuffle", isShuffle)
                    val array = JSONArray()
                    items.forEach { array.put(it.id) }
                    put("surahIds", array)
                }
                file.writeText(json.toString())
            } catch (e: Exception) {
                android.util.Log.e("PlaybackQueueManager", "Failed to persist queue cache", e)
            }
        }
    }

    private fun parseSurahId(mediaId: String): Int? {
        if (mediaId.startsWith("surah:")) {
            return mediaId.removePrefix("surah:").substringBefore(":").toIntOrNull()
        }
        return mediaId.toIntOrNull()
    }

    private fun parseReciterId(mediaId: String): Int? {
        if (mediaId.contains(":reciter:")) {
            return mediaId.substringAfter(":reciter:").substringBefore(":").toIntOrNull()
        }
        return null
    }

    private fun parseQueueUid(mediaId: String): String? {
        if (mediaId.contains(":uid:")) {
            return mediaId.substringAfter(":uid:")
        }
        return null
    }
}
