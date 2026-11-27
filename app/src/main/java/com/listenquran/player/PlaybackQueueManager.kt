package com.listenquran.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

    init {
        scope.launch {
            runCatching { getAllSurahs() }
        }
    }

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
        endAyah: Int? = null,
        queueId: String = UUID.randomUUID().toString(),
        clipBounds: AyahClipBounds? = null,
        customLabel: String? = null,
    ): MediaItem {
        var effectiveReciter = reciter
        var effectiveClipBounds = clipBounds
        if (endAyah != null && effectiveClipBounds == null) {
            val timings = quranRepository.getVerseTimings(reciter.id, surah.id)
            effectiveClipBounds = resolveAyahClipBounds(surah.id, startAyah, endAyah, timings)
            if (effectiveClipBounds == null) {
                val fallbackReciter = getDefaultReciter()
                if (fallbackReciter.id != reciter.id) {
                    val fallbackTimings = quranRepository.getVerseTimings(fallbackReciter.id, surah.id)
                    val fallbackBounds = resolveAyahClipBounds(surah.id, startAyah, endAyah, fallbackTimings)
                    if (fallbackBounds != null) {
                        effectiveReciter = fallbackReciter
                        effectiveClipBounds = fallbackBounds
                    }
                }
            }
        }

        val downloaded = downloadRepository.getDownloadedSurah(surah.id, effectiveReciter.id)
        var effectiveUri = when {
            downloaded != null && File(downloaded.localFilePath).exists() -> Uri.fromFile(File(downloaded.localFilePath))
            effectiveReciter.audioServer != null -> {
                val server = effectiveReciter.audioServer.trimEnd('/') + "/"
                val url = if (server.contains("mp3quran.net") || server.contains("/quran/")) {
                    server + String.format("%03d.mp3", surah.id)
                } else {
                    server + "${surah.id}.mp3"
                }
                Uri.parse(url)
            }
            else -> {
                val defaultMatch = ReciterRepository.defaultReciters.find { it.id == effectiveReciter.id }
                if (defaultMatch?.audioServer != null) {
                    val server = defaultMatch.audioServer.trimEnd('/') + "/"
                    val url = if (server.contains("mp3quran.net") || server.contains("/quran/")) {
                        server + String.format("%03d.mp3", surah.id)
                    } else {
                        server + "${surah.id}.mp3"
                    }
                    Uri.parse(url)
                } else null
            }
        }

        if (effectiveUri == null) {
            val audioData = quranRepository.getChapterAudio(effectiveReciter.id, surah.id)
            if (audioData != null) {
                effectiveUri = Uri.parse(audioData.audioUrl)
            }
        }

        val ayahSuffix = when {
            endAyah != null && endAyah == startAyah -> " • Ayah $startAyah"
            endAyah != null && endAyah > startAyah -> " • Ayahs $startAyah–$endAyah"
            startAyah > 1 -> " • Ayah $startAyah"
            else -> ""
        }
        val displayTitle = customLabel?.takeIf { it.isNotBlank() } ?: "${surah.id}. ${surah.nameSimple}"

        val artworkUri = Uri.parse("android.resource://${context.packageName}/${R.drawable.ic_app_logo}")
        val mediaMetadata = MediaMetadata.Builder()
            .setTitle("${surah.id}. ${surah.nameSimple} (${surah.nameArabic})")
            .setArtist(effectiveReciter.englishName)
            .setAlbumTitle("The Holy Quran")
            .setAlbumArtist(effectiveReciter.englishName)
            .setDisplayTitle(displayTitle)
            .setSubtitle("${effectiveReciter.englishName}$ayahSuffix")
            .setDescription("${surah.translatedName} • ${surah.revelationPlace.replaceFirstChar { it.uppercase() }} • ${surah.versesCount} Verses")
            .setArtworkUri(artworkUri)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .build()

        val encodedMediaId = encodeSurahMediaId(
            surahId = surah.id,
            reciterId = effectiveReciter.id,
            startAyah = startAyah,
            endAyah = endAyah,
            queueId = queueId,
        )

        val builder = MediaItem.Builder()
            .setMediaId(encodedMediaId)
            .setMediaMetadata(mediaMetadata)

        if (effectiveUri != null) {
            builder.setUri(effectiveUri)
        }

        if (effectiveClipBounds != null) {
            builder.setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(effectiveClipBounds.startMs)
                    .setEndPositionMs(effectiveClipBounds.endMs)
                    .build()
            )
        }

        return builder.build()
    }

    fun playQueue(
        surahs: List<Surah>,
        reciter: Reciter,
        startIndex: Int = 0,
        startAyah: Int = 1,
        startPositionMsOverride: Long? = null,
        queueTitle: String? = null,
        autoPlay: Boolean = true,
        withController: ((MediaBrowser) -> Unit) -> Unit
    ) {
        if (surahs.isEmpty()) return
        val clampedIndex = startIndex.coerceIn(0, surahs.size - 1)
        currentSourceTitle = queueTitle

        scope.launch {
            if (cachedSurahs.isNullOrEmpty()) {
                if (surahs.size >= 114) {
                    cachedSurahs = surahs
                } else {
                    getAllSurahs()
                }
            }

            val mediaItems = ArrayList<MediaItem>(surahs.size)
            for (i in surahs.indices) {
                val s = surahs[i]
                val ayah = if (i == clampedIndex) startAyah else 1
                mediaItems.add(buildMediaItem(s, reciter, ayah))
            }

            var startPosMs = startPositionMsOverride ?: 0L
            if (startPositionMsOverride == null && startAyah > 1) {
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
                if (autoPlay) {
                    controller.play()
                }

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
        shuffle: Boolean = false,
        autoPlay: Boolean = true,
        withController: ((MediaBrowser) -> Unit) -> Unit
    ) {
        if (items.isEmpty()) return
        scope.launch {
            val resolvedSurahs = if (allSurahs.isNotEmpty()) allSurahs else getAllSurahs()
            if (resolvedSurahs.isEmpty()) return@launch
            if (cachedSurahs.isNullOrEmpty()) {
                cachedSurahs = resolvedSurahs
            }

            val defaultReciter = getDefaultReciter()
            val clampedIndex = startIndex.coerceIn(0, items.size - 1)
            currentSourceTitle = queueTitle

            val resolvedEntries = items.map { item ->
                val surah = resolvedSurahs.find { it.id == item.surahNumber } ?: resolvedSurahs.first()
                val reciter = allReciters.find { it.id == item.reciterId } ?: defaultReciter
                Triple(item, surah, reciter)
            }

            val timingPairs = buildSet {
                resolvedEntries.forEach { (item, surah, reciter) ->
                    if (item.endAyah != null) {
                        add(reciter.id to surah.id)
                    }
                }
                val target = resolvedEntries[clampedIndex]
                if (target.first.startAyah > 1 || target.first.endAyah != null) {
                    add(target.third.id to target.second.id)
                }
            }
            val primaryTimings = if (timingPairs.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    coroutineScope {
                        timingPairs.map { (rId, sId) ->
                            async { (rId to sId) to quranRepository.getVerseTimings(rId, sId) }
                        }.awaitAll().toMap()
                    }
                }
            } else emptyMap()

            val fallbackPairs = buildSet {
                resolvedEntries.forEach { (item, surah, reciter) ->
                    if (item.endAyah != null && reciter.id != defaultReciter.id) {
                        val t = primaryTimings[reciter.id to surah.id].orEmpty()
                        if (resolveAyahClipBounds(surah.id, item.startAyah, item.endAyah, t) == null) {
                            add(defaultReciter.id to surah.id)
                        }
                    }
                }
            }
            val fallbackTimings = if (fallbackPairs.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    coroutineScope {
                        fallbackPairs.map { (rId, sId) ->
                            async { (rId to sId) to quranRepository.getVerseTimings(rId, sId) }
                        }.awaitAll().toMap()
                    }
                }
            } else emptyMap()
            val allTimingsMap = primaryTimings + fallbackTimings

            val mediaItems = ArrayList<MediaItem>(items.size)
            val queuedSurahs = ArrayList<Surah>(items.size)
            val queuedReciters = ArrayList<Reciter>(items.size)

            for ((item, surah, reciter) in resolvedEntries) {
                var effectiveReciter = reciter
                var clipBounds: AyahClipBounds? = null
                if (item.endAyah != null) {
                    val timings = allTimingsMap[reciter.id to surah.id].orEmpty()
                    clipBounds = resolveAyahClipBounds(surah.id, item.startAyah, item.endAyah, timings)
                    if (clipBounds == null && reciter.id != defaultReciter.id) {
                        val fbTimings = allTimingsMap[defaultReciter.id to surah.id].orEmpty()
                        val fbBounds = resolveAyahClipBounds(surah.id, item.startAyah, item.endAyah, fbTimings)
                        if (fbBounds != null) {
                            effectiveReciter = defaultReciter
                            clipBounds = fbBounds
                        }
                    }
                }
                queuedSurahs.add(surah)
                queuedReciters.add(effectiveReciter)
                mediaItems.add(
                    buildMediaItem(
                        surah = surah,
                        reciter = effectiveReciter,
                        startAyah = item.startAyah,
                        endAyah = item.endAyah,
                        clipBounds = clipBounds,
                        customLabel = item.surahName.takeIf { it != surah.nameSimple },
                    )
                )
            }

            val targetItem = items[clampedIndex]
            val targetSurah = queuedSurahs[clampedIndex]
            val targetReciter = queuedReciters[clampedIndex]
            val targetTimings = allTimingsMap[targetReciter.id to targetSurah.id]
                ?: if (targetItem.startAyah > 1) {
                    quranRepository.getVerseTimings(targetReciter.id, targetSurah.id)
                } else emptyList()
            val isClipped = mediaItems[clampedIndex].clippingConfiguration.endPositionMs != C.TIME_END_OF_SOURCE
            val startPosMs = computeInitialStartPositionMs(
                isClipped = isClipped,
                surahId = targetSurah.id,
                startAyah = targetItem.startAyah,
                timings = targetTimings,
            )

            withController { controller ->
                if (shuffle && mediaItems.size > 1) {
                    val order = ControllableShuffleOrder.createWithCurrentFirst(mediaItems.size, clampedIndex)
                    currentShuffleOrder = order
                    try {
                        exoPlayerProvider.get().setShuffleOrder(order)
                    } catch (e: Exception) {
                        android.util.Log.e("PlaybackQueueManager", "Failed to set custom shuffle order", e)
                    }
                    controller.shuffleModeEnabled = true
                } else {
                    currentShuffleOrder = null
                    controller.shuffleModeEnabled = false
                }

                controller.setMediaItems(mediaItems, clampedIndex, startPosMs)
                controller.prepare()
                if (autoPlay) {
                    controller.play()
                }

                rebuildQueueState(controller)
                persistQueueAsync(queuedSurahs, queuedReciters.getOrNull(clampedIndex) ?: defaultReciter, clampedIndex, queueTitle, shuffle)
            }
        }
    }

    fun switchReciterPreservingQueue(
        reciter: Reciter,
        currentAyahNumber: Int = 1,
        wasPlayingOverride: Boolean? = null,
        autoPlay: Boolean = true,
        withController: ((MediaBrowser) -> Unit) -> Unit
    ) {
        withController { controller ->
            val itemCount = controller.mediaItemCount
            if (itemCount == 0) return@withController
            val currentIndex = controller.currentMediaItemIndex.coerceIn(0, itemCount - 1)
            val wasPlaying = wasPlayingOverride ?: controller.isPlaying
            val wasShuffle = controller.shuffleModeEnabled
            val savedShuffleOrder = currentShuffleOrder
            val savedSourceTitle = currentSourceTitle

            val surahIds = ArrayList<Int>(itemCount)
            val startAyahs = ArrayList<Int>(itemCount)
            val endAyahs = ArrayList<Int?>(itemCount)
            val uids = ArrayList<String>(itemCount)
            for (i in 0 until itemCount) {
                val mi = controller.getMediaItemAt(i)
                surahIds.add(parseSurahId(mi.mediaId) ?: 1)
                startAyahs.add(parseStartAyah(mi.mediaId))
                endAyahs.add(parseEndAyah(mi.mediaId))
                uids.add(parseQueueUid(mi.mediaId) ?: UUID.randomUUID().toString())
            }

            scope.launch {
                val allSurahs = getAllSurahs()
                val defaultReciter = getDefaultReciter()
                val timingSurahIds = buildSet {
                    for (i in 0 until itemCount) {
                        if (endAyahs[i] != null || (i == currentIndex && currentAyahNumber > 1)) {
                            add(surahIds[i])
                        }
                    }
                }
                val newReciterTimings = if (timingSurahIds.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        coroutineScope {
                            timingSurahIds.map { sId ->
                                async { sId to quranRepository.getVerseTimings(reciter.id, sId) }
                            }.awaitAll().toMap()
                        }
                    }
                } else emptyMap()

                val fallbackSurahIds = if (reciter.id != defaultReciter.id) {
                    buildSet {
                        for (i in 0 until itemCount) {
                            val end = endAyahs[i]
                            if (end != null) {
                                val t = newReciterTimings[surahIds[i]].orEmpty()
                                if (resolveAyahClipBounds(surahIds[i], startAyahs[i], end, t) == null) {
                                    add(surahIds[i])
                                }
                            }
                        }
                    }
                } else emptySet()
                val fallbackTimings = if (fallbackSurahIds.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        coroutineScope {
                            fallbackSurahIds.map { sId ->
                                async { sId to quranRepository.getVerseTimings(defaultReciter.id, sId) }
                            }.awaitAll().toMap()
                        }
                    }
                } else emptyMap()

                val mediaItems = ArrayList<MediaItem>(itemCount)
                val queuedSurahs = ArrayList<Surah>(itemCount)

                for (i in 0 until itemCount) {
                    val sId = surahIds[i]
                    val surah = allSurahs.find { it.id == sId } ?: allSurahs.firstOrNull() ?: continue
                    val end = endAyahs[i]
                    val ayah = if (end != null) startAyahs[i] else if (i == currentIndex) currentAyahNumber else startAyahs[i]
                    var effectiveReciter = reciter
                    var clipBounds: AyahClipBounds? = null
                    if (end != null) {
                        clipBounds = resolveAyahClipBounds(sId, ayah, end, newReciterTimings[sId].orEmpty())
                        if (clipBounds == null && reciter.id != defaultReciter.id) {
                            val fbBounds = resolveAyahClipBounds(sId, ayah, end, fallbackTimings[sId].orEmpty())
                            if (fbBounds != null) {
                                effectiveReciter = defaultReciter
                                clipBounds = fbBounds
                            }
                        }
                    }
                    queuedSurahs.add(surah)
                    mediaItems.add(
                        buildMediaItem(
                            surah = surah,
                            reciter = effectiveReciter,
                            startAyah = ayah,
                            endAyah = end,
                            queueId = uids[i],
                            clipBounds = clipBounds,
                        )
                    )
                }
                if (mediaItems.isEmpty()) return@launch

                var startPosMs = 0L
                if (currentIndex in queuedSurahs.indices) {
                    val targetSurah = queuedSurahs[currentIndex]
                    val targetItem = mediaItems[currentIndex]
                    val clipConfig = targetItem.clippingConfiguration
                    val isClipped = clipConfig.endPositionMs != C.TIME_END_OF_SOURCE
                    if (isClipped) {
                        val effectiveReciterId = parseReciterId(targetItem.mediaId) ?: reciter.id
                        val timings = if (effectiveReciterId == reciter.id) {
                            newReciterTimings[targetSurah.id]
                        } else {
                            fallbackTimings[targetSurah.id]
                        } ?: quranRepository.getVerseTimings(effectiveReciterId, targetSurah.id)
                        val startAyahBound = startAyahs[currentIndex]
                        val endAyahBound = endAyahs[currentIndex] ?: startAyahBound
                        if (currentAyahNumber in startAyahBound..endAyahBound && currentAyahNumber > startAyahBound) {
                            val ayahTiming = timings.find { it.verseKey == "${targetSurah.id}:$currentAyahNumber" }
                            if (ayahTiming != null) {
                                val clipDur = (clipConfig.endPositionMs - clipConfig.startPositionMs).coerceAtLeast(0L)
                                startPosMs = (ayahTiming.timestampFrom - clipConfig.startPositionMs).coerceIn(0L, clipDur)
                            }
                        }
                    } else if (currentAyahNumber > 1) {
                        val timings = newReciterTimings[targetSurah.id]
                            ?: quranRepository.getVerseTimings(reciter.id, targetSurah.id)
                        startPosMs = computeInitialStartPositionMs(
                            isClipped = false,
                            surahId = targetSurah.id,
                            startAyah = currentAyahNumber,
                            timings = timings,
                        )
                    }
                }

                withController { c ->
                    currentSourceTitle = savedSourceTitle
                    c.setMediaItems(mediaItems, currentIndex.coerceIn(0, mediaItems.size - 1), startPosMs)
                    if (wasShuffle && savedShuffleOrder != null && savedShuffleOrder.shuffledIndices.size == mediaItems.size) {
                        currentShuffleOrder = savedShuffleOrder
                        try {
                            exoPlayerProvider.get().setShuffleOrder(savedShuffleOrder)
                        } catch (_: Exception) {}
                        c.shuffleModeEnabled = true
                    } else {
                        currentShuffleOrder = null
                        c.shuffleModeEnabled = false
                    }
                    c.prepare()
                    if (wasPlaying && autoPlay) {
                        c.play()
                    }
                    rebuildQueueState(c)
                    persistQueueAsync(queuedSurahs, reciter, currentIndex, savedSourceTitle, wasShuffle)
                }
            }
        }
    }

    fun restoreQueueMetadata(
        sourceTitle: String?,
        shuffleEnabled: Boolean,
        shuffledIndices: List<Int>,
        controller: MediaBrowser,
    ) {
        currentSourceTitle = sourceTitle
        val itemCount = controller.mediaItemCount
        if (shuffleEnabled && shuffledIndices.size == itemCount && itemCount > 1) {
            val order = ControllableShuffleOrder(shuffledIndices.toIntArray())
            currentShuffleOrder = order
            runCatching { exoPlayerProvider.get().setShuffleOrder(order) }
            controller.shuffleModeEnabled = true
        } else {
            currentShuffleOrder = null
            controller.shuffleModeEnabled = false
        }
        rebuildQueueState(controller)
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
        autoPlay: Boolean = true,
        withController: ((MediaBrowser) -> Unit) -> Unit
    ) {
        withController { controller ->
            if (timelineIndex in 0 until controller.mediaItemCount) {
                controller.seekTo(timelineIndex, 0L)
                if (autoPlay && !controller.isPlaying) {
                    controller.play()
                }
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

            val rawTitle = mediaItem.mediaMetadata.displayTitle?.toString()
                ?.removePrefix("$surahId. ")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
            val surah = allSurahs.find { it.id == surahId } ?: Surah(
                id = surahId,
                nameSimple = rawTitle ?: "Surah $surahId",
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
                    startAyah = parseStartAyah(mediaItem.mediaId),
                    endAyah = parseEndAyah(mediaItem.mediaId),
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

    companion object {
        fun encodeSurahMediaId(
            surahId: Int,
            reciterId: Int,
            startAyah: Int = 1,
            endAyah: Int? = null,
            queueId: String,
        ): String {
            val safeStart = startAyah.coerceAtLeast(1)
            return when {
                endAyah != null -> "surah:$surahId:reciter:$reciterId:ayah:$safeStart:end:$endAyah:uid:$queueId"
                safeStart > 1 -> "surah:$surahId:reciter:$reciterId:ayah:$safeStart:uid:$queueId"
                else -> "surah:$surahId:reciter:$reciterId:uid:$queueId"
            }
        }

        fun parseStartAyah(mediaId: String): Int {
            if (!mediaId.startsWith("surah:") || !mediaId.contains(":ayah:")) return 1
            return mediaId.substringAfter(":ayah:")
                .substringBefore(":")
                .toIntOrNull()
                ?.coerceAtLeast(1)
                ?: 1
        }

        fun parseEndAyah(mediaId: String): Int? {
            if (!mediaId.startsWith("surah:") || !mediaId.contains(":end:")) return null
            return mediaId.substringAfter(":end:")
                .substringBefore(":")
                .toIntOrNull()
                ?.takeIf { it >= 1 }
        }
    }
}
