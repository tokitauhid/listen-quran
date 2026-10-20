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
    private val khatamTrackerRepository: com.listenquran.data.repository.khatam.KhatamTrackerRepository,
    private val translationRepository: com.listenquran.data.repository.TranslationRepository,
    private val repetitionPresetRepository: com.listenquran.data.repository.RepetitionPresetRepository,
    private val likedRepository: com.listenquran.data.repository.LikedRepository,
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

    val likedSurahs: StateFlow<List<LikedSurah>> = likedRepository.getAllLiked()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bookmarks: StateFlow<List<Bookmark>> = bookmarkRepository.getAllBookmarks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val repetitionPresets: StateFlow<List<RepetitionPreset>> = repetitionPresetRepository.getAllPresets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val khatmaProgress: StateFlow<KhatmaProgress> = combine(
        khatamTrackerRepository.activeKhatam,
        khatamTrackerRepository.activeRangeSet
    ) { khatam, rangeSet ->
        val nextAyah = rangeSet.nextUnreadAyah ?: 1
        val (surahNum, ayahNum) = com.listenquran.domain.model.khatam.QuranAyahMetadata.toSurahAyah(nextAyah)
        val currentJuz = com.listenquran.domain.model.khatam.QuranAyahMetadata.toJuz(nextAyah)
        val currentPage = com.listenquran.domain.model.khatam.QuranAyahMetadata.toPage(nextAyah)
        val targetDays = khatam?.targetDays ?: 30
        val pagesPerDay = maxOf(1, (604 - currentPage) / maxOf(1, targetDays))
        KhatmaProgress(
            id = 1L,
            title = khatam?.title ?: "Daily Khatma",
            currentPage = currentPage,
            totalPages = 604,
            currentJuz = currentJuz,
            lastSurahNumber = surahNum,
            lastAyahNumber = ayahNum,
            targetDays = targetDays,
            pagesPerDayTarget = pagesPerDay,
            updatedAt = System.currentTimeMillis()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), KhatmaProgress())

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
    val queueState: StateFlow<com.listenquran.player.PlaybackQueueState> = playerManager.queueState
    val currentPositionMs: StateFlow<Long> = playerManager.currentPositionMs
    val currentWordIndex: StateFlow<Int> = playerManager.currentWordIndex
    val syncState: StateFlow<com.listenquran.player.PlaybackSyncState> = playerManager.syncState
    val sleepTimerState: StateFlow<com.listenquran.player.SleepTimerState> = playerManager.sleepTimerState
    val customSleepTimerMinutes: StateFlow<Int> = userPreferencesRepository.customSleepTimerMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 20)

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
            likedRepository.isLiked(pair.first, pair.second)
        } else {
            flowOf(false)
        }
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _currentVerses = MutableStateFlow<List<Ayah>>(emptyList())
    val currentVerses: StateFlow<List<Ayah>> = _currentVerses.asStateFlow()

    val isWordByWordEnabled: StateFlow<Boolean> = userPreferencesRepository.wordByWordEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isTranslationEnabled: StateFlow<Boolean> = userPreferencesRepository.translationEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val retryTranslationTrigger = MutableStateFlow(0)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val translationState: StateFlow<com.listenquran.data.repository.TranslationState> = combine(
        playbackState.map { it.currentSurah?.id }.distinctUntilChanged(),
        userPreferencesRepository.translationEnabled,
        retryTranslationTrigger
    ) { surahId, isEnabled, _ ->
        Pair(surahId, isEnabled)
    }.flatMapLatest { (surahId, isEnabled) ->
        if (surahId != null && isEnabled) {
            translationRepository.getTranslationsForSurah(surahId)
        } else {
            flowOf(com.listenquran.data.repository.TranslationState.Success(surahId ?: 0, emptyMap()))
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        com.listenquran.data.repository.TranslationState.Success(0, emptyMap())
    )

    fun toggleWordByWord() {
        viewModelScope.launch {
            userPreferencesRepository.setWordByWordEnabled(!isWordByWordEnabled.value)
        }
    }

    fun toggleTranslation() {
        viewModelScope.launch {
            userPreferencesRepository.setTranslationEnabled(!isTranslationEnabled.value)
        }
    }

    fun retryLoadTranslation() {
        retryTranslationTrigger.value += 1
    }

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

    fun playSurahById(surahId: Int, startAyah: Int = 1, reciterId: Int? = null) {
        viewModelScope.launch {
            val surah = quranRepository.getSurahById(surahId) ?: return@launch
            val targetReciter = (if (reciterId != null) reciterRepository.getReciterById(reciterId) else null)
                ?: playbackState.value.currentReciter
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

    fun setSleepTimerMode(mode: com.listenquran.player.SleepTimerMode) {
        playerManager.setSleepTimerMode(mode)
    }

    fun cancelSleepTimer() {
        playerManager.cancelSleepTimer()
    }

    fun saveCustomSleepTimerMinutes(minutes: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setCustomSleepTimerMinutes(minutes)
        }
    }

    fun startHifzLoop(config: HifzLoopConfig) {
        val currentSurah = playbackState.value.currentSurah
        if (currentSurah == null || currentSurah.id != config.surahNumber) {
            val targetSurah = surahs.value.find { it.id == config.surahNumber }
            val reciter = playbackState.value.currentReciter ?: reciters.value.firstOrNull()
            if (targetSurah != null && reciter != null) {
                playerManager.playSurah(targetSurah, reciter, startAyah = config.startAyah)
            }
        }
        playerManager.startHifzLoop(config)
    }

    fun stopHifzLoop() {
        playerManager.stopHifzLoop()
    }

    fun saveRepetitionPreset(preset: RepetitionPreset) {
        viewModelScope.launch {
            if (preset.id == 0L) {
                repetitionPresetRepository.savePreset(preset)
            } else {
                repetitionPresetRepository.updatePreset(preset)
            }
        }
    }

    fun deleteRepetitionPreset(id: Long) {
        viewModelScope.launch {
            repetitionPresetRepository.deletePreset(id)
        }
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

    fun deleteBookmark(surahNumber: Int, ayahNumber: Int) {
        viewModelScope.launch {
            bookmarkRepository.deleteBookmark(surahNumber, ayahNumber)
        }
    }

    fun deleteBookmarkById(id: Long) {
        viewModelScope.launch {
            bookmarkRepository.deleteBookmarkById(id)
        }
    }

    fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch {
            bookmarkRepository.deleteBookmark(bookmark)
        }
    }

    fun restoreBookmark(bookmark: Bookmark) {
        viewModelScope.launch {
            bookmarkRepository.saveBookmark(
                surahNumber = bookmark.surahNumber,
                surahName = bookmark.surahName,
                ayahNumber = bookmark.ayahNumber,
                verseKey = bookmark.verseKey,
                textUthmani = bookmark.textUthmani,
                translation = bookmark.translation,
                note = bookmark.note
            )
        }
    }

    suspend fun bookmarkCurrentAyah(): Pair<Surah, Int>? {
        val surah = playbackState.value.currentSurah ?: return null
        val ayahNumber = playbackState.value.currentAyahNumber
        val verses = if (_currentVerses.value.isNotEmpty() && _currentVerses.value.first().surahNumber == surah.id) {
            _currentVerses.value
        } else {
            val fetched = quranRepository.getVerses(surah.id)
            _currentVerses.value = fetched
            fetched
        }
        val ayah = verses.find { it.ayahNumber == ayahNumber }
        val textUthmani = ayah?.textUthmani ?: ""
        val translation = ayah?.translation ?: ""
        bookmarkRepository.saveBookmark(
            surahNumber = surah.id,
            surahName = surah.nameSimple,
            ayahNumber = ayahNumber,
            verseKey = "${surah.id}:$ayahNumber",
            textUthmani = textUthmani,
            translation = translation,
        )
        return Pair(surah, ayahNumber)
    }

    fun renamePlaylist(playlistId: Long, newName: String) {
        viewModelScope.launch {
            playlistRepository.renamePlaylist(playlistId, newName)
        }
    }

    fun toggleLikeCurrentSurah() {
        val surah = playbackState.value.currentSurah ?: return
        val reciter = playbackState.value.currentReciter ?: reciters.value.firstOrNull() ?: return
        viewModelScope.launch {
            val isNowLiked = likedRepository.toggleLike(
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

    fun toggleLikeSurah(surah: Surah, reciter: Reciter? = null) {
        val targetReciter = reciter ?: playbackState.value.currentReciter ?: reciters.value.firstOrNull() ?: return
        viewModelScope.launch {
            val isNowLiked = likedRepository.toggleLike(
                surahNumber = surah.id,
                surahName = surah.nameSimple,
                surahArabic = surah.nameArabic,
                reciterId = targetReciter.id,
                reciterName = targetReciter.englishName
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

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        playerManager.moveQueueItem(fromIndex, toIndex)
    }

    fun addToPlaylist(playlistId: Long, surah: Surah) {
        val reciter = playbackState.value.currentReciter ?: reciters.value.firstOrNull() ?: return
        val isLikedCollection = playlists.value.find { it.id == playlistId }?.isLikedCollection == true
        viewModelScope.launch {
            if (isLikedCollection) {
                likedRepository.addLike(
                    surahNumber = surah.id,
                    surahName = surah.nameSimple,
                    surahArabic = surah.nameArabic,
                    reciterId = reciter.id,
                    reciterName = reciter.englishName,
                )
                android.widget.Toast.makeText(context, "Added to Liked Surahs", android.widget.Toast.LENGTH_SHORT).show()
                return@launch
            }
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
            likedRepository.deleteById(itemId)
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
        val isLikedCollection = playlists.value.find { it.id == playlistId }?.isLikedCollection == true
        return if (isLikedCollection) {
            likedRepository.getAllLiked().map { list ->
                list.map { it.toPlaylistItem(playlistId) }
            }
        } else {
            playlistRepository.getPlaylistItems(playlistId)
        }
    }

    fun playAllLiked() {
        val items = likedSurahs.value.map { it.toPlaylistItem() }
        if (items.isEmpty()) return
        val allSurahs = surahs.value
        val allReciters = reciters.value
        playerManager.playPlaylist(
            items = items,
            allSurahs = allSurahs,
            allReciters = allReciters,
            startIndex = 0,
            queueTitle = "Liked Surahs"
        )
    }

    fun playLikedSurah(liked: LikedSurah) {
        val items = likedSurahs.value.map { it.toPlaylistItem() }
        val targetItem = liked.toPlaylistItem()
        playPlaylistItem(targetItem, items, "Liked Surahs")
    }

    fun deleteLikedSurah(liked: LikedSurah) {
        viewModelScope.launch {
            likedRepository.removeLike(liked.surahNumber, liked.reciterId)
            android.widget.Toast.makeText(context, "Removed from Liked Surahs", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun restoreLikedSurah(liked: LikedSurah) {
        viewModelScope.launch {
            likedRepository.addLike(
                surahNumber = liked.surahNumber,
                surahName = liked.surahName,
                surahArabic = liked.surahArabic,
                reciterId = liked.reciterId,
                reciterName = liked.reciterName,
            )
        }
    }

    fun playPlaylistItem(
        item: PlaylistItem,
        playlistItems: List<PlaylistItem>? = null,
        playlistTitle: String? = null
    ) {
        val allSurahs = surahs.value
        val allReciters = reciters.value
        if (playlistItems != null && playlistItems.isNotEmpty()) {
            val startIndex = playlistItems.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
            playerManager.playPlaylist(
                items = playlistItems,
                allSurahs = allSurahs,
                allReciters = allReciters,
                startIndex = startIndex,
                queueTitle = playlistTitle ?: "Playlist"
            )
        } else {
            val surah = allSurahs.find { it.id == item.surahNumber } ?: return
            val reciter = allReciters.find { it.id == item.reciterId } ?: playbackState.value.currentReciter ?: allReciters.firstOrNull() ?: return
            playerManager.playSurah(surah, reciter, item.startAyah, queueTitle = playlistTitle)
        }
    }

    fun playAllPlaylist(items: List<PlaylistItem>, playlistTitle: String? = null) {
        if (items.isEmpty()) return
        val allSurahs = surahs.value
        val allReciters = reciters.value
        playerManager.playPlaylist(
            items = items,
            allSurahs = allSurahs,
            allReciters = allReciters,
            startIndex = 0,
            queueTitle = playlistTitle ?: "Playlist"
        )
    }

    fun toggleFavoriteReciter(reciter: Reciter) {
        viewModelScope.launch {
            reciterRepository.toggleFavorite(reciter.id, !reciter.isFavorite)
        }
    }

    fun playAllWithReciter(reciter: Reciter) {
        val allSurahs = surahs.value
        if (allSurahs.isEmpty()) return
        playerManager.playSurahs(
            surahs = allSurahs,
            reciter = reciter,
            startIndex = 0,
            startAyah = 1,
            queueTitle = "Recitations by ${reciter.englishName}"
        )
    }

    fun playBrowseCategory(categoryTitle: String, surahIds: List<Int>) {
        val allSurahs = surahs.value
        val matchedSurahs = surahIds.mapNotNull { id -> allSurahs.find { it.id == id } }
        if (matchedSurahs.isEmpty()) return
        val reciter = playbackState.value.currentReciter ?: reciters.value.firstOrNull() ?: return
        playerManager.playSurahs(
            surahs = matchedSurahs,
            reciter = reciter,
            startIndex = 0,
            startAyah = 1,
            queueTitle = categoryTitle
        )
    }

    fun playMix(mixTitle: String, surahIds: List<Int>) {
        playBrowseCategory(mixTitle, surahIds)
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
