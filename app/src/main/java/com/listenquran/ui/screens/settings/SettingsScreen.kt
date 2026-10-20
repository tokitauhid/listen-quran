package com.listenquran.ui.screens.settings

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.StatFs
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.*
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
import androidx.core.content.ContextCompat
import com.listenquran.BuildConfig
import com.listenquran.data.local.entity.DownloadedSurahEntity
import com.listenquran.data.local.preferences.SettingsPreferences
import com.listenquran.domain.model.ListeningStatsSummary
import com.listenquran.domain.model.Reciter
import com.listenquran.domain.model.ThemeMode
import com.listenquran.ui.components.LocalMiniPlayerInset
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentTheme: ThemeMode,
    statsSummary: ListeningStatsSummary,
    settingsPreferences: SettingsPreferences,
    allReciters: List<Reciter>,
    defaultReciter: Reciter?,
    downloadedSurahs: List<DownloadedSurahEntity>,
    cacheSizeBytes: Long,
    totalDownloadedBytes: Long,
    onSelectTheme: (ThemeMode) -> Unit,
    onSelectDefaultReciter: (Reciter) -> Unit,
    onSelectPlaybackSpeed: (Float) -> Unit,
    onSelectCustomSleepTimerMinutes: (Int) -> Unit,
    onToggleWordByWord: (Boolean) -> Unit,
    onToggleTranslation: (Boolean) -> Unit,
    onSetHifzLoop: (Int, Int) -> Unit,
    onToggleDailyReminder: (Boolean, Int, Int) -> Unit,
    onToggleFridayReminder: (Boolean, Int, Int) -> Unit,
    onToggleStatsDetailsExpanded: (Boolean) -> Unit,
    onClearStats: () -> Unit,
    onClearAudioCache: () -> Unit,
    onClearAllDownloads: () -> Unit,
    onDeleteDownloadedSurah: (Int, Int) -> Unit,
    onExportBackup: (Uri) -> Unit,
    onImportBackup: (Uri, Boolean) -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    if (onBack != null) {
        BackHandler(onBack = onBack)
    }

    // Dialog & Interaction states
    var showReciterPicker by remember { mutableStateOf(false) }
    var showClearStatsDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showDeleteAllDownloadsDialog by remember { mutableStateOf(false) }
    var surahToDelete by remember { mutableStateOf<DownloadedSurahEntity?>(null) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var showRestoreOptionsDialog by remember { mutableStateOf(false) }
    var downloadedListExpanded by remember { mutableStateOf(false) }

    // SAF Launchers
    val backupExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) onExportBackup(uri)
    }

    val backupImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingRestoreUri = uri
            showRestoreOptionsDialog = true
        }
    }

    // Notification Permission
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        if (isGranted) {
            onToggleDailyReminder(true, settingsPreferences.dailyReminderHour, settingsPreferences.dailyReminderMinute)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings & Stats",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding()),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = LocalMiniPlayerInset.current + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. STATS SECTION
            item(key = "section_statistics") {
                StatisticsSection(
                    statsSummary = statsSummary,
                    isDetailsExpanded = settingsPreferences.statsDetailsExpanded,
                    onToggleDetails = { onToggleStatsDetailsExpanded(!settingsPreferences.statsDetailsExpanded) },
                    onClearStats = { showClearStatsDialog = true }
                )
            }

            // 2. APPEARANCE SECTION
            item(key = "section_appearance") {
                AppearanceSection(
                    currentTheme = currentTheme,
                    onSelectTheme = onSelectTheme
                )
            }

            // 3. AUDIO & PLAYBACK SECTION
            item(key = "section_playback") {
                AudioPlaybackSection(
                    defaultReciter = defaultReciter,
                    playbackSpeed = settingsPreferences.playbackSpeed,
                    customSleepTimerMinutes = settingsPreferences.customSleepTimerMinutes,
                    onChangeReciterClick = { showReciterPicker = true },
                    onSelectPlaybackSpeed = onSelectPlaybackSpeed,
                    onSelectSleepTimer = onSelectCustomSleepTimerMinutes
                )
            }

            // 4. READING & SCRIPTURE SECTION
            item(key = "section_reading") {
                ReadingScriptureSection(
                    wordByWordEnabled = settingsPreferences.wordByWordEnabled,
                    translationEnabled = settingsPreferences.translationEnabled,
                    onToggleWordByWord = onToggleWordByWord,
                    onToggleTranslation = onToggleTranslation
                )
            }

            // 5. MEMORIZATION & HIFZ SECTION
            item(key = "section_hifz") {
                HifzSection(
                    repeatCount = settingsPreferences.hifzRepeatCount,
                    delaySeconds = settingsPreferences.hifzDelaySeconds,
                    onSetLoop = onSetHifzLoop
                )
            }

            // 6. STORAGE & DOWNLOADS SECTION
            item(key = "section_storage") {
                StorageDownloadsSection(
                    context = context,
                    downloadedSurahs = downloadedSurahs,
                    totalDownloadedBytes = totalDownloadedBytes,
                    cacheSizeBytes = cacheSizeBytes,
                    isListExpanded = downloadedListExpanded,
                    onToggleListExpanded = { downloadedListExpanded = !downloadedListExpanded },
                    onClearCacheClick = { showClearCacheDialog = true },
                    onDeleteAllDownloadsClick = { showDeleteAllDownloadsDialog = true },
                    onDeleteSurahClick = { surahToDelete = it }
                )
            }

            // 7. REMINDERS & NOTIFICATIONS SECTION
            item(key = "section_reminders") {
                RemindersSection(
                    context = context,
                    hasNotificationPermission = hasNotificationPermission,
                    dailyReminderEnabled = settingsPreferences.dailyReminderEnabled,
                    dailyHour = settingsPreferences.dailyReminderHour,
                    dailyMinute = settingsPreferences.dailyReminderMinute,
                    fridayReminderEnabled = settingsPreferences.fridayReminderEnabled,
                    fridayHour = settingsPreferences.fridayReminderHour,
                    fridayMinute = settingsPreferences.fridayReminderMinute,
                    onRequestPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onToggleDailyReminder = onToggleDailyReminder,
                    onToggleFridayReminder = onToggleFridayReminder
                )
            }

            // 8. BACKUP & RESTORE SECTION
            item(key = "section_backup") {
                BackupRestoreSection(
                    lastBackupTimestamp = settingsPreferences.lastBackupTimestamp,
                    onCreateBackupClick = {
                        val timeStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                        backupExportLauncher.launch("listen_quran_backup_$timeStr.json")
                    },
                    onRestoreBackupClick = {
                        backupImportLauncher.launch(arrayOf("application/json", "*/*"))
                    }
                )
            }

            // 9. ABOUT SECTION
            item(key = "section_about") {
                AboutSection()
            }
        }
    }

    // DIALOGS & PICKERS

    // 1. Reciter Picker Dialog
    if (showReciterPicker) {
        ReciterPickerDialog(
            reciters = allReciters,
            currentReciterId = defaultReciter?.id ?: 7,
            onDismiss = { showReciterPicker = false },
            onSelect = {
                onSelectDefaultReciter(it)
                showReciterPicker = false
            }
        )
    }

    // 2. Clear Stats Confirmation Dialog
    if (showClearStatsDialog) {
        AlertDialog(
            onDismissRequest = { showClearStatsDialog = false },
            icon = { Icon(Icons.Outlined.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Reset Listening History?") },
            text = { Text("This will permanently clear your listening stats, total time, and daily streaks. Your saved playlists and bookmarks will not be affected.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearStats()
                        showClearStatsDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear Stats")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearStatsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 3. Clear Cache Confirmation Dialog
    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            icon = { Icon(Icons.Outlined.CleaningServices, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Clear Streamed Cache?") },
            text = { Text("This frees temporary audio chunks and image cache. Your downloaded surahs will NOT be deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAudioCache()
                        showClearCacheDialog = false
                    }
                ) {
                    Text("Clear Cache")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 4. Delete All Downloads Confirmation Dialog
    if (showDeleteAllDownloadsDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDownloadsDialog = false },
            icon = { Icon(Icons.Outlined.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete All Downloaded Surahs?") },
            text = { Text("All downloaded surahs will be removed from your device storage. You will need an active internet connection to stream them again.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAllDownloads()
                        showDeleteAllDownloadsDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDownloadsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 5. Delete Single Downloaded Surah Dialog
    surahToDelete?.let { surah ->
        AlertDialog(
            onDismissRequest = { surahToDelete = null },
            icon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Download?") },
            text = { Text("Remove Surah ${surah.surahName} (${formatBytes(surah.sizeBytes)}) from device storage?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteDownloadedSurah(surah.surahNumber, surah.reciterId)
                        surahToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { surahToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 6. Restore Backup Options Dialog
    if (showRestoreOptionsDialog && pendingRestoreUri != null) {
        AlertDialog(
            onDismissRequest = {
                showRestoreOptionsDialog = false
                pendingRestoreUri = null
            },
            icon = { Icon(Icons.Outlined.FileDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Restore Backup") },
            text = {
                Text("How would you like to restore your data?\n\n• Merge: Adds missing playlists, bookmarks, and stats while keeping your current items intact.\n\n• Replace: Replaces your current playlists, bookmarks, and stats with the backup file.")
            },
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        pendingRestoreUri?.let { uri -> onImportBackup(uri, false) }
                        showRestoreOptionsDialog = false
                        pendingRestoreUri = null
                    }
                ) {
                    Text("Merge (Recommended)")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            showRestoreOptionsDialog = false
                            pendingRestoreUri = null
                        }
                    ) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(4.dp))
                    TextButton(
                        onClick = {
                            pendingRestoreUri?.let { uri -> onImportBackup(uri, true) }
                            showRestoreOptionsDialog = false
                            pendingRestoreUri = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Replace All")
                    }
                }
            }
        )
    }
}

// -------------------------------------------------------------
// SECTION 1: STATISTICS
// -------------------------------------------------------------

@Composable
private fun StatisticsSection(
    statsSummary: ListeningStatsSummary,
    isDetailsExpanded: Boolean,
    onToggleDetails: () -> Unit,
    onClearStats: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Outlined.BarChart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Listening Statistics",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                TextButton(
                    onClick = onClearStats,
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text(
                        "Reset",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // 3 KPI metric cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricCard(
                    title = "Total Time",
                    value = formatTotalDuration(statsSummary.totalSecondsListened),
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Streak",
                    value = "${statsSummary.currentDayStreak} days",
                    highlight = statsSummary.currentDayStreak > 0,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Completed",
                    value = "${statsSummary.totalSurahsCompleted}",
                    modifier = Modifier.weight(1f)
                )
            }

            // Top Reciter
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Top Reciter",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (statsSummary.topReciterName.isNotBlank()) statsSummary.topReciterName else "No sessions yet",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Collapsible Details
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onToggleDetails)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isDetailsExpanded) "Hide detailed breakdown" else "Show detailed breakdown",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = if (isDetailsExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            AnimatedVisibility(
                visible = isDetailsExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    WeeklyListeningBarChart(
                        statsSummary = statsSummary,
                        modifier = Modifier.fillMaxWidth()
                    )

                    ReciterBreakdownDonutChart(
                        statsSummary = statsSummary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    highlight: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

// -------------------------------------------------------------
// SECTION 2: APPEARANCE
// -------------------------------------------------------------

@Composable
private fun AppearanceSection(
    currentTheme: ThemeMode,
    onSelectTheme: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Outlined.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Appearance",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                "Choose your preferred color theme. Changes apply immediately.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Segmented options
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ThemeOptionChip(
                    label = "Dark",
                    selected = currentTheme == ThemeMode.DARK,
                    onClick = { onSelectTheme(ThemeMode.DARK) },
                    modifier = Modifier.weight(1f)
                )
                ThemeOptionChip(
                    label = "Light",
                    selected = currentTheme == ThemeMode.LIGHT,
                    onClick = { onSelectTheme(ThemeMode.LIGHT) },
                    modifier = Modifier.weight(1f)
                )
                ThemeOptionChip(
                    label = "AMOLED",
                    selected = currentTheme == ThemeMode.AMOLED,
                    onClick = { onSelectTheme(ThemeMode.AMOLED) },
                    modifier = Modifier.weight(1f)
                )
                ThemeOptionChip(
                    label = "System",
                    selected = currentTheme == ThemeMode.SYSTEM,
                    onClick = { onSelectTheme(ThemeMode.SYSTEM) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ThemeOptionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Box(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

// -------------------------------------------------------------
// SECTION 3: AUDIO & PLAYBACK
// -------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AudioPlaybackSection(
    defaultReciter: Reciter?,
    playbackSpeed: Float,
    customSleepTimerMinutes: Int,
    onChangeReciterClick: () -> Unit,
    onSelectPlaybackSpeed: (Float) -> Unit,
    onSelectSleepTimer: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Outlined.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Audio & Playback",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Audio Quality Note (Truthful representation)
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        "Audio is streamed and saved in high-quality 128–192 kbps MP3 directly from Quran.com open CDN.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Default Reciter
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Default Reciter",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = defaultReciter?.let { "${it.name} (${it.style ?: "Murattal"})" }
                            ?: "Mishari Rashid al-`Afasy",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = onChangeReciterClick,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Change")
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // Playback Speed
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Playback Speed",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(0.75f, 1.0f, 1.25f, 1.5f).forEach { speed ->
                        FilterChip(
                            selected = kotlin.math.abs(playbackSpeed - speed) < 0.05f,
                            onClick = { onSelectPlaybackSpeed(speed) },
                            label = { Text("${speed}x") }
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // Sleep Timer Presets
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Default Sleep Timer",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(15, 30, 45, 60).forEach { mins ->
                        FilterChip(
                            selected = customSleepTimerMinutes == mins,
                            onClick = { onSelectSleepTimer(mins) },
                            label = { Text("${mins}m") }
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// SECTION 4: READING & SCRIPTURE
// -------------------------------------------------------------

@Composable
private fun ReadingScriptureSection(
    wordByWordEnabled: Boolean,
    translationEnabled: Boolean,
    onToggleWordByWord: (Boolean) -> Unit,
    onToggleTranslation: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Reading & Scripture",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Word-by-Word
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Word-by-word highlighting",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Highlights words in sync with recitation (supported reciters)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = wordByWordEnabled,
                    onCheckedChange = onToggleWordByWord
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // English Translation
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Verse translation",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Display English verse translations in the lyrics player sheet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = translationEnabled,
                    onCheckedChange = onToggleTranslation
                )
            }
        }
    }
}

// -------------------------------------------------------------
// SECTION 5: MEMORIZATION & HIFZ
// -------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HifzSection(
    repeatCount: Int,
    delaySeconds: Int,
    onSetLoop: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Outlined.Repeat,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Memorization & Hifz",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                "Configure default repeat counts and delay gaps for Hifz practice loops.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Repeat Count
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Default Ayah Repetitions",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 3, 5, 10, 0).forEach { count ->
                        FilterChip(
                            selected = repeatCount == count,
                            onClick = { onSetLoop(count, delaySeconds) },
                            label = { Text(if (count == 0) "Loop (∞)" else "${count}x") }
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // Delay Seconds
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Gap Between Ayahs",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 3, 5, 10).forEach { secs ->
                        FilterChip(
                            selected = delaySeconds == secs,
                            onClick = { onSetLoop(repeatCount, secs) },
                            label = { Text(if (secs == 0) "None (0s)" else "${secs}s") }
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// SECTION 6: STORAGE & DOWNLOADS
// -------------------------------------------------------------

@Composable
private fun StorageDownloadsSection(
    context: Context,
    downloadedSurahs: List<DownloadedSurahEntity>,
    totalDownloadedBytes: Long,
    cacheSizeBytes: Long,
    isListExpanded: Boolean,
    onToggleListExpanded: () -> Unit,
    onClearCacheClick: () -> Unit,
    onDeleteAllDownloadsClick: () -> Unit,
    onDeleteSurahClick: (DownloadedSurahEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val statFs = remember {
        try {
            StatFs(context.filesDir.path)
        } catch (_: Exception) {
            null
        }
    }
    val freeDeviceBytes = statFs?.availableBytes ?: 0L

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Outlined.Storage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Storage & Downloads",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // StatFs Metric Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricCard(
                    title = "Downloads",
                    value = formatBytes(totalDownloadedBytes),
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Cache",
                    value = formatBytes(cacheSizeBytes),
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Free Space",
                    value = formatBytes(freeDeviceBytes),
                    modifier = Modifier.weight(1f)
                )
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onClearCacheClick,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(
                        Icons.Outlined.CleaningServices,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Clear Cache", style = MaterialTheme.typography.labelMedium)
                }

                OutlinedButton(
                    onClick = onDeleteAllDownloadsClick,
                    modifier = Modifier.weight(1f),
                    enabled = downloadedSurahs.isNotEmpty(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(
                        Icons.Outlined.DeleteForever,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Delete All", style = MaterialTheme.typography.labelMedium)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // Downloaded Surahs List Accordion
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onToggleListExpanded)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Downloaded Surahs (${downloadedSurahs.size})",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = if (isListExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            AnimatedVisibility(
                visible = isListExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                if (downloadedSurahs.isEmpty()) {
                    Text(
                        "No surahs downloaded yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        downloadedSurahs.forEach { item ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "${item.surahNumber}. ${item.surahName}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            "${item.reciterName} • ${formatBytes(item.sizeBytes)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDeleteSurahClick(item) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Outlined.Delete,
                                            contentDescription = "Delete download",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
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

// -------------------------------------------------------------
// SECTION 7: REMINDERS & NOTIFICATIONS
// -------------------------------------------------------------

@Composable
private fun RemindersSection(
    context: Context,
    hasNotificationPermission: Boolean,
    dailyReminderEnabled: Boolean,
    dailyHour: Int,
    dailyMinute: Int,
    fridayReminderEnabled: Boolean,
    fridayHour: Int,
    fridayMinute: Int,
    onRequestPermission: () -> Unit,
    onToggleDailyReminder: (Boolean, Int, Int) -> Unit,
    onToggleFridayReminder: (Boolean, Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Outlined.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Reminders & Alerts",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Permission Warning Banner if missing
            if (!hasNotificationPermission) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Permission Required",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                "Notifications are disabled on your device. Reminders will not appear.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        TextButton(onClick = onRequestPermission) {
                            Text("Grant", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Daily Reminder
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Daily Listening Reminder",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Skips automatically if you've already listened today",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = dailyReminderEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled && !hasNotificationPermission) {
                            onRequestPermission()
                        } else {
                            onToggleDailyReminder(enabled, dailyHour, dailyMinute)
                        }
                    }
                )
            }

            if (dailyReminderEnabled) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Time: ${formatTime(dailyHour, dailyMinute)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedButton(
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, h, m -> onToggleDailyReminder(true, h, m) },
                                dailyHour,
                                dailyMinute,
                                false
                            ).show()
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("Set Time")
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // Friday Reminder
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Friday Surah Al-Kahf Reminder",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Weekly morning reminder to recite Surah Al-Kahf",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = fridayReminderEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled && !hasNotificationPermission) {
                            onRequestPermission()
                        } else {
                            onToggleFridayReminder(enabled, fridayHour, fridayMinute)
                        }
                    }
                )
            }

            if (fridayReminderEnabled) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Time: Friday at ${formatTime(fridayHour, fridayMinute)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedButton(
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, h, m -> onToggleFridayReminder(true, h, m) },
                                fridayHour,
                                fridayMinute,
                                false
                            ).show()
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("Set Time")
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// SECTION 8: BACKUP & RESTORE
// -------------------------------------------------------------

@Composable
private fun BackupRestoreSection(
    lastBackupTimestamp: Long,
    onCreateBackupClick: () -> Unit,
    onRestoreBackupClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Outlined.Backup,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Backup & Restore",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                "Export your playlists, liked surahs, bookmarks with tadabbur notes, hifz loops, and listening statistics to a secure JSON backup with SHA-256 integrity verification.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Last Backup info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Last Backup",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (lastBackupTimestamp > 0) {
                        SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()).format(Date(lastBackupTimestamp))
                    } else {
                        "Never"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            // Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onCreateBackupClick,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(
                        Icons.Outlined.FileUpload,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Export Backup", style = MaterialTheme.typography.labelMedium)
                }

                OutlinedButton(
                    onClick = onRestoreBackupClick,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(
                        Icons.Outlined.FileDownload,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Restore Backup", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

// -------------------------------------------------------------
// SECTION 9: ABOUT
// -------------------------------------------------------------

@Composable
private fun AboutSection(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    "About Listen Quran",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "App Version",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            Text(
                "Scripture, ayah audio, and timing metadata provided with gratitude to the Quran Foundation (Quran.com) and QuranicAudio open CDN.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "Private & Local-First. No accounts, no advertising, and zero tracking. Audio streaming requires internet; personal data stays strictly on this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// RECITER PICKER DIALOG
// -------------------------------------------------------------

@Composable
private fun ReciterPickerDialog(
    reciters: List<Reciter>,
    currentReciterId: Int,
    onDismiss: () -> Unit,
    onSelect: (Reciter) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filtered = remember(searchQuery, reciters) {
        if (searchQuery.isBlank()) reciters
        else reciters.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            (it.style ?: "").contains(searchQuery, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Select Default Reciter",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search reciters...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filtered, key = { it.id }) { reciter ->
                        val isSelected = reciter.id == currentReciterId
                        Surface(
                            onClick = { onSelect(reciter) },
                            modifier = Modifier.fillMaxWidth(),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        reciter.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                    reciter.style?.let { style ->
                                        Text(
                                            style,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

// -------------------------------------------------------------
// UTILITIES
// -------------------------------------------------------------

private fun formatTotalDuration(seconds: Long): String {
    if (seconds <= 0) return "0m"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) {
        "${hours}h ${minutes}m"
    } else {
        "${minutes}m"
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val kb = bytes / 1024f
    val mb = kb / 1024f
    val gb = mb / 1024f
    return when {
        gb >= 1.0f -> String.format(Locale.US, "%.1f GB", gb)
        mb >= 1.0f -> String.format(Locale.US, "%.1f MB", mb)
        else -> String.format(Locale.US, "%.0f KB", kb)
    }
}

private fun formatTime(hour: Int, minute: Int): String {
    val h = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
    val amPm = if (hour < 12) "AM" else "PM"
    return String.format(Locale.US, "%02d:%02d %s", h, minute, amPm)
}
