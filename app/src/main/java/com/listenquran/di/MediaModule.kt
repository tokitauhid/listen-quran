package com.listenquran.di

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.DefaultAudioTrackBufferSizeProvider
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionToken
import com.listenquran.MainActivity
import com.listenquran.player.AudioPlayerManager
import com.listenquran.player.ListenQuranMediaService
import com.listenquran.player.QuranForwardingPlayer
import com.listenquran.player.QuranLibrarySessionCallback
import com.listenquran.player.cast.CastAudioModeController
import com.listenquran.player.cast.CastPcmAudioSink
import com.listenquran.player.cast.DeviceDiscoveryClient
import com.listenquran.player.cast.DlnaControlClient
import com.listenquran.player.cast.FadeGainAudioProcessor
import com.listenquran.player.cast.OutputDeviceManager
import com.listenquran.player.cast.PcmSource
import com.listenquran.player.cast.PcmStreamHub
import com.listenquran.player.cast.RemoteControlClient
import com.listenquran.player.cast.RoutingAudioSink
import com.listenquran.player.cast.SsdpDiscoveryClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MediaModule {

    @Provides
    @Singleton
    fun provideAudioAttributes(): AudioAttributes {
        return AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
    }

    @Provides
    @Singleton
    fun provideMainFadeGainAudioProcessor(): FadeGainAudioProcessor = FadeGainAudioProcessor()

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @Provides
    @Singleton
    fun provideSimpleCache(@ApplicationContext context: Context): androidx.media3.datasource.cache.SimpleCache {
        val cacheDir = java.io.File(context.filesDir, "offline_audio_cache")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        val databaseProvider = androidx.media3.database.StandaloneDatabaseProvider(context)
        return androidx.media3.datasource.cache.SimpleCache(
            cacheDir,
            androidx.media3.datasource.cache.NoOpCacheEvictor(),
            databaseProvider
        )
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @Provides
    @Singleton
    fun provideCacheDataSourceFactory(
        cache: androidx.media3.datasource.cache.SimpleCache,
    ): androidx.media3.datasource.cache.CacheDataSource.Factory {
        val upstreamFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
        return androidx.media3.datasource.cache.CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun buildStreamingExoPlayer(
        context: Context,
        audioAttributes: AudioAttributes,
        handleAudioFocus: Boolean,
        handleBecomingNoisy: Boolean,
        cacheDataSourceFactory: androidx.media3.datasource.cache.CacheDataSource.Factory,
        fadeGainAudioProcessor: FadeGainAudioProcessor,
        pcmStreamHub: PcmStreamHub,
        pcmSource: PcmSource,
        castAudioModeController: CastAudioModeController,
    ): ExoPlayer {
        val bufferSizeProvider = DefaultAudioTrackBufferSizeProvider.Builder()
            .setMinPcmBufferDurationUs(120_000)
            .setMaxPcmBufferDurationUs(250_000)
            .setPcmBufferMultiplicationFactor(2)
            .build()
        val localSink = DefaultAudioSink.Builder(context)
            .setAudioProcessors(arrayOf(fadeGainAudioProcessor))
            .setAudioTrackBufferSizeProvider(bufferSizeProvider)
            .build()
        val castSink = CastPcmAudioSink(
            fadeGainAudioProcessor = fadeGainAudioProcessor,
            pcmStreamHub = pcmStreamHub,
            source = pcmSource,
        )
        val routingSink = RoutingAudioSink(
            localSink = localSink,
            castSink = castSink,
            castModeController = castAudioModeController,
        )
        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink = routingSink
        }
        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context)
            .setDataSourceFactory(cacheDataSourceFactory)

        val player = ExoPlayer.Builder(context, renderersFactory)
            .setAudioAttributes(audioAttributes, handleAudioFocus)
            .setHandleAudioBecomingNoisy(handleBecomingNoisy)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()

        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        castAudioModeController.addListener {
            mainHandler.post {
                runCatching {
                    player.createMessage { _, _ ->
                        routingSink.syncModeIfNeeded()
                    }.send()
                }
            }
        }
        return player
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @Provides
    @Singleton
    fun provideExoPlayer(
        @ApplicationContext context: Context,
        audioAttributes: AudioAttributes,
        cacheDataSourceFactory: androidx.media3.datasource.cache.CacheDataSource.Factory,
        fadeGainAudioProcessor: FadeGainAudioProcessor,
        pcmStreamHub: PcmStreamHub,
        castAudioModeController: CastAudioModeController,
    ): ExoPlayer {
        return buildStreamingExoPlayer(
            context = context,
            audioAttributes = audioAttributes,
            handleAudioFocus = true,
            handleBecomingNoisy = true,
            cacheDataSourceFactory = cacheDataSourceFactory,
            fadeGainAudioProcessor = fadeGainAudioProcessor,
            pcmStreamHub = pcmStreamHub,
            pcmSource = PcmSource.MAIN,
            castAudioModeController = castAudioModeController,
        )
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @Provides
    @Singleton
    @Named("cue")
    fun provideCueExoPlayer(
        @ApplicationContext context: Context,
        audioAttributes: AudioAttributes,
        cacheDataSourceFactory: androidx.media3.datasource.cache.CacheDataSource.Factory,
        pcmStreamHub: PcmStreamHub,
        castAudioModeController: CastAudioModeController,
    ): ExoPlayer {
        return buildStreamingExoPlayer(
            context = context,
            audioAttributes = audioAttributes,
            handleAudioFocus = false,
            handleBecomingNoisy = false,
            cacheDataSourceFactory = cacheDataSourceFactory,
            fadeGainAudioProcessor = FadeGainAudioProcessor(),
            pcmStreamHub = pcmStreamHub,
            pcmSource = PcmSource.CUE,
            castAudioModeController = castAudioModeController,
        )
    }

    @Provides
    @Singleton
    fun provideMediaLibrarySession(
        @ApplicationContext context: Context,
        player: ExoPlayer,
        playerManagerProvider: Provider<AudioPlayerManager>,
        outputDeviceManager: OutputDeviceManager,
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

        val forwardingPlayer = QuranForwardingPlayer(player, playerManagerProvider, outputDeviceManager)

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

    @Provides
    @Singleton
    fun provideDeviceDiscoveryClient(impl: SsdpDiscoveryClient): DeviceDiscoveryClient = impl

    @Provides
    @Singleton
    fun provideRemoteControlClient(impl: DlnaControlClient): RemoteControlClient = impl
}
