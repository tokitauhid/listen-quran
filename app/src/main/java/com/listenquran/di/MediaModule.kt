package com.listenquran.di

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionToken
import com.listenquran.MainActivity
import com.listenquran.player.AudioPlayerManager
import com.listenquran.player.ListenQuranMediaService
import com.listenquran.player.QuranForwardingPlayer
import com.listenquran.player.QuranLibrarySessionCallback
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MediaModule {

    @Provides
    @Singleton
    fun provideAudioAttributes(): AudioAttributes {
        return AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .setUsage(C.USAGE_MEDIA)
            .build()
    }

    @Provides
    @Singleton
    fun provideExoPlayer(
        @ApplicationContext context: Context,
        audioAttributes: AudioAttributes,
    ): ExoPlayer {
        return ExoPlayer.Builder(context)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
    }

    @Provides
    @Singleton
    fun provideMediaLibrarySession(
        @ApplicationContext context: Context,
        player: ExoPlayer,
        playerManagerProvider: Provider<AudioPlayerManager>,
        callback: QuranLibrarySessionCallback,
    ): MediaLibrarySession {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val forwardingPlayer = QuranForwardingPlayer(player, playerManagerProvider)

        return MediaLibrarySession.Builder(context, forwardingPlayer, callback)
            .setSessionActivity(pendingIntent)
            .build()
    }

    @Provides
    fun provideMediaSession(mediaLibrarySession: MediaLibrarySession): MediaSession {
        return mediaLibrarySession
    }

    @Provides
    @Singleton
    fun provideSessionToken(
        @ApplicationContext context: Context,
    ): SessionToken {
        return SessionToken(
            context,
            ComponentName(context, ListenQuranMediaService::class.java)
        )
    }
}
