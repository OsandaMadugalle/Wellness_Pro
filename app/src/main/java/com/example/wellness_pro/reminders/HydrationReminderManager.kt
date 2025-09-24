// file: com/example/playpal/reminders/HydrationReminderManager.kt
package com.example.wellness_pro.reminders

// Corrected Imports:
import java.text.SimpleDateFormat // Use java.text.SimpleDateFormat
import java.util.Locale          // Use java.util.Locale
import java.util.Calendar

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.wellness_pro.Habit


object HydrationReminderManager {

    private const val TAG = "HydrationReminderMgr"
    private const val REQUEST_CODE_HYDRATION_PREFIX = 12300 // Prefix for unique request codes per habit

    fun scheduleOrUpdateReminder(context: Context, habit: Habit) {
        Log.d(TAG, "scheduleOrUpdateReminder called for habit: $habit")
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val requestCode = REQUEST_CODE_HYDRATION_PREFIX + (habit.id.hashCode() % 1000)
        Log.d(TAG, "Generated requestCode: $requestCode for habit ID ${habit.id}")

        val intent = Intent(context, HydrationAlarmReceiver::class.java).apply {
            action = HydrationAlarmReceiver.ACTION_TRIGGER_HYDRATION_REMINDER
            putExtra(HydrationAlarmReceiver.EXTRA_HABIT_ID, habit.id)
            // Adding a unique data URI can help differentiate intents further if needed, e.g.:
            // data = Uri.parse("wellnesspro://hydrationreminder/${habit.id}") 
        }
        Log.d(TAG, "Intent created with action: ${intent.action} and habit ID: ${habit.id}")

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        Log.d(TAG, "PendingIntent flags: $pendingIntentFlags (Immutable: ${Build.VERSION.SDK_INT >= Build.VERSION_CODES.M})")

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            pendingIntentFlags
        )

        if (pendingIntent == null) {
            Log.e(TAG, "Failed to create PendingIntent for habit ID ${habit.id}. Reminder cannot be set.")
            return
        }
        Log.d(TAG, "PendingIntent created successfully for habit ID ${habit.id}")

        // It's good practice to cancel any existing alarm with the same PendingIntent before setting a new one.
        try {
            alarmManager.cancel(pendingIntent)
            Log.d(TAG, "Attempted to cancel any pre-existing reminder for habit ID ${habit.id} using requestCode $requestCode.")
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling pre-existing reminder for habit ID ${habit.id}: ${e.message}", e)
        }

        if (habit.type.equals("Hydration", ignoreCase = true) &&
            habit.isReminderEnabled &&
            (habit.reminderIntervalMinutes ?: 0) > 0
        ) {
            Log.d(TAG, "Conditions met for scheduling: Type Hydration, Reminder Enabled, Interval > 0.")
            val intervalMillis = (habit.reminderIntervalMinutes!! * 60 * 1000).toLong()
            val now = Calendar.getInstance()
            val triggerTime = Calendar.getInstance()

            // Simplified trigger logic: Start interval from now or from reminderTimeHour/Minute if set for today & future
            var firstTriggerTimeMillis = System.currentTimeMillis() + intervalMillis

            if (habit.reminderTimeHour != null && habit.reminderTimeMinute != null) {
                val todayReminderStartTime = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, habit.reminderTimeHour!!)
                    set(Calendar.MINUTE, habit.reminderTimeMinute!!)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (todayReminderStartTime.timeInMillis > System.currentTimeMillis()) { // If start time is in the future today
                     // If (now + interval) is later than specific start time, use (now + interval)
                     // otherwise, use the specific start time as the very first trigger.
                    firstTriggerTimeMillis = if (firstTriggerTimeMillis > todayReminderStartTime.timeInMillis) {
                        Log.d(TAG, "Initial interval from now (${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(firstTriggerTimeMillis)}) is after specific start time (${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(todayReminderStartTime.timeInMillis)}). Using interval from now.")
                        firstTriggerTimeMillis
                    } else {
                        Log.d(TAG, "Specific start time (${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(todayReminderStartTime.timeInMillis)}) is in the future and before (now + interval). Using specific start time as first trigger.")
                        todayReminderStartTime.timeInMillis
                    }
                } else {
                     Log.d(TAG, "Specific start time (${habit.reminderTimeHour}:${habit.reminderTimeMinute}) is in the past for today. Standard interval from now will apply for first trigger.")
                }
            }
            triggerTime.timeInMillis = firstTriggerTimeMillis
            
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            Log.d(TAG, "Calculated intervalMillis: $intervalMillis. Initial triggerTime set to: ${sdf.format(triggerTime.time)}")

