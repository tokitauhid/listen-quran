package com.listenquran.ui.screens.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.listenquran.domain.model.*
import com.listenquran.ui.components.LocalMiniPlayerInset
import com.listenquran.ui.components.PlaylistArtworkMosaic
import com.listenquran.ui.theme.AmiriQuranFont
import com.listenquran.util.ReciterImages
import kotlinx.coroutines.launch

private enum class LibraryDetailView {
    PLAYLISTS,
    BOOKMARKS,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    playlists: List<Playlist>,
    bookmarks: List<Bookmark>,
    favoriteReciters: List<Reciter>,
    surahs: List<Surah>,
    repetitionPresets: List<RepetitionPreset> = emptyList(),
    likedSurahs: List<LikedSurah> = emptyList(),
    isTranslationEnabled: Boolean = false,
    onPlaylistClick: (Playlist) -> Unit,
    onCreatePlaylist: (String, String) -> Unit,
    onRenamePlaylist: ((Long, String) -> Unit)? = null,
    onDeletePlaylist: ((Long) -> Unit)? = null,
    onBookmarkClick: (Bookmark) -> Unit,
    onPlayBookmark: ((Bookmark) -> Unit)? = null,
    onDeleteBookmark: ((Bookmark) -> Unit)? = null,
    onRestoreBookmark: ((Bookmark) -> Unit)? = null,
    onUpdateBookmarkNote: (Long, String) -> Unit,
    onReciterClick: (Reciter) -> Unit,
    onStartHifzLoop: (HifzLoopConfig) -> Unit,
    onSaveRepetitionPreset: ((RepetitionPreset) -> Unit)? = null,
    onDeleteRepetitionPreset: ((Long) -> Unit)? = null,
    onNavigateToReciters: (() -> Unit)? = null,
    onNavigateToSurahs: (() -> Unit)? = null,
    resetTrigger: Int = 0,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var activeDetailView by remember { mutableStateOf<LibraryDetailView?>(null) }

    // Dialog & sheet states
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var playlistToRename by remember { mutableStateOf<Playlist?>(null) }
    var playlistToDelete by remember { mutableStateOf<Playlist?>(null) }
    var bookmarkToEditNote by remember { mutableStateOf<Bookmark?>(null) }
    var bookmarkToDelete by remember { mutableStateOf<Bookmark?>(null) }
    var showRepetitionSheet by remember { mutableStateOf(false) }
    var repetitionToEdit by remember { mutableStateOf<RepetitionPreset?>(null) }

    // Re-tapping the active Library tab smoothly scrolls to top
    LaunchedEffect(resetTrigger) {
        if (resetTrigger > 0) {
            if (activeDetailView != null) {
                activeDetailView = null
            } else {
                listState.animateScrollToItem(0)
            }
        }
    }

    // Back handling: pop detail view before exiting
    BackHandler(enabled = activeDetailView != null) {
        activeDetailView = null
    }

    val customPlaylists = remember(playlists) { playlists.filter { !it.isLikedCollection } }

    val miniPlayerInset = LocalMiniPlayerInset.current

