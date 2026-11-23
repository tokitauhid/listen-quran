package com.listenquran.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.listenquran.MainActivity
import com.listenquran.R
import com.listenquran.player.cast.OutputDeviceManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
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

    private data class CastForegroundSnapshot(
        val isCastActive: Boolean,
        val isConnecting: Boolean,
        val deviceName: String,
        val trackTitle: String,
        val isPlaying: Boolean,
    )

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

        observeCastForegroundState()
    }

    private fun observeCastForegroundState() {
        serviceScope.launch {
            combine(
                outputDeviceManager.routeState,
                playerManagerProvider.get().playbackState
            ) { route, playback ->
                val active = route.isCasting || route.isConnecting
                CastForegroundSnapshot(
                    isCastActive = active,
                    isConnecting = route.isConnecting,
                    deviceName = route.activeDevice.name,
                    trackTitle = buildTrackTitle(playback),
                    isPlaying = playback.isPlaying,
                )
            }
                .distinctUntilChanged()
                .collect { snap ->
                    if (snap.isCastActive) {
                        wasCastActive = true
                        acquireCastLocks()
                        postCastForegroundNotification(snap.deviceName, snap.isConnecting, snap.trackTitle)
                    } else if (wasCastActive) {
                        wasCastActive = false
                        releaseCastLocks()
                        super.onUpdateNotification(mediaLibrarySession, false)
                    }
                }
        }
    }

    private fun buildTrackTitle(playback: PlaybackState): String {
        val surahTitle = playback.currentSurah?.let { "${it.id}. ${it.nameSimple}" }.orEmpty()
        return if (playback.isRadio && playback.currentRadioStation != null) {
            val stationName = playback.currentRadioStation.name
            if (surahTitle.isNotBlank()) "$stationName • $surahTitle" else stationName
        } else {
            surahTitle
        }
    }

    private fun currentTrackTitle(): String = buildTrackTitle(playerManagerProvider.get().playbackState.value)

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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val route = outputDeviceManager.routeState.value
        if (route.isCasting || route.isConnecting) {
            acquireCastLocks()
            postCastForegroundNotification(route.activeDevice.name, route.isConnecting, currentTrackTitle())
        } else {
            startForegroundNotification()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun startForegroundNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_app_logo)
            .setContentTitle("Listen Quran")
            .setContentText("Quran Audio Playback")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        promoteToForeground(notification)
    }

    private fun postCastForegroundNotification(deviceName: String, isConnecting: Boolean, trackTitle: String) {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val statusText = if (isConnecting) "Connecting to $deviceName..." else "Playing on $deviceName"
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_app_logo)
            .setContentTitle(trackTitle.ifBlank { "Listen Quran" })
            .setContentText(statusText)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
        promoteToForeground(notification)
    }

    private fun promoteToForeground(notification: android.app.Notification) {
        val fgsResult = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }
        if (fgsResult.isFailure) {
            runCatching { NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification) }
        }
    }

    @OptIn(UnstableApi::class)
    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        val route = outputDeviceManager.routeState.value
        if (route.isCasting || route.isConnecting) {
            postCastForegroundNotification(route.activeDevice.name, route.isConnecting, currentTrackTitle())
            return
        }
        super.onUpdateNotification(session, startInForegroundRequired)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val route = outputDeviceManager.routeState.value
        if (route.isCasting || route.isConnecting) {
            return
        }
        super.onTaskRemoved(rootIntent)
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
        serviceScope.cancel()
        releaseCastLocks()
        mediaLibrarySession.run {
            player.release()
            release()
        }
        super.onDestroy()
    }
}
