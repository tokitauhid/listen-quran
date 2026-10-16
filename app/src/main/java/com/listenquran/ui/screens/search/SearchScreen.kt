package com.listenquran.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.listenquran.domain.model.Reciter
import com.listenquran.domain.model.Surah
import com.listenquran.ui.screens.home.SurahListItem
import com.listenquran.ui.theme.AmiriQuranFont

data class BrowseCategory(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val surahTargetId: Int,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    surahs: List<Surah>,
    reciters: List<Reciter>,
    onSurahClick: (Surah) -> Unit,
    onReciterClick: (Reciter) -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }

    val popularShortcuts = listOf(
        "Ayatul Kursi (2:255)" to 2,
        "Surah Al-Mulk" to 67,
        "Surah Yasin" to 36,
        "Surah Ar-Rahman" to 55,
        "Surah Al-Waqi'ah" to 56,
        "Surah Al-Kahf" to 18,
    )

    val categories = listOf(
        BrowseCategory("Juz & Hizb", "Listen section by section", Icons.Rounded.MenuBook, 1),
        BrowseCategory("Ruqyah & Peace", "Protection & calming verses", Icons.Rounded.HealthAndSafety, 1),
        BrowseCategory("Sleep Recitations", "Peaceful recitations before sleep", Icons.Rounded.Bedtime, 67),
        BrowseCategory("Taraweeh & Qiyam", "Night prayers & Tahajjud", Icons.Rounded.NightsStay, 55),
        BrowseCategory("Quranic Duas", "Supplications from the Quran", Icons.Rounded.Favorite, 2),
        BrowseCategory("The 4 Quls", "Surahs Al-Ikhlas to An-Nas", Icons.Rounded.Shield, 112),
    )

    val filteredSurahs = remember(searchQuery, surahs) {
        if (searchQuery.isBlank()) emptyList()
        else surahs.filter {
            it.nameSimple.contains(searchQuery, ignoreCase = true) ||
            it.translatedName.contains(searchQuery, ignoreCase = true) ||
            it.id.toString() == searchQuery.trim() ||
            it.nameArabic.contains(searchQuery)
        }
    }

    val filteredReciters = remember(searchQuery, reciters) {
        if (searchQuery.isBlank()) emptyList()
        else reciters.filter {
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
                    .padding(horizontal = 16.dp, vertical = 12.dp),
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
                                "Search surahs, ayahs, reciters...",
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
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (filteredSurahs.isEmpty() && filteredReciters.isEmpty()) {
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

                    if (filteredSurahs.isNotEmpty()) {
                        item {
                            Text(
                                text = "Surahs",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        items(filteredSurahs) { surah ->
                            SurahListItem(surah = surah, onClick = { onSurahClick(surah) })
                        }
                    }

                    if (filteredReciters.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Reciters",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        items(filteredReciters) { reciter ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onReciterClick(reciter) },
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
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = reciter.englishName.take(1),
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = reciter.englishName,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = reciter.name,
                                            fontFamily = AmiriQuranFont,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Discover & Browse View
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 90.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Popular Ayahs Shortcuts
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
                            items(popularShortcuts) { (name, targetId) ->
                                AssistChip(
                                    onClick = {
                                        surahs.find { it.id == targetId }?.let(onSurahClick)
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

                    // 2-Column Browse Categories
                    item {
                        Text(
                            text = "Browse Categories",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val chunked = categories.chunked(2)
                            for (pair in chunked) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    for (category in pair) {
                                        CategoryCard(
                                            category = category,
                                            onClick = {
                                                surahs.find { it.id == category.surahTargetId }?.let(onSurahClick)
                                            },
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
        modifier = modifier
            .height(104.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
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
