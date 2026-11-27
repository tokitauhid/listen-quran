package com.listenquran.ui.screens.player

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.SpeakerGroup
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.listenquran.player.cast.CastConnectionState
import com.listenquran.player.cast.LocalAudioOutput
import com.listenquran.player.cast.OutputDevice
import com.listenquran.player.cast.OutputDeviceType
import com.listenquran.player.cast.OutputRouteState
import kotlin.math.roundToInt

/**
 * Unified Spotify-style "Listening on" output switcher listing local outputs
 * (phone speaker, connected Bluetooth/wired headphones) alongside discovered Wi-Fi speakers
 * by friendly name only. Tapping any row switches audio output immediately.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutputDevicePickerSheet(
    outputRouteState: OutputRouteState,
    localOutputs: List<LocalAudioOutput>,
    activeLocalOutput: LocalAudioOutput,
    isPlaying: Boolean,
    hasNearbyWifiPermission: Boolean,
    onRequestNearbyWifiPermission: () -> Unit,
    onSelectLocalOutput: (LocalAudioOutput) -> Unit,
    onSelectSpeaker: (OutputDevice) -> Unit,
    onSetVolume: (Int) -> Unit,
    onToggleMute: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isCasting = outputRouteState.isCasting
    val isConnecting = outputRouteState.connectionState == CastConnectionState.CONNECTING

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = if (isCasting) Icons.Rounded.CastConnected else Icons.Rounded.Cast,
                    contentDescription = null,
                    tint = if (isCasting) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Listening on",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNearbyWifiPermission) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Wifi,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Text(
                            text = "Allow Nearby Wi-Fi access to find speakers",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                        FilledTonalButton(onClick = onRequestNearbyWifiPermission) {
                            Text("Allow")
                        }
                    }
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
            ) {
                // Local outputs: "This phone", connected Bluetooth earbuds, wired/USB headphones
                items(localOutputs, key = { "local_${it.id}_${it.name}" }) { local ->
                    val isActive = !isCasting && !isConnecting && local.id == activeLocalOutput.id
                    OutputRow(
                        name = local.name,
                        deviceType = local.type,
                        isActive = isActive,
                        isConnecting = false,
                        isPlaying = isPlaying,
                        onClick = {
                            onSelectLocalOutput(local)
                            onDismiss()
                        },
                    )
                }

                // Discovered Wi-Fi speakers
                items(outputRouteState.discoveredDevices, key = { "remote_${it.id}" }) { speaker ->
                    val isRowActive = isCasting && outputRouteState.activeDevice.id == speaker.id
                    val isRowConnecting = isConnecting && outputRouteState.activeDevice.id == speaker.id
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutputRow(
                            name = speaker.name,
                            deviceType = speaker.deviceType,
                            isActive = isRowActive,
                            isConnecting = isRowConnecting,
                            isPlaying = isPlaying,
                            onClick = { onSelectSpeaker(speaker) },
                        )

                        AnimatedVisibility(visible = isRowActive && speaker.renderingControlUrl != null) {
                            SpeakerVolumeRow(
                                remoteVolume = outputRouteState.remoteVolume,
                                isRemoteMuted = outputRouteState.isRemoteMuted,
                                onSetVolume = onSetVolume,
                                onToggleMute = onToggleMute,
                            )
                        }
                    }
                }

                if (outputRouteState.isScanning) {
                    item(key = "scanning_footer") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "Looking for speakers…",
                                style = MaterialTheme.typography.bodySmall,
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
private fun OutputRow(
    name: String,
    deviceType: OutputDeviceType,
    isActive: Boolean,
    isConnecting: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isActive) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        border = if (isActive) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                imageVector = deviceIconFor(deviceType),
                contentDescription = null,
                tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            when {
                isConnecting -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                isActive -> {
                    EqualizerBars(isAnimating = isPlaying)
                }
            }
        }
    }
}

@Composable
private fun SpeakerVolumeRow(
    remoteVolume: Int,
    isRemoteMuted: Boolean,
    onSetVolume: (Int) -> Unit,
    onToggleMute: () -> Unit,
) {
    var sliderVol by remember(remoteVolume) { mutableFloatStateOf(remoteVolume.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(remoteVolume) {
        if (!isDragging) {
            sliderVol = remoteVolume.toFloat()
        }
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(
                onClick = onToggleMute,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = if (isRemoteMuted) {
                        Icons.AutoMirrored.Rounded.VolumeOff
                    } else {
                        Icons.AutoMirrored.Rounded.VolumeUp
                    },
                    contentDescription = if (isRemoteMuted) "Unmute" else "Mute",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Slider(
                value = sliderVol,
                onValueChange = {
                    isDragging = true
                    sliderVol = it
                },
                onValueChangeFinished = {
                    isDragging = false
                    onSetVolume(sliderVol.roundToInt())
                },
                valueRange = 0f..100f,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${sliderVol.roundToInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(38.dp),
            )
        }
    }
}

@Composable
private fun EqualizerBars(isAnimating: Boolean) {
    val barColor = MaterialTheme.colorScheme.primary
    if (!isAnimating) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.height(16.dp),
        ) {
            listOf(7.dp, 12.dp, 9.dp).forEach { h ->
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(h)
                        .clip(RoundedCornerShape(2.dp))
                        .background(barColor)
                )
            }
        }
        return
    }

    val transition = rememberInfiniteTransition(label = "eq_bars")
    val f1 by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse),
        label = "b1",
    )
    val f2 by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(520, easing = LinearEasing), RepeatMode.Reverse),
        label = "b2",
    )
    val f3 by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(370, easing = LinearEasing), RepeatMode.Reverse),
        label = "b3",
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.height(16.dp),
    ) {
        listOf(f1, f2, f3).forEach { frac ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((16f * frac).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor)
            )
        }
    }
}

private fun deviceIconFor(type: OutputDeviceType) = when (type) {
    OutputDeviceType.PHONE -> Icons.Rounded.PhoneAndroid
    OutputDeviceType.BLUETOOTH -> Icons.Rounded.BluetoothAudio
    OutputDeviceType.WIRED -> Icons.Rounded.Headphones
    OutputDeviceType.TV -> Icons.Rounded.Tv
    OutputDeviceType.RECEIVER -> Icons.Rounded.SpeakerGroup
    OutputDeviceType.SPEAKER, OutputDeviceType.UNKNOWN -> Icons.Rounded.Speaker
}
