package com.listenquran.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.listenquran.domain.model.*
import com.listenquran.ui.MainViewModel
import com.listenquran.ui.screens.home.HomeScreen
import com.listenquran.ui.screens.library.LibraryScreen
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
            HomeScreen(
                surahs = surahs,
                reciters = reciters,
                playlists = playlists,
                radioStations = viewModel.radioStations,
                khatmaProgress = khatmaProgress,
                recentSurahs = recentSurahs,
                onSurahClick = { viewModel.playSurah(it) },
                onReciterClick = onOpenReciterProfile,
                onRadioClick = { viewModel.playRadio(it) },
                onPlaylistClick = onOpenPlaylistDetail,
                onContinueKhatma = { viewModel.continueKhatma() },
                onOpenSettings = { navController.navigate(TopLevelDestination.SETTINGS.route) },
                onNavigateToSearch = { navController.navigate(TopLevelDestination.SEARCH.route) },
                onPlayNext = { viewModel.playNextInQueue(it) },
                onAddToQueue = { viewModel.addToQueue(it) },
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
                favoriteReciters = reciters.filter { it.isFavorite },
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
            SettingsScreen(
                currentTheme = themeMode,
                currentQuality = audioQuality,
                statsSummary = statsSummary,
                notificationsEnabled = notificationsEnabled,
                onSelectTheme = { viewModel.setThemeMode(it) },
                onSelectQuality = { viewModel.setAudioQuality(it) },
                onToggleNotifications = { viewModel.setNotificationsEnabled(it) },
                onExportStats = { viewModel.exportStats() },
                onImportStats = { viewModel.importStats(it) },
                onExportPlaylists = { viewModel.exportPlaylists() },
                onImportPlaylists = { viewModel.importPlaylists(it) },
                onClearAudioCache = { viewModel.clearAudioCache() },
            )
        }
    }
}
