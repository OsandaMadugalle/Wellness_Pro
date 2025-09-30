// file: com/example/wellness_pro/reminders/HydrationReminderManager.kt
package com.example.wellness_pro.reminders

import android.content.Context
import android.util.Log
import com.example.wellness_pro.Habit // Still imported but not used in functions

// This manager is being deprecated in favor of the new HydrationActivity's reminder system.
// Its methods are now empty to prevent scheduling old, habit-specific hydration alarms.
object HydrationReminderManager {

    private const val TAG = "HydrationReminderMgr"
    // private const val REQUEST_CODE_HYDRATION_PREFIX = 12300 // No longer needed

    fun scheduleOrUpdateReminder(context: Context, habit: Habit) {
        Log.d(TAG, "scheduleOrUpdateReminder for habit ID ${habit.id} - Functionality disabled. Use HydrationActivity for hydration reminders.")
        // All original logic for scheduling removed.
    }

    fun cancelSpecificHabitReminder(context: Context, habitId: String) {
        Log.d(TAG, "cancelSpecificHabitReminder for habit ID $habitId - Functionality disabled.")
        // All original logic for cancellation removed.
    }
}
