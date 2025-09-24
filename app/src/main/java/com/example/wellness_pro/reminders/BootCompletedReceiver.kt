// file: com/example/playpal/reminders/BootCompletedReceiver.kt
package com.example.wellness_pro.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.wellness_pro.Habit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i("BootCompletedReceiver", "Device boot completed. Rescheduling reminders.")
            rescheduleAllHydrationReminders(context)
        }
    }

    private fun rescheduleAllHydrationReminders(context: Context) {
        try {
            val prefs = context.getSharedPreferences("PlayPalHabits", Context.MODE_PRIVATE)
            val gson = Gson()
            val json = prefs.getString("habits_list_json", null)
            val typeToken = object : TypeToken<MutableList<Habit>>() {}.type
            val habitsList: MutableList<Habit> = if (json != null) {
                gson.fromJson(json, typeToken) ?: mutableListOf()
            } else {
                mutableListOf()
            }

            habitsList.forEach { habit ->
                if (habit.type.equals("Hydration", ignoreCase = true) &&
                    habit.isReminderEnabled &&
                    (habit.reminderIntervalMinutes ?: 0) > 0
                ) {
                    Log.d("BootCompletedReceiver", "Rescheduling reminder for hydration habit ID: ${habit.id}")
                    HydrationReminderManager.scheduleOrUpdateReminder(context, habit)
                }
            }
            if (habitsList.none { it.type.equals("Hydration", ignoreCase = true) && it.isReminderEnabled }) {
                Log.d("BootCompletedReceiver", "No enabled hydration habits found to reschedule.")
            }

        } catch (e: Exception) {
            Log.e("BootCompletedReceiver", "Error loading habits or rescheduling reminders on boot.", e)
        }
    }
}
