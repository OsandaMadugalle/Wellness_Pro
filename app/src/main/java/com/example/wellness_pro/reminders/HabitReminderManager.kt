package com.example.wellness_pro.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.Calendar

object HabitReminderManager {
    private const val TAG = "HabitReminderManager"
    private const val REQUEST_CODE_HABIT = 300

    fun scheduleHabitReminder(context: Context, habitId: String, hour: Int, minute: Int) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
            }
            if (calendar.timeInMillis <= System.currentTimeMillis()) {
                // schedule for next day if time already passed
                calendar.add(Calendar.DAY_OF_MONTH, 1)
            }

            val intent = Intent(context, HabitAlarmReceiver::class.java).apply {
                action = HabitAlarmReceiver.ACTION_TRIGGER_HABIT_REMINDER
                putExtra(HabitAlarmReceiver.EXTRA_HABIT_ID, habitId)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_HABIT + habitId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            Log.i(TAG, "Scheduled habit reminder for $habitId at ${calendar.time}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule habit reminder: ${e.message}", e)
        }
    }

    fun cancelHabitReminder(context: Context, habitId: String) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, HabitAlarmReceiver::class.java).apply {
                action = HabitAlarmReceiver.ACTION_TRIGGER_HABIT_REMINDER
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_HABIT + habitId.hashCode(),
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
                Log.i(TAG, "Cancelled habit reminder for $habitId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel habit reminder: ${e.message}", e)
        }
    }
}
