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
            .build()
    }

    override fun isCommandAvailable(command: Int): Boolean {
        return when (command) {
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> true
            else -> super.isCommandAvailable(command)
        }
    }

    override fun hasNextMediaItem(): Boolean = true

    override fun hasPreviousMediaItem(): Boolean = true

    override fun seekToNext() {
        playerManagerProvider.get().playNext()
    }

    override fun seekToNextMediaItem() {
        playerManagerProvider.get().playNext()
    }

    override fun seekToPrevious() {
        playerManagerProvider.get().playPrevious()
    }

    override fun seekToPreviousMediaItem() {
        playerManagerProvider.get().playPrevious()
    }
}
