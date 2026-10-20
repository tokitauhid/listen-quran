package com.listenquran.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.res.painterResource
import com.listenquran.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import com.listenquran.ui.components.LocalMiniPlayerInset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.listenquran.domain.model.*
import com.listenquran.ui.components.PlaylistArtworkMosaic
import com.listenquran.ui.screens.search.ParsedVerseQuery
import com.listenquran.ui.screens.search.ReciterSearchItem
import com.listenquran.ui.theme.AmiriQuranFont
import com.listenquran.util.ReciterImages

data class BrowseCategory(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val surahTargetId: Int,
    val surahIds: List<Int> = listOf(surahTargetId),
)

val BrowseCategories = listOf(
    BrowseCategory("Juz & Hizb", "Listen section by section", Icons.Rounded.AutoStories, 1, (1..114).toList()),
    BrowseCategory("Ruqyah & Peace", "Protection & calming verses", Icons.Rounded.HealthAndSafety, 1, listOf(1, 2, 112, 113, 114, 36)),
    BrowseCategory("Sleep Recitations", "Peaceful recitations before sleep", Icons.Rounded.Bedtime, 67, listOf(67, 32, 56, 76)),
    BrowseCategory("Taraweeh & Qiyam", "Night prayers & Tahajjud", Icons.Rounded.NightsStay, 55, listOf(55, 56, 67, 73)),
    BrowseCategory("Quranic Duas", "Supplications from the Quran", Icons.Filled.Favorite, 2, listOf(2, 3, 14)),
    BrowseCategory("The 3 Quls", "Surahs Al-Ikhlas to An-Nas", Icons.Rounded.Shield, 112, listOf(112, 113, 114)),
)

