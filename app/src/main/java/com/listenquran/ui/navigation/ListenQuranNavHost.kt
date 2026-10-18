package com.listenquran.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.listenquran.domain.model.*
import com.listenquran.ui.MainViewModel
import com.listenquran.ui.screens.home.HomeScreen
import com.listenquran.ui.screens.library.LibraryScreen
import com.listenquran.ui.screens.reciters.RecitersScreen
import com.listenquran.ui.screens.search.SearchScreen
import com.listenquran.ui.screens.settings.SettingsScreen
import com.listenquran.ui.screens.surahs.SurahsScreen
import com.listenquran.ui.screens.surahs.SurahsViewModel
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun ListenQuranNavHost(
    navController: NavHostController,
    viewModel: MainViewModel,
    surahs: List<Surah>,
    reciters: List<Reciter>,
    playlists: List<Playlist>,
    bookmarks: List<Bookmark>,
    khatmaProgress: KhatmaProgress,
    statsSummary: ListeningStatsSummary,
    recentSurahs: List<Surah>,
    themeMode: ThemeMode,
    audioQuality: AudioQuality,
    notificationsEnabled: Boolean,
    onOpenPlaylistDetail: (Playlist) -> Unit,
    onOpenReciterProfile: (Reciter) -> Unit,
    onAddToPlaylist: (Surah) -> Unit,
    homeResetTrigger: Int = 0,
    libraryResetTrigger: Int = 0,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = TopLevelDestination.HOME.graphRoute,
        modifier = modifier,
    ) {
        // 1. Home Tab Graph (Home root + Search & Settings sub-pages)
        navigation(
            startDestination = TopLevelDestination.HOME.route,
            route = TopLevelDestination.HOME.graphRoute,
        ) {
            composable(TopLevelDestination.HOME.route) {
                val onSurahClick = androidx.compose.runtime.remember(viewModel) { { surah: Surah -> viewModel.playSurah(surah) } }
                val onContinueKhatma = androidx.compose.runtime.remember(viewModel) { { viewModel.continueKhatma() } }
                val onOpenSettings = androidx.compose.runtime.remember(navController) { { navController.navigateToDetail(TopLevelDestination.SETTINGS.route) } }
                val onNavigateToSearch = androidx.compose.runtime.remember(navController) { { navController.navigateToDetail(TopLevelDestination.SEARCH.route) } }
                val onNavigateToSurahs = androidx.compose.runtime.remember(navController) { { navController.navigateToDetail(TopLevelDestination.SURAHS.route) } }
                val onPlayNext = androidx.compose.runtime.remember(viewModel) { { surah: Surah -> viewModel.playNextInQueue(surah) } }
                val onAddToQueue = androidx.compose.runtime.remember(viewModel) { { surah: Surah -> viewModel.addToQueue(surah) } }
                val onRadioStationClick = androidx.compose.runtime.remember(viewModel) { { station: RadioStation -> viewModel.playRadio(station) } }
                val onNavigateToRadio = androidx.compose.runtime.remember(navController) { { navController.navigateToDetail(TopLevelDestination.RADIO.route) } }
                val radioStations by viewModel.radioStations.collectAsStateWithLifecycle()
                val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()

                HomeScreen(
                    surahs = surahs,
                    playlists = playlists,
                    khatmaProgress = khatmaProgress,
                    recentSurahs = recentSurahs,
                    reciters = reciters,
                    radioStations = radioStations,
                    currentRadioStation = playbackState.currentRadioStation,
                    isRadioPlaying = playbackState.isRadio && playbackState.isPlaying,
                    onSurahClick = onSurahClick,
                    onPlaylistClick = onOpenPlaylistDetail,
                    onContinueKhatma = onContinueKhatma,
                    onOpenKhatam = { navController.navigateToDetail(TopLevelDestination.KHATAM.route) },
                    onOpenSettings = onOpenSettings,
                    onNavigateToSearch = onNavigateToSearch,
                    onNavigateToSurahs = onNavigateToSurahs,
                    onNavigateToRadio = onNavigateToRadio,
                    onReciterClick = onOpenReciterProfile,
                    onVerseClick = { surah, ayah -> viewModel.playSurah(surah, startAyah = ayah) },
                    onPlayNext = onPlayNext,
                    onAddToQueue = onAddToQueue,
                    onAddToPlaylist = onAddToPlaylist,
                    onRadioStationClick = onRadioStationClick,
                    resetTrigger = homeResetTrigger,
                )
            }
            composable(TopLevelDestination.RADIO.route) {
                val radioStations by viewModel.radioStations.collectAsStateWithLifecycle()
                val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
                com.listenquran.ui.screens.radio.RadioScreen(
                    stations = radioStations,
                    playbackState = playbackState,
                    onStationClick = { viewModel.playRadio(it) },
                    onStopRadio = { viewModel.stopRadio() },
                    onResumeSurah = { viewModel.resumeSurahAfterRadio() },
                    onBackClick = { navController.popBackStack() },
                )
            }
            composable(TopLevelDestination.SEARCH.route) {
                SearchScreen(
                    surahs = surahs,
                    reciters = reciters,
                    onSurahClick = { viewModel.playSurah(it) },
                    onVerseClick = { surah, ayah -> viewModel.playSurah(surah, startAyah = ayah) },
                    onReciterClick = onOpenReciterProfile,
                    onBrowseSurahsClick = { navController.navigateToDetail(TopLevelDestination.SURAHS.route) },
                    onPlayNext = { viewModel.playNextInQueue(it) },
                    onAddToQueue = { viewModel.addToQueue(it) },
                    onAddToPlaylist = onAddToPlaylist,
                )
            }
            composable(TopLevelDestination.SURAHS.route) {
                val surahsViewModel: SurahsViewModel = hiltViewModel()
                SurahsScreen(
                    viewModel = surahsViewModel,
                    onBackClick = { navController.popBackStack() },
                    onSurahClick = { surah -> viewModel.playSurah(surah) },
                    onPlayNext = { surah -> viewModel.playNextInQueue(surah) },
                    onAddToQueue = { surah -> viewModel.addToQueue(surah) },
                    onAddToPlaylist = onAddToPlaylist,
                )
            }
            composable(TopLevelDestination.KHATAM.route) {
                val khatamViewModel: com.listenquran.ui.screens.khatam.KhatamViewModel = hiltViewModel()
                val surahs by viewModel.surahs.collectAsStateWithLifecycle()
                com.listenquran.ui.screens.khatam.KhatamScreen(
                    viewModel = khatamViewModel,
                    onBackClick = { navController.popBackStack() },
                    onContinueAyah = { surahNumber, ayahNumber ->
                        val targetSurah = surahs.find { it.id == surahNumber }
                        if (targetSurah != null) {
                            viewModel.playSurah(targetSurah, startAyah = ayahNumber)
                        }
                    }
                )
            }
            composable(TopLevelDestination.SETTINGS.route) {
                val downloadedSurahs by viewModel.downloadedSurahs.collectAsStateWithLifecycle()
                val defaultReciter by viewModel.defaultReciter.collectAsStateWithLifecycle()
                SettingsScreen(
                    currentTheme = themeMode,
                    currentQuality = audioQuality,
                    statsSummary = statsSummary,
                    notificationsEnabled = notificationsEnabled,
                    defaultReciter = defaultReciter,
                    onOpenReciters = { navController.navigateToTab(TopLevelDestination.RECITERS) },
                    downloadedSurahs = downloadedSurahs,
                    onSelectTheme = { viewModel.setThemeMode(it) },
                    onSelectQuality = { viewModel.setAudioQuality(it) },
                    onToggleNotifications = { viewModel.setNotificationsEnabled(it) },
                    onExportStats = { viewModel.exportStats() },
                    onImportStats = { viewModel.importStats(it) },
                    onExportPlaylists = { viewModel.exportPlaylists() },
                    onImportPlaylists = { viewModel.importPlaylists(it) },
                    onExportPlaylistsToUri = { viewModel.exportPlaylistsToUri(it) },
                    onImportPlaylistsFromUri = { viewModel.importPlaylistsFromUri(it) },
                    onExportStatsToUri = { viewModel.exportStatsToUri(it) },
                    onImportStatsFromUri = { viewModel.importStatsFromUri(it) },
                    onDeleteDownloadedSurah = { surahNumber, reciterId ->
                        viewModel.deleteDownloadedSurah(surahNumber, reciterId)
                    },
                    onClearAllDownloads = { viewModel.clearAllDownloads() },
                    onClearAudioCache = { viewModel.clearAudioCache() },
                    onBack = { navController.popBackStack() },
                )
            }
        }

        // 2. Reciters Tab Graph (Reciters root)
        navigation(
            startDestination = TopLevelDestination.RECITERS.route,
            route = TopLevelDestination.RECITERS.graphRoute,
        ) {
            composable(TopLevelDestination.RECITERS.route) {
                val recitersSearchQuery by viewModel.recitersSearchQuery.collectAsStateWithLifecycle()
                RecitersScreen(
                    pagedReciters = viewModel.pagedReciters,
                    searchQuery = recitersSearchQuery,
                    onSearchQueryChange = { viewModel.setRecitersSearchQuery(it) },
                    onReciterClick = onOpenReciterProfile,
                    onToggleFavorite = { viewModel.toggleFavoriteReciter(it) },
                    onRefresh = { viewModel.refreshReciters() }
                )
            }
        }

        // 3. Library Tab Graph (Library root)
        navigation(
            startDestination = TopLevelDestination.LIBRARY.route,
            route = TopLevelDestination.LIBRARY.graphRoute,
        ) {
            composable(TopLevelDestination.LIBRARY.route) {
                LibraryScreen(
                    playlists = playlists,
                    bookmarks = bookmarks,
                    favoriteReciters = reciters.filter { it.isFavorite }.distinctBy { it.name.replace(" ", "") },
                    surahs = surahs,
                    onPlaylistClick = onOpenPlaylistDetail,
                    onCreatePlaylist = { name, desc ->
                        viewModel.createPlaylist(name, desc)
                    },
                    onBookmarkClick = { bookmark ->
                        surahs.find { it.id == bookmark.surahNumber }?.let {
                            viewModel.playSurah(it, startAyah = bookmark.ayahNumber)
                        }
                    },
                    onUpdateBookmarkNote = { id, note ->
                        viewModel.updateBookmarkNote(id, note)
                    },
                    onReciterClick = onOpenReciterProfile,
                    onStartHifzLoop = { config ->
                        viewModel.startHifzLoop(config)
                    },
                    onSetSleepTimer = { minutes ->
                        viewModel.setSleepTimer(minutes)
                    },
                    onSetSleepTimerUntilTime = { hour, minute ->
                        viewModel.setSleepTimerUntilTime(hour, minute)
                    },
                    resetTrigger = libraryResetTrigger,
                )
            }
        }
    }
}
