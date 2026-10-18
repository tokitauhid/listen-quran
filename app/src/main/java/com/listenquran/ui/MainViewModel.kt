package com.listenquran.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.listenquran.data.local.preferences.UserPreferencesRepository
import com.listenquran.data.repository.*
import com.listenquran.domain.model.*
import com.listenquran.player.AudioPlayerManager
import com.listenquran.player.PlaybackState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val quranRepository: QuranRepository,
    private val reciterRepository: ReciterRepository,
    private val playlistRepository: PlaylistRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val khatmaRepository: KhatmaRepository,
    private val statsRepository: StatsRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val downloadRepository: com.listenquran.data.repository.QuranDownloadRepository,
    private val radioRepository: com.listenquran.data.repository.RadioRepository,
    val playerManager: AudioPlayerManager,
) : ViewModel() {

    val surahs: StateFlow<List<Surah>> = quranRepository.getSurahs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val reciters: StateFlow<List<Reciter>> = reciterRepository.getReciters()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recitersSearchQuery = MutableStateFlow("")
    val recitersStyleFilter = MutableStateFlow("All")

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
    val pagedReciters: Flow<PagingData<Reciter>> = recitersSearchQuery
        .debounce { query -> if (query.isEmpty()) 0L else 200L }
        .distinctUntilChanged()
        .flatMapLatest { query ->
            reciterRepository.getPagedReciters(query = query.trim())
        }
        .cachedIn(viewModelScope)

    val playlists: StateFlow<List<Playlist>> = playlistRepository.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bookmarks: StateFlow<List<Bookmark>> = bookmarkRepository.getAllBookmarks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val khatmaProgress: StateFlow<KhatmaProgress> = khatmaRepository.getKhatmaProgress()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), KhatmaProgress())

    val statsSummary: StateFlow<ListeningStatsSummary> = statsRepository.getSummary()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ListeningStatsSummary())

    val recentSurahs: StateFlow<List<Surah>> = combine(
        statsRepository.getRecentSurahNumbers(),
        surahs
    ) { recentIds, allSurahs ->
        val matched = recentIds.mapNotNull { id -> allSurahs.find { it.id == id } }
        if (matched.isNotEmpty()) matched else allSurahs.take(6)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val themeMode: StateFlow<ThemeMode> = userPreferencesRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.DARK)

    val audioQuality: StateFlow<AudioQuality> = userPreferencesRepository.audioQuality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AudioQuality.STANDARD)

    val notificationsEnabled: StateFlow<Boolean> = userPreferencesRepository.notificationsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val radioStations: StateFlow<List<RadioStation>> = radioRepository.getRadioStations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CuratedRadioStations)

    val downloadedSurahs: StateFlow<List<com.listenquran.data.local.entity.DownloadedSurahEntity>> = downloadRepository.getAllDownloadedSurahs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val defaultReciterId: StateFlow<Int> = userPreferencesRepository.lastReciterId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 7)

    val defaultReciter: StateFlow<Reciter?> = combine(
        reciters,
        defaultReciterId
    ) { allReciters, id ->
        allReciters.find { it.id == id } ?: allReciters.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val playbackState: StateFlow<PlaybackState> = playerManager.playbackState
    val currentPositionMs: StateFlow<Long> = playerManager.currentPositionMs
    val currentWordIndex: StateFlow<Int> = playerManager.currentWordIndex

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val isCurrentSurahLiked: StateFlow<Boolean> = combine(
        playbackState,
        defaultReciterId
    ) { state, defId ->
        val surah = state.currentSurah
        val reciterId = state.currentReciter?.id ?: defId
        if (surah != null) {
            surah.id to reciterId
        } else {
            null
        }
    }
    .flatMapLatest { pair ->
        if (pair != null) {
            playlistRepository.isLiked(pair.first, pair.second)
        } else {
            flowOf(false)
        }
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _currentVerses = MutableStateFlow<List<Ayah>>(emptyList())
    val currentVerses: StateFlow<List<Ayah>> = _currentVerses.asStateFlow()

    init {
        viewModelScope.launch {
            reciterRepository.initializeReciters()
            playlistRepository.ensureLikedPlaylistCreated()
            if (userPreferencesRepository.notificationsEnabled.first()) {
                com.listenquran.notification.ReminderScheduler.scheduleReminders(context)
            }
        }
    }

    fun setRecitersSearchQuery(query: String) {
        recitersSearchQuery.value = query
    }

    fun setRecitersStyleFilter(style: String) {
        recitersStyleFilter.value = style
    }

    fun refreshReciters() {
        viewModelScope.launch {
            reciterRepository.refreshReciters()
        }
    }

    fun loadVersesForCurrentSurah() {
        val surahId = playbackState.value.currentSurah?.id ?: return
        viewModelScope.launch {
            _currentVerses.value = quranRepository.getVerses(surahId)
        }
    }

    fun playSurah(surah: Surah, reciter: Reciter? = null, startAyah: Int = 1) {
        val targetReciter = reciter ?: playbackState.value.currentReciter ?: defaultReciter.value ?: reciters.value.firstOrNull() ?: return
        playerManager.playSurah(surah, targetReciter, startAyah)
    }

    fun playSurahById(surahId: Int, startAyah: Int = 1) {
        viewModelScope.launch {
            val surah = quranRepository.getSurahById(surahId) ?: return@launch
            val targetReciter = playbackState.value.currentReciter
                ?: defaultReciter.value
                ?: reciters.value.firstOrNull()
                ?: reciterRepository.getDefaultReciter()
            playerManager.playSurah(surah, targetReciter, startAyah)
        }
    }

    fun playRadio(station: RadioStation) {
        playerManager.playRadio(station)
    }

    fun stopRadio() {
        playerManager.stopRadio()
    }

    fun resumeSurahAfterRadio() {
        playerManager.resumeSurahAfterRadio()
    }

    fun playReciterRadio(reciter: Reciter) {
        val station = RadioStation(
            id = "reciter_${reciter.id}",
            name = "${reciter.englishName} Radio",
            description = "24/7 continuous recitation by ${reciter.englishName}",
            type = RadioStationType.RECITER,
            bannerUrl = reciter.photoUrl ?: com.listenquran.util.ReciterImages.getPhotoUrl(reciter.id, reciter.englishName),
            reciterId = reciter.id,
            reciterName = reciter.englishName,
            targetSurahs = (1..114).toList(),
            poolReciterIds = listOf(reciter.id),
            city = reciter.style?.ifBlank { "Hafs" } ?: "Hafs"
        )
        playerManager.playRadio(station)
    }

    fun reorderQueue(newQueue: List<QueueItem>) {
        playerManager.reorderQueue(newQueue)
    }

    fun togglePlayPause() {
        playerManager.togglePlayPause()
    }

    fun seekTo(positionMs: Long) {
        playerManager.seekTo(positionMs)
    }

    fun seekToAyah(ayahNumber: Int) {
        playerManager.seekToAyah(ayahNumber)
    }

    fun playNext() {
        playerManager.playNext()
    }

    fun playPrevious() {
        playerManager.playPrevious()
    }

    fun setPlaybackSpeed(speed: Float) {
        playerManager.setPlaybackSpeed(speed)
        viewModelScope.launch { userPreferencesRepository.setPlaybackSpeed(speed) }
    }

    fun setSleepTimer(minutes: Int) {
        playerManager.setSleepTimer(minutes)
    }

    fun startHifzLoop(config: HifzLoopConfig) {
        playerManager.startHifzLoop(config)
    }

    fun createPlaylist(name: String, desc: String) {
        viewModelScope.launch {
            playlistRepository.createPlaylist(name, desc)
        }
    }

    fun saveBookmark(ayah: Ayah, note: String) {
        val surah = surahs.value.find { it.id == ayah.surahNumber } ?: return
        viewModelScope.launch {
            bookmarkRepository.saveBookmark(
                surahNumber = ayah.surahNumber,
                surahName = surah.nameSimple,
                ayahNumber = ayah.ayahNumber,
                verseKey = ayah.verseKey,
                textUthmani = ayah.textUthmani,
                translation = ayah.translation,
                note = note
            )
        }
    }

    fun updateBookmarkNote(id: Long, note: String) {
        viewModelScope.launch {
            bookmarkRepository.updateNote(id, note)
        }
    }

    fun toggleLikeCurrentSurah() {
        val surah = playbackState.value.currentSurah ?: return
        val reciter = playbackState.value.currentReciter ?: reciters.value.firstOrNull() ?: return
        viewModelScope.launch {
            val isNowLiked = playlistRepository.toggleLiked(
                surahNumber = surah.id,
                surahName = surah.nameSimple,
                surahArabic = surah.nameArabic,
                reciterId = reciter.id,
                reciterName = reciter.englishName
            )
            val msg = if (isNowLiked) "Added to Liked Surahs" else "Removed from Liked Surahs"
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { userPreferencesRepository.setThemeMode(mode) }
    }

    fun setAudioQuality(quality: AudioQuality) {
        viewModelScope.launch { userPreferencesRepository.setAudioQuality(quality) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setNotificationsEnabled(enabled)
            if (enabled) {
                com.listenquran.notification.ReminderScheduler.scheduleReminders(context)
            } else {
                com.listenquran.notification.ReminderScheduler.cancelReminders(context)
            }
        }
    }

    fun exportStats() {
        viewModelScope.launch {
            statsRepository.exportStatsJson()
        }
    }

    fun continueKhatma() {
        val progress = khatmaProgress.value
        val surah = surahs.value.find { it.id == progress.lastSurahNumber } ?: surahs.value.firstOrNull() ?: return
        playSurah(surah, startAyah = progress.lastAyahNumber)
    }

    fun toggleShuffle() {
        playerManager.toggleShuffle()
    }

    fun toggleRepeat() {
        playerManager.toggleRepeat()
    }

    fun addToQueue(surah: Surah, reciter: Reciter? = null, startAyah: Int = 1) {
        val targetReciter = reciter ?: playbackState.value.currentReciter ?: reciters.value.firstOrNull() ?: return
        playerManager.addToQueue(surah, targetReciter, startAyah)
    }

    fun playNextInQueue(surah: Surah, reciter: Reciter? = null, startAyah: Int = 1) {
        val targetReciter = reciter ?: playbackState.value.currentReciter ?: reciters.value.firstOrNull() ?: return
        playerManager.playNextInQueue(surah, targetReciter, startAyah)
    }

    fun playQueueItem(index: Int) {
        playerManager.playQueueItem(index)
    }

    fun removeFromQueue(index: Int) {
        playerManager.removeFromQueue(index)
    }

    fun clearQueue() {
        playerManager.clearQueue()
    }

    fun setSleepTimerUntilTime(hour: Int, minute: Int) {
        playerManager.setSleepTimerUntilTime(hour, minute)
    }

    fun addToPlaylist(playlistId: Long, surah: Surah) {
        val reciter = playbackState.value.currentReciter ?: reciters.value.firstOrNull() ?: return
        viewModelScope.launch {
            val added = playlistRepository.addToPlaylist(
                playlistId = playlistId,
                surahNumber = surah.id,
                surahName = surah.nameSimple,
                surahArabic = surah.nameArabic,
                reciterId = reciter.id,
                reciterName = reciter.englishName,
            )
            if (added) {
                android.widget.Toast.makeText(context, "Added to playlist", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                android.widget.Toast.makeText(context, "Already in this playlist", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun createAndAddToPlaylist(playlistName: String, surah: Surah) {
        val reciter = playbackState.value.currentReciter ?: reciters.value.firstOrNull() ?: return
        viewModelScope.launch {
            val plId = playlistRepository.createPlaylist(playlistName)
            playlistRepository.addToPlaylist(
                playlistId = plId,
                surahNumber = surah.id,
                surahName = surah.nameSimple,
                surahArabic = surah.nameArabic,
                reciterId = reciter.id,
                reciterName = reciter.englishName,
            )
            android.widget.Toast.makeText(context, "Created and added to playlist", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            playlistRepository.deletePlaylist(playlistId)
        }
    }

    fun deletePlaylistItem(itemId: Long) {
        viewModelScope.launch {
            playlistRepository.deletePlaylistItem(itemId)
        }
    }

    fun reorderPlaylistItems(items: List<PlaylistItem>) {
        viewModelScope.launch {
            playlistRepository.reorderPlaylistItems(items)
        }
    }

    fun setDefaultReciter(reciter: Reciter) {
        viewModelScope.launch {
            userPreferencesRepository.setLastReciterId(reciter.id)
            val currentSurah = playbackState.value.currentSurah
            if (currentSurah != null && !playbackState.value.isRadio) {
                playSurah(currentSurah, reciter, playbackState.value.currentAyahNumber)
            }
        }
    }

    suspend fun getVerseAudioTiming(reciterId: Int, chapterId: Int, verseKey: String): Pair<String, VerseTiming?>? {
        return try {
            val chapterAudio = quranRepository.getChapterAudio(reciterId = reciterId, chapterId = chapterId) ?: return null
            val timing = chapterAudio.timestamps.find { it.verseKey == verseKey }
            Pair(chapterAudio.audioUrl, timing)
        } catch (e: Exception) {
            null
        }
    }

    fun getPlaylistItems(playlistId: Long): Flow<List<PlaylistItem>> {
        return playlistRepository.getPlaylistItems(playlistId)
    }

    fun playPlaylistItem(item: PlaylistItem) {
        val surah = surahs.value.find { it.id == item.surahNumber } ?: return
        val reciter = reciters.value.find { it.id == item.reciterId } ?: playbackState.value.currentReciter ?: reciters.value.firstOrNull() ?: return
        playSurah(surah, reciter, item.startAyah)
    }

    fun playAllPlaylist(items: List<PlaylistItem>) {
        if (items.isEmpty()) return
        playerManager.clearQueue()
        val first = items.first()
        for (i in 1 until items.size) {
            val item = items[i]
            surahs.value.find { it.id == item.surahNumber }?.let {
                playerManager.addToQueue(it)
            }
        }
        playPlaylistItem(first)
    }

    fun toggleFavoriteReciter(reciter: Reciter) {
        viewModelScope.launch {
            reciterRepository.toggleFavorite(reciter.id, !reciter.isFavorite)
        }
    }

    fun playAllWithReciter(reciter: Reciter) {
        val allSurahs = surahs.value
        if (allSurahs.isEmpty()) return
        playerManager.clearQueue()
        for (i in 1 until allSurahs.size) {
            playerManager.addToQueue(allSurahs[i])
        }
        playSurah(allSurahs.first(), reciter)
    }

    fun exportPlaylists() {
        viewModelScope.launch {
            playlistRepository.exportPlaylistsJson()
        }
    }

    fun exportPlaylistsToUri(uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = playlistRepository.exportPlaylistsJson()
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(json.toByteArray(Charsets.UTF_8))
                }
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Playlists exported successfully", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error exporting playlists", e)
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Export failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun importPlaylists(json: String) {
        viewModelScope.launch {
            playlistRepository.importPlaylistsJson(json)
        }
    }

    fun importPlaylistsFromUri(uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).readText()
                } ?: ""
                val success = playlistRepository.importPlaylistsJson(json)
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    if (success) {
                        android.widget.Toast.makeText(context, "Playlists imported successfully", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        android.widget.Toast.makeText(context, "Failed to parse playlists JSON", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error importing playlists", e)
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Import failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun exportStatsToUri(uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = statsRepository.exportStatsJson()
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(json.toByteArray(Charsets.UTF_8))
                }
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Stats exported successfully", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error exporting stats", e)
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Export failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun importStats(json: String) {
        viewModelScope.launch {
            statsRepository.importStatsJson(json)
        }
    }

    fun importStatsFromUri(uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).readText()
                } ?: ""
                val success = statsRepository.importStatsJson(json)
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    if (success) {
                        android.widget.Toast.makeText(context, "Stats imported successfully", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        android.widget.Toast.makeText(context, "Failed to parse stats JSON", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error importing stats", e)
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Import failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun downloadSurah(surah: Surah, reciter: Reciter) {
        viewModelScope.launch {
            android.widget.Toast.makeText(context, "Downloading ${surah.nameSimple}...", android.widget.Toast.LENGTH_SHORT).show()
            val result = downloadRepository.downloadSurah(surah, reciter)
            if (result.isSuccess) {
                android.widget.Toast.makeText(context, "${surah.nameSimple} downloaded successfully", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                android.widget.Toast.makeText(context, "Download failed: ${result.exceptionOrNull()?.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun deleteDownloadedSurah(surahNumber: Int, reciterId: Int) {
        viewModelScope.launch {
            downloadRepository.deleteDownload(surahNumber, reciterId)
            android.widget.Toast.makeText(context, "Download deleted", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun clearAllDownloads() {
        viewModelScope.launch {
            downloadRepository.clearAllDownloads()
            android.widget.Toast.makeText(context, "All downloads deleted", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun clearAudioCache() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                downloadRepository.clearAllDownloads()
                context.cacheDir.deleteRecursively()
            } catch (_: Exception) {}
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(context, "Audio cache cleared", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
}
