package com.listenquran.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.listenquran.MainActivity
import com.listenquran.R
import java.util.Calendar

const val REMINDER_CHANNEL_ID = "listen_quran_reminders"
const val FRIDAY_KAHF_NOTIFICATION_ID = 1001
const val DAILY_STREAK_NOTIFICATION_ID = 1002

fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            REMINDER_CHANNEL_ID,
            "Quran Reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Friday Surah Al-Kahf reminders and daily Quran listening streak notifications"
        }
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}

class FridayKahfReminderWorker(
    private val context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        createNotificationChannel(context)

        // Verify that today is Friday (Calendar.FRIDAY)
        val calendar = Calendar.getInstance()
        if (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.FRIDAY) {
            return Result.success()
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("target_surah", 18) // Al-Kahf
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Surah Al-Kahf Reminder 📖")
            .setContentText("Today is blessed Friday! Don't forget to listen to Surah Al-Kahf for light between the two Fridays.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("The Prophet (ﷺ) said: 'Whoever reads Surah Al-Kahf on Friday, a light will shine for him between the two Fridays.' Listen now with your favorite reciter.")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(FRIDAY_KAHF_NOTIFICATION_ID, notification)

        return Result.success()
    }
}

class DailyStreakReminderWorker(
    private val context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Keep Your Quran Connection Alive ✨")
            .setContentText("Take a moment today to listen to and reflect upon the Holy Quran.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(DAILY_STREAK_NOTIFICATION_ID, notification)

        return Result.success()
    }
}
