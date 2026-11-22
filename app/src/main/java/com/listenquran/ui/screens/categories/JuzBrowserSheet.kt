package com.listenquran.ui.screens.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.listenquran.domain.model.BrowseCategoryCatalog
import com.listenquran.domain.model.JuzSummary
import com.listenquran.domain.model.Surah
import com.listenquran.domain.model.khatam.QuranAyahMetadata
import com.listenquran.ui.theme.AmiriQuranFont
import com.listenquran.ui.theme.BottomSheetShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JuzBrowserSheet(
    surahs: List<Surah>,
    onDismiss: () -> Unit,
    onSelectJuz: (Int) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val juzSummaries = remember { BrowseCategoryCatalog.allJuzSummaries() }
    val surahNames = remember(surahs) { surahs.associate { it.id to it.nameSimple } }
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val columns = if (screenWidthDp >= 600) 3 else 2

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = BottomSheetShape,
        containerColor = colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colorScheme.primaryContainer,
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoStories,
                        contentDescription = null,
                        tint = colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(10.dp),
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Browse by Juz",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                    )
                    Text(
                        text = "Tap any of the 30 Juz to play from its opening Ayah",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.5f))

            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(juzSummaries, key = { it.juzNumber }) { summary ->
                    val startSurahName = surahNames[summary.startSurahId]
                        ?: QuranAyahMetadata.surahEnglishNames.getOrElse(summary.startSurahId - 1) {
                            "Surah ${summary.startSurahId}"
                        }
                    JuzTileCard(
                        summary = summary,
                        startSurahName = startSurahName,
                        onClick = {
                            onSelectJuz(summary.juzNumber)
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun JuzTileCard(
    summary: JuzSummary,
    startSurahName: String,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = colorScheme.surfaceContainerLow,
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colorScheme.primaryContainer,
                ) {
                    Text(
                        text = "Juz ${summary.juzNumber}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }

                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Play Juz ${summary.juzNumber}",
                    tint = colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }

            Text(
                text = summary.arabicName,
                fontFamily = AmiriQuranFont,
                fontSize = 18.sp,
                lineHeight = 38.sp,
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = "$startSurahName ${summary.startSurahId}:${summary.startAyah}",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
