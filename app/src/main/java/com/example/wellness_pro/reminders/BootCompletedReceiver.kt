// file: com/example/wellness_pro/reminders/BootCompletedReceiver.kt
package com.example.wellness_pro.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
// No longer need Habit, Gson, TypeToken for this receiver's new logic

class BootCompletedReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootCompletedReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i(TAG, "Device boot completed. Rescheduling all hydration reminders.")
            // Call the global hydration reminder scheduler
            // The HydrationReminderManager now handles its own logic for finding and setting alarms
            // based on settings from HydrationActivity
            try {
                HydrationReminderManager.scheduleOrUpdateAllReminders(context)
                Log.d(TAG, "Called HydrationReminderManager.scheduleOrUpdateAllReminders.")
            } catch (e: Exception) {
                // Catch any unexpected errors during rescheduling to prevent BootReceiver from crashing
                Log.e(TAG, "Error trying to reschedule hydration reminders on boot.", e)
            }
        }
    }
}
