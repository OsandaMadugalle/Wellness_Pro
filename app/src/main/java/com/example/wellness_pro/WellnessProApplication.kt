package com.example.wellness_pro

import android.app.Application
import android.app.NotificationChannel // ADDED
import android.app.NotificationManager // ADDED
import android.os.Build // ADDED
import android.util.Log
import com.example.wellness_pro.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WellnessProApplication : Application() {

    companion object {
        private const val TAG = "WellnessProApplication"
        // ADDED: Notification Channel Constants
        const val HYDRATION_CHANNEL_ID = "hydration_reminder_channel"
        const val HYDRATION_CHANNEL_NAME = "Hydration Reminders"
        const val HYDRATION_CHANNEL_DESC = "Channel for daily hydration reminders"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: Initializing database...")
        // Initialize the database off the main thread
        CoroutineScope(Dispatchers.IO).launch {
            AppDatabase.initialize(applicationContext)
            Log.d(TAG, "onCreate: Database initialization launched.")
        }

        createNotificationChannels() // ADDED: Call to create channels
    }

    // ADDED: Function to create notification channels
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Hydration Reminder Channel
            val hydrationChannel = NotificationChannel(
                HYDRATION_CHANNEL_ID,
                HYDRATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = HYDRATION_CHANNEL_DESC
                // Optional: Configure other channel properties like lights, vibration, sound
                // enableLights(true)
                // lightColor = Color.BLUE
                // enableVibration(true)
                // setSound(defaultSoundUri, audioAttributes)
            }

            val notificationManager: NotificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(hydrationChannel)
            Log.d(TAG, "Hydration notification channel created.")

            // You can create other notification channels here if needed
        }
    }
}
