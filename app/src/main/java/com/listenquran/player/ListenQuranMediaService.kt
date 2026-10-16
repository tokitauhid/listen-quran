package com.listenquran.player

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ListenQuranMediaService : MediaSessionService() {

    @Inject
    lateinit var player: ExoPlayer

    @Inject
    lateinit var mediaSession: MediaSession

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession {
        return mediaSession
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
