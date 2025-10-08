package com.example.wellness_pro.reminders

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.wellness_pro.LaunchScreen
import com.example.wellness_pro.R
import com.example.wellness_pro.WellnessProApplication

class HabitAlarmReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_TRIGGER_HABIT_REMINDER = "com.example.wellness_pro.ACTION_TRIGGER_HABIT_REMINDER"
        const val EXTRA_HABIT_ID = "extra_habit_id"
        private const val TAG = "HabitAlarmReceiver"
        private const val NOTIFICATION_ID_HABIT = 2001
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Habit alarm received. Action: ${intent.action}")
        if (intent.action == ACTION_TRIGGER_HABIT_REMINDER) {
            val habitId = intent.getStringExtra(EXTRA_HABIT_ID) ?: "unknown"
            showNotification(context, habitId)
        }
    }

    private fun showNotification(context: Context, habitId: String) {
        try {
            val channelId = WellnessProApplication.getHydrationChannelId(context) // reuse channel
            WellnessProApplication.ensureHydrationChannel(context)

            val openIntent = Intent(context, LaunchScreen::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(context, habitId.hashCode(), openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_check_circle_outline)
                .setContentTitle(context.getString(R.string.habits))
                .setContentText(context.getString(R.string.remind_me_at))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)

            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_HABIT + habitId.hashCode(), builder.build())
        } catch (e: Exception) {
            Log.e(TAG, "Error showing habit notification: ${e.message}", e)
        }
    }
}
