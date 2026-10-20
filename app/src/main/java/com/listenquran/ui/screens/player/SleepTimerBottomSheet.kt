package com.listenquran.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.listenquran.player.SleepTimerMode
import com.listenquran.player.SleepTimerState
import java.util.Locale

private data class PresetOption(
    val minutes: Int,
    val title: String,
)

private val PRESET_OPTIONS = listOf(
    PresetOption(5, "5 minutes"),
    PresetOption(10, "10 minutes"),
    PresetOption(15, "15 minutes"),
    PresetOption(30, "30 minutes"),
    PresetOption(45, "45 minutes"),
    PresetOption(60, "1 hour"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerBottomSheet(
    sleepTimerState: SleepTimerState,
    isRadio: Boolean,
    repeatMode: Int,
    customDefaultMinutes: Int,
    onSelectMode: (SleepTimerMode) -> Unit,
    onSaveCustomMinutes: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCustomInputDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(bottom = 16.dp, start = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Timer,
                    contentDescription = null,
                    tint = if (sleepTimerState.isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Sleep Timer",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // 1. Presets (5, 10, 15, 30, 45, 60 minutes)
                items(PRESET_OPTIONS, key = { "preset_${it.minutes}" }) { preset ->
                    val isActive = sleepTimerState.isRunning &&
                            sleepTimerState.mode is SleepTimerMode.Duration &&
                            (sleepTimerState.mode as SleepTimerMode.Duration).totalMinutes == preset.minutes

                    SleepTimerOptionRow(
                        title = preset.title,
                        subtitle = null,
                        isActive = isActive,
                        enabled = true,
                        remainingSeconds = if (isActive) sleepTimerState.remainingSeconds else null,
                        onClick = {
                            onSelectMode(SleepTimerMode.Duration(preset.minutes))
                        }
                    )
                }

                // 2. End of surah
                item(key = "end_of_surah") {
                    val isActive = sleepTimerState.isRunning && sleepTimerState.mode is SleepTimerMode.EndOfSurah
                    val enabled = !isRadio
                    val subtitle = if (isRadio) "Unavailable for continuous radio" else null

                    SleepTimerOptionRow(
                        title = "End of surah",
                        subtitle = subtitle,
                        isActive = isActive,
                        enabled = enabled,
                        remainingSeconds = null,
                        onClick = {
                            if (enabled) {
                                onSelectMode(SleepTimerMode.EndOfSurah)
                            }
                        }
                    )
                }

                // 3. End of playlist
                item(key = "end_of_playlist") {
                    val isActive = sleepTimerState.isRunning && sleepTimerState.mode is SleepTimerMode.EndOfPlaylist
                    val isRepeatAll = repeatMode == Player.REPEAT_MODE_ALL
                    val enabled = !isRadio && !isRepeatAll
                    val subtitle = when {
                        isRadio -> "Unavailable for continuous radio"
                        isRepeatAll -> "Disabled while repeat all is on"
                        else -> null
                    }

                    SleepTimerOptionRow(
                        title = "End of playlist",
                        subtitle = subtitle,
                        isActive = isActive,
                        enabled = enabled,
                        remainingSeconds = null,
                        onClick = {
                            if (enabled) {
                                onSelectMode(SleepTimerMode.EndOfPlaylist)
                            }
                        }
                    )
                }

                // 4. Custom
                item(key = "custom") {
                    val isActive = sleepTimerState.isRunning && sleepTimerState.mode is SleepTimerMode.Custom
                    val currentCustomMinutes = if (sleepTimerState.mode is SleepTimerMode.Custom) {
                        sleepTimerState.mode.totalMinutes
                    } else {
                        customDefaultMinutes
                    }

                    SleepTimerOptionRow(
                        title = if (isActive) "Custom (${currentCustomMinutes}m)" else "Custom",
                        subtitle = if (!isActive) "Set specific duration (1-720 min)" else null,
                        isActive = isActive,
                        enabled = true,
                        remainingSeconds = if (isActive) sleepTimerState.remainingSeconds else null,
                        onClick = {
                            if (isActive) {
                                // Tapping active turns off
                                onSelectMode(SleepTimerMode.Custom(currentCustomMinutes))
                            } else {
                                showCustomInputDialog = true
                            }
                        }
                    )
                }
            }
        }
    }

    if (showCustomInputDialog) {
        CustomTimerDialog(
            initialMinutes = customDefaultMinutes.coerceIn(1, 720),
            onDismiss = { showCustomInputDialog = false },
            onConfirm = { minutes ->
                onSaveCustomMinutes(minutes)
                onSelectMode(SleepTimerMode.Custom(minutes))
                showCustomInputDialog = false
            }
        )
    }
}

@Composable
private fun SleepTimerOptionRow(
    title: String,
    subtitle: String?,
    isActive: Boolean,
    enabled: Boolean,
    remainingSeconds: Long?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = when {
        !enabled -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f)
        isActive -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }

    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        isActive -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    val stateDesc = when {
        !enabled -> "disabled"
        isActive -> "$title, selected, double tap to turn off"
        else -> "$title, not selected"
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .semantics {
                role = Role.RadioButton
                selected = isActive
                stateDescription = stateDesc
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isActive) contentColor.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (isActive) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (remainingSeconds != null) {
                        CountdownBadge(remainingSeconds = remainingSeconds)
                    }
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Active",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CountdownBadge(
    remainingSeconds: Long,
    modifier: Modifier = Modifier,
) {
    val mins = remainingSeconds / 60
    val secs = remainingSeconds % 60
    val formatted = String.format(Locale.getDefault(), "%d:%02d", mins, secs)

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
    ) {
        Text(
            text = formatted,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun CustomTimerDialog(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var textValue by remember { mutableStateOf(initialMinutes.toString()) }
    val parsedInt = textValue.toIntOrNull()
    val isValid = parsedInt != null && parsedInt in 1..720

    val errorMessage = when {
        textValue.isBlank() -> "Please enter minutes"
        parsedInt == null -> "Please enter a valid number"
        parsedInt < 1 -> "Minimum duration is 1 minute"
        parsedInt > 720 -> "Maximum duration is 720 minutes (12 hours)"
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Custom Sleep Timer",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Enter playback duration in minutes (1 to 720):",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Quick stepper with +5 / -5 buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilledTonalIconButton(
                        onClick = {
                            val current = textValue.toIntOrNull() ?: 20
                            val updated = (current - 5).coerceIn(1, 720)
                            textValue = updated.toString()
                        }
                    ) {
                        Icon(Icons.Rounded.Remove, contentDescription = "Decrease 5 minutes")
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    OutlinedTextField(
                        value = textValue,
                        onValueChange = { input ->
                            // Allow only digits and limit to 4 digits
                            if (input.all { it.isDigit() } && input.length <= 4) {
                                textValue = input
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(100.dp),
                        isError = errorMessage != null && textValue.isNotEmpty()
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    FilledTonalIconButton(
                        onClick = {
                            val current = textValue.toIntOrNull() ?: 20
                            val updated = (current + 5).coerceIn(1, 720)
                            textValue = updated.toString()
                        }
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Increase 5 minutes")
                    }
                }

                if (errorMessage != null && textValue.isNotEmpty()) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    parsedInt?.let { onConfirm(it) }
                },
                enabled = isValid
            ) {
                Text("Start")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
