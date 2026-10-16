package com.listenquran.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.listenquran.data.local.preferences.UserPreferencesRepository
import com.listenquran.data.repository.*
import com.listenquran.domain.model.*
import com.listenquran.player.AudioPlayerManager
import com.listenquran.player.PlaybackState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val quranRepository: QuranRepository,
    private val reciterRepository: ReciterRepository,
    private val playlistRepository: PlaylistRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val khatmaRepository: KhatmaRepository,
    private val radioRepository: RadioRepository,
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

    val radioStations: List<RadioStation> = radioRepository.getRadioStations()

    val statsSummary: StateFlow<ListeningStatsSummary> = statsRepository.getSummary()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ListeningStatsSummary())

    val themeMode: StateFlow<ThemeMode> = userPreferencesRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.DARK)

    val audioQuality: StateFlow<AudioQuality> = userPreferencesRepository.audioQuality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AudioQuality.STANDARD)

    val notificationsEnabled: StateFlow<Boolean> = userPreferencesRepository.notificationsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val playbackState: StateFlow<PlaybackState> = playerManager.playbackState

    private val _currentVerses = MutableStateFlow<List<Ayah>>(emptyList())
    val currentVerses: StateFlow<List<Ayah>> = _currentVerses.asStateFlow()

    init {
        viewModelScope.launch {
            playlistRepository.ensureLikedPlaylistCreated()
        }

        // Whenever current Surah changes, fetch verses for synced scripture
        viewModelScope.launch {
            playbackState.map { it.currentSurah?.id }.distinctUntilChanged().collect { surahId ->
                if (surahId != null) {
                    val verses = quranRepository.getVerses(surahId)
                    _currentVerses.value = verses
                } else {
                    _currentVerses.value = emptyList()
                }
            }
        }
    }

    fun playSurah(surah: Surah, reciter: Reciter? = null, startAyah: Int = 1) {
        val targetReciter = reciter ?: playbackState.value.currentReciter ?: reciters.value.firstOrNull() ?: return
        playerManager.playSurah(surah, targetReciter, startAyah)
    }

    fun playRadio(station: RadioStation) {
        playerManager.playRadio(station)
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
}