// Cached gradient brushes to avoid shader allocations during scrolling
private val LikedGradient = Brush.verticalGradient(listOf(Color(0xFF005237), Color(0xFF1D211F)))
private val DefaultPlaylistGradient = Brush.verticalGradient(listOf(Color(0xFF334B3F), Color(0xFF191C1B)))
private val DailyMixGradient = Brush.verticalGradient(listOf(Color(0xFF005237), Color(0xFF1D211F)))
private val WeeklyMixGradient = Brush.verticalGradient(listOf(Color(0xFF334B3F), Color(0xFF191C1B)))
private val FridayMixGradient = Brush.verticalGradient(listOf(Color(0xFF5F4100), Color(0xFF1D211F)))
private val TahajjudMixGradient = Brush.verticalGradient(listOf(Color(0xFF004D40), Color(0xFF111413)))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    surahs: List<Surah>,
    playlists: List<Playlist>,
    likedSurahs: List<LikedSurah> = emptyList(),
    khatmaProgress: KhatmaProgress,
    recentSurahs: List<Surah> = emptyList(),
    reciters: List<Reciter> = emptyList(),
    onSurahClick: (Surah) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onContinueKhatma: () -> Unit,
    onOpenSettings: () -> Unit,
    onNavigateToSearch: (() -> Unit)? = null,
    onNavigateToSurahs: (() -> Unit)? = null,
    onReciterClick: ((Reciter) -> Unit)? = null,
    onVerseClick: ((Surah, Int) -> Unit)? = null,
    onPlayNext: ((Surah) -> Unit)? = null,
    onAddToQueue: ((Surah) -> Unit)? = null,
    onAddToPlaylist: ((Surah) -> Unit)? = null,
    onRadioStationClick: ((RadioStation) -> Unit)? = null,
    onPlayCategory: ((String, List<Int>) -> Unit)? = null,
    radioStations: List<RadioStation> = CuratedRadioStations,
    currentRadioStation: RadioStation? = null,
    isRadioPlaying: Boolean = false,
    onNavigateToRadio: (() -> Unit)? = null,
    onOpenKhatam: (() -> Unit)? = null,
    resetTrigger: Int = 0,
    modifier: Modifier = Modifier,
) {
    var isSearchOpen by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()
    val homeListState = rememberLazyListState()

    LaunchedEffect(resetTrigger) {
        if (resetTrigger > 0) {
            isSearchOpen = false
            searchQuery = ""
            homeListState.scrollToItem(0)
        }
    }

    LaunchedEffect(isSearchOpen) {
        if (isSearchOpen) {
            delay(100)
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    BackHandler(enabled = isSearchOpen) {
        if (searchQuery.isNotBlank()) {
            searchQuery = ""
        } else {
            isSearchOpen = false
        }
    }

    val popularShortcuts = remember {
        listOf(
            Triple("Ayatul Kursi (2:255)", 2, 255),
            Triple("Surah Al-Mulk", 67, 1),
            Triple("Surah Yasin", 36, 1),
            Triple("Surah Ar-Rahman", 55, 1),
            Triple("Surah Al-Waqi'ah", 56, 1),
            Triple("Surah Al-Kahf", 18, 1),
            Triple("The 4 Quls (112:1)", 112, 1),
        )
    }

    // Detect Surah:Ayah pattern e.g. "2:255" or "18:10"
    val parsedVerse: ParsedVerseQuery? = remember(searchQuery, surahs) {
        val trimmed = searchQuery.trim()
        val parts = trimmed.split(":", " ")
        if (parts.size == 2) {
            val sNum = parts[0].toIntOrNull()
            val aNum = parts[1].toIntOrNull()
            if (sNum != null && aNum != null && sNum in 1..114) {
                val matchedSurah = surahs.find { it.id == sNum }
                if (matchedSurah != null && aNum in 1..matchedSurah.versesCount) {
                    ParsedVerseQuery(sNum, aNum, matchedSurah)
                } else null
            } else null
        } else null
    }

    val filteredSurahs = remember(searchQuery, surahs) {
        if (searchQuery.isBlank()) emptyList()
        else surahs.filter {
            it.nameSimple.contains(searchQuery, ignoreCase = true) ||
            it.translatedName.contains(searchQuery, ignoreCase = true) ||
            it.nameComplex.contains(searchQuery, ignoreCase = true) ||
            it.id.toString() == searchQuery.trim() ||
            it.nameArabic.contains(searchQuery)
        }
    }

    val uniqueReciters = remember(reciters) {
        reciters.distinctBy { it.name.replace(" ", "") }
    }

    val filteredReciters = remember(searchQuery, uniqueReciters) {
        if (searchQuery.isBlank()) emptyList()
        else uniqueReciters.filter {
            it.englishName.contains(searchQuery, ignoreCase = true) ||
            it.name.contains(searchQuery)
        }
    }

    // Preserve scroll positions of horizontal carousels across parent LazyColumn scrolls
    val playlistsRowState = rememberLazyListState()
    val mixesRowState = rememberLazyListState()
    val radioRowState = rememberLazyListState()
    val recitersRowState = rememberLazyListState()

    val categoryPairs = remember {
        BrowseCategories.chunked(2)
    }

    // Memoize derived data to eliminate work during layout & recomposition
    val displaySurahs = remember(recentSurahs, surahs) {
        (if (recentSurahs.isNotEmpty()) recentSurahs else surahs).take(6)
    }
    val pairs = remember(displaySurahs) {
        displaySurahs.chunked(2)
    }

    val featuredSurahs = remember(surahs) {
        val targetIds = setOf(1, 2, 18, 36, 55, 56, 67, 112, 113, 114)
        surahs.filter { it.id in targetIds }
    }

    val featuredReciters = remember(uniqueReciters) {
        val priorityIds = listOf(7, 2, 3, 6, 9, 10, 4, 5, 159, 174, 13, 19, 160, 158)
        uniqueReciters
            .sortedWith(
                compareBy<Reciter> { reciter ->
                    val idx = priorityIds.indexOf(reciter.id)
                    if (idx >= 0) idx else 1000
                }.thenByDescending {
                    ReciterImages.getPhotoUrl(it.id, it.englishName, it.photoUrl) != null
                }
            )
            .take(12)
    }

    Scaffold(
        topBar = {
            if (isSearchOpen) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = {
                            isSearchOpen = false
                            searchQuery = ""
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Close search",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    title = {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    text = "Search surahs, ayahs, reciters...",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                        )
                    },
                    actions = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            } else {
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
                        Image(
                            painter = painterResource(id = R.drawable.ic_app_logo),
                            contentDescription = "Listen Quran Logo",
                            modifier = Modifier
                                .padding(start = 16.dp, end = 8.dp)
                                .size(36.dp)
                                .clip(CircleShape)
                        )
                    },
                    actions = {
                        IconButton(onClick = { isSearchOpen = true }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) { paddingValues ->
        if (isSearchOpen) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (searchQuery.isNotBlank()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = LocalMiniPlayerInset.current + 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Direct Verse Match
                        if (parsedVerse != null && parsedVerse.surah != null) {
                            item(key = "direct_verse_${parsedVerse.surahNumber}_${parsedVerse.ayahNumber}") {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable {
                                            if (onVerseClick != null) {
                                                onVerseClick(parsedVerse.surah, parsedVerse.ayahNumber)
                                            } else {
                                                onSurahClick(parsedVerse.surah)
                                            }
                                        },
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Direct Ayah Match",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "Surah ${parsedVerse.surah.nameSimple} : Ayah ${parsedVerse.ayahNumber}",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Text(
                                                text = "Tap to listen directly from this verse",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                            )
                                        }
                                        FilledIconButton(
                                            onClick = {
                                                if (onVerseClick != null) {
                                                    onVerseClick(parsedVerse.surah, parsedVerse.ayahNumber)
                                                } else {
                                                    onSurahClick(parsedVerse.surah)
                                                }
                                            },
                                            colors = IconButtonDefaults.filledIconButtonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            )
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = "Play Verse")
                                        }
                                    }
                                }
                            }
                        }

                        // Surah Results
                        if (filteredSurahs.isNotEmpty()) {
                            item(key = "surahs_header") {
                                Text(
                                    text = "Surahs (${filteredSurahs.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            items(filteredSurahs, key = { "surah_${it.id}" }) { surah ->
                                SurahListItem(
                                    surah = surah,
                                    onClick = { onSurahClick(surah) },
                                    onPlayNext = onPlayNext?.let { fn -> { fn(surah) } },
                                    onAddToQueue = onAddToQueue?.let { fn -> { fn(surah) } },
                                    onAddToPlaylist = onAddToPlaylist?.let { fn -> { fn(surah) } },
                                )
                            }
                        }

                        // Reciter Results
                        if (filteredReciters.isNotEmpty()) {
                            item(key = "reciters_header") {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Reciters (${filteredReciters.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            items(filteredReciters, key = { "reciter_${it.id}" }) { reciter ->
                                ReciterSearchItem(
                                    reciter = reciter,
                                    onClick = { onReciterClick?.invoke(reciter) }
                                )
                            }
                        }

                        // Empty State
                        if (parsedVerse == null && filteredSurahs.isEmpty() && filteredReciters.isEmpty()) {
                            item(key = "empty_results") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 48.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No results found for \"$searchQuery\"",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 0.dp, end = 0.dp, top = 0.dp, bottom = LocalMiniPlayerInset.current + 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item(key = "popular_recitations_header") {
                            Text(
                                text = "Popular Recitations",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(popularShortcuts, key = { it.first }) { (name, targetId, startAyah) ->
                                    AssistChip(
                                        onClick = {
                                            surahs.find { it.id == targetId }?.let { surah ->
                                                if (startAyah > 1 && onVerseClick != null) {
                                                    onVerseClick(surah, startAyah)
                                                } else {
                                                    onSurahClick(surah)
                                                }
                                            }
                                        },
                                        label = { Text(name) },
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                            labelColor = MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                }
                            }
                        }

                        item(key = "browse_surahs_search_card") {
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        onNavigateToSurahs?.invoke()
                                    },
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                tonalElevation = 2.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.AutoStories,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Browse All Surahs",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                state = homeListState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(start = 0.dp, end = 0.dp, top = 0.dp, bottom = LocalMiniPlayerInset.current + 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Playlists (Liked on Top, Custom Playlists Carousel)
                if (playlists.isNotEmpty()) {
                    item(key = "playlists_header", contentType = "section_header") {
                        SectionHeader(title = "Your Playlists")
                    }
                    item(key = "playlists_carousel", contentType = "horizontal_carousel") {
                        LazyRow(
                            state = playlistsRowState,
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(playlists, key = { it.id }, contentType = { "playlist_card" }) { playlist ->
                                Surface(
                                    modifier = Modifier
                                        .width(148.dp)
                                        .height(130.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    onClick = { onPlaylistClick(playlist) }
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(if (playlist.isLikedCollection) LikedGradient else DefaultPlaylistGradient)
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        if (!playlist.isLikedCollection && playlist.items.size >= 4) {
                                            PlaylistArtworkMosaic(
                                                items = playlist.items,
                                                size = 36.dp
                                            )
                                        } else {
                                            Icon(
                                                imageVector = if (playlist.isLikedCollection) Icons.Filled.Favorite else Icons.AutoMirrored.Rounded.QueueMusic,
                                                contentDescription = null,
                                                tint = if (playlist.isLikedCollection) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = playlist.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val countText = if (playlist.isLikedCollection) {
                                                "${likedSurahs.size} ${if (likedSurahs.size == 1) "recitation" else "recitations"}"
                                            } else {
                                                "${playlist.items.size} tracks"
                                            }
                                            Text(
                                                text = countText,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Browse Categories (Granular rows for fast, smooth scrolling)
                item(key = "browse_categories_header", contentType = "section_header") {
                        SectionHeader(title = "Browse Categories")
                    }
                    items(
                        items = categoryPairs,
                        key = { pair: List<BrowseCategory> -> "cat_${pair.joinToString("_") { it.title }}" },
                        contentType = { "category_row" }
                    ) { pair ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            for (category in pair) {
                                CategoryCard(
                                    category = category,
                                    onClick = {
                                        if (onPlayCategory != null) {
                                            onPlayCategory(category.title, category.surahIds)
                                        } else {
                                            surahs.find { it.id == category.surahTargetId }?.let(onSurahClick)
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (pair.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                // 3. Recently Listened (Granular rows)
                if (displaySurahs.isNotEmpty()) {
                    item(key = "recent_surahs_header", contentType = "section_header") {
                        SectionHeader(title = "Recently Listened")
                    }
                    items(
                        items = pairs,
                        key = { pair -> "recent_${pair.map { it.id }.joinToString("_")}" },
                        contentType = { "recent_row" }
                    ) { pair ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
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

                // 4. Khatma Tracking Card
                item(key = "khatma_widget", contentType = "khatma_card") {
                    KhatmaWidget(
                        progress = khatmaProgress,
                        onContinue = onContinueKhatma,
                        onClick = onOpenKhatam ?: onContinueKhatma,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                // 5. Trending Daily / Weekly Mixes
                item(key = "trending_mixes_header", contentType = "section_header") {
                    SectionHeader(title = "Trending from Quran.com")
                }
                item(key = "trending_mixes_carousel", contentType = "horizontal_carousel") {
                    LazyRow(
                        state = mixesRowState,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item(key = "daily_mix", contentType = "mix_card") {
                            MixCard(
                                title = "Daily Mix",
                                subtitle = "Al-Kahf • Yasin • Mulk",
                                gradient = DailyMixGradient,
                                onClick = {
                                    if (onPlayCategory != null) {
                                        onPlayCategory("Daily Mix", listOf(18, 36, 67))
                                    } else {
                                        surahs.find { it.id == 18 }?.let(onSurahClick) ?: surahs.firstOrNull()?.let(onSurahClick)
                                    }
                                }
                            )
                        }
                        item(key = "weekly_mix", contentType = "mix_card") {
                            MixCard(
                                title = "Weekly Mix",
                                subtitle = "Ar-Rahman • Al-Waqi'ah",
                                gradient = WeeklyMixGradient,
                                onClick = {
                                    if (onPlayCategory != null) {
                                        onPlayCategory("Weekly Mix", listOf(55, 56, 67))
                                    } else {
                                        surahs.find { it.id == 55 }?.let(onSurahClick) ?: surahs.firstOrNull()?.let(onSurahClick)
                                    }
                                }
                            )
                        }
                        item(key = "friday_surahs", contentType = "mix_card") {
                            MixCard(
                                title = "Friday Surahs",
                                subtitle = "Surah Al-Kahf recitation",
                                gradient = FridayMixGradient,
                                onClick = {
                                    if (onPlayCategory != null) {
                                        onPlayCategory("Friday Surahs", listOf(18, 32, 76, 62))
                                    } else {
                                        surahs.find { it.id == 18 }?.let(onSurahClick)
                                    }
                                }
                            )
                        }
                        item(key = "tahajjud_mix", contentType = "mix_card") {
                            MixCard(
                                title = "Tahajjud Mix",
                                subtitle = "Deep contemplation recitations",
                                gradient = TahajjudMixGradient,
                                onClick = {
                                    if (onPlayCategory != null) {
                                        onPlayCategory("Tahajjud Mix", listOf(73, 75, 76, 67))
                                    } else {
                                        surahs.find { it.id == 73 }?.let(onSurahClick) ?: surahs.firstOrNull()?.let(onSurahClick)
                                    }
                                }
                            )
                        }
                    }
                }

                // 6. Live 24/7 Quran Radio
                item(key = "quran_radio_header", contentType = "section_header") {
                    SectionHeader(
                        title = "Radio from Quran.com",
                        onSeeAllClick = onNavigateToRadio
                    )
                }
                item(key = "quran_radio_carousel", contentType = "horizontal_carousel") {
                    LazyRow(
                        state = radioRowState,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val displayStations = radioStations.ifEmpty { CuratedRadioStations }
                        items(displayStations, key = { it.id }, contentType = { "radio_card" }) { station ->
                            val isStationPlaying = isRadioPlaying && currentRadioStation?.id == station.id
                            RadioStationCard(
                                station = station,
                                isPlaying = isStationPlaying,
                                onClick = { onRadioStationClick?.invoke(station) }
                            )
                        }
                    }
                }

                // 7. Featured Reciters (Discovery Carousel)
                if (featuredReciters.isNotEmpty()) {
                    item(key = "featured_reciters_header", contentType = "section_header") {
                        SectionHeader(title = "Featured Reciters")
                    }
                    item(key = "featured_reciters_carousel", contentType = "horizontal_carousel") {
                        LazyRow(
                            state = recitersRowState,
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(featuredReciters, key = { it.id }, contentType = { "reciter_card" }) { reciter ->
                                FeaturedReciterCard(
                                    reciter = reciter,
                                    onClick = { onReciterClick?.invoke(reciter) }
                                )
                            }
                        }
                    }
                }

                // 8. Popular Surahs
                item(key = "popular_surahs_header", contentType = "section_header") {
                    SectionHeader(
                        title = "Popular Surahs",
                        onSeeAllClick = onNavigateToSurahs
                    )
                }
                items(featuredSurahs, key = { "featured_surah_${it.id}" }, contentType = { "surah_item" }) { surah ->
                    SurahListItem(
                        surah = surah,
                        onClick = { onSurahClick(surah) },
                        onPlayNext = onPlayNext?.let { fn -> { fn(surah) } },
                        onAddToQueue = onAddToQueue?.let { fn -> { fn(surah) } },
                        onAddToPlaylist = onAddToPlaylist?.let { fn -> { fn(surah) } },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                    )
                }

                item(key = "browse_all_surahs_button", contentType = "action_button") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        OutlinedButton(
                            onClick = {
                                onNavigateToSurahs?.invoke()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoStories,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Browse All 114 Surahs")
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
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        onClick = onClick
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
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
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
    gradient: Brush,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifierCard,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(gradient)
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

private val modifierCard = Modifier
    .width(148.dp)
    .height(160.dp)



@Composable
fun SurahListItem(
    surah: Surah,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onPlayNext: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
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

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = surah.nameSimple,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${surah.translatedName} • ${surah.versesCount} Ayahs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = surah.nameArabic,
                    fontFamily = AmiriQuranFont,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (onPlayNext != null || onAddToQueue != null || onAddToPlaylist != null) {
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
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
                                        showMenu = false
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
                                        showMenu = false
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

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    onSeeAllClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (onSeeAllClick != null) {
            TextButton(onClick = onSeeAllClick) {
                Text(
                    text = "See all",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun RadioStationCard(
    station: RadioStation,
    isPlaying: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .width(170.dp)
            .height(160.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = if (isPlaying) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        onClick = onClick
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!station.bannerUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(station.bannerUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = station.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.85f))
                            )
                        )
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF003824), Color(0xFF191C1B))
                            )
                        )
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isLive = station.type == RadioStationType.CURATED && station.isLive
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isLive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (isLive) Color.White else MaterialTheme.colorScheme.onPrimaryContainer)
                            )
                            Text(
                                text = if (isLive) "LIVE" else "RADIO",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isLive) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                                fontSize = 10.sp
                            )
                        }
                    }

                    if (isPlaying) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Playing",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(4.dp)
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Radio,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Column {
                    Text(
                        text = station.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = station.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.75f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryCard(
    category: BrowseCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(104.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Column {
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = category.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun FeaturedReciterCard(
    reciter: Reciter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val photoUrl = remember(reciter) {
        ReciterImages.getPhotoUrl(reciter.id, reciter.englishName, reciter.photoUrl)
    }
    var isImageError by remember(photoUrl) { mutableStateOf(false) }

    Surface(
        modifier = modifier.width(136.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(64.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = reciter.englishName.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
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
                }
            }
            Text(
                text = reciter.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = reciter.style?.ifBlank { "Hafs" } ?: "Hafs",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
