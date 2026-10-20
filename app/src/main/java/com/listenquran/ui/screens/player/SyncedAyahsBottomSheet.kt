package com.listenquran.ui.screens.player

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Spellcheck
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.listenquran.data.repository.TranslationState
import com.listenquran.domain.model.Ayah
import com.listenquran.domain.model.Reciter
import com.listenquran.domain.model.VerseTiming
import com.listenquran.player.PlaybackState
import com.listenquran.ui.components.LocalMiniPlayerInset
import com.listenquran.ui.theme.AmiriQuranFont
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncedAyahsBottomSheet(
    playbackState: PlaybackState,
    verses: List<Ayah>,
    currentWordIndex: Int = 0,
    reciters: List<Reciter> = emptyList(),
    isWordByWordEnabled: Boolean = false,
    onToggleWordByWord: () -> Unit = {},
    isTranslationEnabled: Boolean = false,
    onToggleTranslation: () -> Unit = {},
    translationState: TranslationState = TranslationState.Success(0, emptyMap()),
    onRetryTranslation: () -> Unit = {},
    onDismiss: () -> Unit,
    onSeekToAyah: (Int) -> Unit,
    onSaveBookmark: (Ayah, String) -> Unit,
    onShareAsImage: (Ayah) -> Unit,
    onPauseMainPlayer: () -> Unit = {},
    onSetDefaultReciter: (Reciter) -> Unit = {},
    getVerseAudioTiming: suspend (Int, Int, String) -> Pair<String, VerseTiming?>? = { _, _, _ -> null },
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var selectedAyahForNote by remember { mutableStateOf<Ayah?>(null) }
    var noteText by remember { mutableStateOf("") }
    var comparingAyah by remember { mutableStateOf<Ayah?>(null) }
    val bottomInset = LocalMiniPlayerInset.current

    val translationMap = remember(translationState) {
        (translationState as? TranslationState.Success)?.translations ?: emptyMap()
    }

    // Auto-scroll state & geometry
    val activeAyahIndex = remember(playbackState.currentAyahNumber, verses) {
        verses.indexOfFirst { it.ayahNumber == playbackState.currentAyahNumber }
    }

    val isUserDragging by listState.interactionSource.collectIsDraggedAsState()
    var lazyListCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val density = LocalDensity.current

    // Single deterministic auto-scroll controller
    val scroller = remember(listState) {
        ReaderAutoScroller(listState, coroutineScope)
    }
    DisposableEffect(scroller) {
        onDispose {
            scroller.dispose()
        }
    }
    val isAutoFollowPaused by scroller.isAutoFollowPaused.collectAsState()

    // Keep live viewport coordinates and insets updated in scroller
    scroller.lazyListCoordinates = lazyListCoordinates
    scroller.viewportHeight = lazyListCoordinates?.size?.height?.toFloat() ?: 0f
    scroller.bottomPaddingPx = with(density) { (32.dp + bottomInset).toPx() }

    // User drag pauses auto-follow; 3s idle resumes
    LaunchedEffect(isUserDragging) {
        scroller.onUserDrag(isUserDragging)
    }

    // Single playback target stream: submitted whenever active verse or active word changes
    LaunchedEffect(playbackState.currentAyahNumber, currentWordIndex, playbackState.isSyncAvailable) {
        if (playbackState.isSyncAvailable && activeAyahIndex >= 0) {
            scroller.submitTarget(
                ScrollTarget(
                    verseIndex = activeAyahIndex,
                    wordIndex = currentWordIndex,
                    reason = ScrollReason.PLAYBACK
                )
            )
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
            // Header: Surah title, Ayah counter, Word-by-Word toggle, Translation toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = playbackState.currentSurah?.nameSimple ?: "Surah Scripture",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val subtitle = buildString {
                        if (playbackState.isSyncAvailable) {
                            append("Ayah ${playbackState.currentAyahNumber} of ${verses.size}")
                        } else {
                            append("${verses.size} Ayahs")
                        }
                        if (isTranslationEnabled) {
                            val transName = (translationState as? TranslationState.Success)?.translationName ?: "Saheeh International"
                            append(" · $transName")
                        }
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!playbackState.isSyncAvailable) {
                        AssistChip(
                            onClick = {},
                            label = { Text("Sync unavailable", style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                leadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        )
                    }

                    // Word-by-Word Toggle (Spellcheck icon)
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = {
                            PlainTooltip {
                                Text(if (isWordByWordEnabled) "Word by word: On" else "Word by word: Off")
                            }
                        },
                        state = rememberTooltipState()
                    ) {
                        FilledTonalIconToggleButton(
                            checked = isWordByWordEnabled,
                            onCheckedChange = { onToggleWordByWord() },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = if (isWordByWordEnabled) Icons.Filled.Spellcheck else Icons.Outlined.Spellcheck,
                                contentDescription = if (isWordByWordEnabled) "Word by word, on" else "Word by word, off",
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Translation Toggle (Translate icon)
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = {
                            PlainTooltip {
                                Text(if (isTranslationEnabled) "Translation: On" else "Translation: Off")
                            }
                        },
                        state = rememberTooltipState()
                    ) {
                        FilledTonalIconToggleButton(
                            checked = isTranslationEnabled,
                            onCheckedChange = { onToggleTranslation() },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Translate,
                                contentDescription = if (isTranslationEnabled) "English translation, on" else "English translation, off",
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { lazyListCoordinates = it },
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 32.dp + bottomInset)
                    ) {
                        items(
                            items = verses,
                            key = { it.id },
                            contentType = { "ayah_card" }
                        ) { ayah ->
                            val isActive = playbackState.isSyncAvailable && ayah.ayahNumber == playbackState.currentAyahNumber

                            AyahCard(
                                ayah = ayah,
                                isActive = isActive,
                                isWordByWord = isWordByWordEnabled,
                                isTranslationEnabled = isTranslationEnabled,
                                translationText = translationMap[ayah.ayahNumber],
                                translationState = translationState,
                                onRetryTranslation = onRetryTranslation,
                                currentWordIndex = if (isActive) currentWordIndex else 0,
                                onActiveWordPositioned = if (isActive) {
                                    { wordCoords ->
                                        scroller.onActiveWordPositioned(activeAyahIndex, currentWordIndex, wordCoords)
                                    }
                                } else null,
                                onCardClick = {
                                    val targetIndex = verses.indexOfFirst { it.ayahNumber == ayah.ayahNumber }
                                    if (targetIndex >= 0) {
                                        scroller.submitTarget(
                                            ScrollTarget(
                                                verseIndex = targetIndex,
                                                wordIndex = 1,
                                                reason = ScrollReason.TAP
                                            )
                                        )
                                    }
                                    onSeekToAyah(ayah.ayahNumber)
                                },
                                onCompareClick = { comparingAyah = ayah },
                                onBookmarkClick = {
                                    selectedAyahForNote = ayah
                                    noteText = ""
                                },
                                onShareImageClick = { onShareAsImage(ayah) },
                                onCopyClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val copyTranslation = if (isTranslationEnabled) (translationMap[ayah.ayahNumber] ?: ayah.translation) else ""
                                    val clipText = if (copyTranslation.isNotBlank()) {
                                        "${ayah.textUthmani}\n\n\"$copyTranslation\" [Surah ${ayah.verseKey}]"
                                    } else {
                                        "${ayah.textUthmani} [Surah ${ayah.verseKey}]"
                                    }
                                    val clip = ClipData.newPlainText("Ayah ${ayah.verseKey}", clipText)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Copied Ayah ${ayah.verseKey}", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }

                    // Floating Follow Recitation Chip
                    if (isAutoFollowPaused && playbackState.isSyncAvailable) {
                        Surface(
                            onClick = {
                                scroller.resumeFollow(reason = ScrollReason.JUMP)
                            },
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 16.dp + bottomInset)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Navigation,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Follow recitation",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
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

    // Reciter A/B Comparison Bottom Sheet
    if (comparingAyah != null) {
        ReciterCompareBottomSheet(
            ayah = comparingAyah!!,
            surah = playbackState.currentSurah,
            reciters = reciters,
            currentReciter = playbackState.currentReciter,
            onDismiss = { comparingAyah = null },
            onPauseMainPlayer = onPauseMainPlayer,
            onSetDefaultReciter = onSetDefaultReciter,
            getVerseAudioTiming = getVerseAudioTiming
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AyahCard(
    ayah: Ayah,
    isActive: Boolean,
    isWordByWord: Boolean,
    isTranslationEnabled: Boolean,
    translationText: String?,
    translationState: TranslationState,
    onRetryTranslation: () -> Unit,
    currentWordIndex: Int,
    onActiveWordPositioned: ((LayoutCoordinates) -> Unit)? = null,
    onCardClick: () -> Unit,
    onCompareClick: () -> Unit,
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
            .combinedClickable(
                onClick = onCardClick,
                onLongClick = onCompareClick
            ),
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
                    IconButton(onClick = onCompareClick, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.CompareArrows,
                            contentDescription = "Compare Recitations",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
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
                        val wordModifier = if (isSpokenWord && onActiveWordPositioned != null) {
                            Modifier.onGloballyPositioned { coords ->
                                onActiveWordPositioned(coords)
                            }
                        } else {
                            Modifier
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(wordModifier)
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
                                lineHeight = 44.sp,
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
                    lineHeight = 48.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // English Translation Line
            if (isTranslationEnabled) {
                when (translationState) {
                    is TranslationState.Loading -> {
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .height(14.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f))
                        )
                    }
                    is TranslationState.Error -> {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Translation unavailable offline",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            if (!translationState.isOffline) {
                                Text(
                                    text = "Retry",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { onRetryTranslation() }
                                )
                            }
                        }
                    }
                    is TranslationState.Success -> {
                        val textToDisplay = translationText ?: ayah.translation
                        if (textToDisplay.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = textToDisplay,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 22.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
