package com.listenquran.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.listenquran.domain.model.*
import com.listenquran.ui.MainViewModel
import com.listenquran.ui.screens.home.HomeScreen
import com.listenquran.ui.screens.library.LibraryScreen
import com.listenquran.ui.screens.reciters.RecitersScreen
import com.listenquran.ui.screens.search.SearchScreen
import com.listenquran.ui.screens.settings.SettingsScreen

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
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = TopLevelDestination.HOME.route,
        modifier = modifier,
    ) {
        composable(TopLevelDestination.HOME.route) {
            val onSurahClick = androidx.compose.runtime.remember(viewModel) { { surah: Surah -> viewModel.playSurah(surah) } }
            val onContinueKhatma = androidx.compose.runtime.remember(viewModel) { { viewModel.continueKhatma() } }
            val onOpenSettings = androidx.compose.runtime.remember(navController) { { navController.navigate(TopLevelDestination.SETTINGS.route) } }
            val onNavigateToSearch = androidx.compose.runtime.remember(navController) { { navController.navigate(TopLevelDestination.SEARCH.route) } }
            val onPlayNext = androidx.compose.runtime.remember(viewModel) { { surah: Surah -> viewModel.playNextInQueue(surah) } }
            val onAddToQueue = androidx.compose.runtime.remember(viewModel) { { surah: Surah -> viewModel.addToQueue(surah) } }
            val onRadioStationClick = androidx.compose.runtime.remember(viewModel) { { station: RadioStation -> viewModel.playRadio(station) } }

            HomeScreen(
                surahs = surahs,
                playlists = playlists,
                khatmaProgress = khatmaProgress,
                recentSurahs = recentSurahs,
                onSurahClick = onSurahClick,
                onPlaylistClick = onOpenPlaylistDetail,
                onContinueKhatma = onContinueKhatma,
                onOpenSettings = onOpenSettings,
                onNavigateToSearch = onNavigateToSearch,
                onPlayNext = onPlayNext,
                onAddToQueue = onAddToQueue,
                onRadioStationClick = onRadioStationClick,
            )
        }
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
        composable(TopLevelDestination.SEARCH.route) {
            SearchScreen(
                surahs = surahs,
                reciters = reciters,
                onSurahClick = { viewModel.playSurah(it) },
                onVerseClick = { surah, ayah -> viewModel.playSurah(surah, startAyah = ayah) },
                onReciterClick = onOpenReciterProfile,
                onPlayNext = { viewModel.playNextInQueue(it) },
                onAddToQueue = { viewModel.addToQueue(it) },
            )
        }
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
                onOpenReciters = { navController.navigate(TopLevelDestination.RECITERS.route) },
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
            )
        }
    }
}
