package com.listenquran.ui.screens.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.listenquran.domain.model.BrowseCategory
import com.listenquran.domain.model.CategoryEntry
import com.listenquran.domain.model.CategoryKind
import com.listenquran.domain.model.Surah
import com.listenquran.player.SleepTimerMode
import com.listenquran.ui.screens.home.SurahListItem
import com.listenquran.ui.theme.AmiriQuranFont
import com.listenquran.ui.theme.BottomSheetShape

private enum class LengthRevelationFilter(val label: String) {
    SHORT("Short (<20)"),
    MEDIUM("Medium (20–80)"),
    LONG("Long (80+)"),
    MAKKI("Makki"),
    MADANI("Madani"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDetailSheet(
    category: BrowseCategory,
    surahs: List<Surah>,
    onDismiss: () -> Unit,
    onPlayAll: (entries: List<CategoryEntry>) -> Unit,
    onShuffle: (entries: List<CategoryEntry>) -> Unit,
    onPlayEntry: (index: Int, entries: List<CategoryEntry>) -> Unit,
    onSaveAsPlaylist: (entries: List<CategoryEntry>) -> Unit,
    onPlayWithSleepTimer: ((entries: List<CategoryEntry>, mode: SleepTimerMode) -> Unit)? = null,
    onPlayNext: ((Surah) -> Unit)? = null,
    onAddToQueue: ((Surah) -> Unit)? = null,
    onAddToPlaylist: ((Surah) -> Unit)? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    val tonePalette = resolveCategoryTonePalette(category.tone, colorScheme)
    val surahMap = remember(surahs) { surahs.associateBy { it.id } }

    var selectedLengthFilter by rememberSaveable(category.id) {
        mutableStateOf(LengthRevelationFilter.SHORT)
    }

    val displayedEntries = remember(category, surahs, selectedLengthFilter) {
        if (category.kind == CategoryKind.LENGTH_AND_REVELATION) {
            val filtered = when (selectedLengthFilter) {
                LengthRevelationFilter.SHORT -> surahs.filter { it.versesCount < 20 }
                LengthRevelationFilter.MEDIUM -> surahs.filter { it.versesCount in 20..80 }
                LengthRevelationFilter.LONG -> surahs.filter { it.versesCount > 80 }
                LengthRevelationFilter.MAKKI -> surahs.filter { it.isMakki }
                LengthRevelationFilter.MADANI -> surahs.filter { !it.isMakki }
            }
            filtered.map { CategoryEntry(surahId = it.id) }
        } else {
            category.entries
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = BottomSheetShape,
        containerColor = colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = tonePalette.badgeContainer,
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = categoryIconFor(category.id),
                            contentDescription = null,
                            tint = tonePalette.badgeContent,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = category.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Text(
                            text = category.arabicLabel,
                            fontFamily = AmiriQuranFont,
                            fontSize = 18.sp,
                            lineHeight = 36.sp,
                            color = colorScheme.tertiary,
                            maxLines = 1,
                        )
                    }
                    val countText = if (category.id == "quranic_duas") {
                        "${displayedEntries.size} Duas"
                    } else {
                        "${displayedEntries.size} ${if (displayedEntries.size == 1) "item" else "items"}"
                    }
                    Text(
                        text = "${category.description} • $countText",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (category.kind == CategoryKind.LENGTH_AND_REVELATION) {
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(LengthRevelationFilter.entries, key = { it.name }) { filter ->
                        val selected = selectedLengthFilter == filter
                        FilterChip(
                            selected = selected,
                            onClick = { selectedLengthFilter = filter },
                            label = { Text(filter.label) },
                            leadingIcon = if (selected) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                                    )
                                }
                            } else null,
                            shape = MaterialTheme.shapes.small,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Primary actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = {
                        onPlayAll(displayedEntries)
                        onDismiss()
                    },
                    enabled = displayedEntries.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Play All")
                }

                FilledTonalButton(
                    onClick = {
                        onShuffle(displayedEntries)
                        onDismiss()
                    },
                    enabled = displayedEntries.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Shuffle")
                }

                FilledTonalIconButton(
                    onClick = { onSaveAsPlaylist(displayedEntries) },
                    enabled = displayedEntries.isNotEmpty(),
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                        contentDescription = "Save as Playlist",
                    )
                }
            }

            if (category.showSleepTimerShortcut && onPlayWithSleepTimer != null && displayedEntries.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AssistChip(
                        onClick = {
                            onPlayWithSleepTimer(displayedEntries, SleepTimerMode.Duration(30))
                            onDismiss()
                        },
                        label = { Text("Play + 30m Timer") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Timer,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                    AssistChip(
                        onClick = {
                            onPlayWithSleepTimer(displayedEntries, SleepTimerMode.EndOfSurah)
                            onDismiss()
                        },
                        label = { Text("Play + End of Surah") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Timer,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.5f))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(
                    items = displayedEntries,
                    key = { idx, entry -> "${category.id}_${idx}_${entry.surahId}_${entry.startAyah}" },
                ) { index, entry ->
                    val surah = surahMap[entry.surahId]
                    if (surah != null) {
                        if (entry.label != null || entry.subtitle != null || entry.startAyah > 1 || entry.endAyah != null) {
                            CategoryVerseEntryRow(
                                entry = entry,
                                surah = surah,
                                onClick = {
                                    onPlayEntry(index, displayedEntries)
                                    onDismiss()
                                },
                                onPlayNext = onPlayNext?.let { fn -> { fn(surah) } },
                                onAddToQueue = onAddToQueue?.let { fn -> { fn(surah) } },
                                onAddToPlaylist = onAddToPlaylist?.let { fn -> { fn(surah) } },
                            )
                        } else {
                            SurahListItem(
                                surah = surah,
                                onClick = {
                                    onPlayEntry(index, displayedEntries)
                                    onDismiss()
                                },
                                onPlayNext = onPlayNext?.let { fn -> { fn(surah) } },
                                onAddToQueue = onAddToQueue?.let { fn -> { fn(surah) } },
                                onAddToPlaylist = onAddToPlaylist?.let { fn -> { fn(surah) } },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryVerseEntryRow(
    entry: CategoryEntry,
    surah: Surah,
    onClick: () -> Unit,
    onPlayNext: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
) {
    var showMenu by remember { mutableStateOf(false) }
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = colorScheme.surfaceContainerLow,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "${surah.id}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.primary,
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.label ?: surah.nameSimple,
                        style = MaterialTheme.typography.titleMedium,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = entry.subtitle ?: "${surah.nameSimple} • Ayah ${entry.startAyah}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = surah.nameArabic,
                    fontFamily = AmiriQuranFont,
                    fontSize = 18.sp,
                    lineHeight = 38.sp,
                    color = colorScheme.onSurface,
                )

                if (onPlayNext != null || onAddToQueue != null || onAddToPlaylist != null) {
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Options",
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            if (onPlayNext != null) {
                                DropdownMenuItem(
                                    text = { Text("Play Next") },
                                    leadingIcon = {
                                        Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null)
                                    },
                                    onClick = {
                                        showMenu = false
                                        onPlayNext()
                                    },
                                )
                            }
                            if (onAddToQueue != null) {
                                DropdownMenuItem(
                                    text = { Text("Add to Queue") },
                                    leadingIcon = {
                                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null)
                                    },
                                    onClick = {
                                        showMenu = false
                                        onAddToQueue()
                                    },
                                )
                            }
                            if (onAddToPlaylist != null) {
                                DropdownMenuItem(
                                    text = { Text("Add to Playlist") },
                                    leadingIcon = {
                                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null)
                                    },
                                    onClick = {
                                        showMenu = false
                                        onAddToPlaylist()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
