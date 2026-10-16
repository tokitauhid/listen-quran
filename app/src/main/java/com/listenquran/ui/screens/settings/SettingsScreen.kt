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
    onExportPlaylists: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showThemeDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }

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

            // Audio & Downloads
            item {
                Text(
                    text = "Audio & Streaming",
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

            // Backup & Export
            item {
                Text(
                    text = "Backup & Data",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
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
