package com.listenquran

import android.content.Intent
import android.os.Bundle
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
            ListenQuranApp()
        }
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
            viewModel.playSurahById(surahId, startAyah, if (reciterId > 0) reciterId else null)
        }
    }
}
