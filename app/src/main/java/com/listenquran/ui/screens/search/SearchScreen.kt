package com.listenquran.ui.screens.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import com.listenquran.ui.components.LocalMiniPlayerInset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.listenquran.domain.model.Reciter
import com.listenquran.domain.model.Surah
import com.listenquran.ui.screens.home.SurahListItem
import com.listenquran.ui.theme.AmiriQuranFont
import com.listenquran.util.ReciterImages

data class ParsedVerseQuery(
    val surahNumber: Int,
    val ayahNumber: Int,
    val surah: Surah?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    surahs: List<Surah>,
    reciters: List<Reciter>,
    onSurahClick: (Surah) -> Unit,
    onVerseClick: (Surah, Int) -> Unit,
    onReciterClick: (Reciter) -> Unit,
    onBrowseSurahsClick: () -> Unit,
    onPlayNext: ((Surah) -> Unit)? = null,
    onAddToQueue: ((Surah) -> Unit)? = null,
    onAddToPlaylist: ((Surah) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }
    var searchFilter by remember { mutableStateOf("All") }
    val searchFilters = listOf("All", "Surahs", "Verses", "Reciters")

    BackHandler(enabled = searchQuery.isNotBlank()) {
        searchQuery = ""
    }

    val popularShortcuts = listOf(
        Triple("Ayatul Kursi (2:255)", 2, 255),
        Triple("Surah Al-Mulk", 67, 1),
        Triple("Surah Yasin", 36, 1),
        Triple("Surah Ar-Rahman", 55, 1),
        Triple("Surah Al-Waqi'ah", 56, 1),
        Triple("Surah Al-Kahf", 18, 1),
        Triple("The 4 Quls (112:1)", 112, 1),
    )

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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .statusBarsPadding()
        ) {
            // Full Pill M3 Search Bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                "Search surahs, ayahs (e.g. 2:255), reciters...",
                                style = MaterialTheme.typography.bodyLarge,
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
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // If searching: display results
            if (searchQuery.isNotBlank()) {
                // Filter chips when searching
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(searchFilters, key = { it }) { filter ->
                        FilterChip(
                            selected = searchFilter == filter,
                            onClick = { searchFilter = filter },
                            label = { Text(filter) },
                            shape = RoundedCornerShape(8.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = LocalMiniPlayerInset.current + 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Direct Verse Match (plan.md §1.2: "Any Verse / Ayah by surah and ayah number")
                    if (parsedVerse != null && parsedVerse.surah != null && searchFilter in listOf("All", "Verses")) {
                        item {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        onVerseClick(parsedVerse.surah, parsedVerse.ayahNumber)
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
                                            onVerseClick(parsedVerse.surah, parsedVerse.ayahNumber)
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

                    // 2. Surah Results
                    if (searchFilter in listOf("All", "Surahs") && filteredSurahs.isNotEmpty()) {
                        item {
                            Text(
                                text = "Surahs (${filteredSurahs.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        items(filteredSurahs, key = { it.id }) { surah ->
                            SurahListItem(
                                surah = surah,
                                onClick = { onSurahClick(surah) },
                                onPlayNext = onPlayNext?.let { fn -> { fn(surah) } },
                                onAddToQueue = onAddToQueue?.let { fn -> { fn(surah) } },
                                onAddToPlaylist = onAddToPlaylist?.let { fn -> { fn(surah) } },
                            )
                        }
                    }

                    // 3. Reciter Results
                    if (searchFilter in listOf("All", "Reciters") && filteredReciters.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Reciters (${filteredReciters.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        items(filteredReciters, key = { it.id }) { reciter ->
                            ReciterSearchItem(
                                reciter = reciter,
                                onClick = { onReciterClick(reciter) }
                            )
                        }
                    }

                    // Empty State
                    if (parsedVerse == null && filteredSurahs.isEmpty() && filteredReciters.isEmpty()) {
                        item {
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
                // Discover & Browse View
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 0.dp, end = 0.dp, top = 0.dp, bottom = LocalMiniPlayerInset.current + 16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Popular Ayahs Shortcuts (plan.md §1.2: Popular Ayahs shortcuts)
                    item {
                        Text(
                            text = "Popular Recitations",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 16.dp)
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
                                            if (startAyah > 1) {
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

                    if (searchFilter == "Reciters") {
                        item {
                            Text(
                                text = "All Reciters (${uniqueReciters.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                        }

                        items(uniqueReciters, key = { it.id }) { reciter ->
                            ReciterSearchItem(
                                reciter = reciter,
                                onClick = { onReciterClick(reciter) },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                        }
                    } else {
                        // Browse Surahs dedicated entry
                        item {
                            Text(
                                text = "Surahs",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable(onClick = onBrowseSurahsClick),
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
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Browse Surahs",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .rotate(180f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReciterSearchItem(
    reciter: Reciter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val photoUrl = remember(reciter) {
        ReciterImages.getPhotoUrl(reciter.id, reciter.englishName, reciter.photoUrl)
    }
    var isImageError by remember(photoUrl) { mutableStateOf(false) }

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
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = reciter.englishName.take(1).uppercase(),
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reciter.englishName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${reciter.qiraah} • ${reciter.style ?: "Murattal"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = reciter.name,
                fontFamily = AmiriQuranFont,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
