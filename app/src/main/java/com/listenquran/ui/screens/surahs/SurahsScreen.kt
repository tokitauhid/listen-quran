package com.listenquran.ui.screens.surahs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.listenquran.domain.model.Surah
import com.listenquran.ui.screens.home.SurahListItem
import com.listenquran.ui.theme.AmiriQuranFont

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurahsScreen(
    viewModel: SurahsViewModel,
    onBackClick: () -> Unit,
    onSurahClick: (Surah) -> Unit,
    onPlayNext: ((Surah) -> Unit)? = null,
    onAddToQueue: ((Surah) -> Unit)? = null,
    onAddToPlaylist: ((Surah) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val isGridMode by viewModel.isGridMode.collectAsStateWithLifecycle()
    val currentPlayingSurahId by viewModel.currentPlayingSurahId.collectAsStateWithLifecycle()

    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    var showSortMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Surahs",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        val subtitleText = when (val state = uiState) {
                            is SurahsUiState.Success -> {
                                if (searchQuery.isNotBlank()) "${state.filteredCount} of ${state.totalCount} Surahs"
                                else "${state.totalCount} Surahs"
                            }
                            else -> "114 Surahs"
                        }
                        Text(
                            text = subtitleText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    // Sort order button
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "Sort order"
                            )
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Mushaf Order (1 to 114)") },
                                onClick = {
                                    viewModel.setSortOrder(SurahSortOrder.MUSHAF)
                                    showSortMenu = false
                                },
                                leadingIcon = {
                                    RadioButton(
                                        selected = sortOrder == SurahSortOrder.MUSHAF,
                                        onClick = null
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Revelation Order") },
                                onClick = {
                                    viewModel.setSortOrder(SurahSortOrder.REVELATION)
                                    showSortMenu = false
                                },
                                leadingIcon = {
                                    RadioButton(
                                        selected = sortOrder == SurahSortOrder.REVELATION,
                                        onClick = null
                                    )
                                }
                            )
                        }
                    }

                    // Grid / List mode toggle
                    IconButton(onClick = { viewModel.toggleGridMode() }) {
                        Icon(
                            imageVector = if (isGridMode) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                            contentDescription = if (isGridMode) "Switch to List View" else "Switch to Grid View"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Compact Search / Filter Field
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TextField(
                        value = searchQuery,
                        onValueChange = { viewModel.onSearchQueryChange(it) },
                        placeholder = {
                            Text(
                                "Filter by name or number (e.g. 55 or Rahman)...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            disabledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        ),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Main Content: Grid or List
            when (val state = uiState) {
                is SurahsUiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                is SurahsUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                is SurahsUiState.Success -> {
                    if (state.surahs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "No surahs match \"$searchQuery\"",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                    Text("Clear Filter")
                                }
                            }
                        }
                    } else if (isGridMode) {
                        // Dense Grid View: minSize = 100.dp (3-4 columns on phone)
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 100.dp),
                            state = gridState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(
                                items = state.surahs,
                                key = { it.id },
                                contentType = { "surah_tile" }
                            ) { tile ->
                                SurahGridTile(
                                    tile = tile,
                                    isPlaying = tile.id == currentPlayingSurahId,
                                    onClick = { onSurahClick(tile.surah) },
                                    onPlayNext = onPlayNext?.let { fn -> { fn(tile.surah) } },
                                    onAddToQueue = onAddToQueue?.let { fn -> { fn(tile.surah) } },
                                    onAddToPlaylist = onAddToPlaylist?.let { fn -> { fn(tile.surah) } },
                                )
                            }
                        }
                    } else {
                        // List View
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(
                                items = state.surahs,
                                key = { it.id },
                                contentType = { "surah_list_row" }
                            ) { tile ->
                                SurahListItem(
                                    surah = tile.surah,
                                    onClick = { onSurahClick(tile.surah) },
                                    onPlayNext = onPlayNext?.let { fn -> { fn(tile.surah) } },
                                    onAddToQueue = onAddToQueue?.let { fn -> { fn(tile.surah) } },
                                    onAddToPlaylist = onAddToPlaylist?.let { fn -> { fn(tile.surah) } },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dense, accessible Surah Grid Tile with 48dp minimum touch target and M3 tokens.
 */
@Composable
fun SurahGridTile(
    tile: SurahTileUi,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onPlayNext: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
) {
    val containerColor = if (isPlaying) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    val borderStroke = if (isPlaying) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    } else null

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 102.dp)
            .semantics { contentDescription = tile.contentDescription }
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = borderStroke,
        tonalElevation = if (isPlaying) 4.dp else 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Row: Surah Number Pill (Start) + Arabic Calligraphy (End)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            if (isPlaying) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tile.numberText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isPlaying) MaterialTheme.colorScheme.onPrimary
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = tile.nameArabic,
                    fontFamily = AmiriQuranFont,
                    fontSize = 17.sp,
                    lineHeight = 34.sp, // 2.0x line-height prevents diacritics / tashkeel clipping
                    color = if (isPlaying) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    textAlign = TextAlign.End,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Footer Details: Transliterated English Name + Subtitle + Three Dot Menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tile.nameSimple,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = tile.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (onPlayNext != null || onAddToQueue != null || onAddToPlaylist != null) {
                    var showTileMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(
                            onClick = { showTileMenu = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options for ${tile.nameSimple}",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showTileMenu,
                            onDismissRequest = { showTileMenu = false }
                        ) {
                            if (onPlayNext != null) {
                                DropdownMenuItem(
                                    text = { Text("Play Next") },
                                    leadingIcon = {
                                        Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null)
                                    },
                                    onClick = {
                                        showTileMenu = false
                                        onPlayNext()
                                    }
                                )
                            }
                            if (onAddToQueue != null) {
                                DropdownMenuItem(
                                    text = { Text("Add to Queue") },
                                    leadingIcon = {
                                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null)
                                    },
                                    onClick = {
                                        showTileMenu = false
                                        onAddToQueue()
                                    }
                                )
                            }
                            if (onAddToPlaylist != null) {
                                DropdownMenuItem(
                                    text = { Text("Add to Playlist") },
                                    leadingIcon = {
                                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null)
                                    },
                                    onClick = {
                                        showTileMenu = false
                                        onAddToPlaylist()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
