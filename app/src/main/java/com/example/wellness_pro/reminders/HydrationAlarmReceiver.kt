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
import com.example.wellness_pro.LaunchScreen
import com.example.wellness_pro.R
import com.example.wellness_pro.WellnessProApplication
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
        const val ACTION_SNOOZE_HYDRATION_REMINDER = "com.example.wellness_pro.ACTION_SNOOZE_HYDRATION_REMINDER"
        const val ACTION_MARK_HYDRATION_DRANK = "com.example.wellness_pro.ACTION_MARK_HYDRATION_DRANK"

        // EXTRA_REMINDER_TIME can be used to pass the specific time for which this reminder is firing
        // This can be useful for logging or for making notification IDs unique if needed.
        const val EXTRA_REMINDER_TIME = "extra_reminder_time"
        const val EXTRA_SNOOZE_MINUTES = "extra_snooze_minutes"
        private const val GENERIC_HYDRATION_ID = "GENERAL_HYDRATION_REMINDER"
        private const val SNOOZE_MINUTES_DEFAULT = 15
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive - Alarm received! Timestamp: ${System.currentTimeMillis()}")
        Log.d(TAG, "onReceive - Intent Action: ${intent.action}")

        when (intent.action) {
            ACTION_TRIGGER_HYDRATION_REMINDER -> {
                val reminderTimeInfo = intent.getStringExtra(EXTRA_REMINDER_TIME) ?: "Unknown time"
                Log.i(TAG, "onReceive - Matched ACTION_TRIGGER_HYDRATION_REMINDER for time: $reminderTimeInfo")
                showNotification(context, reminderTimeInfo, GENERIC_HYDRATION_ID)
                Log.d(TAG, "onReceive - Rescheduling next reminder after current one fired.")
                HydrationReminderManager.scheduleOrUpdateAllReminders(context)
            }
            ACTION_SNOOZE_HYDRATION_REMINDER -> {
                val snoozeMinutes = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, SNOOZE_MINUTES_DEFAULT)
                val snoozeMillis = System.currentTimeMillis() + snoozeMinutes * 60 * 1000
                Log.i(TAG, "onReceive - SNOOZE pressed. Rescheduling hydration reminder for $snoozeMinutes minutes later.")
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                val snoozeIntent = Intent(context, HydrationAlarmReceiver::class.java).apply {
                    action = ACTION_TRIGGER_HYDRATION_REMINDER
                    putExtra(EXTRA_REMINDER_TIME, "Snoozed")
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    NOTIFICATION_ID_HYDRATION + 1, // Unique code for snooze
                    snoozeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, snoozeMillis, pendingIntent)
                // Optionally, show a Toast
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(context, context.getString(R.string.snoozed_for_x_minutes, snoozeMinutes), android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            ACTION_MARK_HYDRATION_DRANK -> {
                Log.i(TAG, "onReceive - MARK AS DRANK pressed. Logging water intake.")
                val prefsName = com.example.wellness_pro.ui.HydrationActivity.PREFS_NAME
                val keyPrefix = com.example.wellness_pro.ui.HydrationActivity.KEY_HYDRATION_INTAKE_PREFIX
                val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                val intakeKey = keyPrefix + today
                val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                val current = prefs.getInt(intakeKey, 0)
                prefs.edit().putInt(intakeKey, current + 1).putLong("last_drink_timestamp", System.currentTimeMillis()).apply()
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(context, context.getString(R.string.marked_one_glass), android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            else -> {
                Log.w(TAG, "onReceive - Received intent with unexpected action: ${intent.action}")
            }
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

        val openAppIntent = Intent(context, com.example.wellness_pro.LaunchScreen::class.java).apply { // CHANGED to LaunchScreen
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

        val notificationIcon = R.drawable.ic_water_drop
        val prefs = context.getSharedPreferences(com.example.wellness_pro.ui.HydrationActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val customMessage = prefs.getString("custom_hydration_message", null)?.takeIf { it.isNotBlank() }
        val notificationTitle = context.getString(R.string.stay_hydrated_title)
        val baseContent = context.getString(R.string.hydration_reminder_content, reminderTimeInfo)
        val notificationContent = customMessage ?: baseContent

        // Ensure channel exists per current prefs and get its ID
        com.example.wellness_pro.WellnessProApplication.ensureHydrationChannel(context)
        val channelId = com.example.wellness_pro.WellnessProApplication.getHydrationChannelId(context)

        // --- Add Snooze Action ---
        val snoozeIntent = Intent(context, HydrationAlarmReceiver::class.java).apply {
            action = ACTION_SNOOZE_HYDRATION_REMINDER
            putExtra(EXTRA_SNOOZE_MINUTES, SNOOZE_MINUTES_DEFAULT)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            NOTIFICATION_ID_HYDRATION + 1,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snoozeAction = NotificationCompat.Action.Builder(
            R.drawable.ic_clear_all, // Use an existing icon
            context.getString(R.string.snooze),
            snoozePendingIntent
        ).build()

        // --- Add Mark as Drank Action ---
        val markIntent = Intent(context, HydrationAlarmReceiver::class.java).apply {
            action = ACTION_MARK_HYDRATION_DRANK
        }
        val markPendingIntent = PendingIntent.getBroadcast(
            context,
            NOTIFICATION_ID_HYDRATION + 2,
            markIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val markAction = NotificationCompat.Action.Builder(
            R.drawable.ic_check_circle_outline, // Use an existing icon
            context.getString(R.string.mark_as_drank),
            markPendingIntent
        ).build()

        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(notificationIcon)
            .setContentTitle(notificationTitle)
            .setContentText(notificationContent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .addAction(snoozeAction)
            .addAction(markAction)

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
                val notificationDao = AppDatabase.getInstance().appNotificationDao()
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
