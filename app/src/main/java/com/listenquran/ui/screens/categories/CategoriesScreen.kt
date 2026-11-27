package com.listenquran.ui.screens.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.listenquran.domain.model.BrowseCategory
import com.listenquran.domain.model.BrowseCategoryCatalog
import com.listenquran.domain.model.CategoryEntry
import com.listenquran.domain.model.CategoryGroup
import com.listenquran.domain.model.CategoryKind
import com.listenquran.domain.model.Surah
import com.listenquran.player.SleepTimerMode
import com.listenquran.player.cast.OutputRouteState
import com.listenquran.ui.components.LocalMiniPlayerInset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    surahs: List<Surah>,
    onBackClick: () -> Unit,
    onPlayCategory: (BrowseCategory, List<CategoryEntry>?, Int, Boolean, SleepTimerMode?) -> Unit,
    onPlayJuz: (Int) -> Unit,
    onSaveCategoryAsPlaylist: (BrowseCategory, List<CategoryEntry>?) -> Unit,
    onPlayNext: ((Surah) -> Unit)? = null,
    onAddToQueue: ((Surah) -> Unit)? = null,
    onAddToPlaylist: ((Surah) -> Unit)? = null,
    outputRouteState: OutputRouteState = OutputRouteState(),
    onOpenOutputPicker: (() -> Unit)? = null,
    defaultSleepTimerMinutes: Int = 30,
    modifier: Modifier = Modifier,
) {
    var selectedGroupName by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedCategoryForSheet by remember { mutableStateOf<BrowseCategory?>(null) }
    var showJuzSheet by remember { mutableStateOf(false) }

    val selectedGroup = remember(selectedGroupName) {
        selectedGroupName?.let { name -> CategoryGroup.entries.find { it.name == name } }
    }

    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val columns = when {
        screenWidthDp >= 840 -> 4
        screenWidthDp >= 600 -> 3
        else -> 2
    }

    val groupedCategories = remember(selectedGroup) {
        val groupsToShow = if (selectedGroup != null) listOf(selectedGroup) else CategoryGroup.entries
        groupsToShow.map { group ->
            group to BrowseCategoryCatalog.allCategories.filter { it.group == group }
        }
    }

    val openCategory: (BrowseCategory) -> Unit = { category ->
        if (category.kind == CategoryKind.JUZ_BROWSER) {
            showJuzSheet = true
        } else {
            selectedCategoryForSheet = category
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Browse Categories",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "filter_all") {
                    val selected = selectedGroup == null
                    FilterChip(
                        selected = selected,
                        onClick = { selectedGroupName = null },
                        label = {
                            Text(
                                text = "All (${BrowseCategoryCatalog.allCategories.size})",
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
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
                items(CategoryGroup.entries, key = { it.name }) { group ->
                    val selected = selectedGroup == group
                    FilterChip(
                        selected = selected,
                        onClick = { selectedGroupName = group.name },
                        label = {
                            Text(
                                text = group.label,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
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

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 0.dp,
                    end = 0.dp,
                    top = 8.dp,
                    bottom = LocalMiniPlayerInset.current + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                groupedCategories.forEach { (group, categories) ->
                    item(key = "group_header_${group.name}", contentType = "group_header") {
                        Text(
                            text = group.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp),
                        )
                    }

                    val rows = categories.chunked(columns)
                    items(
                        items = rows,
                        key = { row -> "row_${group.name}_${row.joinToString("_") { it.id }}" },
                        contentType = { "category_row" },
                    ) { row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            for (category in row) {
                                CategoryCard(
                                    category = category,
                                    onClick = { openCategory(category) },
                                    onQuickPlay = if (category.kind == CategoryKind.CURATED_COLLECTION) {
                                        { onPlayCategory(category, null, 0, false, null) }
                                    } else null,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            repeat(columns - row.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }

    selectedCategoryForSheet?.let { category ->
        CategoryDetailSheet(
            category = category,
            surahs = surahs,
            onDismiss = { selectedCategoryForSheet = null },
            onPlayAll = { entries -> onPlayCategory(category, entries, 0, false, null) },
            onShuffle = { entries -> onPlayCategory(category, entries, 0, true, null) },
            onPlayEntry = { index, entries -> onPlayCategory(category, entries, index, false, null) },
            onSaveAsPlaylist = { entries -> onSaveCategoryAsPlaylist(category, entries) },
            onPlayWithSleepTimer = { entries, mode -> onPlayCategory(category, entries, 0, false, mode) },
            onPlayNext = onPlayNext,
            onAddToQueue = onAddToQueue,
            onAddToPlaylist = onAddToPlaylist,
            outputRouteState = outputRouteState,
            onOpenOutputPicker = onOpenOutputPicker,
            defaultSleepTimerMinutes = defaultSleepTimerMinutes,
        )
    }

    if (showJuzSheet) {
        JuzBrowserSheet(
            surahs = surahs,
            onDismiss = { showJuzSheet = false },
            onSelectJuz = onPlayJuz,
        )
    }
}
