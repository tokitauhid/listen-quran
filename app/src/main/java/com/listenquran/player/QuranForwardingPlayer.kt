package com.listenquran.player

import androidx.media3.common.DeviceInfo
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import com.listenquran.player.cast.OutputDeviceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArraySet
import javax.inject.Provider

class QuranForwardingPlayer(
    player: Player,
    private val playerManagerProvider: Provider<AudioPlayerManager>,
    private val outputDeviceManager: OutputDeviceManager,
) : ForwardingPlayer(player) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val listeners = CopyOnWriteArraySet<Player.Listener>()

    private data class RemoteVolSnapshot(
        val isCasting: Boolean,
        val deviceId: String,
        val volume: Int,
        val isMuted: Boolean,
    )

    init {
        scope.launch {
            var previous: RemoteVolSnapshot? = null
            outputDeviceManager.routeState
                .map { route ->
                    RemoteVolSnapshot(
                        isCasting = route.isCasting,
                        deviceId = route.activeDevice.id,
                        volume = route.remoteVolume,
                        isMuted = route.isRemoteMuted,
                    )
                }
                .distinctUntilChanged()
                .collect { snap ->
                    val prev = previous
                    previous = snap
                    if (prev == null) return@collect

                    if (prev.isCasting != snap.isCasting || prev.deviceId != snap.deviceId) {
                        val cmds = availableCommands
                        val info = deviceInfo
                        val vol = deviceVolume
                        val muted = isDeviceMuted
                        listeners.forEach { listener ->
                            listener.onAvailableCommandsChanged(cmds)
                            listener.onDeviceInfoChanged(info)
                            listener.onDeviceVolumeChanged(vol, muted)
                        }
                    } else if (snap.isCasting && (prev.volume != snap.volume || prev.isMuted != snap.isMuted)) {
                        listeners.forEach { listener ->
                            listener.onDeviceVolumeChanged(snap.volume, snap.isMuted)
                        }
                    }
                }
        }
    }

    override fun addListener(listener: Player.Listener) {
        listeners.add(listener)
        super.addListener(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        listeners.remove(listener)
        super.removeListener(listener)
    }

    override fun getAvailableCommands(): Player.Commands {
        val builder = super.getAvailableCommands().buildUpon()
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_DEFAULT_POSITION)
            .add(Player.COMMAND_SEEK_BACK)
            .add(Player.COMMAND_SEEK_FORWARD)
        if (outputDeviceManager.isCasting) {
            builder
                .add(Player.COMMAND_GET_DEVICE_VOLUME)
                .add(Player.COMMAND_SET_DEVICE_VOLUME)
                .add(Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS)
                .add(Player.COMMAND_ADJUST_DEVICE_VOLUME)
                .add(Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS)
        }
        return builder.build()
    }

    override fun isCommandAvailable(command: Int): Boolean {
        return when (command) {
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
            Player.COMMAND_SEEK_BACK,
            Player.COMMAND_SEEK_FORWARD -> true
            Player.COMMAND_GET_DEVICE_VOLUME,
            Player.COMMAND_SET_DEVICE_VOLUME,
            Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS,
            Player.COMMAND_ADJUST_DEVICE_VOLUME,
            Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS ->
                outputDeviceManager.isCasting || super.isCommandAvailable(command)
            else -> super.isCommandAvailable(command)
        }
    }

    override fun getDeviceInfo(): DeviceInfo {
        val route = outputDeviceManager.routeState.value
        return if (route.isCasting) {
            DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE)
                .setMinVolume(0)
                .setMaxVolume(100)
                .setRoutingControllerId(route.activeDevice.id)
                .build()
        } else {
            super.getDeviceInfo()
        }
    }

    override fun getDeviceVolume(): Int {
        val route = outputDeviceManager.routeState.value
        return if (route.isCasting) route.remoteVolume else super.getDeviceVolume()
    }

    override fun isDeviceMuted(): Boolean {
        val route = outputDeviceManager.routeState.value
        return if (route.isCasting) route.isRemoteMuted else super.isDeviceMuted()
    }

    @Deprecated("Deprecated in Media3 Player")
    override fun setDeviceVolume(volume: Int) {
        setDeviceVolume(volume, 0)
    }

    override fun setDeviceVolume(volume: Int, flags: Int) {
        if (outputDeviceManager.isCasting) {
            val target = volume.coerceIn(0, 100)
            scope.launch(Dispatchers.IO) {
                outputDeviceManager.setRemoteVolume(target)
            }
        } else {
            super.setDeviceVolume(volume, flags)
        }
    }

    @Deprecated("Deprecated in Media3 Player")
    override fun increaseDeviceVolume() {
        increaseDeviceVolume(0)
    }

    override fun increaseDeviceVolume(flags: Int) {
        if (outputDeviceManager.isCasting) {
            val next = (outputDeviceManager.routeState.value.remoteVolume + VOLUME_STEP).coerceIn(0, 100)
            scope.launch(Dispatchers.IO) {
                outputDeviceManager.setRemoteVolume(next)
            }
        } else {
            super.increaseDeviceVolume(flags)
        }
    }

    @Deprecated("Deprecated in Media3 Player")
    override fun decreaseDeviceVolume() {
        decreaseDeviceVolume(0)
    }

    override fun decreaseDeviceVolume(flags: Int) {
        if (outputDeviceManager.isCasting) {
            val next = (outputDeviceManager.routeState.value.remoteVolume - VOLUME_STEP).coerceIn(0, 100)
            scope.launch(Dispatchers.IO) {
                outputDeviceManager.setRemoteVolume(next)
            }
        } else {
            super.decreaseDeviceVolume(flags)
        }
    }

    @Deprecated("Deprecated in Media3 Player")
    override fun setDeviceMuted(muted: Boolean) {
        setDeviceMuted(muted, 0)
    }

    override fun setDeviceMuted(muted: Boolean, flags: Int) {
        if (outputDeviceManager.isCasting) {
            scope.launch(Dispatchers.IO) {
                outputDeviceManager.setRemoteMuted(muted)
            }
        } else {
            super.setDeviceMuted(muted, flags)
        }
    }

    override fun hasNextMediaItem(): Boolean {
        return super.hasNextMediaItem() || playerManagerProvider.get().playbackState.value.isRadio
    }

    override fun hasPreviousMediaItem(): Boolean {
        return super.hasPreviousMediaItem()
    }

    override fun seekToNext() {
        if (super.hasNextMediaItem()) {
            super.seekToNext()
        } else {
            playerManagerProvider.get().playNext()
        }
    }

    override fun seekToNextMediaItem() {
        if (super.hasNextMediaItem()) {
            super.seekToNextMediaItem()
        } else {
            playerManagerProvider.get().playNext()
        }
    }

    override fun seekToPrevious() {
        if (super.hasPreviousMediaItem()) {
            super.seekToPrevious()
        } else {
            playerManagerProvider.get().playPrevious()
        }
    }

    override fun seekToPreviousMediaItem() {
        if (super.hasPreviousMediaItem()) {
            super.seekToPreviousMediaItem()
        } else {
            playerManagerProvider.get().playPrevious()
        }
    }

    private companion object {
        const val VOLUME_STEP = 5
    }
}
