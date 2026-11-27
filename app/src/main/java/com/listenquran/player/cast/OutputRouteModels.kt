package com.listenquran.player.cast

import androidx.compose.runtime.Immutable

enum class CastProtocol {
    LOCAL,
    DLNA
}

enum class OutputDeviceType {
    PHONE,
    BLUETOOTH,
    WIRED,
    TV,
    SPEAKER,
    RECEIVER,
    UNKNOWN
}

enum class CastConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

enum class LiveStreamFormat {
    WAV,
    L16
}

@Immutable
data class OutputDevice(
    val id: String,
    val name: String,
    val manufacturer: String? = null,
    val modelName: String? = null,
    val protocol: CastProtocol = CastProtocol.DLNA,
    val deviceType: OutputDeviceType = OutputDeviceType.SPEAKER,
    val avTransportControlUrl: String? = null,
    val renderingControlUrl: String? = null,
    val locationUrl: String? = null,
    val hostAddress: String? = null,
) {
    companion object {
        val LOCAL_PHONE = OutputDevice(
            id = "local_phone",
            name = "This phone",
            protocol = CastProtocol.LOCAL,
            deviceType = OutputDeviceType.PHONE,
        )
    }
}

@Immutable
data class OutputRouteState(
    val activeDevice: OutputDevice = OutputDevice.LOCAL_PHONE,
    val discoveredDevices: List<OutputDevice> = emptyList(),
    val isScanning: Boolean = false,
    val connectionState: CastConnectionState = CastConnectionState.DISCONNECTED,
    val remoteVolume: Int = 50,
    val isRemoteMuted: Boolean = false,
    val errorMessage: String? = null,
    val speakerDelayMs: Long = 0L,
) {
    val isCasting: Boolean
        get() = activeDevice.protocol != CastProtocol.LOCAL &&
            connectionState == CastConnectionState.CONNECTED

    val isConnecting: Boolean
        get() = activeDevice.protocol != CastProtocol.LOCAL &&
            connectionState == CastConnectionState.CONNECTING
}

data class LiveTrackMetadata(
    val title: String,
    val artist: String,
    val album: String = "The Holy Quran",
    val artworkUrl: String? = null,
)

data class RemotePositionInfo(
    val relTimeMs: Long,
    val trackDurationMs: Long,
    val trackUri: String?,
)

enum class RemoteTransportState {
    PLAYING,
    PAUSED,
    STOPPED,
    TRANSITIONING,
    NO_MEDIA,
    UNKNOWN;

    companion object {
        fun fromUpnpString(value: String?): RemoteTransportState {
            return when (value?.trim()?.uppercase()) {
                "PLAYING" -> PLAYING
                "PAUSED_PLAYBACK", "PAUSED" -> PAUSED
                "STOPPED" -> STOPPED
                "TRANSITIONING" -> TRANSITIONING
                "NO_MEDIA_PRESENT" -> NO_MEDIA
                else -> UNKNOWN
            }
        }
    }
}
