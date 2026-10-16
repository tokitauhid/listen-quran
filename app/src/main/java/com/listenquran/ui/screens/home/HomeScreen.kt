package com.listenquran.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.listenquran.domain.model.*
import com.listenquran.ui.theme.AmiriQuranFont

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    surahs: List<Surah>,
    reciters: List<Reciter>,
    playlists: List<Playlist>,
    radioStations: List<RadioStation>,
    khatmaProgress: KhatmaProgress,
    recentSurahs: List<Surah> = emptyList(),
    onSurahClick: (Surah) -> Unit,
    onReciterClick: (Reciter) -> Unit,
    onRadioClick: (RadioStation) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onContinueKhatma: () -> Unit,
    onOpenSettings: () -> Unit,
    onNavigateToSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedFilter by remember { mutableStateOf("All") }
    val filterCategories = listOf("All", "Surahs", "Playlists", "Reciters", "Radio")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Listen Quran",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    Box(
                        modifier = Modifier
                            .padding(start = 16.dp, end = 8.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "LQ",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 14.sp
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Notifications",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
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
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 90.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Filter Chips
            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filterCategories, key = { it }) { category ->
                        FilterChip(
                            selected = selectedFilter == category,
                            onClick = { selectedFilter = category },
                            label = { Text(category) },
                            shape = RoundedCornerShape(8.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        )
                    }
                }
            }

            // 1. Playlists (Liked on Top, Custom Playlists Carousel - plan.md §1.1.1 & §1.1.2)
            if (selectedFilter in listOf("All", "Playlists") && playlists.isNotEmpty()) {
                item {
                    SectionHeader(title = "Your Playlists")
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(playlists, key = { it.id }) { playlist ->
                            Surface(
                                modifier = Modifier
                                    .width(148.dp)
                                    .height(130.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { onPlaylistClick(playlist) },
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            if (playlist.isLikedCollection) {
                                                Brush.verticalGradient(
                                                    listOf(Color(0xFF005237), Color(0xFF1D211F))
                                                )
                                            } else {
                                                Brush.verticalGradient(
                                                    listOf(Color(0xFF334B3F), Color(0xFF191C1B))
                                                )
                                            }
                                        )
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Icon(
                                        imageVector = if (playlist.isLikedCollection) Icons.Filled.Favorite else Icons.Rounded.QueueMusic,
                                        contentDescription = null,
                                        tint = if (playlist.isLikedCollection) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Column {
                                        Text(
                                            text = playlist.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (playlist.isLikedCollection) "Pinned" else "Playlist",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. Recently Listened 2x3 Tile Grid (plan.md §1.1.4)
            if (selectedFilter in listOf("All", "Surahs")) {
                item {
                    SectionHeader(title = "Recently Listened")
                    Spacer(modifier = Modifier.height(4.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val displaySurahs = if (recentSurahs.isNotEmpty()) recentSurahs.take(6) else surahs.take(6)
                        val pairs = displaySurahs.chunked(2)

                        for (pair in pairs) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (item in pair) {
                                    QuickAccessTile(
                                        title = item.nameSimple,
                                        badge = "${item.id}",
                                        onClick = { onSurahClick(item) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (pair.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            // 3. Khatma Tracking Card (plan.md §1.1.7)
            if (selectedFilter in listOf("All", "Surahs")) {
                item {
                    KhatmaWidget(
                        progress = khatmaProgress,
                        onContinue = onContinueKhatma,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            // 4. Trending Daily / Weekly Mixes (plan.md §1.1.3)
            if (selectedFilter in listOf("All", "Playlists")) {
                item {
                    SectionHeader(title = "Trending from Quran.com")
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            MixCard(
                                title = "Daily Mix",
                                subtitle = "Al-Kahf • Yasin • Mulk",
                                gradient = listOf(Color(0xFF005237), Color(0xFF1D211F)),
                                onClick = { surahs.firstOrNull()?.let(onSurahClick) }
                            )
                        }
                        item {
                            MixCard(
                                title = "Weekly Mix",
                                subtitle = "Ar-Rahman • Al-Waqi'ah",
                                gradient = listOf(Color(0xFF334B3F), Color(0xFF191C1B)),
                                onClick = { surahs.getOrNull(54)?.let(onSurahClick) }
                            )
                        }
                        item {
                            MixCard(
                                title = "Friday Surahs",
                                subtitle = "Surah Al-Kahf recitation",
                                gradient = listOf(Color(0xFF5F4100), Color(0xFF1D211F)),
                                onClick = { surahs.find { it.id == 18 }?.let(onSurahClick) }
                            )
                        }
                        item {
                            MixCard(
                                title = "Tahajjud Mix",
                                subtitle = "Deep contemplation recitations",
                                gradient = listOf(Color(0xFF004D40), Color(0xFF111413)),
                                onClick = { surahs.getOrNull(66)?.let(onSurahClick) }
                            )
                        }
                    }
                }
            }

            // 5. Radio from Quran.com (plan.md §1.1.5)
            if (selectedFilter in listOf("All", "Radio")) {
                item {
                    SectionHeader(title = "Radio from Quran.com")
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(radioStations, key = { it.id }) { station ->
                            RadioStationCard(
                                station = station,
                                onClick = { onRadioClick(station) }
                            )
                        }
                    }
                }
            }

            // 6. Recommended Reciters (plan.md §1.1.6)
            if (selectedFilter in listOf("All", "Reciters")) {
                item {
                    SectionHeader(title = "Recommended Reciters")
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(reciters, key = { it.id }) { reciter ->
                            ReciterAvatarCard(
                                reciter = reciter,
                                onClick = { onReciterClick(reciter) }
                            )
                        }
                    }
                }
            }

            // 7. Surahs Shortcut (clean & fast, no 114 items lag!)
            if (selectedFilter == "Surahs") {
                item {
                    SectionHeader(title = "Popular Surahs")
                }
                val featuredSurahs = surahs.filter { it.id in listOf(1, 2, 18, 36, 55, 56, 67, 112, 113, 114) }
                items(featuredSurahs, key = { it.id }) { surah ->
                    SurahListItem(
                        surah = surah,
                        onClick = { onSurahClick(surah) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        OutlinedButton(onClick = onNavigateToSearch) {
                            Text("Browse All 114 Surahs in Search")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuickAccessTile(
    title: String,
    badge: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontSize = 16.sp
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }
}

@Composable
fun KhatmaWidget(
    progress: KhatmaProgress,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(64.dp)
            ) {
                CircularProgressIndicator(
                    progress = { progress.progressFraction },
                    strokeWidth = 6.dp,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxSize()
                )
                Text(
                    text = "${progress.progressPercentage}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Daily Khatma Tracker",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${progress.currentPage} of 604 Pages • Juz ${progress.currentJuz}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${progress.pagesPerDayTarget} pages/day to finish",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            FilledIconButton(
                onClick = onContinue,
                shape = RoundedCornerShape(14.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Continue Khatma"
                )
            }
        }
    }
}

@Composable
fun MixCard(
    title: String,
    subtitle: String,
    gradient: List<Color>,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .width(148.dp)
            .height(160.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(gradient))
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoStories,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun RadioStationCard(
    station: RadioStation,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .width(156.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Radio,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = station.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "24/7 Live Stream",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun ReciterAvatarCard(
    reciter: Reciter,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(96.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(2.dp, MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            val initials = reciter.englishName.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("")
            Text(
                text = initials,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = reciter.englishName,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SurahListItem(
    surah: Surah,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "${surah.id}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Column {
                    Text(
                        text = surah.nameSimple,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${surah.translatedName} • ${surah.versesCount} Ayahs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = surah.nameArabic,
                fontFamily = AmiriQuranFont,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    )
}
