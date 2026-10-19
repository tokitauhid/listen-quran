package com.listenquran.player

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import javax.inject.Provider

class QuranForwardingPlayer(
    player: Player,
    private val playerManagerProvider: Provider<AudioPlayerManager>,
) : ForwardingPlayer(player) {

    override fun getAvailableCommands(): Player.Commands {
        return super.getAvailableCommands().buildUpon()
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_DEFAULT_POSITION)
            .add(Player.COMMAND_SEEK_BACK)
            .add(Player.COMMAND_SEEK_FORWARD)
            .build()
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
            else -> super.isCommandAvailable(command)
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
}
