package com.listenquran.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.listenquran.domain.model.ListeningStatsSummary
import com.listenquran.domain.model.Reciter
import com.listenquran.domain.model.Surah
import com.listenquran.player.PlaybackState
import com.listenquran.player.SurahResumeProgress
import com.listenquran.ui.theme.AmiriQuranFont
import com.listenquran.util.ReciterImages
import java.util.Calendar

@Composable
fun HomeGreetingAndSuggestionHeader(
    statsSummary: ListeningStatsSummary,
    surahs: List<Surah>,
    onSurahClick: (Surah) -> Unit,
    modifier: Modifier = Modifier,
) {
    val calendar = remember { Calendar.getInstance() }
    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    val isFriday = calendar.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY

    val greeting = when {
        hour in 4..11 -> "Good morning"
        hour in 12..16 -> "Good afternoon"
        hour in 17..20 -> "Good evening"
        else -> "Peaceful night"
    }

    val (suggestedSurahId, suggestionSubtitle) = when {
        isFriday -> 18 to "Friday Sunnah · Al-Kahf"
        hour >= 20 || hour < 4 -> 67 to "Night reflection · Al-Mulk"
        hour in 4..10 -> 36 to "Morning recitation · Yasin"
        else -> 55 to "Afternoon calm · Ar-Rahman"
    }
    val suggestedSurah = remember(surahs, suggestedSurahId) {
        surahs.find { it.id == suggestedSurahId }
    }

    val todayMinutes = remember(statsSummary.weeklyActivity) {
        statsSummary.weeklyActivity.lastOrNull()?.minutes ?: 0
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = greeting,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(
                        imageVector = if (statsSummary.currentDayStreak > 0) {
                            Icons.Rounded.LocalFireDepartment
                        } else {
                            Icons.Rounded.Schedule
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp),
                    )
                    val pillText = when {
                        statsSummary.currentDayStreak > 0 && todayMinutes > 0 ->
                            "${statsSummary.currentDayStreak}d · ${todayMinutes}m today"
                        statsSummary.currentDayStreak > 0 ->
                            "${statsSummary.currentDayStreak}d streak"
                        todayMinutes > 0 ->
                            "${todayMinutes}m today"
                        else ->
                            "Start listening"
                    }
                    Text(
                        text = pillText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        if (suggestedSurah != null) {
            Surface(
                onClick = { onSurahClick(suggestedSurah) },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = suggestionSubtitle,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "Play",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
fun JumpBackInHeroCard(
    playbackState: PlaybackState,
    currentPositionMs: Long,
    fallbackProgress: SurahResumeProgress?,
    surahs: List<Surah>,
    reciters: List<Reciter>,
    onTogglePlayPause: () -> Unit,
    onResumeProgress: (SurahResumeProgress) -> Unit,
    onOpenFullPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeSurah = playbackState.currentSurah?.takeIf { !playbackState.isRadio }
    val activeReciter = playbackState.currentReciter
    val progressSurah = if (activeSurah == null && fallbackProgress != null) {
        surahs.find { it.id == fallbackProgress.surahId }
    } else null
    val progressReciter = if (activeSurah == null && fallbackProgress != null) {
        reciters.find { it.id == fallbackProgress.reciterId }
    } else null

    val surah = activeSurah ?: progressSurah ?: return
    val reciter = activeReciter ?: progressReciter
    val posMs = if (activeSurah != null) currentPositionMs else (fallbackProgress?.positionMs ?: 0L)
    val durMs = if (activeSurah != null) playbackState.durationMs else (fallbackProgress?.durationMs ?: 0L)
    val isPlaying = activeSurah != null && playbackState.isPlaying

    val fraction = if (durMs > 0L) (posMs.toFloat() / durMs.toFloat()).coerceIn(0f, 1f) else 0f
    val remainingMins = if (durMs > posMs) (((durMs - posMs) / 60_000L).toInt()).coerceAtLeast(1) else 0
    val photoUrl = remember(reciter) {
        reciter?.let { ReciterImages.getPhotoUrl(it.id, it.englishName, it.photoUrl) }
    }

    val colorScheme = MaterialTheme.colorScheme
    val heroBrush = remember(colorScheme) {
        Brush.linearGradient(
            listOf(
                colorScheme.primaryContainer.copy(alpha = 0.85f),
                colorScheme.surfaceContainerHigh,
            )
        )
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable {
                if (activeSurah != null) onOpenFullPlayer()
                else if (fallbackProgress != null) onResumeProgress(fallbackProgress)
            },
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(heroBrush)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    modifier = Modifier.size(56.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = surah.id.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        if (photoUrl != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(photoUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = reciter?.englishName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isPlaying) "NOW LISTENING" else "JUMP BACK IN",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    Text(
                        text = "${surah.id}. ${surah.nameSimple}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = surah.nameArabic,
                        fontFamily = AmiriQuranFont,
                        fontSize = 18.sp,
                        lineHeight = 36.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                        maxLines = 1,
                    )
                    if (reciter != null) {
                        Text(
                            text = reciter.englishName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                FilledIconButton(
                    onClick = {
                        if (activeSurah != null) {
                            onTogglePlayPause()
                        } else if (fallbackProgress != null) {
                            onResumeProgress(fallbackProgress)
                        }
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier.size(50.dp),
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Resume",
                        modifier = Modifier.size(26.dp),
                    )
                }
            }

            if (durMs > 0L) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = if (activeSurah != null) {
                                "Ayah ${playbackState.currentAyahNumber}"
                            } else {
                                "Ayah ${fallbackProgress?.lastAyahNumber ?: 1}"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (remainingMins > 0) {
                            Text(
                                text = "$remainingMins min left",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ContinueListeningCarousel(
    entries: List<SurahResumeProgress>,
    surahs: List<Surah>,
    reciters: List<Reciter>,
    onResumeProgress: (SurahResumeProgress) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) return

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(entries, key = { "resume_${it.surahId}_${it.reciterId}" }) { entry ->
            val surah = surahs.find { it.id == entry.surahId } ?: return@items
            val reciter = reciters.find { it.id == entry.reciterId }
            Surface(
                onClick = { onResumeProgress(entry) },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.width(204.dp),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${surah.id}. ${surah.nameSimple}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Resume",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Text(
                        text = reciter?.englishName ?: "Quran Recitation",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    LinearProgressIndicator(
                        progress = { entry.fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                    Text(
                        text = if (entry.remainingMinutes > 0) "${entry.remainingMinutes} min left" else "Almost done",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
    }
}

@Composable
fun BecauseYouListenedCarousel(
    reciter: Reciter,
    surahs: List<Surah>,
    excludeSurahId: Int?,
    onPlaySurahWithReciter: (Surah, Reciter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val recommendedIds = remember(excludeSurahId) {
        listOf(36, 55, 67, 18, 56, 19, 12, 2).filter { it != excludeSurahId }.take(6)
    }
    val recommendedSurahs = remember(surahs, recommendedIds) {
        recommendedIds.mapNotNull { id -> surahs.find { it.id == id } }
    }
    if (recommendedSurahs.isEmpty()) return

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(recommendedSurahs, key = { "rec_${reciter.id}_${it.id}" }) { surah ->
            Surface(
                onClick = { onPlaySurahWithReciter(surah, reciter) },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.width(156.dp),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = surah.nameArabic,
                        fontFamily = AmiriQuranFont,
                        fontSize = 20.sp,
                        lineHeight = 40.sp,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                    Text(
                        text = "${surah.id}. ${surah.nameSimple}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${surah.versesCount} Ayahs",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
