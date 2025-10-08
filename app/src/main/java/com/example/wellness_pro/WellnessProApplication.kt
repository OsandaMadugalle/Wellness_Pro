package com.example.wellness_pro

import android.app.Application
import android.app.NotificationChannel // ADDED
import android.app.NotificationManager // ADDED
import android.os.Build // ADDED
import android.util.Log
import com.example.wellness_pro.db.AppDatabase
import kotlinx.coroutines.runBlocking
import android.content.Context

class WellnessProApplication : Application() {

    companion object {
        private const val TAG = "WellnessProApplication"
    // Notification Channel Constants
        const val HYDRATION_CHANNEL_ID = "hydration_reminder_channel"
        const val HYDRATION_CHANNEL_NAME = "Hydration Reminders"
        const val HYDRATION_CHANNEL_DESC = "Channel for daily hydration reminders"
        private const val PREFS_NAME = com.example.wellness_pro.ui.SetHydrationActivity.PREFS_NAME
        private const val PREF_KEY_SOUND = "reminder_sound_enabled"
        private const val PREF_KEY_VIBRATION = "reminder_vibration_enabled"
        private const val PREF_KEY_CHANNEL_ID = "hydration_channel_id"

        fun getHydrationChannelId(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(PREF_KEY_CHANNEL_ID, HYDRATION_CHANNEL_ID) ?: HYDRATION_CHANNEL_ID
        }

        fun ensureHydrationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val soundEnabled = prefs.getBoolean(PREF_KEY_SOUND, true)
                val vibrationEnabled = prefs.getBoolean(PREF_KEY_VIBRATION, true)
                val dynamicChannelId = "${HYDRATION_CHANNEL_ID}_s${if (soundEnabled) 1 else 0}_v${if (vibrationEnabled) 1 else 0}"

                val notificationManager: NotificationManager =
                    context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager

                // Create channel with desired settings (new ID if options changed)
                val channel = NotificationChannel(
                    dynamicChannelId,
                    HYDRATION_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = HYDRATION_CHANNEL_DESC
                    enableVibration(vibrationEnabled)
                    if (!soundEnabled) {
                        setSound(null, null)
                    }
                }
                notificationManager.createNotificationChannel(channel)

                // Save current channel id for use by notifications
                if (prefs.getString(PREF_KEY_CHANNEL_ID, null) != dynamicChannelId) {
                    prefs.edit().putString(PREF_KEY_CHANNEL_ID, dynamicChannelId).apply()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: Initializing database (blocking)...")
        // Initialize the database synchronously so other components don't access it before ready
        runBlocking {
            AppDatabase.initialize(applicationContext)
        }
        Log.d(TAG, "onCreate: Database initialization complete.")

    createNotificationChannels()
        ensureHydrationChannel(this)
    }

    // Function to create notification channels
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
