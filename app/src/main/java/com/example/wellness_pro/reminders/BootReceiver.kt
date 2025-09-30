package com.example.wellness_pro.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED && context != null) {
            Log.i(TAG, "Device boot completed. Re-scheduling hydration reminders.")
            HydrationReminderManager.scheduleOrUpdateAllReminders(context)
        } else {
            Log.w(TAG, "Received intent with action: ${intent?.action} or null context. No action taken.")
        }
    }
}
