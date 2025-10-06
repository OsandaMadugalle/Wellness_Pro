package com.example.wellness_pro

import kotlinx.coroutines.launch 
import androidx.lifecycle.lifecycleScope 
import kotlinx.coroutines.flow.collectLatest 
import android.view.View
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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.card.MaterialCardView
import androidx.constraintlayout.widget.Group
import com.example.wellness_pro.db.AppDatabase
import com.example.wellness_pro.viewmodel.MoodViewModel
import com.example.wellness_pro.viewmodel.MoodViewModelFactory
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

    // --- Cached SharedPreferences Data --- //
    // Step Counter Cache
    private var cachedStepPreviousTotalStepsSaved = 0f
    private var cachedStepLastSaveDate: String = ""
    private var cachedStepCurrentDailySteps = 0
    private var cachedStepLastSensorHardwareValue = 0f

    // Hydration Cache
    private var cachedHydrationDailyGoal = 8 // Default goal
    private var cachedHydrationCurrentIntake = 0
    private var cachedHydrationDataDate: String = ""

    // Screen Time Cache
    private var cachedScreenTimePersistentMillis = 0L
    private var cachedScreenTimeLastSaveDate: String = ""
    // --- End Cached SharedPreferences Data --- //

    private lateinit var sensorManager: SensorManager
    private var stepCounterSensor: Sensor? = null
    private var currentHardwareStepValue = 0f // Current value from sensor event

    private lateinit var textViewStepCounterValue: TextView
    private lateinit var textViewDate: TextView
    private lateinit var textViewScreenTimeValue: TextView

    private lateinit var moodViewModel: MoodViewModel
    private lateinit var currentMoodCardView: MaterialCardView
    private lateinit var textViewCurrentMoodEmoji: TextView
    private lateinit var textViewCurrentMoodDescription: TextView
    private lateinit var textViewCurrentMoodTimestamp: TextView
    private lateinit var textViewCurrentMoodNotesLabel: TextView
    private lateinit var textViewCurrentMoodNotes: TextView
    private lateinit var textViewNoMoodsLoggedYet: TextView
    private lateinit var groupMoodDisplay: Group

    private lateinit var stepCounterPrefs: SharedPreferences
    private lateinit var hydrationPrefs: SharedPreferences
    private lateinit var screenTimePrefs: SharedPreferences

    private lateinit var progressBarHydration: ProgressBar
    private lateinit var textViewHydrationProgress: TextView
    private lateinit var textViewNoHydrationData: TextView

    companion object {
        const val STEP_PREFS_NAME = "PlayPalStepCounterPrefs"
        const val KEY_PREVIOUS_TOTAL_STEPS_SAVED = "previousTotalStepsSaved"
        const val KEY_LAST_STEP_SAVE_DATE = "lastStepSaveDate"
        const val KEY_CURRENT_DAILY_STEPS = "currentDailySteps"
        const val KEY_LAST_SENSOR_HARDWARE_VALUE = "lastSensorHardwareValue"

        const val ACTION_STEPS_UPDATED = "com.example.wellness_pro.ACTION_STEPS_UPDATED"
        const val EXTRA_CURRENT_STEPS = "extra_current_steps"
        const val EXTRA_STEPS_DATE = "extra_steps_date"

        const val HYDRATION_PREFS_NAME = "HydrationPrefs"
        const val KEY_HYDRATION_DAILY_GOAL = "dailyGoal"
        const val KEY_HYDRATION_INTAKE_PREFIX = "intake_"
    }

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
        
        textViewStepCounterValue = findViewById(R.id.textViewStepCounterValue)
        textViewDate = findViewById(R.id.textViewDate)
        textViewScreenTimeValue = findViewById(R.id.textViewScreenTimeValue)

        currentMoodCardView = findViewById(R.id.currentMoodCardView)
        textViewCurrentMoodEmoji = findViewById(R.id.textViewCurrentMoodEmoji)
        textViewCurrentMoodDescription = findViewById(R.id.textViewCurrentMoodDescription)
        textViewCurrentMoodTimestamp = findViewById(R.id.textViewCurrentMoodTimestamp)
        textViewCurrentMoodNotesLabel = findViewById(R.id.textViewCurrentMoodNotesLabel)
        textViewCurrentMoodNotes = findViewById(R.id.textViewCurrentMoodNotes)
        textViewNoMoodsLoggedYet = findViewById(R.id.textViewNoMoodsLoggedYet)
        groupMoodDisplay = findViewById(R.id.groupMoodDisplay)

        progressBarHydration = findViewById(R.id.progressBarHydration)
        textViewHydrationProgress = findViewById(R.id.textViewHydrationProgress)
        textViewNoHydrationData = findViewById(R.id.textViewNoHydrationData)

        val sdf = SimpleDateFormat("EEEE, MMM dd, yyyy", Locale.getDefault())
        textViewDate.text = sdf.format(Date())

        stepCounterPrefs = getSharedPreferences(STEP_PREFS_NAME, Context.MODE_PRIVATE)
        hydrationPrefs = getSharedPreferences(HYDRATION_PREFS_NAME, Context.MODE_PRIVATE)
        screenTimePrefs = getSharedPreferences(BaseActivity.SCREEN_TIME_PREFS_NAME, Context.MODE_PRIVATE)

        loadInitialDataToCache() // Load all data into cache

        try {
            val moodDao = AppDatabase.getInstance().moodDao() 
            val moodViewModelFactory = MoodViewModelFactory(moodDao)
            moodViewModel = ViewModelProvider(this, moodViewModelFactory)[MoodViewModel::class.java]
        } catch (e: Exception) {
            Log.e("DashboardScreen", "Error initializing MoodViewModel: ${e.message}", e)
            currentMoodCardView.visibility = View.GONE 
        }

        setupInsets()
        setupNavigationButtons()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        checkAndRequestActivityRecognitionPermission() // This will call setupStepCounterSensor if permission granted

        screenTimeUpdateRunnable = Runnable {
            if (this@DashboardScreen.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                !this@DashboardScreen.isFinishing &&
                !this@DashboardScreen.isDestroyed
            ) {
                refreshAndDisplayAppScreenTime() // Uses cached values, refreshes if date changes
                screenTimeUpdateHandler.postDelayed(screenTimeUpdateRunnable, SCREEN_TIME_UPDATE_INTERVAL_MS)
            }
        }
        
        if (::moodViewModel.isInitialized) { 
            observeLatestMood()
        }
    }

    private fun loadInitialDataToCache() {
        // Load Step Data to Cache
        cachedStepLastSaveDate = stepCounterPrefs.getString(KEY_LAST_STEP_SAVE_DATE, "") ?: ""
        cachedStepLastSensorHardwareValue = stepCounterPrefs.getFloat(KEY_LAST_SENSOR_HARDWARE_VALUE, 0f)
        val todayStepDate = getCurrentStepDateString()

        if (cachedStepLastSaveDate != todayStepDate) { // New day for steps
            cachedStepPreviousTotalStepsSaved = cachedStepLastSensorHardwareValue // Use last known hardware value as new baseline
            cachedStepCurrentDailySteps = 0
            cachedStepLastSaveDate = todayStepDate
            // Persist this new day baseline immediately
            stepCounterPrefs.edit() 
                .putFloat(KEY_PREVIOUS_TOTAL_STEPS_SAVED, cachedStepPreviousTotalStepsSaved)
                .putInt(KEY_CURRENT_DAILY_STEPS, cachedStepCurrentDailySteps)
                .putString(KEY_LAST_STEP_SAVE_DATE, cachedStepLastSaveDate)
                .putFloat(KEY_LAST_SENSOR_HARDWARE_VALUE, cachedStepLastSensorHardwareValue) // also update this to current baseline
                .apply()
            Log.i("DashboardScreen", "loadInitialDataToCache: New step day. Baseline: $cachedStepPreviousTotalStepsSaved")
        } else { // Same day
            cachedStepPreviousTotalStepsSaved = stepCounterPrefs.getFloat(KEY_PREVIOUS_TOTAL_STEPS_SAVED, 0f)
            cachedStepCurrentDailySteps = stepCounterPrefs.getInt(KEY_CURRENT_DAILY_STEPS, 0)
        }
        Log.i("DashboardScreen", "loadInitialDataToCache (Steps): PrevSaved=$cachedStepPreviousTotalStepsSaved, Daily=$cachedStepCurrentDailySteps, LastSaveDate=$cachedStepLastSaveDate, LastHW=$cachedStepLastSensorHardwareValue")

        // Load Hydration Data to Cache
        cachedHydrationDailyGoal = hydrationPrefs.getInt(KEY_HYDRATION_DAILY_GOAL, 8)
        val todayHydrationDate = getCurrentHydrationDateString()
        cachedHydrationCurrentIntake = hydrationPrefs.getInt(KEY_HYDRATION_INTAKE_PREFIX + todayHydrationDate, 0)
        cachedHydrationDataDate = todayHydrationDate
        Log.d("DashboardScreen", "loadInitialDataToCache (Hydration): Goal=$cachedHydrationDailyGoal, Intake=$cachedHydrationCurrentIntake, Date=$cachedHydrationDataDate")

        // Load Screen Time Data to Cache
        val todayScreenTimeDate = BaseActivity.getCurrentDateStringForScreenTime()
        cachedScreenTimeLastSaveDate = screenTimePrefs.getString(BaseActivity.KEY_LAST_SCREEN_TIME_SAVE_DATE, "") ?: ""
        cachedScreenTimePersistentMillis = if (todayScreenTimeDate == cachedScreenTimeLastSaveDate) {
            screenTimePrefs.getLong(BaseActivity.KEY_DAILY_APP_SCREEN_TIME, 0L)
        } else {
            0L // New day, reset persistent time for cache
        }
        Log.d("DashboardScreen", "loadInitialDataToCache (ScreenTime): PersistentMs=$cachedScreenTimePersistentMillis, LastSaveDate=$cachedScreenTimeLastSaveDate")
    }

    private fun performDailyStepInitialization() {
        val todayStepDate = getCurrentStepDateString()
        if (cachedStepLastSaveDate != todayStepDate) {
            Log.i("DashboardScreen", "performDailyStepInitialization: New day detected. Previous HW: $cachedStepLastSensorHardwareValue, Current HW: $currentHardwareStepValue")
            // Use the most recent hardware value as the baseline for the new day.
            // If currentHardwareStepValue is 0 (sensor not yet delivered), use the last known hardware value.
            cachedStepPreviousTotalStepsSaved = if (currentHardwareStepValue > 0f) currentHardwareStepValue else cachedStepLastSensorHardwareValue
            cachedStepCurrentDailySteps = 0
            cachedStepLastSaveDate = todayStepDate
            // Persist this new day initial state
            stepCounterPrefs.edit() 
                .putFloat(KEY_PREVIOUS_TOTAL_STEPS_SAVED, cachedStepPreviousTotalStepsSaved)
                .putInt(KEY_CURRENT_DAILY_STEPS, cachedStepCurrentDailySteps)
                .putString(KEY_LAST_STEP_SAVE_DATE, cachedStepLastSaveDate)
                .putFloat(KEY_LAST_SENSOR_HARDWARE_VALUE, cachedStepPreviousTotalStepsSaved) // Update last HW to this new baseline
                .apply()
            Log.i("DashboardScreen", "performDailyStepInitialization: Steps reset for new day. New baseline: $cachedStepPreviousTotalStepsSaved")
        }
        // If it's the same day, cached values from loadInitialDataToCache or subsequent updates should be fine.
    }

    private fun refreshHydrationData() {
        val todayHydrationDate = getCurrentHydrationDateString()
        cachedHydrationDailyGoal = hydrationPrefs.getInt(KEY_HYDRATION_DAILY_GOAL, 8)
        cachedHydrationCurrentIntake = hydrationPrefs.getInt(KEY_HYDRATION_INTAKE_PREFIX + todayHydrationDate, 0)
        cachedHydrationDataDate = todayHydrationDate
        Log.d("DashboardScreen", "refreshHydrationData: Refreshed hydration. Goal=$cachedHydrationDailyGoal, Intake=$cachedHydrationCurrentIntake, Date=$cachedHydrationDataDate")
    }
    
    private fun observeLatestMood() {
        lifecycleScope.launch {
            moodViewModel.latestMoodEntry.collectLatest { moodEntry ->
                if (moodEntry == null) {
                    textViewNoMoodsLoggedYet.visibility = View.VISIBLE
                    groupMoodDisplay.visibility = View.GONE
                    currentMoodCardView.visibility = View.VISIBLE 
                } else {
                    textViewNoMoodsLoggedYet.visibility = View.GONE
                    groupMoodDisplay.visibility = View.VISIBLE
                    currentMoodCardView.visibility = View.VISIBLE

                    textViewCurrentMoodEmoji.text = getEmojiForMoodLevel(moodEntry.moodLevel)
                    textViewCurrentMoodDescription.text = getDescriptionForMoodLevel(moodEntry.moodLevel)
                    
                    val moodTimestampFormat = SimpleDateFormat("MMM d, hh:mm a", Locale.getDefault())
                    textViewCurrentMoodTimestamp.text = "Logged: ${moodTimestampFormat.format(Date(moodEntry.timestamp))}"

                    if (!moodEntry.notes.isNullOrBlank()) {
                        textViewCurrentMoodNotesLabel.visibility = View.VISIBLE
                        textViewCurrentMoodNotes.visibility = View.VISIBLE
                        textViewCurrentMoodNotes.text = moodEntry.notes
                    } else {
                        textViewCurrentMoodNotesLabel.visibility = View.GONE
                        textViewCurrentMoodNotes.visibility = View.GONE
                        textViewCurrentMoodNotes.text = ""
                    }
                }
            }
        }
    }

    private fun getEmojiForMoodLevel(moodLevel: Int): String {
        return when (moodLevel) {
            1 -> "😭" 
            2 -> "🙁" 
            3 -> "😐" 
            4 -> "🙂" 
            5 -> "😄" 
            else -> "❓"
        }
    }

    private fun getDescriptionForMoodLevel(moodLevel: Int): String {
        return when (moodLevel) {
            1 -> "Feeling Very Sad"
            2 -> "Feeling Sad"
            3 -> "Feeling Neutral"
            4 -> "Feeling Okay"
            5 -> "Feeling Happy"
            else -> "Mood not specified"
        }
    }

    private fun getCurrentHydrationDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun updateHydrationProgressUI() {
        Log.d("DashboardScreen", "Updating Hydration UI (from cache): Goal=$cachedHydrationDailyGoal, Intake=$cachedHydrationCurrentIntake")
        if (cachedHydrationDailyGoal > 0) {
            textViewNoHydrationData.visibility = View.GONE
            progressBarHydration.visibility = View.VISIBLE
            textViewHydrationProgress.visibility = View.VISIBLE

            progressBarHydration.max = cachedHydrationDailyGoal
            progressBarHydration.progress = cachedHydrationCurrentIntake.coerceIn(0, cachedHydrationDailyGoal)

            textViewHydrationProgress.text = "$cachedHydrationCurrentIntake/$cachedHydrationDailyGoal glasses"
        } else {
            textViewNoHydrationData.visibility = View.VISIBLE
            progressBarHydration.visibility = View.GONE
            textViewHydrationProgress.visibility = View.GONE
            textViewNoHydrationData.text = "Set your hydration goal."
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
            // Initial data already loaded to cache; daily initialization will occur in onResume
            sensorManager.registerListener(this, stepCounterSensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

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
    }

    override fun onResume() {
        super.onResume()
        performDailyStepInitialization() // Handles new day logic for steps using cache and current sensor
        updateUISteps() // Update UI with current cached/calculated steps

        refreshAndDisplayAppScreenTime() // Uses cached screen time, updates if day changed
        if (this::screenTimeUpdateRunnable.isInitialized) {
            screenTimeUpdateHandler.postDelayed(screenTimeUpdateRunnable, SCREEN_TIME_UPDATE_INTERVAL_MS)
        }
        refreshHydrationData() // Updates hydration from prefs
        updateHydrationProgressUI() // Always update UI after check
        
        // Re-register sensor listener if it was unregistered in onPause
        if (stepCounterSensor != null && ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
            || (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && stepCounterSensor != null)){
            sensorManager.registerListener(this, stepCounterSensor, SensorManager.SENSOR_DELAY_UI)
            Log.d("DashboardScreen", "Sensor listener re-registered in onResume.")
        }
    }

    override fun onPause() {
        super.onPause()
        if(stepCounterSensor != null) { // Only unregister if sensor was available
            sensorManager.unregisterListener(this, stepCounterSensor)
            Log.d("DashboardScreen", "Sensor listener unregistered in onPause.")
        }
        saveCachedStepDataToPrefs() // Save latest cached step data
        if (this::screenTimeUpdateRunnable.isInitialized) {
            screenTimeUpdateHandler.removeCallbacks(screenTimeUpdateRunnable)
        }
    }

    private fun refreshAndDisplayAppScreenTime() {
        val today = BaseActivity.getCurrentDateStringForScreenTime()
        if (cachedScreenTimeLastSaveDate != today) { // Day has changed
            cachedScreenTimePersistentMillis = 0L // Reset for new day
            cachedScreenTimeLastSaveDate = today
            // Update SharedPreferences for the new day's zeroed screen time if necessary (optional here as it's mainly for display)
             screenTimePrefs.edit().putString(BaseActivity.KEY_LAST_SCREEN_TIME_SAVE_DATE, today).putLong(BaseActivity.KEY_DAILY_APP_SCREEN_TIME, 0L).apply()
            Log.d("DashboardScreen", "refreshAndDisplayAppScreenTime: New screen time day. Cache reset.")
        } else {
            // If same day, ensure cachedScreenTimePersistentMillis is from prefs if it wasn't loaded or was reset
            // This normally should be handled by loadInitialDataToCache, but as a safeguard:
            if(cachedScreenTimePersistentMillis == 0L && screenTimePrefs.getString(BaseActivity.KEY_LAST_SCREEN_TIME_SAVE_DATE, "") == today) {
                 cachedScreenTimePersistentMillis = screenTimePrefs.getLong(BaseActivity.KEY_DAILY_APP_SCREEN_TIME, 0L)
            }
        }

        var currentSessionMillis = 0L
        if (this.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && foregroundStartTime > 0) {
            currentSessionMillis = System.currentTimeMillis() - foregroundStartTime
        }
        val totalDisplayTimeMillis = cachedScreenTimePersistentMillis + currentSessionMillis
        updateScreenTimeUI(totalDisplayTimeMillis)
    }

    private fun updateScreenTimeUI(totalMillis: Long) {
        textViewScreenTimeValue.text = BaseActivity.formatMillisToHMS(totalMillis)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            currentHardwareStepValue = event.values[0]
            Log.d("DashboardScreen", "Sensor event: New hardware total_steps = $currentHardwareStepValue")

            // Handle initial baseline if not set or if sensor restarted
            if (cachedStepPreviousTotalStepsSaved == 0f && currentHardwareStepValue > 0f) {
                // If it's a new day, performDailyStepInitialization in onResume should handle it.
                // If same day but baseline is 0 (e.g. fresh install, first sensor event), set baseline.
                if (cachedStepLastSaveDate == getCurrentStepDateString()) {
                    cachedStepPreviousTotalStepsSaved = currentHardwareStepValue
                    cachedStepCurrentDailySteps = 0
                    Log.i("DashboardScreen", "Sensor event: Initial baseline set for today to $currentHardwareStepValue")
                } 
            }
            
            cachedStepCurrentDailySteps = (currentHardwareStepValue - cachedStepPreviousTotalStepsSaved).toInt()

            if (cachedStepCurrentDailySteps < 0) { // Indicates a device restart or sensor anomaly
                Log.w("DashboardScreen", "Sensor event: Negative current steps ($cachedStepCurrentDailySteps). Resetting baseline.")
                cachedStepPreviousTotalStepsSaved = currentHardwareStepValue
                cachedStepCurrentDailySteps = 0
            }
            cachedStepLastSensorHardwareValue = currentHardwareStepValue // Always update last known hardware value
            updateUISteps()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { /* Not used */ }

    private fun updateUISteps() {
        textViewStepCounterValue.text = cachedStepCurrentDailySteps.toString()
        // Persist current state of cached step data
        saveCurrentCachedStepsToPrefs()
        Log.d("DashboardScreen", "UI steps updated to $cachedStepCurrentDailySteps (from cache) and saved to prefs.")

        val intent = Intent(ACTION_STEPS_UPDATED).apply {
            putExtra(EXTRA_CURRENT_STEPS, cachedStepCurrentDailySteps)
            putExtra(EXTRA_STEPS_DATE, cachedStepLastSaveDate) // Use cached date
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }
    
    private fun saveCurrentCachedStepsToPrefs(){
         stepCounterPrefs.edit().apply {
            putFloat(KEY_PREVIOUS_TOTAL_STEPS_SAVED, cachedStepPreviousTotalStepsSaved)
            putString(KEY_LAST_STEP_SAVE_DATE, cachedStepLastSaveDate)
            putInt(KEY_CURRENT_DAILY_STEPS, cachedStepCurrentDailySteps)
            putFloat(KEY_LAST_SENSOR_HARDWARE_VALUE, cachedStepLastSensorHardwareValue)
            apply()
        }
    }

    // This is called from onPause to save the final state.
    private fun saveCachedStepDataToPrefs() { 
        saveCurrentCachedStepsToPrefs() // Use the common method
        Log.i("DashboardScreen", "saveCachedStepDataToPrefs (onPause): PrevSaved=$cachedStepPreviousTotalStepsSaved, Daily=$cachedStepCurrentDailySteps, Date=$cachedStepLastSaveDate, LastHW=$cachedStepLastSensorHardwareValue")
    }

    private fun getCurrentStepDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }

    // Not directly used anymore, logic integrated into performDailyStepInitialization and loadInitialDataToCache
    // private fun isNewStepDay(savedDateString: String): Boolean {
    //     if (savedDateString.isEmpty()) return true
    //     return savedDateString != getCurrentStepDateString()
    // }
}
