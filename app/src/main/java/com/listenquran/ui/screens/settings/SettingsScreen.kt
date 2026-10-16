package com.listenquran.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.listenquran.domain.model.AudioQuality
import com.listenquran.domain.model.ListeningStatsSummary
import com.listenquran.domain.model.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentTheme: ThemeMode,
    currentQuality: AudioQuality,
    statsSummary: ListeningStatsSummary,
    notificationsEnabled: Boolean,
    onSelectTheme: (ThemeMode) -> Unit,
    onSelectQuality: (AudioQuality) -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    onExportStats: () -> Unit,
    onImportStats: (String) -> Unit = {},
    onExportPlaylists: () -> Unit,
    onImportPlaylists: (String) -> Unit = {},
    onClearAudioCache: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showThemeDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showImportPlaylistsDialog by remember { mutableStateOf(false) }
    var importPlaylistsText by remember { mutableStateOf("") }
    var showImportStatsDialog by remember { mutableStateOf(false) }
    var importStatsText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings & Stats",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Full Listening Stats Section
            item {
                Text(
                    text = "Listening Statistics",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatBox(
                                label = "Total Time",
                                value = "${statsSummary.totalSecondsListened / 3600}h ${(statsSummary.totalSecondsListened % 3600) / 60}m",
                                modifier = Modifier.weight(1f)
                            )
                            StatBox(
                                label = "Current Streak",
                                value = "${statsSummary.currentDayStreak} Days",
                                modifier = Modifier.weight(1f)
                            )
                            StatBox(
                                label = "Completed",
                                value = "${statsSummary.totalSurahsCompleted} Surahs",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Appearance & Theme
            item {
                Text(
                    text = "Appearance",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                SettingCard(
                    title = "Theme",
                    subtitle = when (currentTheme) {
                        ThemeMode.DARK -> "Dark Theme (Default)"
                        ThemeMode.LIGHT -> "Light Theme"
                        ThemeMode.AMOLED -> "AMOLED Pitch Black"
                        ThemeMode.SYSTEM -> "System Default"
                    },
                    icon = Icons.Rounded.Palette,
                    onClick = { showThemeDialog = true }
                )
            }

            // Audio & Downloads (plan.md §1.5: Manage Downloads & Quality)
            item {
                Text(
                    text = "Downloads & Streaming",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                SettingCard(
                    title = "Audio Quality",
                    subtitle = if (currentQuality == AudioQuality.HIGH) "High Quality (192 kbps)" else "Standard MP3 (128 kbps)",
                    icon = Icons.Rounded.HighQuality,
                    onClick = { showQualityDialog = true }
                )
            }

            item {
                SettingCard(
                    title = "Manage Downloads & Offline Storage",
                    subtitle = "Clear temporary and cached audio files to free storage",
                    icon = Icons.Rounded.Storage,
                    onClick = { showClearCacheDialog = true }
                )
            }

            // Notifications
            item {
                Text(
                    text = "Reminders & Notifications",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Notifications,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = "Daily Quran Reminders",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Friday Kahf alert & streak reminder",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = notificationsEnabled,
                            onCheckedChange = onToggleNotifications
                        )
                    }
                }
            }

            // Backup & Export (plan.md §1.5: Import/Export Playlists & Stats)
            item {
                Text(
                    text = "Backup & Data",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onExportPlaylists()
                                Toast.makeText(context, "Playlists exported to JSON", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export Playlists")
                        }

                        Button(
                            onClick = { showImportPlaylistsDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import Playlists")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onExportStats()
                                Toast.makeText(context, "Stats exported to JSON", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Assessment, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export Stats")
                        }

                        Button(
                            onClick = { showImportStatsDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import Stats")
                        }
                    }
                }
            }
        }
    }

    // Theme Dialog
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Choose Theme") },
            text = {
                Column {
                    ThemeMode.values().forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectTheme(mode)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentTheme == mode,
                                onClick = {
                                    onSelectTheme(mode)
                                    showThemeDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = when (mode) {
                                    ThemeMode.DARK -> "Dark Theme (Default)"
                                    ThemeMode.LIGHT -> "Light Theme"
                                    ThemeMode.AMOLED -> "AMOLED Pitch Black"
                                    ThemeMode.SYSTEM -> "System Default"
                                },
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("Done") }
            }
        )
    }

    // Quality Dialog
    if (showQualityDialog) {
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            title = { Text("Audio Quality") },
            text = {
                Column {
                    AudioQuality.values().forEach { quality ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectQuality(quality)
                                    showQualityDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentQuality == quality,
                                onClick = {
                                    onSelectQuality(quality)
                                    showQualityDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (quality == AudioQuality.HIGH) "High Quality (192 kbps)" else "Standard MP3 (128 kbps)",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQualityDialog = false }) { Text("Done") }
            }
        )
    }

    // Clear Cache Dialog
    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("Clear Offline Audio Cache") },
            text = { Text("This will delete locally cached recitations and chapter audio to reclaim storage space. Your playlists, bookmarks, and Khatma progress will NOT be touched.") },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAudioCache()
                        showClearCacheDialog = false
                        Toast.makeText(context, "Audio cache cleared", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear Storage")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Import Playlists Dialog
    if (showImportPlaylistsDialog) {
        AlertDialog(
            onDismissRequest = { showImportPlaylistsDialog = false },
            title = { Text("Import Playlists from JSON") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Paste previously exported playlist JSON below to restore:")
                    OutlinedTextField(
                        value = importPlaylistsText,
                        onValueChange = { importPlaylistsText = it },
                        placeholder = { Text("[{\"name\": \"My Playlist\", ...}]") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (importPlaylistsText.isNotBlank()) {
                            onImportPlaylists(importPlaylistsText.trim())
                            importPlaylistsText = ""
                            showImportPlaylistsDialog = false
                            Toast.makeText(context, "Playlists imported successfully", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportPlaylistsDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Import Stats Dialog
    if (showImportStatsDialog) {
        AlertDialog(
            onDismissRequest = { showImportStatsDialog = false },
            title = { Text("Import Listening Stats from JSON") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Paste previously exported stats JSON below:")
                    OutlinedTextField(
                        value = importStatsText,
                        onValueChange = { importStatsText = it },
                        placeholder = { Text("[{\"surah\": 1, \"duration\": 120, ...}]") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (importStatsText.isNotBlank()) {
                            onImportStats(importStatsText.trim())
                            importStatsText = ""
                            showImportStatsDialog = false
                            Toast.makeText(context, "Stats imported successfully", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportStatsDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun StatBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SettingCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
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
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
