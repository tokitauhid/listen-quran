package com.listenquran

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.listenquran.ui.ListenQuranApp
import com.listenquran.ui.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        com.listenquran.notification.createReminderNotificationChannel(this)

        handleIntent(intent)

        setContent {
            ListenQuranApp(viewModel = viewModel)
        }

        val splashDeadlineMs = SystemClock.elapsedRealtime() + 450L
        val contentView = findViewById<View>(android.R.id.content)
        contentView.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                return if (viewModel.isInitialStartupReady.value || SystemClock.elapsedRealtime() >= splashDeadlineMs) {
                    contentView.viewTreeObserver.removeOnPreDrawListener(this)
                    true
                } else {
                    false
                }
            }
        })
    }

    override fun onStart() {
        super.onStart()
        viewModel.startBackgroundDiscovery()
        viewModel.autoCheckForUpdatesIfDue()
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResumeCheckInstallPermission(this)
    }

    override fun onStop() {
        viewModel.stopBackgroundDiscovery()
        super.onStop()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (viewModel.outputRouteState.value.isCasting &&
            (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
        ) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                val delta = if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) 5 else -5
                viewModel.adjustRemoteVolume(delta)
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val surahId = intent?.getIntExtra("PLAY_SURAH_ID", -1) ?: -1
        if (surahId > 0) {
            val startAyah = intent?.getIntExtra("PLAY_START_AYAH", 1) ?: 1
            val reciterId = intent?.getIntExtra("PLAY_RECITER_ID", -1) ?: -1
            intent?.removeExtra("PLAY_SURAH_ID")
            intent?.removeExtra("PLAY_START_AYAH")
            intent?.removeExtra("PLAY_RECITER_ID")
            viewModel.playSurahById(surahId, startAyah, if (reciterId > 0) reciterId else null)
        }
    }
}
