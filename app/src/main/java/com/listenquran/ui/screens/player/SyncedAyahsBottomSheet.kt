package com.listenquran.ui.screens.player

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.listenquran.domain.model.Ayah
import com.listenquran.player.PlaybackState
import com.listenquran.ui.theme.AmiriQuranFont
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncedAyahsBottomSheet(
    playbackState: PlaybackState,
    verses: List<Ayah>,
    currentWordIndex: Int = 0,
    onDismiss: () -> Unit,
    onSeekToAyah: (Int) -> Unit,
    onSaveBookmark: (Ayah, String) -> Unit,
    onShareAsImage: (Ayah) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var isWordByWordMode by remember { mutableStateOf(false) }
    var selectedAyahForNote by remember { mutableStateOf<Ayah?>(null) }
    var noteText by remember { mutableStateOf("") }

    // Auto-scroll to active Ayah
    val activeAyahIndex = verses.indexOfFirst { it.ayahNumber == playbackState.currentAyahNumber }
    LaunchedEffect(playbackState.currentAyahNumber) {
        if (activeAyahIndex >= 0) {
            coroutineScope.launch {
                listState.animateScrollToItem((activeAyahIndex - 1).coerceAtLeast(0))
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Header: Surah title, Ayah counter, Word-by-Word toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = playbackState.currentSurah?.nameSimple ?: "Surah Scripture",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Ayah ${playbackState.currentAyahNumber} of ${verses.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FilterChip(
                    selected = isWordByWordMode,
                    onClick = { isWordByWordMode = !isWordByWordMode },
                    label = { Text("Word-by-Word") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Translate,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Ayahs list
            if (verses.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    itemsIndexed(verses) { _, ayah ->
                        val isActive = ayah.ayahNumber == playbackState.currentAyahNumber

                        AyahCard(
                            ayah = ayah,
                            isActive = isActive,
                            isWordByWord = isWordByWordMode,
                            currentWordIndex = if (isActive) currentWordIndex else 0,
                            onCardClick = { onSeekToAyah(ayah.ayahNumber) },
                            onBookmarkClick = {
                                selectedAyahForNote = ayah
                                noteText = ""
                            },
                            onShareImageClick = { onShareAsImage(ayah) },
                            onCopyClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText(
                                    "Ayah ${ayah.verseKey}",
                                    "${ayah.textUthmani}\n\n\"${ayah.translation}\" [Surah ${ayah.verseKey}]"
                                )
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Copied Ayah ${ayah.verseKey}", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }

    // Bookmark Note Dialog (Tadabbur)
    if (selectedAyahForNote != null) {
        val targetAyah = selectedAyahForNote!!
        AlertDialog(
            onDismissRequest = { selectedAyahForNote = null },
            title = { Text("Bookmark Ayah ${targetAyah.verseKey}") },
            text = {
                Column {
                    Text(
                        text = "Add a personal reflection note (Tadabbur) for this verse:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        label = { Text("Reflection Note") },
                        placeholder = { Text("What touches your heart about this verse...") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveBookmark(targetAyah, noteText)
                        selectedAyahForNote = null
                    }
                ) {
                    Text("Save Bookmark")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedAyahForNote = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun AyahCard(
    ayah: Ayah,
    isActive: Boolean,
    isWordByWord: Boolean,
    currentWordIndex: Int,
    onCardClick: () -> Unit,
    onBookmarkClick: () -> Unit,
    onShareImageClick: () -> Unit,
    onCopyClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (isActive) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f)
    }

    val borderModifier = if (isActive) {
        Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
    } else {
        Modifier
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(borderModifier)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onCardClick),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Verse medallion header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)
                ) {
                    Text(
                        text = "${ayah.ayahNumber}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onBookmarkClick, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.BookmarkBorder,
                            contentDescription = "Bookmark",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onShareImageClick, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = "Share as Image",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onCopyClick, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy text",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Arabic Scripture
            if (isWordByWord && ayah.words.isNotEmpty()) {
                // Word-by-word flow
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ayah.words.forEach { word ->
                        val isSpokenWord = isActive && word.position == currentWordIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSpokenWord) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainer
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = word.transliteration,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSpokenWord) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = word.translation,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSpokenWord) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = word.textUthmani,
                                fontFamily = AmiriQuranFont,
                                fontSize = 22.sp,
                                color = if (isSpokenWord) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = ayah.textUthmani,
                    fontFamily = AmiriQuranFont,
                    fontSize = 26.sp,
                    lineHeight = 44.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // English Translation
            Text(
                text = ayah.translation,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp
            )
        }
    }
}
