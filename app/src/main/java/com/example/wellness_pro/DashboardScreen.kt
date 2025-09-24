// file: com/example/playpal/DashboardScreen.kt
package com.example.wellness_pro

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.localbroadcastmanager.content.LocalBroadcastManager // Added
import com.example.wellness_pro.navbar.BaseActivity
import com.example.wellness_pro.navbar.BaseBottomNavActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DashboardScreen : BaseBottomNavActivity(), SensorEventListener {

    override val layoutId: Int
        get() = R.layout.activity_dashboard_screen

    override val currentNavControllerItemId: Int
        get() = R.id.navButtonDashboard

    private lateinit var sensorManager: SensorManager
    private var stepCounterSensor: Sensor? = null
    private var totalStepsHardwareValue = 0f
    private var previousTotalStepsSaved = 0f
    private var currentSteps = 0

    private lateinit var textViewStepCounterValue: TextView
    private lateinit var textViewDate: TextView
    private lateinit var textViewScreenTimeValue: TextView

    private lateinit var stepCounterPrefs: SharedPreferences

    companion object {
        const val STEP_PREFS_NAME = "PlayPalStepCounterPrefs"
        const val KEY_PREVIOUS_TOTAL_STEPS_SAVED = "previousTotalStepsSaved"
        const val KEY_LAST_STEP_SAVE_DATE = "lastStepSaveDate"
        const val KEY_CURRENT_DAILY_STEPS = "currentDailySteps"
        const val KEY_LAST_SENSOR_HARDWARE_VALUE = "lastSensorHardwareValue"

        // For LocalBroadcastManager
        const val ACTION_STEPS_UPDATED = "com.example.wellness_pro.ACTION_STEPS_UPDATED" // Corrected package name
        const val EXTRA_CURRENT_STEPS = "extra_current_steps"
        const val EXTRA_STEPS_DATE = "extra_steps_date"
    }

    private lateinit var screenTimePrefs: SharedPreferences
    private val screenTimeUpdateHandler = Handler(Looper.getMainLooper())
    private lateinit var screenTimeUpdateRunnable: Runnable
    private val SCREEN_TIME_UPDATE_INTERVAL_MS = 5000L

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.i("DashboardScreen", "ACTIVITY_RECOGNITION permission granted.")
                setupStepCounterSensor()
            } else {
                Log.w("DashboardScreen", "ACTIVITY_RECOGNITION permission denied.")
                textViewStepCounterValue.text = "N/A"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ... (findViewById and date setup as before)
        textViewStepCounterValue = findViewById(R.id.textViewStepCounterValue)
        textViewDate = findViewById(R.id.textViewDate)
        textViewScreenTimeValue = findViewById(R.id.textViewScreenTimeValue)

        val sdf = SimpleDateFormat("EEEE, MMM dd, yyyy", Locale.getDefault())
        textViewDate.text = sdf.format(Date())

        stepCounterPrefs = getSharedPreferences(STEP_PREFS_NAME, Context.MODE_PRIVATE)
        screenTimePrefs = getSharedPreferences(BaseActivity.SCREEN_TIME_PREFS_NAME, Context.MODE_PRIVATE)

        setupInsets()
        setupNavigationButtons()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        checkAndRequestActivityRecognitionPermission()

        screenTimeUpdateRunnable = Runnable {
            if (this@DashboardScreen.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                !this@DashboardScreen.isFinishing &&
                !this@DashboardScreen.isDestroyed
            ) {
                loadAndDisplayAppScreenTime()
                screenTimeUpdateHandler.postDelayed(screenTimeUpdateRunnable, SCREEN_TIME_UPDATE_INTERVAL_MS)
            }
        }
    }


    private fun checkAndRequestActivityRecognitionPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACTIVITY_RECOGNITION
                ) == PackageManager.PERMISSION_GRANTED -> {
                    Log.d("DashboardScreen", "ACTIVITY_RECOGNITION permission already granted.")
                    setupStepCounterSensor()
                }
                shouldShowRequestPermissionRationale(Manifest.permission.ACTIVITY_RECOGNITION) -> {
                    Log.d("DashboardScreen", "Showing rationale for ACTIVITY_RECOGNITION permission.")
                    requestPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                }
                else -> {
                    Log.d("DashboardScreen", "Requesting ACTIVITY_RECOGNITION permission.")
                    requestPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                }
            }
        } else {
            Log.d("DashboardScreen", "No need for ACTIVITY_RECOGNITION permission (SDK < Q).")
            setupStepCounterSensor()
        }
    }

    private fun setupStepCounterSensor() {
        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (stepCounterSensor == null) {
            Log.e("DashboardScreen", "No step counter sensor found on this device.")
            textViewStepCounterValue.text = "N/A"
        } else {
            Log.i("DashboardScreen", "Step counter sensor found. Registering listener.")
            loadStepData()
            sensorManager.registerListener(this, stepCounterSensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    // setupInsets, setupNavigationButtons, loadAndDisplayAppScreenTime, updateScreenTimeUI as before
    private fun setupInsets() {
        findViewById<LinearLayout?>(R.id.headerLayout)?.let { header ->
            ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
                val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
                v.updatePadding(top = statusBarInsets.top)
                insets
            }
        }
        findViewById<ConstraintLayout?>(R.id.main)?.let { main ->
            ViewCompat.setOnApplyWindowInsetsListener(main) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.updatePadding(left = systemBars.left, right = systemBars.right)
                insets
            }
        }
    }

    private fun setupNavigationButtons() {
        findViewById<ImageView?>(R.id.buttonNotify)?.setOnClickListener { startActivity(Intent(this, NotificationScreen::class.java)) }
        // Removed listeners for ToDo, FocusTracker, BmiTracker buttons as the buttons themselves were removed from XML
    }


    override fun onResume() {
        super.onResume()
        resetStepsIfNewDay()
        loadStepData()
        updateUISteps() // This will also send the initial broadcast if steps are > 0

        loadAndDisplayAppScreenTime()
        if (this::screenTimeUpdateRunnable.isInitialized) {
            screenTimeUpdateHandler.postDelayed(screenTimeUpdateRunnable, SCREEN_TIME_UPDATE_INTERVAL_MS)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this, stepCounterSensor)
        saveStepData()
        if (this::screenTimeUpdateRunnable.isInitialized) {
            screenTimeUpdateHandler.removeCallbacks(screenTimeUpdateRunnable)
        }
    }

    private fun loadAndDisplayAppScreenTime() {
        val today = BaseActivity.getCurrentDateStringForScreenTime()
        val lastSaveDate = screenTimePrefs.getString(BaseActivity.KEY_LAST_SCREEN_TIME_SAVE_DATE, "")
        val persistentScreenTimeMillis = if (today == lastSaveDate) {
            screenTimePrefs.getLong(BaseActivity.KEY_DAILY_APP_SCREEN_TIME, 0L)
        } else { 0L }

        var currentSessionMillis = 0L
        if (this.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && foregroundStartTime > 0) {
            currentSessionMillis = System.currentTimeMillis() - foregroundStartTime
        }
        val totalDisplayTimeMillis = persistentScreenTimeMillis + currentSessionMillis
        updateScreenTimeUI(totalDisplayTimeMillis)
    }

    private fun updateScreenTimeUI(totalMillis: Long) {
        textViewScreenTimeValue.text = BaseActivity.formatMillisToHMS(totalMillis)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            totalStepsHardwareValue = event.values[0]
            Log.d("DashboardScreen", "Sensor event: New hardware total_steps = $totalStepsHardwareValue")

            if (previousTotalStepsSaved == 0f && totalStepsHardwareValue > 0f) {
                if (isNewStepDay(stepCounterPrefs.getString(KEY_LAST_STEP_SAVE_DATE, "") ?: "")) {
                    previousTotalStepsSaved = totalStepsHardwareValue
                    currentSteps = 0
                } else if (stepCounterPrefs.getFloat(KEY_PREVIOUS_TOTAL_STEPS_SAVED, 0f) == 0f) {
                    previousTotalStepsSaved = totalStepsHardwareValue
                    currentSteps = 0
                }
            }
            currentSteps = (totalStepsHardwareValue - previousTotalStepsSaved).toInt()

            if (currentSteps < 0) {
                Log.w("DashboardScreen", "Sensor event: Negative current steps ($currentSteps). Resetting baseline.")
                previousTotalStepsSaved = totalStepsHardwareValue
                currentSteps = 0
            }
            updateUISteps() // Display, save, and broadcast
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { /* Not used */ }

    private fun updateUISteps() {
        textViewStepCounterValue.text = currentSteps.toString()
        val todayDateString = getCurrentStepDateString()

        stepCounterPrefs.edit().apply {
            putInt(KEY_CURRENT_DAILY_STEPS, currentSteps)
            putFloat(KEY_LAST_SENSOR_HARDWARE_VALUE, totalStepsHardwareValue)
            putString(KEY_LAST_STEP_SAVE_DATE, todayDateString) // Ensure date is consistent
            apply()
        }
        Log.d("DashboardScreen", "UI steps updated to $currentSteps and saved. Broadcasting.")

        // Send local broadcast
        val intent = Intent(ACTION_STEPS_UPDATED).apply {
            putExtra(EXTRA_CURRENT_STEPS, currentSteps)
            putExtra(EXTRA_STEPS_DATE, todayDateString)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun saveStepData() { // Called onPause
        stepCounterPrefs.edit().apply {
            putFloat(KEY_PREVIOUS_TOTAL_STEPS_SAVED, previousTotalStepsSaved)
            putString(KEY_LAST_STEP_SAVE_DATE, getCurrentStepDateString())
            putInt(KEY_CURRENT_DAILY_STEPS, currentSteps)
            putFloat(KEY_LAST_SENSOR_HARDWARE_VALUE, totalStepsHardwareValue)
            apply()
        }
        Log.i("DashboardScreen", "Step data fully saved. PrevSaved: $previousTotalStepsSaved, CurrentDaily: $currentSteps, Date: ${getCurrentStepDateString()}, LastHardware: $totalStepsHardwareValue")
    }

    private fun loadStepData() {
        val savedDate = stepCounterPrefs.getString(KEY_LAST_STEP_SAVE_DATE, "") ?: ""
        val lastHardwareValueFromPrefs = stepCounterPrefs.getFloat(KEY_LAST_SENSOR_HARDWARE_VALUE, 0f)

        if (isNewStepDay(savedDate)) {
            Log.i("DashboardScreen", "loadStepData: New day detected.")
            previousTotalStepsSaved = if (totalStepsHardwareValue > 0f) totalStepsHardwareValue else lastHardwareValueFromPrefs
            currentSteps = 0
        } else {
            previousTotalStepsSaved = stepCounterPrefs.getFloat(KEY_PREVIOUS_TOTAL_STEPS_SAVED, 0f)
            currentSteps = stepCounterPrefs.getInt(KEY_CURRENT_DAILY_STEPS, 0)

            if (totalStepsHardwareValue > 0f && previousTotalStepsSaved == 0f && currentSteps == 0) {
                if (previousTotalStepsSaved == 0f) {
                    Log.w("DashboardScreen", "loadStepData: Same day, but previousTotalStepsSaved is 0. Using totalStepsHardwareValue as baseline if available.")
                    previousTotalStepsSaved = totalStepsHardwareValue
                }
            } else if (totalStepsHardwareValue > 0f && previousTotalStepsSaved > totalStepsHardwareValue) {
                Log.w("DashboardScreen", "loadStepData: Sensor hardware value ($totalStepsHardwareValue) is less than saved baseline ($previousTotalStepsSaved). Resetting baseline.")
                previousTotalStepsSaved = totalStepsHardwareValue
            }
        }
        Log.i("DashboardScreen", "Step data loaded. PrevSaved: $previousTotalStepsSaved, CurrentDaily: $currentSteps, LastSaveDate: $savedDate, CurrentHardware: $totalStepsHardwareValue")
        // updateUISteps() will be called by onResume or onSensorChanged
    }

    private fun resetStepsIfNewDay() {
        val lastSaveDate = stepCounterPrefs.getString(KEY_LAST_STEP_SAVE_DATE, "") ?: ""
        if (isNewStepDay(lastSaveDate)) {
            Log.i("DashboardScreen", "resetStepsIfNewDay: New day confirmed. Current hardware sensor value: $totalStepsHardwareValue")
            previousTotalStepsSaved = if (totalStepsHardwareValue > 0f) {
                totalStepsHardwareValue
            } else {
                stepCounterPrefs.getFloat(KEY_LAST_SENSOR_HARDWARE_VALUE, 0f)
            }
            currentSteps = 0
            // Save the reset state. updateUISteps will also save and broadcast.
            val editor = stepCounterPrefs.edit()
            editor.putFloat(KEY_PREVIOUS_TOTAL_STEPS_SAVED, previousTotalStepsSaved)
            editor.putInt(KEY_CURRENT_DAILY_STEPS, currentSteps)
            editor.putString(KEY_LAST_STEP_SAVE_DATE, getCurrentStepDateString())
            // KEY_LAST_SENSOR_HARDWARE_VALUE will be updated by updateUISteps if totalStepsHardwareValue changes
            editor.apply()

            updateUISteps() // This will also broadcast the reset (0 steps for new day)
            Log.i("DashboardScreen", "Steps reset for new day. New baseline (previousTotalStepsSaved): $previousTotalStepsSaved")
        }
    }

    private fun getCurrentStepDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun isNewStepDay(savedDateString: String): Boolean {
        if (savedDateString.isEmpty()) return true
        return savedDateString != getCurrentStepDateString()
    }
}
