package com.listenquran.ui

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.listenquran.domain.model.Ayah
import com.listenquran.domain.model.Playlist
import com.listenquran.domain.model.Reciter
import com.listenquran.ui.components.MiniPlayer
import com.listenquran.ui.navigation.ListenQuranNavHost
import com.listenquran.ui.navigation.TopLevelDestination
import com.listenquran.ui.screens.player.*
import com.listenquran.ui.screens.playlist.AddToPlaylistSheet
import com.listenquran.ui.screens.playlist.PlaylistDetailScreen
import com.listenquran.ui.screens.reciter.ReciterProfileScreen
import com.listenquran.ui.theme.ListenQuranTheme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun ListenQuranApp(
    viewModel: MainViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val surahs by viewModel.surahs.collectAsStateWithLifecycle()
    val reciters by viewModel.reciters.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val khatmaProgress by viewModel.khatmaProgress.collectAsStateWithLifecycle()
    val statsSummary by viewModel.statsSummary.collectAsStateWithLifecycle()
    val recentSurahs by viewModel.recentSurahs.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val audioQuality by viewModel.audioQuality.collectAsStateWithLifecycle()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val isCurrentSurahLiked by viewModel.isCurrentSurahLiked.collectAsStateWithLifecycle()
    val currentVerses by viewModel.currentVerses.collectAsStateWithLifecycle()

    var showFullPlayer by remember { mutableStateOf(false) }
    var showVersesSheet by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showActionsMenuSheet by remember { mutableStateOf(false) }
    var showAddToPlaylistSheet by remember { mutableStateOf(false) }
    var selectedPlaylistForDetail by remember { mutableStateOf<Playlist?>(null) }
    var selectedReciterForProfile by remember { mutableStateOf<Reciter?>(null) }
    var ayahToShare by remember { mutableStateOf<Ayah?>(null) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }

    ListenQuranTheme(themeMode = themeMode) {
        SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                bottomBar = {
                    Column {
                        AnimatedVisibility(
                            visible = playbackState.currentSurah != null && !showFullPlayer,
                            enter = fadeIn(tween(300)) + expandVertically(tween(300)),
                            exit = fadeOut(tween(300)) + shrinkVertically(tween(300))
                        ) {
                            MiniPlayer(
                                playbackState = playbackState,
                                currentPositionProvider = { viewModel.currentPositionMs.value },
                                isLiked = isCurrentSurahLiked,
                                onTogglePlayPause = { viewModel.togglePlayPause() },
                                onToggleLike = { viewModel.toggleLikeCurrentSurah() },
                                onOpenFullPlayer = { showFullPlayer = true },
                                sharedTransitionScope = this@SharedTransitionLayout,
                                animatedVisibilityScope = this@AnimatedVisibility,
                            )
                        }

                    // M3 Navigation Bar (80dp height, active pill container)
                    NavigationBar(
                        modifier = Modifier.height(80.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 0.dp,
                    ) {
                        TopLevelDestination.values().forEach { destination ->
                            val selected = currentRoute == destination.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(destination.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    val icon = when (destination) {
                                        TopLevelDestination.HOME -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
                                        TopLevelDestination.RECITERS -> if (selected) Icons.Filled.RecordVoiceOver else Icons.Outlined.RecordVoiceOver
                                        TopLevelDestination.SEARCH -> if (selected) Icons.Filled.Search else Icons.Outlined.Search
                                        TopLevelDestination.LIBRARY -> if (selected) Icons.Rounded.Bookmarks else Icons.Outlined.Bookmarks
                                        TopLevelDestination.SETTINGS -> if (selected) Icons.Filled.Settings else Icons.Outlined.Settings
                                    }
                                    Icon(imageVector = icon, contentDescription = destination.label)
                                },
                                label = {
                                    Text(
                                        text = destination.label,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            )
                        }
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
        ) { innerPadding ->
            ListenQuranNavHost(
                navController = navController,
                viewModel = viewModel,
                surahs = surahs,
                reciters = reciters,
                playlists = playlists,
                bookmarks = bookmarks,
                khatmaProgress = khatmaProgress,
                statsSummary = statsSummary,
                recentSurahs = recentSurahs,
                themeMode = themeMode,
                audioQuality = audioQuality,
                notificationsEnabled = notificationsEnabled,
                onOpenPlaylistDetail = { selectedPlaylistForDetail = it },
                onOpenReciterProfile = { selectedReciterForProfile = it },
                modifier = Modifier.padding(innerPadding),
            )
        }

            // Full Player Modal Screen
            AnimatedVisibility(
                visible = showFullPlayer,
                enter = fadeIn(animationSpec = tween(500, easing = FastOutSlowInEasing)) +
                        slideInVertically(
                            animationSpec = tween(500, easing = FastOutSlowInEasing),
                            initialOffsetY = { it }
                        ),
                exit = fadeOut(animationSpec = tween(500, easing = FastOutSlowInEasing)) +
                        slideOutVertically(
                            animationSpec = tween(500, easing = FastOutSlowInEasing),
                            targetOffsetY = { it }
                        ),
            ) {
                FullPlayerScreen(
                    playbackState = playbackState,
                    isLiked = isCurrentSurahLiked,
                    currentPositionMsFlow = viewModel.currentPositionMs,
                    onCollapse = { showFullPlayer = false },
                    onTogglePlayPause = { viewModel.togglePlayPause() },
                    onSeekTo = { viewModel.seekTo(it) },
                    onPrevious = { viewModel.playPrevious() },
                    onNext = { viewModel.playNext() },
                    onToggleLike = { viewModel.toggleLikeCurrentSurah() },
                    onToggleShuffle = { viewModel.toggleShuffle() },
                    onToggleRepeat = { viewModel.toggleRepeat() },
                    onSetPlaybackSpeed = { viewModel.setPlaybackSpeed(it) },
                    onOpenVersesSheet = { showVersesSheet = true },
                    onOpenQueue = { showQueueSheet = true },
                    onOpenSleepTimer = { showSleepTimerDialog = true },
                    onOpenActionsMenu = { showActionsMenuSheet = true },
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this@AnimatedVisibility,
                )
            }
        }

        // Synced Scripture Bottom Sheet
        if (showVersesSheet) {
            val currentWordIndex by viewModel.currentWordIndex.collectAsStateWithLifecycle()
            LaunchedEffect(playbackState.currentSurah?.id) {
                viewModel.loadVersesForCurrentSurah()
            }
            SyncedAyahsBottomSheet(
                playbackState = playbackState,
                verses = currentVerses,
                currentWordIndex = currentWordIndex,
                reciters = reciters,
                onDismiss = { showVersesSheet = false },
                onSeekToAyah = { viewModel.seekToAyah(it) },
                onSaveBookmark = { ayah, note -> viewModel.saveBookmark(ayah, note) },
                onShareAsImage = { ayah -> ayahToShare = ayah },
                onPauseMainPlayer = {
                    if (playbackState.isPlaying) {
                        viewModel.togglePlayPause()
                    }
                },
                onSetDefaultReciter = { viewModel.setDefaultReciter(it) },
                getVerseAudioTiming = { rId, cId, vKey -> viewModel.getVerseAudioTiming(rId, cId, vKey) },
            )
        }

        // Queue Modal Bottom Sheet
        if (showQueueSheet) {
            QueueModalSheet(
                playbackState = playbackState,
                onDismiss = { showQueueSheet = false },
                onPlayQueueItem = { viewModel.playQueueItem(it) },
                onRemoveFromQueue = { viewModel.removeFromQueue(it) },
                onClearQueue = { viewModel.clearQueue() },
                onReorderQueue = { viewModel.reorderQueue(it) },
            )
        }

        // Player Actions Menu Bottom Sheet
        if (showActionsMenuSheet) {
            PlayerActionsMenuSheet(
                playbackState = playbackState,
                isLiked = isCurrentSurahLiked,
                onToggleLike = { viewModel.toggleLikeCurrentSurah() },
                onDismiss = { showActionsMenuSheet = false },
                onPlayNextInQueue = {
                    playbackState.currentSurah?.let { viewModel.playNextInQueue(it) }
                },
                onAddToQueue = {
                    playbackState.currentSurah?.let { viewModel.addToQueue(it) }
                },
                onShareSurah = {
                    playbackState.currentSurah?.let { surah ->
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, "Listen to Surah ${surah.nameSimple} on Listen Quran: https://quran.com/${surah.id}")
                            type = "text/plain"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, "Share Surah")
                        context.startActivity(shareIntent)
                    }
                },
                onAddToPlaylist = { showAddToPlaylistSheet = true },
                onOpenQueue = { showQueueSheet = true },
                onGoToReciter = {
                    playbackState.currentReciter?.let {
                        selectedReciterForProfile = it
                    }
                },
                onGoToReciterRadio = {
                    playbackState.currentReciter?.let {
                        viewModel.playReciterRadio(it)
                    }
                },
                onOpenSleepTimer = { showSleepTimerDialog = true }
            )
        }

        // Add to Playlist Sheet
        if (showAddToPlaylistSheet && playbackState.currentSurah != null) {
            AddToPlaylistSheet(
                surah = playbackState.currentSurah!!,
                playlists = playlists,
                onDismiss = { showAddToPlaylistSheet = false },
                onSelectPlaylist = { pl ->
                    viewModel.addToPlaylist(pl.id, playbackState.currentSurah!!)
                },
                onCreateAndAdd = { name ->
                    viewModel.createAndAddToPlaylist(name, playbackState.currentSurah!!)
                }
            )
        }

        // Playlist Detail Full Screen
        if (selectedPlaylistForDetail != null) {
            val pl = selectedPlaylistForDetail!!
            val items by viewModel.getPlaylistItems(pl.id).collectAsStateWithLifecycle(emptyList())
            PlaylistDetailScreen(
                playlist = pl,
                items = items,
                onBack = { selectedPlaylistForDetail = null },
                onPlayTrack = { viewModel.playPlaylistItem(it) },
                onPlayAll = { viewModel.playAllPlaylist(items) },
                onDeleteItem = { viewModel.deletePlaylistItem(it) },
                onDeletePlaylist = {
                    viewModel.deletePlaylist(pl.id)
                    selectedPlaylistForDetail = null
                },
                onReorderItems = { viewModel.reorderPlaylistItems(it) }
            )
        }

        // Reciter Profile Full Screen
        if (selectedReciterForProfile != null) {
            val currentSelected = selectedReciterForProfile!!
            val rec = reciters.find { it.id == currentSelected.id } ?: currentSelected
            ReciterProfileScreen(
                reciter = rec,
                surahs = surahs,
                onBack = { selectedReciterForProfile = null },
                onPlaySurah = { s, r -> viewModel.playSurah(s, r) },
                onPlayAll = { viewModel.playAllWithReciter(it) },
                onToggleFavorite = { reciterToToggle ->
                    viewModel.toggleFavoriteReciter(reciterToToggle)
                    selectedReciterForProfile = reciterToToggle.copy(isFavorite = !reciterToToggle.isFavorite)
                },
                onStartRadio = { viewModel.playReciterRadio(it) }
            )
        }

        // Share Ayah as Image Dialog
        if (ayahToShare != null) {
            val surahName = playbackState.currentSurah?.nameSimple ?: ""
            ShareAyahImageDialog(
                ayah = ayahToShare!!,
                surahName = surahName,
                onDismiss = { ayahToShare = null }
            )
        }

        // Sleep Timer Selection Dialog
        if (showSleepTimerDialog) {
            AlertDialog(
                onDismissRequest = { showSleepTimerDialog = false },
                title = { Text("Sleep Timer") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(
                            0 to "Turn off timer",
                            15 to "15 minutes",
                            30 to "30 minutes",
                            45 to "45 minutes",
                            60 to "1 hour",
                            120 to "2 hours",
                            180 to "3 hours"
                        ).forEach { (mins, label) ->
                            TextButton(
                                onClick = {
                                    viewModel.setSleepTimer(mins)
                                    showSleepTimerDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSleepTimerDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
