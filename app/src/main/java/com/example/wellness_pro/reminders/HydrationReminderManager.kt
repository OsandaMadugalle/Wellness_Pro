package com.example.wellness_pro.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
// Import HydrationActivity to access its constants for SharedPreferences
import com.example.wellness_pro.ui.HydrationActivity // Make sure this path is correct
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TreeSet
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object HydrationReminderManager {

    internal const val TAG = "HydrationReminderMgr"

    // Use the SharedPreferences name defined in HydrationActivity/DashboardScreen
    private const val PREFS_NAME = HydrationActivity.PREFS_NAME // This should now be "HydrationPrefs"
    
    // The key for reminder times is also defined in HydrationActivity. 
    // If it's not public, you might need to use the string directly, but it's better to reference it.
    // Assuming HydrationActivity.KEY_REMINDER_TIMES was how it was intended, or directly using the string:
    private const val KEY_REMINDER_TIMES = "reminderTimesSet" // This is the actual string value from HydrationActivity
    private const val KEY_MASTER_ENABLED = "reminders_master_enabled"
    private const val KEY_PAUSE_UNTIL_MILLIS = "reminders_pause_until_millis"

    // NOTIFICATION_ID from here is not directly used by HydrationAlarmReceiver anymore for posting,
    // but REQUEST_CODE_ALARM is still for this manager's PendingIntent uniqueness.
    // HydrationAlarmReceiver uses its own NOTIFICATION_ID_HYDRATION for posting.
    private const val REQUEST_CODE_ALARM = 200

    fun scheduleOrUpdateAllReminders(context: Context) {
        Log.d(TAG, "scheduleOrUpdateAllReminders called, using PREFS_NAME: $PREFS_NAME")
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val masterEnabled = prefs.getBoolean(KEY_MASTER_ENABLED, true)
        val pauseUntil = prefs.getLong(KEY_PAUSE_UNTIL_MILLIS, 0L)
        val now = System.currentTimeMillis()

        if (!masterEnabled) {
            Log.i(TAG, "Hydration reminders are disabled by master toggle. Cancelling existing alarms.")
            cancelExistingAlarms(context, alarmManager)
            return
        }
        if (pauseUntil > now) {
            Log.i(TAG, "Hydration reminders are paused until ${formatMillisToDateTime(pauseUntil)}. Cancelling existing alarms.")
            cancelExistingAlarms(context, alarmManager)
            return
        }

        cancelExistingAlarms(context, alarmManager) // Clear previous alarms first

        val nextAlarmTimeMillis = calculateNextAlarmTimeMillis(context, now)

        if (nextAlarmTimeMillis != null) {
            val intent = Intent(context, HydrationAlarmReceiver::class.java).apply {
                action = HydrationAlarmReceiver.ACTION_TRIGGER_HYDRATION_REMINDER
                putExtra(HydrationAlarmReceiver.EXTRA_REMINDER_TIME, formatMillisToDateTime(nextAlarmTimeMillis))
            }
            Log.d(TAG, "Intent created with action: ${intent.action} and extra: ${intent.getStringExtra(HydrationAlarmReceiver.EXTRA_REMINDER_TIME)}")

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_ALARM, 
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextAlarmTimeMillis, pendingIntent)
                        Log.i(TAG, "Next hydration alarm scheduled for: ${formatMillisToDateTime(nextAlarmTimeMillis)}")
                    } else {
                        Log.w(TAG, "Cannot schedule exact alarms. Falling back to WorkManager.")
                        enqueueWorkManagerFallback(context, nextAlarmTimeMillis - System.currentTimeMillis())
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextAlarmTimeMillis, pendingIntent)
                    Log.i(TAG, "Next hydration alarm scheduled for: ${formatMillisToDateTime(nextAlarmTimeMillis)}")
                }
            } catch (se: SecurityException) {
                Log.e(TAG, "SecurityException while scheduling exact alarm. Falling back to WorkManager.", se)
                enqueueWorkManagerFallback(context, (nextAlarmTimeMillis - System.currentTimeMillis()).coerceAtLeast(0))
            }

        } else {
            Log.d(TAG, "No upcoming reminder times to schedule.")
        }
    }

    private fun calculateNextAlarmTimeMillis(context: Context, currentTimeMillis: Long): Long? {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val masterEnabled = sharedPreferences.getBoolean(KEY_MASTER_ENABLED, true)
        val pauseUntil = sharedPreferences.getLong(KEY_PAUSE_UNTIL_MILLIS, 0L)
        if (!masterEnabled) {
            Log.d(TAG, "calculateNextAlarmTimeMillis: master disabled -> null")
            return null
        }
        if (pauseUntil > currentTimeMillis) {
            Log.d(TAG, "calculateNextAlarmTimeMillis: paused until ${formatMillisToDateTime(pauseUntil)} -> null")
            return null
        }

        // Smart reminders: time since last drink
        val smartEnabled = sharedPreferences.getBoolean("smart_reminders_enabled", false)
        if (smartEnabled) {
            val lastDrinkTs = sharedPreferences.getLong("last_drink_timestamp", 0L)
            val thresholdMinutes = sharedPreferences.getInt("no_drink_threshold_minutes", 60)
            if (lastDrinkTs > 0 && thresholdMinutes > 0) {
                val elapsed = currentTimeMillis - lastDrinkTs
                val thresholdMs = thresholdMinutes * 60L * 1000L
                if (elapsed >= thresholdMs) {
                    Log.d(TAG, "Smart reminder: threshold exceeded (elapsed=${elapsed}ms, threshold=${thresholdMs}ms). Scheduling ASAP.")
                    return currentTimeMillis + 15_000L // 15 seconds from now
                }
            }
        }

        val reminderTimesStringSet = sharedPreferences.getStringSet(KEY_REMINDER_TIMES, emptySet()) ?: emptySet()

        if (reminderTimesStringSet.isEmpty()) {
            Log.d(TAG, "No reminder times found in SharedPreferences (using $PREFS_NAME and key $KEY_REMINDER_TIMES).")
            return null
        }

        // Parse time strings robustly (avoid using Date parsing which can pick today's date at epoch)
        val timeRegex = Regex("\\s*(\\d{1,2}):(\\d{2})\\s*([APap][Mm])?\\s*")
        val sortedTimesToday = TreeSet<Long>()

        val currentCalendar = Calendar.getInstance().apply { timeInMillis = currentTimeMillis }

        for (timeStr in reminderTimesStringSet) {
            try {
                val match = timeRegex.matchEntire(timeStr)
                if (match == null) {
                    Log.w(TAG, "Unrecognized reminder time format: $timeStr")
                    continue
                }
                var hour = match.groupValues[1].toInt()
                val minute = match.groupValues[2].toInt()
                val ampm = match.groupValues[3].uppercase()
                if (ampm.isNotEmpty()) {
                    if (ampm == "AM" && hour == 12) hour = 0
                    else if (ampm == "PM" && hour != 12) hour += 12
                }

                val reminderCalendar = Calendar.getInstance().apply {
                    timeInMillis = currentCalendar.timeInMillis
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                sortedTimesToday.add(reminderCalendar.timeInMillis)
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
        val intent = Intent(context, HydrationAlarmReceiver::class.java).apply {
            action = HydrationAlarmReceiver.ACTION_TRIGGER_HYDRATION_REMINDER 
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_ALARM,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE 
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel() 
            Log.d(TAG, "Cancelled existing hydration alarms with matching intent.")
        } else {
            Log.d(TAG, "No existing hydration alarm PendingIntent found to cancel with this specific intent structure.")
        }
    }

    private fun enqueueWorkManagerFallback(context: Context, delayMillis: Long) {
        try {
            val delay = delayMillis.coerceAtLeast(0)
            val request = OneTimeWorkRequestBuilder<HydrationReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "HydrationReminderWork",
                ExistingWorkPolicy.REPLACE,
                request
            )
            Log.i(TAG, "WorkManager fallback enqueued with delay ${delay}ms")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enqueue WorkManager fallback", e)
        }
    }

    internal fun formatMillisToDateTime(timeMillis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(timeMillis)
    }
}
