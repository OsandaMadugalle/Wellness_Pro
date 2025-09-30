package com.example.wellness_pro.reminders

// import android.app.NotificationChannel // Not strictly needed if WellnessProApplication creates it
// import android.app.NotificationManager // Not strictly needed if WellnessProApplication creates it
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat // Use Compat version
import com.example.wellness_pro.LaunchScreen // CHANGED: To open LaunchScreen
import com.example.wellness_pro.R
import com.example.wellness_pro.WellnessProApplication // ADDED: To use its channel ID constant
import com.example.wellness_pro.db.AppDatabase
import com.example.wellness_pro.db.AppNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class HydrationAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "HydrationAlarmReceiver"
        // NOTIFICATION_ID can be made more dynamic if multiple hydration notifications should stack
        // For now, a single ID means new notifications update the existing one.
        const val NOTIFICATION_ID_HYDRATION = 1001 // Renamed for clarity
        // CHANNEL_ID is now referenced from WellnessProApplication to ensure consistency
        // const val CHANNEL_ID = "hydration_reminder_channel" // This will use WellnessProApplication.HYDRATION_CHANNEL_ID

        const val ACTION_TRIGGER_HYDRATION_REMINDER = "com.example.wellness_pro.ACTION_TRIGGER_HYDRATION_REMINDER"

        // EXTRA_REMINDER_TIME can be used to pass the specific time for which this reminder is firing
        // This can be useful for logging or for making notification IDs unique if needed.
        const val EXTRA_REMINDER_TIME = "extra_reminder_time"
        private const val GENERIC_HYDRATION_ID = "GENERAL_HYDRATION_REMINDER" // ADDED
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive - Alarm received! Timestamp: ${System.currentTimeMillis()}")
        Log.d(TAG, "onReceive - Intent Action: ${intent.action}")

        if (intent.action == ACTION_TRIGGER_HYDRATION_REMINDER) {
            val reminderTimeInfo = intent.getStringExtra(EXTRA_REMINDER_TIME) ?: "Unknown time"
            Log.i(TAG, "onReceive - Matched ACTION_TRIGGER_HYDRATION_REMINDER for time: $reminderTimeInfo")

            // For general hydration, we might not have a specific habitId from the intent.
            // We'll use a generic one for DB logging.
            showNotification(context, reminderTimeInfo, GENERIC_HYDRATION_ID)

            // CRUCIAL: Reschedule the next alarm
            Log.d(TAG, "onReceive - Rescheduling next reminder after current one fired.")
            HydrationReminderManager.scheduleOrUpdateAllReminders(context) // ADDED THIS LINE

        } else {
            Log.w(TAG, "onReceive - Received intent with unexpected action: ${intent.action}")
        }
    }

    private fun showNotification(context: Context, reminderTimeInfo: String, loggingId: String) {
        Log.d(TAG, "showNotification - Called for reminder: $reminderTimeInfo, loggingId: $loggingId")
        // Use NotificationManagerCompat for better compatibility
        val notificationManager = NotificationManagerCompat.from(context)

        // Channel creation is handled in WellnessProApplication, but good practice to ensure it here too
        // especially if this receiver could be triggered before Application.onCreate in some edge cases.
        // However, WellnessProApplication's HYDRATION_CHANNEL_ID should be used.
        // Notification channel creation logic (if needed here) should use WellnessProApplication.HYDRATION_CHANNEL_ID

        val openAppIntent = Intent(context, LaunchScreen::class.java).apply { // CHANGED to LaunchScreen
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            //putExtra("reminder_fired_at", reminderTimeInfo) // Optional: pass info to the activity
        }

        // Using reminderTimeInfo.hashCode() to make request code for PendingIntent more unique if needed for different times
        val requestCode = reminderTimeInfo.hashCode()
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(context, requestCode, openAppIntent, pendingIntentFlags)
        Log.d(TAG, "showNotification - PendingIntent for notification tap created.")

        val notificationIcon = R.drawable.ic_water_drop // IMPORTANT: Ensure you have this drawable
                                                        // Or change to a generic one like R.mipmap.ic_launcher

        val notificationTitle = "Stay Hydrated!"
        val notificationContent = "Time for a glass of water ($reminderTimeInfo)." // Added time info

        val notificationBuilder = NotificationCompat.Builder(context, WellnessProApplication.HYDRATION_CHANNEL_ID) // Use channel from Application class
            .setSmallIcon(notificationIcon)
            .setContentTitle(notificationTitle)
            .setContentText(notificationContent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)

        try {
            // Use NOTIFICATION_ID_HYDRATION. If multiple reminders need to stack, this ID needs to be unique per alarm.
            // For now, each new hydration reminder will update the previous one.
            notificationManager.notify(NOTIFICATION_ID_HYDRATION, notificationBuilder.build())
            Log.i(TAG, "showNotification - System notification displayed successfully for: $reminderTimeInfo.")
        } catch (e: SecurityException) {
            Log.e(TAG, "showNotification - SecurityException displaying notification (missing POST_NOTIFICATIONS permission?): ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "showNotification - Error displaying system notification for $reminderTimeInfo: ${e.message}", e)
        }

        saveNotificationToDatabase(context, loggingId, notificationTitle, notificationContent)
    }

    private fun saveNotificationToDatabase(context: Context, originId: String, title: String, message: String) {
        Log.d(TAG, "saveNotificationToDatabase - Called for originId: $originId, Title: $title")
        CoroutineScope(Dispatchers.IO).launch {
            Log.d(TAG, "saveNotificationToDatabase - Coroutine started on Dispatchers.IO")
            try {
                val notificationDao = AppDatabase.getInstance().appNotificationDao() // CORRECTED: Removed context argument
                val appNotification = AppNotification(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    message = message, // Message already includes reminderTimeInfo
                    timestamp = System.currentTimeMillis(),
                    isRead = false,
                    relatedHabitId = originId // Will be GENERIC_HYDRATION_ID for these
                )
                Log.d(TAG, "saveNotificationToDatabase - AppNotification object created: $appNotification")

                notificationDao.insertNotification(appNotification)
                Log.i(TAG, "saveNotificationToDatabase - Notification successfully saved. ID: ${appNotification.id}, Related ID: $originId")
            } catch (e: Exception) {
                Log.e(TAG, "saveNotificationToDatabase - ERROR saving notification for $originId: ${e.message}", e)
            }
        }
    }
}
