package com.listenquran.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.CompareArrows
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.listenquran.domain.model.Ayah
import com.listenquran.domain.model.Reciter
import com.listenquran.domain.model.Surah
import com.listenquran.domain.model.VerseTiming
import com.listenquran.ui.theme.AmiriQuranFont
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReciterCompareBottomSheet(
    ayah: Ayah,
    surah: Surah?,
    reciters: List<Reciter>,
    currentReciter: Reciter?,
    onDismiss: () -> Unit,
    onPauseMainPlayer: () -> Unit,
    onSetDefaultReciter: (Reciter) -> Unit,
    getVerseAudioTiming: suspend (Int, Int, String) -> Pair<String, VerseTiming?>?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Scoped lightweight secondary ExoPlayer for previewing reciters
    val previewPlayer = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(Unit) {
        onDispose {
            previewPlayer.stop()
            previewPlayer.release()
        }
    }

    var activePlayingReciterId by remember { mutableStateOf<Int?>(null) }
    var isLoadingAudio by remember { mutableStateOf(false) }

    // Select 3 reciters to compare: current reciter + 2 distinct prominent reciters
    val compareReciters = remember(reciters, currentReciter) {
        val list = mutableListOf<Reciter>()
        currentReciter?.let { list.add(it) }

        val candidateIds = listOf(7, 2, 6, 3, 4) // Mishary, AbdulBaset, Al-Husary, Sudais, Shatri
        for (id in candidateIds) {
            if (list.size >= 3) break
            reciters.find { it.id == id && list.none { added -> added.id == it.id } }?.let { list.add(it) }
        }
        for (r in reciters) {
            if (list.size >= 3) break
            if (list.none { it.id == r.id }) list.add(r)
        }
        list
    }

    // Monitor preview playback position to stop at timestampTo
    LaunchedEffect(activePlayingReciterId) {
        while (isActive && activePlayingReciterId != null) {
            delay(200)
            if (!previewPlayer.isPlaying && previewPlayer.playbackState == Player.STATE_ENDED) {
                activePlayingReciterId = null
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            previewPlayer.stop()
            onDismiss()
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.CompareArrows,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Column {
                    Text(
                        text = "Reciter A/B Comparison",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Surah ${surah?.nameSimple ?: ""} • Verse ${ayah.verseKey}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Ayah Scripture Preview Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = ayah.textUthmani,
                        fontFamily = AmiriQuranFont,
                        fontSize = 24.sp,
                        lineHeight = 40.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = ayah.translation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "COMPARE RECITATIONS",
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.2.sp,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Reciter Cards
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                compareReciters.forEach { reciter ->
                    val isPlayingThis = activePlayingReciterId == reciter.id
                    val isDefault = currentReciter?.id == reciter.id

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (isPlayingThis) 1.5.dp else 1.dp,
                                color = if (isPlayingThis) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(16.dp)
                            ),
                        shape = RoundedCornerShape(16.dp),
                        color = if (isPlayingThis) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                        else MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Initials avatar
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = reciter.englishName.split(" ")
                                                .mapNotNull { it.firstOrNull()?.toString() }
                                                .take(2).joinToString(""),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = reciter.englishName,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (isDefault) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = CircleShape,
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                ) {
                                                    Text(
                                                        text = "Current",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = "${reciter.qiraah} • ${reciter.style ?: "Murattal"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                // Play / Pause Button
                                FilledIconButton(
                                    onClick = {
                                        if (isPlayingThis) {
                                            previewPlayer.pause()
                                            activePlayingReciterId = null
                                        } else {
                                            onPauseMainPlayer()
                                            isLoadingAudio = true
                                            coroutineScope.launch {
                                                val timingData = getVerseAudioTiming(reciter.id, ayah.surahNumber, ayah.verseKey)
                                                isLoadingAudio = false
                                                if (timingData != null) {
                                                    val (url, timing) = timingData
                                                    val startMs = timing?.timestampFrom ?: 0L
                                                    val endMs = timing?.timestampTo ?: 0L

                                                    previewPlayer.stop()
                                                    previewPlayer.setMediaItem(
                                                        MediaItem.fromUri(url),
                                                        startMs
                                                    )
                                                    previewPlayer.prepare()
                                                    previewPlayer.play()
                                                    activePlayingReciterId = reciter.id

                                                    // Auto stop at verse end
                                                    if (endMs > startMs) {
                                                        launch {
                                                            while (previewPlayer.isPlaying && previewPlayer.currentPosition < endMs) {
                                                                delay(100)
                                                            }
                                                            if (activePlayingReciterId == reciter.id) {
                                                                previewPlayer.pause()
                                                                activePlayingReciterId = null
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = if (isPlayingThis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                                        contentColor = if (isPlayingThis) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    if (isLoadingAudio && activePlayingReciterId == reciter.id) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    } else if (isPlayingThis) {
                                        Icon(Icons.Default.Pause, contentDescription = "Pause Preview")
                                    } else {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "Play Preview")
                                    }
                                }
                            }

                            // Set as default reciter action chip
                            if (!isDefault) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            onSetDefaultReciter(reciter)
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Set as default reciter",
                                            style = MaterialTheme.typography.labelSmall
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
}
