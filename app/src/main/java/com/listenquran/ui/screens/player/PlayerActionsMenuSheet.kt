package com.listenquran.ui.screens.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.listenquran.player.PlaybackState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerActionsMenuSheet(
    playbackState: PlaybackState,
    isLiked: Boolean = false,
    onToggleLike: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onShareSurah: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onOpenQueue: () -> Unit,
    onGoToReciter: () -> Unit,
    onGoToReciterRadio: () -> Unit = {},
    onOpenSleepTimer: () -> Unit,
    onPlayNextInQueue: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val surah = playbackState.currentSurah
    val reciter = playbackState.currentReciter

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header info
            if (surah != null) {
                Column(modifier = Modifier.padding(bottom = 16.dp, start = 8.dp)) {
                    Text(
                        text = "Surah ${surah.nameSimple}",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = reciter?.englishName ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (onPlayNextInQueue != null) {
                ActionItem(
                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                    title = "Play Next",
                    subtitle = "Add to the top of queue",
                    onClick = {
                        onDismiss()
                        onPlayNextInQueue()
                    }
                )
            }

            if (onAddToQueue != null) {
                ActionItem(
                    icon = Icons.Default.PlaylistAdd,
                    title = "Add to Queue",
                    subtitle = "Add to the end of queue",
                    onClick = {
                        onDismiss()
                        onAddToQueue()
                    }
                )
            }

            if (onToggleLike != null) {
                ActionItem(
                    icon = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    title = if (isLiked) "Remove from Liked Surahs" else "Save to Liked Surahs",
                    subtitle = if (isLiked) "In your Liked Surahs collection" else "Save to your Liked Surahs collection",
                    onClick = {
                        onDismiss()
                        onToggleLike()
                    }
                )
            }

            ActionItem(
                icon = Icons.Default.Share,
                title = "Share Surah",
                subtitle = "Share link & details with friends",
                onClick = {
                    onDismiss()
                    onShareSurah()
                }
            )

            ActionItem(
                icon = Icons.Default.PlaylistAdd,
                title = "Add to Playlist",
                subtitle = "Add to a custom playlist or collection",
                onClick = {
                    onDismiss()
                    onAddToPlaylist()
                }
            )

            ActionItem(
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                title = "Go to Queue",
                subtitle = "${playbackState.queue.size} upcoming tracks",
                onClick = {
                    onDismiss()
                    onOpenQueue()
                }
            )

            if (reciter != null) {
                ActionItem(
                    icon = Icons.Rounded.Person,
                    title = "Go to Reciter",
                    subtitle = reciter.englishName,
                    onClick = {
                        onDismiss()
                        onGoToReciter()
                    }
                )

                ActionItem(
                    icon = Icons.Rounded.Radio,
                    title = "Go to Reciter Radio",
                    subtitle = "Continuous shuffle of ${reciter.englishName}",
                    onClick = {
                        onDismiss()
                        onGoToReciterRadio()
                    }
                )
            }

            val timerSubtitle = when {
                playbackState.stopAtEndOfSurah -> "Stopping at end of surah"
                playbackState.sleepTimerMode is com.listenquran.player.SleepTimerMode.EndOfPlaylist -> "Stopping at end of playlist"
                playbackState.sleepTimerRemainingSeconds != null -> {
                    val m = playbackState.sleepTimerRemainingSeconds / 60
                    val s = playbackState.sleepTimerRemainingSeconds % 60
                    "Stopping in ${m}m ${s}s"
                }
                else -> "Set a timer to pause playback"
            }

            val isTimerActive = playbackState.sleepTimerRemainingSeconds != null ||
                    playbackState.stopAtEndOfSurah ||
                    playbackState.sleepTimerMode !is com.listenquran.player.SleepTimerMode.Off

            ActionItem(
                icon = if (isTimerActive) Icons.Filled.Timer else Icons.Default.Timer,
                title = "Sleep Timer",
                subtitle = timerSubtitle,
                onClick = {
                    onDismiss()
                    onOpenSleepTimer()
                }
            )
        }
    }
}

@Composable
private fun ActionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = androidx.compose.ui.graphics.Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
