package com.listenquran.player.cast

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Immutable
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Provider
import javax.inject.Singleton

@Immutable
data class LocalAudioOutput(
    val id: Int,
    val name: String,
    val type: OutputDeviceType,
    val deviceInfo: AudioDeviceInfo?,
) {
    companion object {
        val FALLBACK_PHONE = LocalAudioOutput(
            id = -1,
            name = "This phone",
            type = OutputDeviceType.PHONE,
            deviceInfo = null,
        )
    }
}

/**
 * Tracks live local audio outputs (phone speaker, connected Bluetooth A2DP/BLE headsets,
 * wired/USB headphones) via [AudioManager.registerAudioDeviceCallback] and routes Media3
 * playback via [ExoPlayer.setPreferredAudioDevice].
 */
@Singleton
class LocalAudioOutputs @Inject constructor(
    @ApplicationContext context: Context,
    private val exoPlayerProvider: Provider<ExoPlayer>,
    @Named("cue") private val cuePlayerProvider: Provider<ExoPlayer>,
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _outputs = MutableStateFlow<List<LocalAudioOutput>>(listOf(LocalAudioOutput.FALLBACK_PHONE))
    val outputs: StateFlow<List<LocalAudioOutput>> = _outputs.asStateFlow()

    private val _preferredOutputId = MutableStateFlow<Int?>(null)

    val activeLocalOutput: StateFlow<LocalAudioOutput> = combine(_outputs, _preferredOutputId) { list, prefId ->
        val preferred = prefId?.let { id -> list.firstOrNull { it.id == id } }
        preferred
            ?: list.firstOrNull { it.type == OutputDeviceType.BLUETOOTH || it.type == OutputDeviceType.WIRED }
            ?: list.firstOrNull { it.type == OutputDeviceType.PHONE }
            ?: LocalAudioOutput.FALLBACK_PHONE
    }.stateIn(mainScope, SharingStarted.Eagerly, LocalAudioOutput.FALLBACK_PHONE)

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            val hasNewExternal = addedDevices?.any { isExternalMediaOutput(it) } == true
            val currentPref = _preferredOutputId.value
            val currentPrefIsPhone = currentPref != null &&
                _outputs.value.firstOrNull { it.id == currentPref }?.type == OutputDeviceType.PHONE
            refreshOutputs()
            // If user plugged in / connected BT and hadn't explicitly pinned phone speaker, let Android auto-route
            if (hasNewExternal && !currentPrefIsPhone && currentPref != null) {
                clearPreference()
            }
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            val removedIds = removedDevices?.map { it.id }?.toSet().orEmpty()
            refreshOutputs()
            val prefId = _preferredOutputId.value
            if (prefId != null && prefId in removedIds) {
                clearPreference()
            }
        }
    }

    init {
        refreshOutputs()
        audioManager?.registerAudioDeviceCallback(deviceCallback, Handler(Looper.getMainLooper()))
    }

    fun refreshOutputs() {
        val raw = audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.toList().orEmpty()
        val parsed = ArrayList<LocalAudioOutput>()
        val seenKeys = HashSet<String>()

        var phoneDevice: AudioDeviceInfo? = null
        for (info in raw) {
            if (info.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                phoneDevice = info
                break
            }
        }
        parsed.add(
            LocalAudioOutput(
                id = phoneDevice?.id ?: -1,
                name = "This phone",
                type = OutputDeviceType.PHONE,
                deviceInfo = phoneDevice,
            )
        )

        for (info in raw) {
            val outType = classifyOutputType(info) ?: continue
            val rawName = info.productName?.toString()?.trim().orEmpty()
            val displayName = when {
                rawName.isNotEmpty() -> rawName
                outType == OutputDeviceType.BLUETOOTH -> "Bluetooth audio"
                else -> "Wired headphones"
            }
            val dedupKey = "${outType.name}:${displayName.lowercase()}"
            if (seenKeys.add(dedupKey)) {
                parsed.add(
                    LocalAudioOutput(
                        id = info.id,
                        name = displayName,
                        type = outType,
                        deviceInfo = info,
                    )
                )
            }
        }

        _outputs.value = parsed
    }

    fun selectOutput(output: LocalAudioOutput) {
        _preferredOutputId.value = output.id
        mainScope.launch {
            runCatching { exoPlayerProvider.get().setPreferredAudioDevice(output.deviceInfo) }
            runCatching { cuePlayerProvider.get().setPreferredAudioDevice(output.deviceInfo) }
        }
    }

    private fun clearPreference() {
        _preferredOutputId.value = null
        mainScope.launch {
            runCatching { exoPlayerProvider.get().setPreferredAudioDevice(null) }
            runCatching { cuePlayerProvider.get().setPreferredAudioDevice(null) }
        }
    }

    private fun isExternalMediaOutput(info: AudioDeviceInfo): Boolean = classifyOutputType(info) != null

    private fun classifyOutputType(info: AudioDeviceInfo): OutputDeviceType? {
        val type = info.type
        if (type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) {
            return OutputDeviceType.BLUETOOTH
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (type == AudioDeviceInfo.TYPE_BLE_HEADSET || type == AudioDeviceInfo.TYPE_BLE_SPEAKER) {
                return OutputDeviceType.BLUETOOTH
            }
        }
        return when (type) {
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE -> OutputDeviceType.WIRED
            else -> null
        }
    }
}
