package com.listenquran.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val playerManager: AudioPlayerManager,
) : ViewModel() {

    val surahs: StateFlow<List<Surah>> = quranRepository.getSurahs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val reciters: StateFlow<List<Reciter>> = reciterRepository.getReciters()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    val playbackState: StateFlow<PlaybackState> = playerManager.playbackState
    val currentPositionMs: StateFlow<Long> = playerManager.currentPositionMs
    val currentWordIndex: StateFlow<Int> = playerManager.currentWordIndex

    private val _currentVerses = MutableStateFlow<List<Ayah>>(emptyList())
    val currentVerses: StateFlow<List<Ayah>> = _currentVerses.asStateFlow()

    init {
        viewModelScope.launch {
            playlistRepository.ensureLikedPlaylistCreated()
        }
    }

    fun loadVersesForCurrentSurah() {
        val surahId = playbackState.value.currentSurah?.id ?: return
        viewModelScope.launch {
            _currentVerses.value = quranRepository.getVerses(surahId)
        }
    }

    fun playSurah(surah: Surah, reciter: Reciter? = null, startAyah: Int = 1) {
        val targetReciter = reciter ?: playbackState.value.currentReciter ?: reciters.value.firstOrNull() ?: return
        playerManager.playSurah(surah, targetReciter, startAyah)
    }

    fun playSurahById(surahId: Int, startAyah: Int = 1) {
        viewModelScope.launch {
            val surah = quranRepository.getSurahById(surahId) ?: return@launch
            val targetReciter = playbackState.value.currentReciter
                ?: reciters.value.firstOrNull()
                ?: reciterRepository.getDefaultReciter()
            playerManager.playSurah(surah, targetReciter, startAyah)
        }
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
        val reciter = playbackState.value.currentReciter ?: return
        viewModelScope.launch {
            val isLiked = playlistRepository.isLiked(surah.id, reciter.id).first()
            playlistRepository.toggleLiked(
                surahNumber = surah.id,
                surahName = surah.nameSimple,
                surahArabic = surah.nameArabic,
                reciterId = reciter.id,
                reciterName = reciter.englishName,
                isCurrentlyLiked = isLiked
            )
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { userPreferencesRepository.setThemeMode(mode) }
    }

    fun setAudioQuality(quality: AudioQuality) {
        viewModelScope.launch { userPreferencesRepository.setAudioQuality(quality) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.setNotificationsEnabled(enabled) }
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
            playlistRepository.addToPlaylist(
                playlistId = playlistId,
                surahNumber = surah.id,
                surahName = surah.nameSimple,
                surahArabic = surah.nameArabic,
                reciterId = reciter.id,
                reciterName = reciter.englishName,
            )
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

    fun importPlaylists(json: String) {
        viewModelScope.launch {
            playlistRepository.importPlaylistsJson(json)
        }
    }

    fun importStats(json: String) {
        viewModelScope.launch {
            statsRepository.importStatsJson(json)
        }
    }

    fun clearAudioCache() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.cacheDir.deleteRecursively()
            } catch (_: Exception) {}
        }
    }
}
