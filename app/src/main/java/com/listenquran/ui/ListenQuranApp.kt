package com.listenquran.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.listenquran.domain.model.Ayah
import com.listenquran.domain.model.Playlist
import com.listenquran.domain.model.Reciter
import com.listenquran.domain.model.Surah
import com.listenquran.ui.components.LocalMiniPlayerInset
import com.listenquran.ui.components.MiniPlayer
import com.listenquran.ui.navigation.ListenQuranNavHost
import com.listenquran.ui.navigation.TopLevelDestination
import com.listenquran.ui.navigation.navigateToTab
import com.listenquran.ui.screens.player.*
import com.listenquran.ui.screens.playlist.AddToPlaylistSheet
import com.listenquran.ui.screens.playlist.PlaylistDetailScreen
import com.listenquran.ui.screens.reciter.ReciterProfileScreen
import com.listenquran.ui.theme.ListenQuranTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

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
    val likedSurahs by viewModel.likedSurahs.collectAsStateWithLifecycle()
    val khatmaProgress by viewModel.khatmaProgress.collectAsStateWithLifecycle()
    val statsSummary by viewModel.statsSummary.collectAsStateWithLifecycle()
    val recentSurahs by viewModel.recentSurahs.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val audioQuality by viewModel.audioQuality.collectAsStateWithLifecycle()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val queueState by viewModel.queueState.collectAsStateWithLifecycle()
    val isCurrentSurahLiked by viewModel.isCurrentSurahLiked.collectAsStateWithLifecycle()
    val currentVerses by viewModel.currentVerses.collectAsStateWithLifecycle()
    val defaultReciterId by viewModel.defaultReciterId.collectAsStateWithLifecycle()
    val sleepTimerState by viewModel.sleepTimerState.collectAsStateWithLifecycle()
    val customSleepTimerMinutes by viewModel.customSleepTimerMinutes.collectAsStateWithLifecycle()

    var showFullPlayer by remember { mutableStateOf(false) }
    var showVersesSheet by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showActionsMenuSheet by remember { mutableStateOf(false) }
    var showAddToPlaylistSheet by remember { mutableStateOf(false) }
    var surahToAddToPlaylist by remember { mutableStateOf<Surah?>(null) }
    var selectedPlaylistForDetail by remember { mutableStateOf<Playlist?>(null) }
    var selectedReciterForProfile by remember { mutableStateOf<Reciter?>(null) }
    var ayahToShare by remember { mutableStateOf<Ayah?>(null) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }

    var homeResetTrigger by remember { mutableIntStateOf(0) }
    var libraryResetTrigger by remember { mutableIntStateOf(0) }

    var backPressedOnce by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.snackbarEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val hasAnyOverlayOpen = showFullPlayer ||
            selectedPlaylistForDetail != null ||
            selectedReciterForProfile != null ||
            ayahToShare != null ||
            showSleepTimerDialog ||
            showAddToPlaylistSheet ||
            surahToAddToPlaylist != null ||
            showActionsMenuSheet ||
            showQueueSheet ||
            showVersesSheet

    BackHandler(enabled = surahToAddToPlaylist != null) {
        surahToAddToPlaylist = null
    }

    // Priority 1: Dismiss Reciter Profile overlay if open
    BackHandler(enabled = selectedReciterForProfile != null) {
        selectedReciterForProfile = null
    }

    // Priority 2: Dismiss Playlist Detail overlay if open
    BackHandler(enabled = selectedPlaylistForDetail != null && selectedReciterForProfile == null) {
        selectedPlaylistForDetail = null
    }

    // Priority 3: Collapse Full Player to Mini Player if open
    BackHandler(enabled = showFullPlayer && selectedReciterForProfile == null && selectedPlaylistForDetail == null) {
        showFullPlayer = false
    }

    // Priority 4: Back handling when no overlay is open
    BackHandler(enabled = !hasAnyOverlayOpen) {
        val currentDestination = navBackStackEntry?.destination
        val isAtHomeRoot = currentDestination?.route == TopLevelDestination.HOME.route
        if (!isAtHomeRoot && navController.previousBackStackEntry != null) {
            navController.popBackStack()
        } else if (!isAtHomeRoot) {
            navController.navigateToTab(TopLevelDestination.HOME)
        } else {
            if (backPressedOnce) {
                context.findActivity()?.finish()
            } else {
                backPressedOnce = true
                Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
                coroutineScope.launch {
                    delay(2000)
                    backPressedOnce = false
                }
            }
        }
    }

    val currentDestination = navBackStackEntry?.destination
    val isAtTabRoot = currentDestination?.let { dest ->
        TopLevelDestination.bottomNavDestinations.any { tab ->
            dest.route == tab.route
        }
    } ?: true
    val isDetailOverlayOpen = selectedPlaylistForDetail != null || selectedReciterForProfile != null
    val showBottomBar = isAtTabRoot && !isDetailOverlayOpen

    val isImeVisible = WindowInsets.ime.asPaddingValues().calculateBottomPadding() > 0.dp
    val hasActivePlayback = playbackState.currentSurah != null || playbackState.isRadio
    val isMiniPlayerVisible = hasActivePlayback && !showFullPlayer && !isImeVisible

    ListenQuranTheme(themeMode = themeMode) {
        SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (!showBottomBar) Modifier.navigationBarsPadding()
                                else Modifier
                            )
                    ) {
                        AnimatedVisibility(
                            visible = isMiniPlayerVisible,
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
                                onStopRadio = { viewModel.stopRadio() },
                                onStopHifz = { viewModel.stopHifzLoop() },
                                sharedTransitionScope = this@SharedTransitionLayout,
                                animatedVisibilityScope = this@AnimatedVisibility,
                            )
                        }

                        AnimatedVisibility(
                            visible = showBottomBar,
                            enter = fadeIn(tween(250)) + expandVertically(tween(250)),
                            exit = fadeOut(tween(250)) + shrinkVertically(tween(250))
                        ) {
                            NavigationBar(
                                modifier = Modifier.height(80.dp),
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                tonalElevation = 0.dp,
                            ) {
                                TopLevelDestination.bottomNavDestinations.forEach { destination ->
                                    val selected = currentDestination?.hierarchy?.any {
                                        it.route == destination.graphRoute || it.route == destination.route
                                    } == true
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = {
                                            // Dismiss all open modal overlays & sheets so the tapped tab is immediately visible
                                            selectedPlaylistForDetail = null
                                            selectedReciterForProfile = null
                                            showFullPlayer = false
                                            showVersesSheet = false
                                            showQueueSheet = false
                                            showActionsMenuSheet = false
                                            showAddToPlaylistSheet = false
                                            showSleepTimerDialog = false
                                            ayahToShare = null

                                            if (destination == TopLevelDestination.HOME) {
                                                homeResetTrigger++
                                            }
                                            if (destination == TopLevelDestination.LIBRARY) {
                                                libraryResetTrigger++
                                            }

                                            if (selectedPlaylistForDetail != null) {
                                                selectedPlaylistForDetail = null
                                            }
                                            if (selectedReciterForProfile != null) {
                                                selectedReciterForProfile = null
                                            }
                                            navController.navigateToTab(destination) {
                                                if (destination == TopLevelDestination.HOME) {
                                                    homeResetTrigger++
                                                }
                                                if (destination == TopLevelDestination.LIBRARY) {
                                                    libraryResetTrigger++
                                                }
                                            }
                                        },
                                        icon = {
                                            val icon = when (destination) {
                                                TopLevelDestination.HOME -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
                                                TopLevelDestination.RECITERS -> if (selected) Icons.Filled.RecordVoiceOver else Icons.Outlined.RecordVoiceOver
                                                TopLevelDestination.SEARCH -> if (selected) Icons.Filled.Search else Icons.Outlined.Search
                                                TopLevelDestination.LIBRARY -> if (selected) Icons.Rounded.Bookmarks else Icons.Outlined.Bookmarks
                                                TopLevelDestination.SETTINGS -> if (selected) Icons.Filled.Settings else Icons.Outlined.Settings
                                                TopLevelDestination.SURAHS -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
                                                TopLevelDestination.RADIO -> Icons.Rounded.Radio
                                                TopLevelDestination.KHATAM -> Icons.Filled.Home
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
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
            ) { innerPadding ->
                val measuredBottomInset = innerPadding.calculateBottomPadding()
                CompositionLocalProvider(
                    LocalMiniPlayerInset provides measuredBottomInset
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        ListenQuranNavHost(
                            navController = navController,
                            viewModel = viewModel,
                            surahs = surahs,
                            reciters = reciters,
                            playlists = playlists,
                            bookmarks = bookmarks,
                            likedSurahs = likedSurahs,
                            khatmaProgress = khatmaProgress,
                            statsSummary = statsSummary,
                            recentSurahs = recentSurahs,
                            themeMode = themeMode,
                            audioQuality = audioQuality,
                            notificationsEnabled = notificationsEnabled,
                            onOpenPlaylistDetail = { selectedPlaylistForDetail = it },
                            onOpenReciterProfile = { selectedReciterForProfile = it },
                            onAddToPlaylist = { surahToAddToPlaylist = it },
                            homeResetTrigger = homeResetTrigger,
                            libraryResetTrigger = libraryResetTrigger,
                            modifier = Modifier.fillMaxSize(),
                        )

                        // Playlist Detail Full Screen inside Scaffold content so MiniPlayer in bottomBar stays visible
                        if (selectedPlaylistForDetail != null) {
                            val pl = selectedPlaylistForDetail!!
                            val items by viewModel.getPlaylistItems(pl.id).collectAsStateWithLifecycle(emptyList())
                            PlaylistDetailScreen(
                                playlist = pl,
                                items = items,
                                onBack = { selectedPlaylistForDetail = null },
                                onPlayTrack = { viewModel.playPlaylistItem(it, items, pl.name) },
                                onPlayAll = { viewModel.playAllPlaylist(items, pl.name) },
                                onDeleteItem = { viewModel.deletePlaylistItem(it) },
                                onDeletePlaylist = {
                                    viewModel.deletePlaylist(pl.id)
                                    selectedPlaylistForDetail = null
                                },
                                onReorderItems = { viewModel.reorderPlaylistItems(it) },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Reciter Profile Full Screen inside Scaffold content so MiniPlayer in bottomBar stays visible
                        if (selectedReciterForProfile != null) {
                            val currentSelected = selectedReciterForProfile!!
                            val rec = reciters.find { it.id == currentSelected.id } ?: currentSelected
                            ReciterProfileScreen(
                                reciter = rec,
                                allReciters = reciters,
                                surahs = surahs,
                                defaultReciterId = defaultReciterId,
                                onBack = { selectedReciterForProfile = null },
                                onPlaySurah = { s, r -> viewModel.playSurah(s, r) },
                                onPlayAll = { viewModel.playAllWithReciter(it) },
                                onToggleFavorite = { reciterToToggle ->
                                    viewModel.toggleFavoriteReciter(reciterToToggle)
                                    selectedReciterForProfile = reciterToToggle.copy(isFavorite = !reciterToToggle.isFavorite)
                                },
                                onSetDefaultReciter = { viewModel.setDefaultReciter(it) },
                                onStartRadio = { viewModel.playReciterRadio(it) },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
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
                    queueState = queueState,
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
                    onStopRadio = { viewModel.stopRadio() },
                    onResumeSurah = { viewModel.resumeSurahAfterRadio() },
                    onRetryRadio = {
                        playbackState.currentRadioStation?.let { viewModel.playRadio(it) }
                    },
                    onStopHifz = { viewModel.stopHifzLoop() },
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this@AnimatedVisibility,
                )
            }
        }

        // Synced Scripture Bottom Sheet
        if (showVersesSheet) {
            val currentWordIndex by viewModel.currentWordIndex.collectAsStateWithLifecycle()
            val isWordByWordEnabled by viewModel.isWordByWordEnabled.collectAsStateWithLifecycle()
            val isTranslationEnabled by viewModel.isTranslationEnabled.collectAsStateWithLifecycle()
            val translationState by viewModel.translationState.collectAsStateWithLifecycle()

            LaunchedEffect(playbackState.currentSurah?.id) {
                viewModel.loadVersesForCurrentSurah()
            }
            SyncedAyahsBottomSheet(
                playbackState = playbackState,
                verses = currentVerses,
                currentWordIndex = currentWordIndex,
                reciters = reciters,
                isWordByWordEnabled = isWordByWordEnabled,
                onToggleWordByWord = { viewModel.toggleWordByWord() },
                isTranslationEnabled = isTranslationEnabled,
                onToggleTranslation = { viewModel.toggleTranslation() },
                translationState = translationState,
                onRetryTranslation = { viewModel.retryLoadTranslation() },
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
                queueState = queueState,
                playbackState = playbackState,
                onDismiss = { showQueueSheet = false },
                onSeekToQueueItem = { viewModel.playQueueItem(it) },
                onRemoveFromQueue = { viewModel.removeFromQueue(it) },
                onClearQueue = { viewModel.clearQueue() },
                onMoveQueueItem = { from, to -> viewModel.moveQueueItem(from, to) },
            )
        }

        // Player Actions Menu Bottom Sheet
        if (showActionsMenuSheet) {
            val currentAyahBookmarked = bookmarks.any {
                it.surahNumber == playbackState.currentSurah?.id && it.ayahNumber == playbackState.currentAyahNumber
            }
            PlayerActionsMenuSheet(
                playbackState = playbackState,
                isLiked = isCurrentSurahLiked,
                onToggleLike = { viewModel.toggleLikeCurrentSurah() },
                isAyahBookmarked = currentAyahBookmarked,
                onToggleBookmarkAyah = {
                    val surah = playbackState.currentSurah
                    val ayahNum = playbackState.currentAyahNumber
                    if (surah != null) {
                        val existing = bookmarks.find { it.surahNumber == surah.id && it.ayahNumber == ayahNum }
                        if (existing != null) {
                            viewModel.deleteBookmark(existing)
                            coroutineScope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = "Removed bookmark ${surah.nameSimple} $ayahNum",
                                    actionLabel = "Undo",
                                    duration = SnackbarDuration.Short
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    viewModel.restoreBookmark(existing)
                                }
                            }
                        } else {
                            coroutineScope.launch {
                                val saved = viewModel.bookmarkCurrentAyah()
                                if (saved != null) {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Bookmarked ${surah.nameSimple} $ayahNum",
                                        actionLabel = "Undo",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.deleteBookmark(surah.id, ayahNum)
                                    }
                                }
                            }
                        }
                    }
                },
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
                onAddToPlaylist = {
                    showActionsMenuSheet = false
                    showAddToPlaylistSheet = true
                },
                onOpenQueue = {
                    showActionsMenuSheet = false
                    showQueueSheet = true
                },
                onGoToReciter = {
                    playbackState.currentReciter?.let {
                        showActionsMenuSheet = false
                        showFullPlayer = false
                        selectedReciterForProfile = it
                    }
                },
                onGoToReciterRadio = {
                    playbackState.currentReciter?.let {
                        showActionsMenuSheet = false
                        viewModel.playReciterRadio(it)
                    }
                },
                onOpenSleepTimer = {
                    showActionsMenuSheet = false
                    showSleepTimerDialog = true
                }
            )
        }

        // Add to Playlist Sheet
        val targetSurahForPlaylist = surahToAddToPlaylist ?: if (showAddToPlaylistSheet) playbackState.currentSurah else null
        if (targetSurahForPlaylist != null) {
            AddToPlaylistSheet(
                surah = targetSurahForPlaylist,
                playlists = playlists,
                onDismiss = {
                    surahToAddToPlaylist = null
                    showAddToPlaylistSheet = false
                },
                onSelectPlaylist = { pl ->
                    viewModel.addToPlaylist(pl.id, targetSurahForPlaylist)
                    surahToAddToPlaylist = null
                    showAddToPlaylistSheet = false
                },
                onCreateAndAdd = { name ->
                    viewModel.createAndAddToPlaylist(name, targetSurahForPlaylist)
                    surahToAddToPlaylist = null
                    showAddToPlaylistSheet = false
                }
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

        // Sleep Timer Bottom Sheet
        if (showSleepTimerDialog) {
            SleepTimerBottomSheet(
                sleepTimerState = sleepTimerState,
                isRadio = playbackState.isRadio,
                repeatMode = playbackState.repeatMode,
                customDefaultMinutes = customSleepTimerMinutes,
                onSelectMode = { mode ->
                    viewModel.setSleepTimerMode(mode)
                },
                onSaveCustomMinutes = { mins ->
                    viewModel.saveCustomSleepTimerMinutes(mins)
                },
                onDismiss = { showSleepTimerDialog = false }
            )
        }
    }
}
