// file: com/example/playpal/reminders/HydrationAlarmReceiver.kt
package com.example.wellness_pro.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.wellness_pro.HabitsScreen // Or your main screen to open on tap
import com.example.wellness_pro.R // Make sure you have your app icon resource
import com.example.wellness_pro.db.AppDatabase // Assuming this is your Room database class
import com.example.wellness_pro.db.AppNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class HydrationAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "HydrationAlarmReceiver"
        const val NOTIFICATION_ID = 1001 // This is for the system notification
        const val CHANNEL_ID = "hydration_reminder_channel"
        const val ACTION_TRIGGER_HYDRATION_REMINDER = "com.example.wellness_pro.ACTION_TRIGGER_HYDRATION_REMINDER" // Corrected package name
        const val EXTRA_HABIT_ID = "habit_id_for_reminder"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive - Alarm received! Timestamp: ${System.currentTimeMillis()}")
        Log.d(TAG, "onReceive - Intent Action: ${intent.action}")
        Log.d(TAG, "onReceive - Intent Extras: ${intent.extras?.keySet()?.joinToString { key -> "$key: ${intent.extras?.get(key)}" }}")

        if (intent.action == ACTION_TRIGGER_HYDRATION_REMINDER) {
            val habitId = intent.getStringExtra(EXTRA_HABIT_ID)
            Log.i(TAG, "onReceive - Matched ACTION_TRIGGER_HYDRATION_REMINDER for habit ID: $habitId")

            if (habitId.isNullOrBlank()) {
                Log.e(TAG, "onReceive - Habit ID is null or blank. Cannot process reminder. Aborting.")
                return
            }
            Log.d(TAG, "onReceive - Proceeding to show notification for habit ID: $habitId")
            showNotification(context, habitId) // Pass habitId along
        } else {
            Log.w(TAG, "onReceive - Received intent with unexpected action: ${intent.action}")
        }
    }

    private fun showNotification(context: Context, habitId: String) {
        Log.d(TAG, "showNotification - Called for habit ID: $habitId")
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Hydration Reminders"
            val descriptionText = "Reminders to drink water"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "showNotification - Notification channel '$CHANNEL_ID' created or ensured.")
        }

        val openAppIntent = Intent(context, HabitsScreen::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            // Optionally, pass habitId to the screen if you want to navigate to a specific habit
            // putExtra("focus_habit_id", habitId) 
        }
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(context, habitId.hashCode(), openAppIntent, pendingIntentFlags) // Use habitId.hashCode() for a more unique request code for the PI
        Log.d(TAG, "showNotification - PendingIntent for notification tap created.")

        // TODO: Potentially fetch habit details using habitId to customize notification title/text further
        // For now, using generic text as before.
        val notificationIcon = R.drawable.ic_launcher_foreground // Ensure this drawable exists

        val notificationTitle = "Stay Hydrated!"
        val notificationContent = "Time for a glass of water. Keep up with your hydration goal!"

        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(notificationIcon)
            .setContentTitle(notificationTitle)
            .setContentText(notificationContent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true) // Avoids re-alerting if the notification is updated but still visible

        Log.d(TAG, "showNotification - Attempting to display system notification (ID: $NOTIFICATION_ID) for habit: $habitId")
        try {
            notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build()) // Use a consistent NOTIFICATION_ID or a unique one per habit if multiple can appear
            Log.i(TAG, "showNotification - System notification displayed successfully for habit ID: $habitId.")
        } catch (e: Exception) {
            Log.e(TAG, "showNotification - Error displaying system notification for habit ID $habitId: ${e.message}", e)
            // If it fails here, saving to DB might still be useful for an in-app list, but the user won't get a system tray notification.
        }
        
        Log.d(TAG, "showNotification - Proceeding to save notification to database for habit ID: $habitId")
        saveNotificationToDatabase(context, habitId, notificationTitle, notificationContent)
    }

    private fun saveNotificationToDatabase(context: Context, habitIdOrigin: String, title: String, message: String) {
        Log.d(TAG, "saveNotificationToDatabase - Called for habitIdOrigin: $habitIdOrigin, Title: $title")
        CoroutineScope(Dispatchers.IO).launch {
            Log.d(TAG, "saveNotificationToDatabase - Coroutine started on Dispatchers.IO")
            try {
                val notificationDao = AppDatabase.getInstance().appNotificationDao()
                val appNotification = AppNotification(
                    id = UUID.randomUUID().toString(), 
                    title = title,
                    message = "$message (Related to habit: $habitIdOrigin)", // Include habitId for context
                    timestamp = System.currentTimeMillis(),
                    isRead = false,
                    relatedHabitId = habitIdOrigin // Store the originating habit ID
                )
                Log.d(TAG, "saveNotificationToDatabase - AppNotification object created: $appNotification")
                
                notificationDao.insertNotification(appNotification)
                Log.i(TAG, "saveNotificationToDatabase - Notification successfully saved to app database. ID: ${appNotification.id}, Related Habit ID: $habitIdOrigin")
            } catch (e: Exception) {
                Log.e(TAG, "saveNotificationToDatabase - ERROR saving notification to database for habit ID $habitIdOrigin: ${e.message}", e)
            }
        }
    }
}
