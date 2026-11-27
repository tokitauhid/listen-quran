package com.listenquran.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.listenquran.R
import com.listenquran.player.cast.OutputDeviceManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Provider

@AndroidEntryPoint
class ListenQuranMediaService : MediaLibraryService() {

    @Inject
    lateinit var player: ExoPlayer

    @Inject
    lateinit var mediaLibrarySession: MediaLibrarySession

    @Inject
    lateinit var outputDeviceManager: OutputDeviceManager

    @Inject
    lateinit var playerManagerProvider: Provider<AudioPlayerManager>

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var castWakeLock: PowerManager.WakeLock? = null
    private var castWifiLock: WifiManager.WifiLock? = null
    private var wasCastActive = false

    companion object {
        const val CHANNEL_ID = "listen_quran_playback_channel"
        const val NOTIFICATION_ID = 1001
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(CHANNEL_ID)
            .setChannelName(R.string.app_name)
            .setNotificationId(NOTIFICATION_ID)
            .build()
            .apply {
                setSmallIcon(R.drawable.ic_app_logo)
            }
        setMediaNotificationProvider(notificationProvider)

        observeCastLocks()
    }

    private fun observeCastLocks() {
        serviceScope.launch {
            outputDeviceManager.routeState
                .map { it.isCasting || it.isConnecting }
                .distinctUntilChanged()
                .collect { active ->
                    if (active) {
                        wasCastActive = true
                        acquireCastLocks()
                    } else if (wasCastActive) {
                        wasCastActive = false
                        releaseCastLocks()
                    }
                }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Quran Audio Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Quran recitation audio controls and playback status"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    @OptIn(UnstableApi::class)
    override fun onTaskRemoved(rootIntent: Intent?) {
        runCatching {
            playerManagerProvider.get().saveSessionSync(timeoutMs = 500L)
        }
        runCatching {
            runBlocking {
                withTimeoutOrNull(1500L) {
                    outputDeviceManager.disconnectAndAwaitStop()
                }
            }
        }
        runCatching { player.pause() }
        pauseAllPlayersAndStopSelf()
    }

    private fun acquireCastLocks() {
        runCatching {
            if (castWakeLock == null) {
                val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
                castWakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ListenQuran:CastWakeLock")?.apply {
                    setReferenceCounted(false)
                }
            }
            if (castWakeLock?.isHeld == false) {
                castWakeLock?.acquire(6 * 60 * 60 * 1000L)
            }
        }
        runCatching {
            if (castWifiLock == null) {
                val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val lockType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                } else {
                    @Suppress("DEPRECATION")
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF
                }
                castWifiLock = wm?.createWifiLock(lockType, "ListenQuran:CastWifiLock")?.apply {
                    setReferenceCounted(false)
                }
            }
            if (castWifiLock?.isHeld == false) {
                castWifiLock?.acquire()
            }
        }
    }

    private fun releaseCastLocks() {
        runCatching {
            if (castWakeLock?.isHeld == true) {
                castWakeLock?.release()
            }
        }
        runCatching {
            if (castWifiLock?.isHeld == true) {
                castWifiLock?.release()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession {
        return mediaLibrarySession
    }

    override fun onDestroy() {
        runCatching {
            playerManagerProvider.get().saveSessionSync(timeoutMs = 300L)
        }
        serviceScope.cancel()
        releaseCastLocks()
        mediaLibrarySession.run {
            player.release()
            release()
        }
        super.onDestroy()
    }
}
