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
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.content.getSystemService
import com.example.wellness_pro.ui.MoodLogActivity

abstract class BaseActivity : AppCompatActivity() {

    @get:LayoutRes
    abstract val layoutId: Int

    // Changed to protected to be accessible by subclasses like DashboardScreen
    protected var foregroundStartTime: Long = 0L
    private lateinit var screenTimePrefs: SharedPreferences

    // --- Shake-to-add-mood support ---
    private var sensorManager: SensorManager? = null
    private var accelSensor: Sensor? = null
    private var lastShakeTimestamp: Long = 0L
    private val shakeDebounceMs = 1000L // 1s debounce
    private var shakeThreshold = 1.2f // delta threshold (g) for spikes; mapped from prefs
    private var lastMagnitude = 0f
    private var spikeCount = 0
    private val spikeWindowMs = 700L // require N spikes within this window
    private val requiredSpikes = 2
    private val PREF_SHAKE_ENABLED = "shake_quick_mood_enabled"
    private val PREF_SHAKE_SENSITIVITY = "shake_sensitivity"
    // ----------------------------------

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

    /**
     * Protected helper to test vibration from child activities (unified logging and API handling).
     */
    protected fun testVibrate(durationMs: Long = 150L) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator == null) {
                Log.w("BaseActivity_Shake", "testVibrate: No vibrator service available on device.")
                try { android.widget.Toast.makeText(this, "No vibrator available.", android.widget.Toast.LENGTH_SHORT).show() } catch (_: Exception) {}
                return
            }
            Log.d("BaseActivity_Shake", "testVibrate: triggering vibration for ${durationMs}ms")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs)
            }
        } catch (e: Exception) {
            Log.w("BaseActivity_Shake", "testVibrate failed: ${e.message}")
            try { android.widget.Toast.makeText(this, "Vibration test failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show() } catch (_: Exception) {}
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(layoutId)

        screenTimePrefs = getSharedPreferences(SCREEN_TIME_PREFS_NAME, Context.MODE_PRIVATE)
        resetAppScreenTimeIfNeeded(false)

        // Initialize sensor manager for shake detection
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    override fun onResume() {
        super.onResume()
        foregroundStartTime = System.currentTimeMillis() // Set when any subclass (like DashboardScreen) resumes
        resetAppScreenTimeIfNeeded(true)
        Log.d("BaseActivity_ScreenTime", "${this.javaClass.simpleName} - Resumed at $foregroundStartTime. Tracking start.")

        // Register accelerometer listener for shake-to-add-mood if enabled
        try {
            val prefs = getSharedPreferences(SCREEN_TIME_PREFS_NAME, Context.MODE_PRIVATE)
            val enabled = getSharedPreferences("AppSettingsPrefs", Context.MODE_PRIVATE)
                .getBoolean(PREF_SHAKE_ENABLED, true)
            if (enabled && accelSensor != null && sensorManager != null) {
                // Map stored sensitivity (float) to threshold: lower sensitivity => higher threshold
                val storedSens = getSharedPreferences("AppSettingsPrefs", Context.MODE_PRIVATE).getFloat(PREF_SHAKE_SENSITIVITY, 2.7f)
                shakeThreshold = storedSens.coerceIn(0.5f, 5.5f)
                Log.d("BaseActivity_Shake", "Registering shake listener. accelSensor=${accelSensor!=null}, threshold=$shakeThreshold, enabled=$enabled")
                sensorManager?.registerListener(accelListener, accelSensor, SensorManager.SENSOR_DELAY_GAME)
            }
        } catch (e: Exception) {
            Log.w("BaseActivity_Shake", "Failed to register shake listener: ${e.message}")
        }
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

        // Unregister accelerometer listener
        try {
            sensorManager?.unregisterListener(accelListener)
        } catch (e: Exception) {
            // ignore
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

    // SensorEventListener implementation for shake detection
    private val accelListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event == null) return
            if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            // magnitude of acceleration (including gravity)
            val magnitude = kotlin.math.sqrt(x * x + y * y + z * z)
            // convert to g units
            val gMagnitude = magnitude / SensorManager.GRAVITY_EARTH
            val delta = kotlin.math.abs(gMagnitude - lastMagnitude)
            lastMagnitude = gMagnitude

            // Spike detection: consider delta > threshold a spike
            if (delta > shakeThreshold) {
                val now = System.currentTimeMillis()
                // If previous spike was long ago, reset count
                if (now - lastShakeTimestamp > spikeWindowMs) {
                    spikeCount = 0
                }
                spikeCount += 1
                lastShakeTimestamp = now
                Log.d("BaseActivity_Shake", "Spike detected (delta=$delta), spikeCount=$spikeCount")
                try {
                    android.widget.Toast.makeText(this@BaseActivity, "Shake spike detected ($spikeCount)", android.widget.Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {}

                // If we have enough spikes within window, trigger action
                if (spikeCount >= requiredSpikes && now - lastShakeTimestamp <= spikeWindowMs) {
                    // reset count to avoid immediate retrigger
                    spikeCount = 0
                    Log.d("BaseActivity_Shake", "Detected required spikes ($requiredSpikes). Opening MoodLogActivity.")
                    // vibrate briefly
                    try {
                        // On API 31+ prefer VibratorManager to get default vibrator
                        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val vm = getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
                            vm?.defaultVibrator
                        } else {
                            @Suppress("DEPRECATION")
                            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                        }

                        if (vibrator == null) {
                            Log.w("BaseActivity_Shake", "No vibrator service available on device.")
                        } else {
                            // Log whether device reports vibrator capability
                            try {
                                val hasVib = try {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1) {
                                        vibrator.hasVibrator()
                                    } else true
                                } catch (_: Exception) { true }
                                Log.d("BaseActivity_Shake", "Vibrator present: true, hasVibrator()=$hasVib")
                            } catch (_: Exception) {
                                // ignore
                            }

                            Log.d("BaseActivity_Shake", "Triggering short vibration (150ms)")
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                vibrator.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
                            } else {
                                @Suppress("DEPRECATION")
                                vibrator.vibrate(150)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("BaseActivity_Shake", "Vibrate failed: ${e.message}")
                    }

                    // Launch MoodLogActivity as quick mood
                    try {
                        val intent = Intent(this@BaseActivity, MoodLogActivity::class.java)
                        intent.putExtra(MoodLogActivity.EXTRA_QUICK_MOOD, true)
                        intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(intent)
                    } catch (e: Exception) {
                        Log.e("BaseActivity_Shake", "Failed to start MoodLogActivity: ${e.message}", e)
                    }
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }
}
