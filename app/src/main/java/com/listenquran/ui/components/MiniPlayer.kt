package com.listenquran.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.listenquran.player.PlaybackState

import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MiniPlayer(
    playbackState: PlaybackState,
    currentPositionProvider: () -> Long,
    isLiked: Boolean,
    onTogglePlayPause: () -> Unit,
    onToggleLike: () -> Unit,
    onOpenFullPlayer: () -> Unit,
    onStopRadio: (() -> Unit)? = null,
    onStopHifz: (() -> Unit)? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier,
) {
    val isRadio = playbackState.isRadio
    val station = playbackState.currentRadioStation
    val surah = playbackState.currentSurah

    if (!isRadio && surah == null) return

    val isHifz = playbackState.isHifzActive && playbackState.hifzConfig != null
    val hifzConfig = playbackState.hifzConfig
    val repeatTotal = if (hifzConfig?.repeatCount == 0) "∞" else "${hifzConfig?.repeatCount ?: 0}"
    val rangeStr = if (hifzConfig != null && hifzConfig.startAyah == hifzConfig.endAyah) {
        "${hifzConfig.surahNumber}:${hifzConfig.startAyah}"
    } else if (hifzConfig != null) {
        "${hifzConfig.surahNumber}:${hifzConfig.startAyah}–${hifzConfig.endAyah}"
    } else ""
    val hifzStatus = "Repeating $rangeStr · ${playbackState.hifzCurrentLoop} of $repeatTotal"

    val title = if (isRadio) station?.name ?: surah?.nameSimple ?: "Quran Radio" else surah?.nameSimple ?: ""
    val reciter = playbackState.currentReciter?.englishName ?: ""
    val subtitle = if (isHifz) {
        hifzStatus
    } else if (isRadio) {
        "Radio · ${station?.name ?: "Quran.com"}"
    } else if (playbackState.isSyncAvailable) {
        "$reciter • Ayah ${playbackState.currentAyahNumber}"
    } else {
        reciter
    }

    val containerModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "player_container"),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = { _, _ -> tween(500, easing = FastOutSlowInEasing) }
            )
        }
    } else Modifier

    val artworkModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedElement(
                state = rememberSharedContentState(key = "player_artwork"),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = { _, _ -> tween(500, easing = FastOutSlowInEasing) }
            )
        }
    } else Modifier

    val titleModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedElement(
                state = rememberSharedContentState(key = "player_title"),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = { _, _ -> tween(500, easing = FastOutSlowInEasing) }
            )
        }
    } else Modifier

    val subtitleModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedElement(
                state = rememberSharedContentState(key = "player_reciter"),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = { _, _ -> tween(500, easing = FastOutSlowInEasing) }
            )
        }
    } else Modifier

    Surface(
        onClick = onOpenFullPlayer,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 8.dp)
            .semantics {
                contentDescription = if (isRadio) {
                    "Radio station ${station?.name ?: "Quran Radio"}, ${if (playbackState.isPlaying) "Playing" else "Paused"}, tap to open player"
                } else {
                    "Now playing ${surah?.nameSimple}, tap to open player"
                }
            }
            .then(containerModifier),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Thumbnail container (44x44dp, shape: 10dp)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isRadio) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer)
                        .then(artworkModifier),
                    contentAlignment = Alignment.Center
                ) {
                    if (isRadio) {
                        if (!station?.bannerUrl.isNullOrBlank()) {
                            coil.compose.AsyncImage(
                                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                    .data(station?.bannerUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = station?.name,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Radio,
                                contentDescription = "Quran Radio",
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    } else {
                        Text(
                            text = "${surah?.id}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 15.sp,
                        )
                    }
                }

                // Title & Subtitle column
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isRadio) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(end = 6.dp)
                            ) {
                                Text(
                                    text = "RADIO",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiary,
                                    fontSize = 9.sp,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        } else if (isHifz) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(end = 6.dp)
                            ) {
                                Text(
                                    text = "REPEAT",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiary,
                                    fontSize = 9.sp,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = titleModifier
                        )
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = subtitleModifier
                    )
                }

                // Stop Radio Button, Stop Repetition Button, or Heart / Like button (for Surahs)
                if (isRadio) {
                    IconButton(
                        onClick = { onStopRadio?.invoke() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Stop Radio",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                } else if (isHifz && onStopHifz != null) {
                    IconButton(
                        onClick = onStopHifz,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Stop Repetition",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = onToggleLike,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = if (isLiked) "Unlike" else "Like",
                            tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // M3 Play/Pause FAB (40x40dp, shape: 12dp, container: primary)
                FilledIconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    )
                ) {
                    if (playbackState.isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = if (playbackState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Bottom edge linear progress indicator
            if (isRadio) {
                if (playbackState.isBuffering) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f),
                    )
                }
            } else if (playbackState.durationMs > 0) {
                LinearProgressIndicator(
                    progress = {
                        val dur = playbackState.durationMs
                        if (dur > 0) {
                            (currentPositionProvider().toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                        } else 0f
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f),
                )
            }
        }
    }
}
