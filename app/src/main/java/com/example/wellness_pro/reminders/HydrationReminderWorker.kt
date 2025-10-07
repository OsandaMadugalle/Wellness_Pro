package com.example.wellness_pro.reminders

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters

class HydrationReminderWorker(appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {

	override fun doWork(): Result {
		return try {
			Log.d("HydrationReminderWorker", "doWork: Executing hydration reminder via WorkManager")
			val intent = Intent(applicationContext, HydrationAlarmReceiver::class.java).apply {
				action = HydrationAlarmReceiver.ACTION_TRIGGER_HYDRATION_REMINDER
				putExtra(HydrationAlarmReceiver.EXTRA_REMINDER_TIME, "WorkManager")
			}
			applicationContext.sendBroadcast(intent)
			HydrationReminderManager.scheduleOrUpdateAllReminders(applicationContext)
			Result.success()
		} catch (e: Exception) {
			Log.e("HydrationReminderWorker", "doWork: Error executing hydration reminder", e)
			Result.retry()
		}
	}
}