    // Full screen "See all" page for Playlists, Bookmarks, or Liked
    if (activeDetailView != null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = when (activeDetailView) {
                                LibraryDetailView.PLAYLISTS -> "All Playlists"
                                LibraryDetailView.BOOKMARKS -> "All Bookmarks"
                                null -> ""
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { activeDetailView = null }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Library"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = modifier
        ) { innerPadding ->
            when (activeDetailView) {
                LibraryDetailView.PLAYLISTS -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 140.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 16.dp,
                            bottom = miniPlayerInset + 16.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            NewPlaylistTile(onClick = { showCreatePlaylistDialog = true })
                        }
                        items(customPlaylists, key = { it.id }) { playlist ->
                            PlaylistGridTile(
                                playlist = playlist,
                                onClick = { onPlaylistClick(playlist) },
                                onRename = { playlistToRename = playlist },
                                onDelete = { playlistToDelete = playlist }
                            )
                        }
                    }
                }
                LibraryDetailView.BOOKMARKS -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 16.dp,
                            bottom = miniPlayerInset + 16.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(bookmarks, key = { it.id }) { bookmark ->
                            BookmarkGridCard(
                                bookmark = bookmark,
                                isTranslationEnabled = isTranslationEnabled,
                                onClick = { onBookmarkClick(bookmark) },
                                onPlay = { onPlayBookmark?.invoke(bookmark) ?: onBookmarkClick(bookmark) },
                                onEditNote = { bookmarkToEditNote = bookmark },
                                onDelete = {
                                    onDeleteBookmark?.invoke(bookmark)
                                    scope.launch {
                                        val res = snackbarHostState.showSnackbar(
                                            message = "Removed bookmark ${bookmark.surahName} ${bookmark.ayahNumber}",
                                            actionLabel = "Undo",
                                            duration = SnackbarDuration.Short
                                        )
                                        if (res == SnackbarResult.ActionPerformed) {
                                            onRestoreBookmark?.invoke(bookmark)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
                null -> {}
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Library",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                actions = {
                    IconButton(
                        onClick = { showCreatePlaylistDialog = true },
                        modifier = Modifier.semantics { contentDescription = "Create new playlist" }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Playlist",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = miniPlayerInset)
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding()),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = miniPlayerInset + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // ==========================================
            // 1. SECTION: Liked Entry
            // ==========================================
            item(key = "section_liked_entry", contentType = "liked_entry") {
                val likedPlaylist = remember(playlists) {
                    playlists.find { it.isLikedCollection } ?: Playlist(id = 1L, name = "Liked Surahs", isLikedCollection = true)
                }
                val count = likedSurahs.size
                val countText = if (count == 0) {
                    "0 recitations · Tap to view liked recitations"
                } else {
                    "$count ${if (count == 1) "recitation" else "recitations"}"
                }
                val talkBackLabel = "Liked, $count items, opens liked list"

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onPlaylistClick(likedPlaylist) }
                        .semantics { contentDescription = talkBackLabel },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Color(0xFF6CDAA2), Color(0xFF005237))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Favorite,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "Liked",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = countText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // ==========================================
            // 2. SECTION: Playlists (grid)
            // ==========================================
            item(key = "section_playlists_header", contentType = "header") {
                SectionHeader(
                    title = "Playlists",
                    actionText = if (customPlaylists.size > 3) "See all" else null,
                    onActionClick = { activeDetailView = LibraryDetailView.PLAYLISTS }
                )
            }

            item(key = "section_playlists_grid", contentType = "playlists_grid") {
                // Render adaptive preview: 2 columns chunked rows
                val allPreviewTiles = listOf<Playlist?>(null) + customPlaylists.take(3)
                val rows = allPreviewTiles.chunked(2)

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    rows.forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowItems.forEach { playlist ->
                                Box(modifier = Modifier.weight(1f)) {
                                    if (playlist == null) {
                                        NewPlaylistTile(onClick = { showCreatePlaylistDialog = true })
                                    } else {
                                        PlaylistGridTile(
                                            playlist = playlist,
                                            onClick = { onPlaylistClick(playlist) },
                                            onRename = { playlistToRename = playlist },
                                            onDelete = { playlistToDelete = playlist }
                                        )
                                    }
                                }
                            }
                            if (rowItems.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // ==========================================
            // 3. SECTION: Bookmarked Ayahs (grid)
            // ==========================================
            item(key = "section_bookmarks_header", contentType = "header") {
                SectionHeader(
                    title = "Bookmarked Ayahs",
                    actionText = if (bookmarks.size > 4) "See all" else null,
                    onActionClick = { activeDetailView = LibraryDetailView.BOOKMARKS }
                )
            }

            if (bookmarks.isEmpty()) {
                item(key = "section_bookmarks_empty", contentType = "empty") {
                    EmptyStateCard(
                        icon = Icons.Outlined.BookmarkBorder,
                        title = "No bookmarks yet",
                        hint = "Tap the bookmark icon in the player or verses view while listening to save an Ayah.",
                    )
                }
            } else {
                item(key = "section_bookmarks_grid", contentType = "bookmarks_grid") {
                    val previewBookmarks = bookmarks.take(4)
                    val rows = previewBookmarks.chunked(2)

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        rows.forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                rowItems.forEach { bookmark ->
                                    Box(modifier = Modifier.weight(1f)) {
                                        BookmarkGridCard(
                                            bookmark = bookmark,
                                            isTranslationEnabled = isTranslationEnabled,
                                            onClick = { onBookmarkClick(bookmark) },
                                            onPlay = { onPlayBookmark?.invoke(bookmark) ?: onBookmarkClick(bookmark) },
                                            onEditNote = { bookmarkToEditNote = bookmark },
                                            onDelete = {
                                                onDeleteBookmark?.invoke(bookmark)
                                                scope.launch {
                                                    val res = snackbarHostState.showSnackbar(
                                                        message = "Removed bookmark ${bookmark.surahName} ${bookmark.ayahNumber}",
                                                        actionLabel = "Undo",
                                                        duration = SnackbarDuration.Short
                                                    )
                                                    if (res == SnackbarResult.ActionPerformed) {
                                                        onRestoreBookmark?.invoke(bookmark)
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                                if (rowItems.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // 4. SECTION: Repetition
            // ==========================================
            item(key = "section_repetition_header", contentType = "header") {
                SectionHeader(
                    title = "Repetition",
                    actionText = "+ New",
                    onActionClick = {
                        repetitionToEdit = null
                        showRepetitionSheet = true
                    }
                )
            }

            if (repetitionPresets.isEmpty()) {
                item(key = "section_repetition_empty", contentType = "empty") {
                    EmptyStateCard(
                        icon = Icons.Rounded.Repeat,
                        title = "No repetition presets saved",
                        hint = "Create repetition ranges to practice and memorize verses with automatic looping.",
                        actionLabel = "New Repetition",
                        onActionClick = {
                            repetitionToEdit = null
                            showRepetitionSheet = true
                        }
                    )
                }
            } else {
                items(repetitionPresets, key = { it.id }) { preset ->
                    val surahName = remember(preset.surahNumber, surahs) {
                        surahs.find { it.id == preset.surahNumber }?.nameSimple ?: "Surah ${preset.surahNumber}"
                    }
                    RepetitionPresetCard(
                        preset = preset,
                        surahName = surahName,
                        onPlay = {
                            onStartHifzLoop(
                                HifzLoopConfig(
                                    surahNumber = preset.surahNumber,
                                    startAyah = preset.startAyah,
                                    endAyah = preset.endAyah,
                                    repeatCount = preset.repeatCount,
                                    delaySeconds = preset.delaySeconds,
                                )
                            )
                        },
                        onEdit = {
                            repetitionToEdit = preset
                            showRepetitionSheet = true
                        },
                        onDelete = {
                            onDeleteRepetitionPreset?.invoke(preset.id)
                        }
                    )
                }
            }

            // ==========================================
            // 5. SECTION: Favorite Reciters
            // ==========================================
            item(key = "section_reciters_header", contentType = "header") {
                SectionHeader(
                    title = "Favorite Reciters",
                    actionText = if (favoriteReciters.size > 5) "Explore" else null,
                    onActionClick = onNavigateToReciters ?: {}
                )
            }

            if (favoriteReciters.isEmpty()) {
                item(key = "section_reciters_empty", contentType = "empty") {
                    EmptyStateCard(
                        icon = Icons.Outlined.Person,
                        title = "No favorite reciters yet",
                        hint = "Heart reciters to add them to your favorites for instant access to their recitations.",
                        actionLabel = "Browse Reciters",
                        onActionClick = onNavigateToReciters
                    )
                }
            } else {
                item(key = "section_reciters_row", contentType = "reciters_row") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(favoriteReciters, key = { it.id }) { reciter ->
                            FavoriteReciterTile(
                                reciter = reciter,
                                onClick = { onReciterClick(reciter) }
                            )
                        }
                    }
                }
            }
        }
    }

    // ==========================================
    // DIALOGS & BOTTOM SHEETS
    // ==========================================

    // 1. Create Playlist Dialog
    if (showCreatePlaylistDialog) {
        var newName by remember { mutableStateOf("") }
        var newDesc by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("New Playlist") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Playlist Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newDesc,
                        onValueChange = { newDesc = it },
                        label = { Text("Description (Optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            onCreatePlaylist(newName.trim(), newDesc.trim())
                            showCreatePlaylistDialog = false
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 2. Rename Playlist Dialog
    if (playlistToRename != null) {
        val target = playlistToRename!!
        var renameText by remember { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { playlistToRename = null },
            title = { Text("Rename Playlist") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Playlist Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameText.isNotBlank()) {
                            onRenamePlaylist?.invoke(target.id, renameText.trim())
                            playlistToRename = null
                        }
                    }
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 3. Delete Playlist Confirmation Dialog
    if (playlistToDelete != null) {
        val target = playlistToDelete!!
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            title = { Text("Delete Playlist") },
            text = { Text("Are you sure you want to delete \"${target.name}\"? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeletePlaylist?.invoke(target.id)
                        playlistToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 4. Edit Reflection Note Dialog
    if (bookmarkToEditNote != null) {
        val target = bookmarkToEditNote!!
        var noteText by remember { mutableStateOf(target.note) }
        AlertDialog(
            onDismissRequest = { bookmarkToEditNote = null },
            title = { Text("Reflection Note (${target.verseKey})") },
            text = {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Reflection (Tadabbur)") },
                    placeholder = { Text("Enter your personal reflection...") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateBookmarkNote(target.id, noteText.trim())
                        bookmarkToEditNote = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { bookmarkToEditNote = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 5. Create / Edit Repetition Preset Modal Sheet
    if (showRepetitionSheet) {
        RepetitionPresetBottomSheet(
            initialPreset = repetitionToEdit,
            surahs = surahs,
            onDismiss = {
                showRepetitionSheet = false
                repetitionToEdit = null
            },
            onSave = { preset ->
                onSaveRepetitionPreset?.invoke(preset)
                showRepetitionSheet = false
                repetitionToEdit = null
            }
        )
    }
}

// ==========================================
// COMPONENT HELPERS & TILES
// ==========================================

@Composable
private fun SectionHeader(
    title: String,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (actionText != null && onActionClick != null) {
            TextButton(
                onClick = onActionClick,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = actionText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    hint: String,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onActionClick != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onActionClick,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(text = actionLabel, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}


@Composable
private fun NewPlaylistTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Create new playlist" },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(
            1.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "New playlist",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistGridTile(
    playlist: Playlist,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            )
            .semantics {
                contentDescription = "${playlist.name}, ${playlist.items.size} recitations"
            },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                PlaylistArtworkMosaic(
                    items = playlist.items,
                    size = 56.dp
                )

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Playlist options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onRename()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            Column {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${playlist.items.size} ${if (playlist.items.size == 1) "recitation" else "recitations"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun BookmarkGridCard(
    bookmark: Bookmark,
    isTranslationEnabled: Boolean,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onEditNote: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            )
            .semantics {
                contentDescription = "Bookmarked Ayah ${bookmark.surahName} ${bookmark.verseKey}"
            },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header reference & 3-dot menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${bookmark.surahName} ${bookmark.verseKey}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Bookmark options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit Reflection") },
                            leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onEditNote()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Remove Bookmark") },
                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Arabic scripture (Amiri Quran font with >= 2.0 line-height multiplier to prevent diacritic clipping)
            Text(
                text = bookmark.textUthmani,
                fontFamily = AmiriQuranFont,
                fontSize = 18.sp,
                lineHeight = 36.sp,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            // English translation line (only rendered when translation toggle is enabled and cached)
            if (isTranslationEnabled && bookmark.translation.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "\"${bookmark.translation}\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Optional personal reflection note chip
            if (bookmark.note.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Notes,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = bookmark.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Play action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                FilledIconButton(
                    onClick = onPlay,
                    modifier = Modifier.size(32.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play from this Ayah",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RepetitionPresetCard(
    preset: RepetitionPreset,
    surahName: String,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayLabel = preset.label.ifBlank {
        val range = if (preset.startAyah == preset.endAyah) "${preset.startAyah}" else "${preset.startAyah}–${preset.endAyah}"
        val count = if (preset.repeatCount == 0) "∞" else "${preset.repeatCount}x"
        "$surahName $range ×$count"
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onPlay)
            .semantics { contentDescription = "Repetition preset: $displayLabel, tap to start" },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Repeat,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Ayahs ${preset.startAyah} to ${preset.endAyah} • ${if (preset.repeatCount == 0) "Continuous" else "${preset.repeatCount} repeats"} • ${preset.delaySeconds}s delay",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Edit preset",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Delete preset",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoriteReciterTile(
    reciter: Reciter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val photoUrl = remember(reciter) {
        ReciterImages.getPhotoUrl(reciter.id, reciter.englishName, reciter.photoUrl)
    }
    var isImageError by remember(photoUrl) { mutableStateOf(false) }
    val initials = remember(reciter.englishName) {
        val parts = reciter.englishName.trim().split(" ")
        if (parts.size >= 2) {
            "${parts[0].firstOrNull()?.uppercaseChar() ?: ""}${parts[1].firstOrNull()?.uppercaseChar() ?: ""}"
        } else {
            reciter.englishName.take(2).uppercase()
        }
    }

    Column(
        modifier = modifier
            .width(96.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Favorite reciter ${reciter.englishName}" },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF005237), Color(0xFF1D211F))
                    )
                )
                .border(
                    width = 1.5.dp,
                    color = MaterialTheme.colorScheme.tertiary,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (photoUrl != null && !isImageError) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(photoUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = reciter.englishName,
                    contentScale = ContentScale.Crop,
                    onError = { isImageError = true },
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            }
            if (photoUrl == null || isImageError) {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = reciter.englishName,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepetitionPresetBottomSheet(
    initialPreset: RepetitionPreset?,
    surahs: List<Surah>,
    onDismiss: () -> Unit,
    onSave: (RepetitionPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier
    ) {
        RepetitionEditorContent(
            initialPreset = initialPreset,
            surahs = surahs,
            onSave = onSave,
            modifier = Modifier.padding(bottom = 24.dp)
        )
    }
}

@Composable
internal fun RepetitionEditorContent(
    initialPreset: RepetitionPreset?,
    surahs: List<Surah>,
    onSave: (RepetitionPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedSurahId by remember { mutableIntStateOf(initialPreset?.surahNumber ?: 1) }
    val currentSurah = remember(selectedSurahId, surahs) {
        surahs.find { it.id == selectedSurahId } ?: surahs.firstOrNull()
    }
    val maxAyahs = currentSurah?.versesCount ?: 7

    var startAyahText by remember { mutableStateOf(initialPreset?.startAyah?.toString() ?: "1") }
    var endAyahText by remember { mutableStateOf(initialPreset?.endAyah?.toString() ?: maxAyahs.coerceAtMost(7).toString()) }
    var repeatCountText by remember { mutableStateOf(if (initialPreset?.repeatCount == 0) "" else (initialPreset?.repeatCount?.toString() ?: "10")) }
    var isInfiniteRepeat by remember { mutableStateOf(initialPreset?.repeatCount == 0) }
    var delaySeconds by remember { mutableIntStateOf(initialPreset?.delaySeconds ?: 3) }
    var customLabel by remember { mutableStateOf(initialPreset?.label ?: "") }
    var showSurahDropdown by remember { mutableStateOf(false) }

    val validation = remember(startAyahText, endAyahText, repeatCountText, maxAyahs, isInfiniteRepeat) {
        RepetitionRangeValidator.validate(
            startText = startAyahText,
            endText = endAyahText,
            repeatCountText = repeatCountText,
            maxAyahs = maxAyahs,
            isInfiniteRepeat = isInfiniteRepeat
        )
    }

    val defaultLabel = remember(currentSurah, validation.parsedStartAyah, validation.parsedEndAyah, validation.parsedRepeatCount, isInfiniteRepeat) {
        val sName = currentSurah?.nameSimple ?: "Surah $selectedSurahId"
        val start = validation.parsedStartAyah ?: 1
        val end = validation.parsedEndAyah ?: start
        val rStr = if (start == end) "$start" else "$start–$end"
        val cStr = if (isInfiniteRepeat || validation.parsedRepeatCount == 0) "∞" else "${validation.parsedRepeatCount ?: 10}x"
        "$sName $rStr ×$cStr"
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp)
            .imePadding()
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
            Text(
                text = if (initialPreset == null) "New Repetition Preset" else "Edit Repetition Preset",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Surah Selector
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Surah",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showSurahDropdown = true },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${currentSurah?.id}. ${currentSurah?.nameSimple ?: "Select Surah"} (${currentSurah?.nameArabic ?: ""})",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Select Surah",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showSurahDropdown,
                        onDismissRequest = { showSurahDropdown = false },
                        modifier = Modifier
                            .heightIn(max = 280.dp)
                            .fillMaxWidth(0.9f)
                    ) {
                        surahs.forEach { s ->
                            DropdownMenuItem(
                                text = { Text("${s.id}. ${s.nameSimple} (${s.nameArabic})") },
                                onClick = {
                                    selectedSurahId = s.id
                                    startAyahText = "1"
                                    endAyahText = s.versesCount.coerceAtMost(7).toString()
                                    showSurahDropdown = false
                                }
                            )
                        }
                    }
                }
            }

            // Ayah Range Selector: From Ayah
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "From Ayah (1 to $maxAyahs)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val cur = startAyahText.toIntOrNull() ?: 1
                            if (cur > 1) startAyahText = (cur - 1).toString()
                        },
                        enabled = (startAyahText.toIntOrNull() ?: 1) > 1
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease start ayah")
                    }
                    OutlinedTextField(
                        value = startAyahText,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() } && input.length <= 4) {
                                startAyahText = input
                            }
                        },
                        label = { Text("From Ayah") },
                        isError = validation.startError != null,
                        supportingText = {
                            Text(validation.startError ?: "1 to $maxAyahs")
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        trailingIcon = {
                            if (startAyahText.isNotEmpty()) {
                                IconButton(onClick = { startAyahText = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear start ayah")
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            val cur = startAyahText.toIntOrNull() ?: 0
                            if (cur < maxAyahs) startAyahText = (cur + 1).toString()
                        },
                        enabled = (startAyahText.toIntOrNull() ?: 0) < maxAyahs
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Increase start ayah")
                    }
                }
            }

            // Ayah Range Selector: To Ayah
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "To Ayah (optional, blank = single ayah)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val cur = endAyahText.toIntOrNull() ?: (startAyahText.toIntOrNull() ?: 1)
                            if (cur > 1) endAyahText = (cur - 1).toString()
                        },
                        enabled = (endAyahText.toIntOrNull() ?: 1) > 1
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease to ayah")
                    }
                    OutlinedTextField(
                        value = endAyahText,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() } && input.length <= 4) {
                                endAyahText = input
                            }
                        },
                        label = { Text("To Ayah") },
                        placeholder = { Text("Blank = single ayah") },
                        isError = validation.endError != null,
                        supportingText = {
                            Text(validation.endError ?: "Up to $maxAyahs")
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        trailingIcon = {
                            if (endAyahText.isNotEmpty()) {
                                IconButton(onClick = { endAyahText = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear to ayah")
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            val cur = endAyahText.toIntOrNull() ?: (startAyahText.toIntOrNull() ?: 1)
                            if (cur < maxAyahs) endAyahText = (cur + 1).toString()
                        },
                        enabled = (endAyahText.toIntOrNull() ?: 0) < maxAyahs
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Increase to ayah")
                    }
                }

                if (validation.endError?.contains("≥ start") == true && startAyahText.isNotBlank()) {
                    SuggestionChip(
                        onClick = { endAyahText = startAyahText },
                        label = { Text("Set end = start ($startAyahText)") },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    )
                }
            }

            // Repeat Count (Chips + Manual Entry / Steppers)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Repeat Count",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (isInfiniteRepeat) "Continuous (∞)" else "${validation.parsedRepeatCount ?: repeatCountText} times",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(1, 3, 5, 10, 20, 0).forEach { count ->
                        val isSelected = if (count == 0) isInfiniteRepeat else (!isInfiniteRepeat && repeatCountText == "$count")
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (count == 0) {
                                    isInfiniteRepeat = true
                                    repeatCountText = ""
                                } else {
                                    isInfiniteRepeat = false
                                    repeatCountText = "$count"
                                }
                            },
                            label = { Text(if (count == 0) "∞" else "${count}x") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }

                if (!isInfiniteRepeat) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                val cur = repeatCountText.toIntOrNull() ?: 10
                                if (cur > 1) repeatCountText = (cur - 1).toString()
                            },
                            enabled = (repeatCountText.toIntOrNull() ?: 10) > 1
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Decrease repeat count")
                        }
                        OutlinedTextField(
                            value = repeatCountText,
                            onValueChange = { input ->
                                if (input.all { it.isDigit() } && input.length <= 3) {
                                    repeatCountText = input
                                }
                            },
                            label = { Text("Repetitions (1–100)") },
                            isError = validation.repeatCountError != null,
                            supportingText = {
                                if (validation.repeatCountError != null) Text(validation.repeatCountError)
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            trailingIcon = {
                                if (repeatCountText.isNotEmpty()) {
                                    IconButton(onClick = { repeatCountText = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear repeat count")
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                val cur = repeatCountText.toIntOrNull() ?: 1
                                if (cur < 100) repeatCountText = (cur + 1).toString()
                            },
                            enabled = (repeatCountText.toIntOrNull() ?: 1) < 100
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Increase repeat count")
                        }
                    }
                }
            }

            // Delay between repetitions
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Delay between loops",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 3, 5, 10).forEach { delay ->
                        FilterChip(
                            selected = delaySeconds == delay,
                            onClick = { delaySeconds = delay },
                            label = { Text("${delay}s") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        )
                    }
                }
            }

            // Optional Label
            OutlinedTextField(
                value = customLabel,
                onValueChange = { customLabel = it },
                label = { Text("Label (Optional)") },
                placeholder = { Text(defaultLabel) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Save Button
            Button(
                onClick = {
                    if (validation.isValid) {
                        val start = validation.parsedStartAyah ?: 1
                        val end = validation.parsedEndAyah ?: start
                        val repeats = validation.parsedRepeatCount ?: 10
                        val finalLabel = customLabel.ifBlank { defaultLabel }
                        onSave(
                            RepetitionPreset(
                                id = initialPreset?.id ?: 0L,
                                surahNumber = selectedSurahId,
                                startAyah = start,
                                endAyah = end,
                                repeatCount = repeats,
                                delaySeconds = delaySeconds,
                                label = finalLabel,
                            )
                        )
                    }
                },
                enabled = validation.isValid,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Preset")
            }
        }
}


