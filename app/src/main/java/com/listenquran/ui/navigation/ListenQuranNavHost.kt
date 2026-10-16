package com.listenquran.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.listenquran.ui.MainViewModel
import com.listenquran.ui.screens.home.HomeScreen
import com.listenquran.ui.screens.library.LibraryScreen
import com.listenquran.ui.screens.search.SearchScreen
import com.listenquran.ui.screens.settings.SettingsScreen

@Composable
fun ListenQuranNavHost(
    navController: NavHostController,
    viewModel: MainViewModel,
    surahs: List<com.listenquran.domain.model.Surah>,
    reciters: List<com.listenquran.domain.model.Reciter>,
    playlists: List<com.listenquran.domain.model.Playlist>,
    bookmarks: List<com.listenquran.domain.model.Bookmark>,
    khatmaProgress: com.listenquran.domain.model.KhatmaProgress,
    statsSummary: com.listenquran.domain.model.ListeningStatsSummary,
    themeMode: com.listenquran.domain.model.ThemeMode,
    audioQuality: com.listenquran.domain.model.AudioQuality,
    notificationsEnabled: Boolean,
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
                onSurahClick = { viewModel.playSurah(it) },
                onReciterClick = { reciter ->
                    surahs.firstOrNull()?.let { viewModel.playSurah(it, reciter) }
                },
                onRadioClick = { viewModel.playRadio(it) },
                onPlaylistClick = { playlist ->
                    val firstItem = playlist.items.firstOrNull()
                    if (firstItem != null) {
                        surahs.find { it.id == firstItem.surahNumber }?.let {
                            viewModel.playSurah(it)
                        }
                    } else {
                        surahs.firstOrNull()?.let { viewModel.playSurah(it) }
                    }
                },
                onContinueKhatma = { viewModel.continueKhatma() },
                onOpenSettings = { navController.navigate(TopLevelDestination.SETTINGS.route) },
            )
        }
        composable(TopLevelDestination.SEARCH.route) {
            SearchScreen(
                surahs = surahs,
                reciters = reciters,
                onSurahClick = { viewModel.playSurah(it) },
                onReciterClick = { reciter ->
                    surahs.firstOrNull()?.let { viewModel.playSurah(it, reciter) }
                },
            )
        }
        composable(TopLevelDestination.LIBRARY.route) {
            LibraryScreen(
                playlists = playlists,
                bookmarks = bookmarks,
                favoriteReciters = reciters.filter { it.isFavorite },
                surahs = surahs,
                onPlaylistClick = { playlist ->
                    surahs.firstOrNull()?.let { viewModel.playSurah(it) }
                },
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
                onReciterClick = { reciter ->
                    surahs.firstOrNull()?.let { viewModel.playSurah(it, reciter) }
                },
                onStartHifzLoop = { config ->
                    viewModel.startHifzLoop(config)
                },
                onSetSleepTimer = { minutes ->
                    viewModel.setSleepTimer(minutes)
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
                onExportPlaylists = { /* Export */ },
            )
        }
    }
}
