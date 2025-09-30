package com.example.wellness_pro.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TreeSet

object HydrationReminderManager {

    internal const val TAG = "HydrationReminderMgr"
    private const val PREFS_NAME = "HydrationSettingsPrefs" // Must match HydrationActivity
    private const val KEY_REMINDER_TIMES = "reminderTimesSet" // Must match HydrationActivity
    // NOTIFICATION_ID from here is not directly used by HydrationAlarmReceiver anymore for posting,
    // but REQUEST_CODE_ALARM is still for this manager's PendingIntent uniqueness.
    // HydrationAlarmReceiver uses its own NOTIFICATION_ID_HYDRATION for posting.
    private const val REQUEST_CODE_ALARM = 200

    fun scheduleOrUpdateAllReminders(context: Context) {
        Log.d(TAG, "scheduleOrUpdateAllReminders called")
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        cancelExistingAlarms(context, alarmManager) // Clear previous alarms first

        val nextAlarmTimeMillis = calculateNextAlarmTimeMillis(context, System.currentTimeMillis())

        if (nextAlarmTimeMillis != null) {
            val intent = Intent(context, HydrationAlarmReceiver::class.java).apply {
                // Set the action that HydrationAlarmReceiver expects
                action = HydrationAlarmReceiver.ACTION_TRIGGER_HYDRATION_REMINDER
                // Pass the reminder time as an extra
                // HydrationAlarmReceiver expects this as a String, let's use the formatted time
                putExtra(HydrationAlarmReceiver.EXTRA_REMINDER_TIME, formatMillisToDateTime(nextAlarmTimeMillis))
            }
            Log.d(TAG, "Intent created with action: ${intent.action} and extra: ${intent.getStringExtra(HydrationAlarmReceiver.EXTRA_REMINDER_TIME)}")

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_ALARM, // This request code is for the PendingIntent itself
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextAlarmTimeMillis, pendingIntent)
                        Log.i(TAG, "Next hydration alarm scheduled for: ${formatMillisToDateTime(nextAlarmTimeMillis)}")
                    } else {
                        Log.w(TAG, "Cannot schedule exact alarms. Hydration reminders might be inaccurate. User needs to grant 'Alarms & reminders' permission manually.")
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextAlarmTimeMillis, pendingIntent)
                    Log.i(TAG, "Next hydration alarm scheduled for: ${formatMillisToDateTime(nextAlarmTimeMillis)}")
                }
            } catch (se: SecurityException) {
                Log.e(TAG, "SecurityException while scheduling exact alarm. Check SCHEDULE_EXACT_ALARM permission.", se)
                val appContext = context.applicationContext
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(appContext, "Could not schedule reminders due to permission issues. Check logs.", Toast.LENGTH_LONG).show()
                }
            }

        } else {
            Log.d(TAG, "No upcoming reminder times to schedule.")
        }
    }

    private fun calculateNextAlarmTimeMillis(context: Context, currentTimeMillis: Long): Long? {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val reminderTimesStringSet = sharedPreferences.getStringSet(KEY_REMINDER_TIMES, emptySet()) ?: emptySet()

        if (reminderTimesStringSet.isEmpty()) {
            Log.d(TAG, "No reminder times found in SharedPreferences.")
            return null
        }

        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault()) // This is how times are saved from HydrationActivity
        val sortedTimesToday = TreeSet<Long>()

        val currentCalendar = Calendar.getInstance().apply {
            timeInMillis = currentTimeMillis
        }

        for (timeStr in reminderTimesStringSet) {
            try {
                val parsedDate = timeFormat.parse(timeStr)
                parsedDate?.let {
                    val reminderCalendar = Calendar.getInstance().apply {
                        time = it
                        set(Calendar.YEAR, currentCalendar.get(Calendar.YEAR))
                        set(Calendar.MONTH, currentCalendar.get(Calendar.MONTH))
                        set(Calendar.DAY_OF_MONTH, currentCalendar.get(Calendar.DAY_OF_MONTH))
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    sortedTimesToday.add(reminderCalendar.timeInMillis)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing reminder time string from SharedPreferences: $timeStr", e)
            }
        }

        for (timeMillis in sortedTimesToday) {
            if (timeMillis > currentTimeMillis) {
                Log.d(TAG, "Next alarm time (today): ${formatMillisToDateTime(timeMillis)}")
                return timeMillis
            }
        }

        if (sortedTimesToday.isNotEmpty()) {
            val firstTimeTomorrowCalendar = Calendar.getInstance().apply {
                timeInMillis = sortedTimesToday.first()
                add(Calendar.DAY_OF_MONTH, 1)
            }
            Log.d(TAG, "Next alarm time (tomorrow): ${formatMillisToDateTime(firstTimeTomorrowCalendar.timeInMillis)}")
            return firstTimeTomorrowCalendar.timeInMillis
        }
        Log.d(TAG, "Could not determine a next alarm time.")
        return null
    }

    fun cancelAllReminders(context: Context) {
        Log.d(TAG, "cancelAllReminders called")
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        cancelExistingAlarms(context, alarmManager)
    }

    private fun cancelExistingAlarms(context: Context, alarmManager: AlarmManager) {
        // Create an intent that matches the one used for scheduling (including action and extras if they affect matching)
        // For cancellation, typically matching the base intent (class and request code) is enough
        // but if FLAG_UPDATE_CURRENT was used, a fully matching intent might be needed by some Android versions.
        // Let's keep it simple for now, as action and extras in the intent to be cancelled are often not critical for AlarmManager.cancel().
        val intent = Intent(context, HydrationAlarmReceiver::class.java).apply {
            action = HydrationAlarmReceiver.ACTION_TRIGGER_HYDRATION_REMINDER // Match the action for robustness
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_ALARM,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE // Use FLAG_NO_CREATE to check existence
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel() // Also cancel the PendingIntent itself
            Log.d(TAG, "Cancelled existing hydration alarms with matching intent.")
        } else {
            Log.d(TAG, "No existing hydration alarm PendingIntent found to cancel with this specific intent structure.")
        }
    }

    internal fun formatMillisToDateTime(timeMillis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(timeMillis)
    }
}