            // Check against end time if provided (Reminder: This logic might need to be more robust for multi-day scenarios)
            habit.reminderEndTimeHour?.let {
                val endHour = habit.reminderEndTimeHour!!
                val endMinute = habit.reminderEndTimeMinute ?: 0 // Assuming end is at the start of the hour or specified minute
                val triggerCal = Calendar.getInstance().apply { timeInMillis = triggerTime.timeInMillis }

                if (triggerCal.get(Calendar.HOUR_OF_DAY) > endHour || 
                    (triggerCal.get(Calendar.HOUR_OF_DAY) == endHour && triggerCal.get(Calendar.MINUTE) >= endMinute)) {
                    Log.w(TAG, "Calculated first trigger for habit ID ${habit.id} (${sdf.format(triggerTime.time)}) is at or after specified end time (${String.format("%02d:%02d", endHour, endMinute)}). Reminder will NOT be set for now.")
                    return // Do not schedule if the first trigger is already past the end time.
                }
                 Log.d(TAG, "First trigger (${sdf.format(triggerTime.time)}) is before end time (${String.format("%02d:%02d", endHour, endMinute)}). Proceeding with scheduling.")
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (!alarmManager.canScheduleExactAlarms()) {
                        Log.w(TAG, "SCHEDULE_EXACT_ALARM permission not granted for habit ID ${habit.id}. Alarm might be inexact or fail.")
                        // For critical reminders, prompt user or use alternative like WorkManager for guaranteed execution.
                    }
                }

                Log.i(TAG, "Attempting to schedule REPEATING alarm for habit ID ${habit.id}. Trigger: ${sdf.format(triggerTime.time)}, Interval: $intervalMillis ms, RequestCode: $requestCode")
                alarmManager.setRepeating(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime.timeInMillis,
                    intervalMillis,
                    pendingIntent
                )
                Log.i(TAG, "SUCCESS: Hydration reminder scheduled for habit ID ${habit.id}. Interval: ${habit.reminderIntervalMinutes} min. First trigger at: ${sdf.format(triggerTime.time)}")

            } catch (se: SecurityException) {
                Log.e(TAG, "SecurityException scheduling reminder for habit ID ${habit.id}. Do you have SCHEDULE_EXACT_ALARM? ${se.message}", se)
            } catch (e: Exception) {
                Log.e(TAG, "Exception scheduling reminder for habit ID ${habit.id}: ${e.message}", e)
            }
        } else {
            Log.i(TAG, "Hydration reminder conditions NOT MET for habit ID ${habit.id}. isReminderEnabled: ${habit.isReminderEnabled}, interval: ${habit.reminderIntervalMinutes}. (Reminder was already cancelled if it existed)." )
        }
    }

    fun cancelSpecificHabitReminder(context: Context, habitId: String) {
        Log.d(TAG, "cancelSpecificHabitReminder called for habitId: $habitId")
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val requestCode = REQUEST_CODE_HYDRATION_PREFIX + (habitId.hashCode() % 1000)
        Log.d(TAG, "Generated requestCode for cancellation: $requestCode for habit ID $habitId")

        val intent = Intent(context, HydrationAlarmReceiver::class.java).apply {
            action = HydrationAlarmReceiver.ACTION_TRIGGER_HYDRATION_REMINDER
            putExtra(HydrationAlarmReceiver.EXTRA_HABIT_ID, habitId) // Important for the receiver if it were to be accidentally triggered by a stale PI
            // data = Uri.parse("wellnesspro://hydrationreminder/$habitId") // Match if used in creation
        }
        Log.d(TAG, "Intent for finding PendingIntent: action=${intent.action}, habitId=$habitId")

        // To cancel, the PendingIntent must match the one used for scheduling (action, request code, and intent extras/data if used to make it unique)
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE // FLAG_NO_CREATE to check existence
        } else {
            PendingIntent.FLAG_NO_CREATE
        }
        Log.d(TAG, "PendingIntent flags for cancellation: $pendingIntentFlags")

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent, // The same intent (or at least one that matches via filterEquals) used for creation
            pendingIntentFlags
        )

        if (pendingIntent != null) {
            Log.d(TAG, "PendingIntent FOUND for habit ID $habitId (requestCode $requestCode). Attempting to cancel alarm and PendingIntent.")
            try {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel() // Also cancel the PendingIntent itself
                Log.i(TAG, "SUCCESS: Cancelled hydration reminder for habit ID $habitId.")
            } catch (e: Exception) {
                Log.e(TAG, "Error during alarmManager.cancel or pendingIntent.cancel for habit ID $habitId: ${e.message}", e)
            }
        } else {
            Log.w(TAG, "No active reminder (PendingIntent NOT FOUND) to cancel for habit ID $habitId with requestCode $requestCode. This is normal if it was never set or already cancelled.")
        }
    }
}
