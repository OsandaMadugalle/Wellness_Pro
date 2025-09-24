// file: com/example/playpal/navbar/BaseActivity.kt
package com.example.wellness_pro.navbar

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

abstract class BaseActivity : AppCompatActivity() {

    @get:LayoutRes
    abstract val layoutId: Int

    // Changed to protected to be accessible by subclasses like DashboardScreen
    protected var foregroundStartTime: Long = 0L
    private lateinit var screenTimePrefs: SharedPreferences

    companion object {
        const val SCREEN_TIME_PREFS_NAME = "AppScreenTimePrefsPlayPal"
        const val KEY_DAILY_APP_SCREEN_TIME = "dailyAppScreenTime"
        const val KEY_LAST_SCREEN_TIME_SAVE_DATE = "lastScreenTimeSaveDate"

        fun formatMillisToHMS(millis: Long): String {
            val hours = TimeUnit.MILLISECONDS.toHours(millis)
            val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
            return String.format(Locale.getDefault(), "%dh %02dm", hours, minutes)
        }

        fun getCurrentDateStringForScreenTime(): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            return sdf.format(Date())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(layoutId)

        screenTimePrefs = getSharedPreferences(SCREEN_TIME_PREFS_NAME, Context.MODE_PRIVATE)
        resetAppScreenTimeIfNeeded(false)
    }

    override fun onResume() {
        super.onResume()
        foregroundStartTime = System.currentTimeMillis() // Set when any subclass (like DashboardScreen) resumes
        resetAppScreenTimeIfNeeded(true)
        Log.d("BaseActivity_ScreenTime", "${this.javaClass.simpleName} - Resumed at $foregroundStartTime. Tracking start.")
    }

    override fun onPause() {
        super.onPause()
        if (foregroundStartTime > 0) {
            val timeInForeground = System.currentTimeMillis() - foregroundStartTime
            if (timeInForeground > 0) {
                addAppScreenTime(timeInForeground)
                Log.d("BaseActivity_ScreenTime", "${this.javaClass.simpleName} - Paused. Tracked ${timeInForeground / 1000}s.")
            } else {
                Log.d("BaseActivity_ScreenTime", "${this.javaClass.simpleName} - Paused. No significant time in foreground.")
            }
            foregroundStartTime = 0L // Reset for next activity or next resume of this one
        } else {
            Log.d("BaseActivity_ScreenTime", "${this.javaClass.simpleName} - Paused. No foreground start time was set.")
        }
    }

    private fun addAppScreenTime(durationMillis: Long) {
        val today = getCurrentDateStringForScreenTime()
        val lastSaveDate = screenTimePrefs.getString(KEY_LAST_SCREEN_TIME_SAVE_DATE, "")

        val currentScreenTimeForToday = if (today == lastSaveDate) {
            screenTimePrefs.getLong(KEY_DAILY_APP_SCREEN_TIME, 0L)
        } else {
            Log.i("BaseActivity_ScreenTime", "New day in addAppScreenTime. Date: $today, Last Save: $lastSaveDate.")
            screenTimePrefs.edit().putString(KEY_LAST_SCREEN_TIME_SAVE_DATE, today).apply()
            0L
        }

        val newTotalScreenTime = currentScreenTimeForToday + durationMillis
        screenTimePrefs.edit().putLong(KEY_DAILY_APP_SCREEN_TIME, newTotalScreenTime).apply()
        Log.i("BaseActivity_ScreenTime", "Added ${durationMillis / 1000}s. Today's total: ${newTotalScreenTime / 1000}s.")
    }

    private fun resetAppScreenTimeIfNeeded(logIfReset: Boolean) {
        val today = getCurrentDateStringForScreenTime()
        val lastSaveDate = screenTimePrefs.getString(KEY_LAST_SCREEN_TIME_SAVE_DATE, "")

        if (today != lastSaveDate) {
            screenTimePrefs.edit()
                .putLong(KEY_DAILY_APP_SCREEN_TIME, 0L)
                .putString(KEY_LAST_SCREEN_TIME_SAVE_DATE, today)
                .apply()
            if (logIfReset) {
                Log.i("BaseActivity_ScreenTime", "App screen time reset for new day: $today. Prev save: $lastSaveDate")
            }
        }
    }
}
