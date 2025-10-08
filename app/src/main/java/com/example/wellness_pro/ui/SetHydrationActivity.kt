package com.example.wellness_pro.ui

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.wellness_pro.R
import com.example.wellness_pro.navbar.BaseBottomNavActivity
import com.example.wellness_pro.reminders.HydrationReminderManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings

class SetHydrationActivity : BaseBottomNavActivity() {

    override val layoutId: Int
        get() = R.layout.activity_set_hydration

    override val currentNavControllerItemId: Int
        get() = R.id.navButtonHydration // Keep hydration tab selected in nav bar

    private lateinit var recyclerViewSetReminderTimes: RecyclerView
    private lateinit var textViewSetNoReminders: TextView
    private lateinit var buttonSetAddTime: Button
    private lateinit var buttonSaveHydrationSettings: Button
    private lateinit var editTextSetGlassesGoal: EditText
    private lateinit var textViewSetRemindersInfo: TextView
    private lateinit var headerLayoutSetHydration: LinearLayout
    private lateinit var switchMasterEnableReminders: android.widget.Switch
    private lateinit var buttonPauseForDay: Button
    private lateinit var editTextCustomHydrationMessage: EditText
    private lateinit var switchReminderSound: android.widget.Switch
    private lateinit var switchReminderVibration: android.widget.Switch
    private lateinit var switchEnableSmartReminders: android.widget.Switch
    private lateinit var editTextNoDrinkThresholdMinutes: EditText

    private lateinit var reminderTimesAdapter: ReminderTimesAdapter // Assuming this adapter is available
    private val reminderTimesList = mutableListOf<String>()
    private var currentSetGlassesGoal: Int = 0

    companion object {
        private const val TAG = "SetHydrationActivity"
        const val PREFS_NAME = "HydrationPrefs" // Must match DashboardScreen & HydrationActivity
        const val KEY_GLASSES_GOAL = "dailyGoal" // Must match DashboardScreen & HydrationActivity
        private const val KEY_REMINDER_TIMES = "reminderTimesSet" // Must match HydrationActivity
    }

    private lateinit var sharedPreferences: SharedPreferences

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d(TAG, "POST_NOTIFICATIONS permission granted.")
            if (reminderTimesAdapter.getTimes().isNotEmpty()) {
                HydrationReminderManager.scheduleOrUpdateAllReminders(this)
                Toast.makeText(this, "Reminders will be scheduled.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.w(TAG, "POST_NOTIFICATIONS permission denied.")
            Toast.makeText(this, "Reminders will not work without notification permission.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        headerLayoutSetHydration = findViewById(R.id.headerLayoutSetHydration)
        // Preserve original top padding and add system bars inset (matches HabitsScreen behavior)
        if (headerLayoutSetHydration.getTag(R.id.tag_padding_top) == null) {
            headerLayoutSetHydration.setTag(R.id.tag_padding_top, headerLayoutSetHydration.paddingTop)
        }
        ViewCompat.setOnApplyWindowInsetsListener(headerLayoutSetHydration) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val originalTopPadding = (view.getTag(R.id.tag_padding_top) as? Int) ?: view.paddingTop
            view.updatePadding(top = insets.top + originalTopPadding)
            WindowInsetsCompat.CONSUMED
        }

        editTextSetGlassesGoal = findViewById(R.id.editTextSetGlassesGoal)
        textViewSetRemindersInfo = findViewById(R.id.textViewSetRemindersInfo)
        recyclerViewSetReminderTimes = findViewById(R.id.recyclerViewSetReminderTimes)
        textViewSetNoReminders = findViewById(R.id.textViewSetNoReminders)
        buttonSetAddTime = findViewById(R.id.buttonSetAddTime)
        buttonSaveHydrationSettings = findViewById(R.id.buttonSaveHydrationSettings)
        switchMasterEnableReminders = findViewById(R.id.switchMasterEnableReminders)
        buttonPauseForDay = findViewById(R.id.buttonPauseForDay)
        editTextCustomHydrationMessage = findViewById(R.id.editTextCustomHydrationMessage)
        switchReminderSound = findViewById(R.id.switchReminderSound)
        switchReminderVibration = findViewById(R.id.switchReminderVibration)
        switchEnableSmartReminders = findViewById(R.id.switchEnableSmartReminders)
        editTextNoDrinkThresholdMinutes = findViewById(R.id.editTextNoDrinkThresholdMinutes)

        setupRecyclerView()
        setupGlassesGoalListener()
        loadHydrationSettingsToUI()
        loadMasterAndPauseStateToUI()
        maybeShowBatteryOptimizationWarning()
        loadCustomMessageToUI()
        loadNotificationOptionsToUI()
        loadSmartRemindersToUI()
        updateRemindersCountInfoText()
        updateEmptyViewVisibility()

        buttonSetAddTime.setOnClickListener {
            showTimePickerDialog()
        }

        switchMasterEnableReminders.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("reminders_master_enabled", isChecked).apply()
            if (isChecked) {
                HydrationReminderManager.scheduleOrUpdateAllReminders(this)
            } else {
                HydrationReminderManager.cancelAllReminders(this)
            }
        }

        switchReminderSound.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("reminder_sound_enabled", isChecked).apply()
            com.example.wellness_pro.WellnessProApplication.ensureHydrationChannel(this)
            Toast.makeText(this, getString(R.string.notification_option_applied), Toast.LENGTH_SHORT).show()
        }

        switchReminderVibration.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("reminder_vibration_enabled", isChecked).apply()
            com.example.wellness_pro.WellnessProApplication.ensureHydrationChannel(this)
            Toast.makeText(this, getString(R.string.notification_option_applied), Toast.LENGTH_SHORT).show()
        }

        switchEnableSmartReminders.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("smart_reminders_enabled", isChecked).apply()
            HydrationReminderManager.scheduleOrUpdateAllReminders(this)
        }

        editTextNoDrinkThresholdMinutes.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val minutes = s?.toString()?.toIntOrNull() ?: 0
                sharedPreferences.edit().putInt("no_drink_threshold_minutes", minutes).apply()
            }
        })

        buttonPauseForDay.setOnClickListener {
            val pauseUntil = System.currentTimeMillis() + 24L * 60L * 60L * 1000L
            sharedPreferences.edit().putLong("reminders_pause_until_millis", pauseUntil).apply()
            Toast.makeText(this, getString(R.string.paused_for_today_until, formatDateTime(pauseUntil)), Toast.LENGTH_SHORT).show()
            HydrationReminderManager.cancelAllReminders(this)
        }

        buttonSaveHydrationSettings.setOnClickListener {
            val goalToSave = editTextSetGlassesGoal.text.toString().toIntOrNull() ?: 0
            
            if (goalToSave <= 0 && reminderTimesAdapter.getTimes().isNotEmpty()) {
                 editTextSetGlassesGoal.error = "Set a goal if adding reminders"
                 Toast.makeText(this, "Please set a valid daily glasses goal if you have reminders.", Toast.LENGTH_SHORT).show()
                 return@setOnClickListener
            } else if (goalToSave < 0) { 
                 editTextSetGlassesGoal.error = "Goal cannot be negative"
                 Toast.makeText(this, "Goal cannot be negative.", Toast.LENGTH_SHORT).show()
                 return@setOnClickListener
            }

            currentSetGlassesGoal = goalToSave 
            val timesToSave = reminderTimesAdapter.getTimes()
            saveHydrationSettingsToPrefs(currentSetGlassesGoal, timesToSave)
            saveCustomMessageToPrefs()

            if (timesToSave.isNotEmpty()) {
                Toast.makeText(this, "Settings saved! Reminders updating.", Toast.LENGTH_SHORT).show()
                checkAndRequestNotificationPermissionThenSchedule()
            } else {
                Toast.makeText(this, "Settings saved! All reminders cleared.", Toast.LENGTH_SHORT).show()
                HydrationReminderManager.cancelAllReminders(this)
            }
            finish() // Close this activity and return to HydrationActivity
        }
    }

    private fun checkAndRequestNotificationPermissionThenSchedule() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    Log.d(TAG, "POST_NOTIFICATIONS permission already granted for SetHydrationActivity.")
                    HydrationReminderManager.scheduleOrUpdateAllReminders(this)
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    Toast.makeText(this, "Notification permission is required for hydration reminders.", Toast.LENGTH_LONG).show()
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                else -> {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            HydrationReminderManager.scheduleOrUpdateAllReminders(this)
        }
    }

    private fun saveHydrationSettingsToPrefs(goal: Int, times: List<String>) {
        val editor = sharedPreferences.edit()
        editor.putInt(KEY_GLASSES_GOAL, goal)
        editor.putStringSet(KEY_REMINDER_TIMES, times.toSet())
        editor.apply()
        Log.d(TAG, "Hydration settings saved from SetHydrationActivity. Goal: $goal, Times: ${times.joinToString()}")
    }

    private fun loadHydrationSettingsToUI() {
        currentSetGlassesGoal = sharedPreferences.getInt(KEY_GLASSES_GOAL, 0)

        if (currentSetGlassesGoal > 0) {
            editTextSetGlassesGoal.setText(currentSetGlassesGoal.toString())
        } else {
            editTextSetGlassesGoal.setText("")
        }

        val savedTimes = sharedPreferences.getStringSet(KEY_REMINDER_TIMES, emptySet()) ?: emptySet()
        reminderTimesList.clear()
        reminderTimesList.addAll(savedTimes.sorted())
        Log.d(TAG, "Hydration settings loaded in SetHydrationActivity. Goal: $currentSetGlassesGoal, Times: ${reminderTimesList.joinToString()}")

        if (::reminderTimesAdapter.isInitialized) {
            reminderTimesAdapter.notifyDataSetChanged()
        }
    }

    private fun loadMasterAndPauseStateToUI() {
        val masterEnabled = sharedPreferences.getBoolean("reminders_master_enabled", true)
        val pauseUntil = sharedPreferences.getLong("reminders_pause_until_millis", 0L)
        switchMasterEnableReminders.isChecked = masterEnabled
        if (pauseUntil > System.currentTimeMillis()) {
            buttonPauseForDay.text = getString(R.string.paused_until_label, formatDateTime(pauseUntil))
        } else {
            buttonPauseForDay.text = getString(R.string.pause_for_today)
        }
    }

    private fun formatDateTime(millis: Long): String {
        val sdf = SimpleDateFormat("MMM d, yyyy hh:mm a", Locale.getDefault())
        return sdf.format(java.util.Date(millis))
    }

    private fun setupRecyclerView() {
        reminderTimesAdapter = ReminderTimesAdapter(reminderTimesList) { position ->
            val removedTime = reminderTimesList[position]
            Log.d(TAG, "Remove reminder time $removedTime at position $position.")
            reminderTimesAdapter.removeTime(position)
            updateEmptyViewVisibility()
            updateRemindersCountInfoText()
        }
        recyclerViewSetReminderTimes.adapter = reminderTimesAdapter
        recyclerViewSetReminderTimes.layoutManager = LinearLayoutManager(this)
    }

    private fun setupGlassesGoalListener() {
        editTextSetGlassesGoal.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                currentSetGlassesGoal = s.toString().toIntOrNull() ?: 0
                updateRemindersCountInfoText() 
            }
        })
    }

    private fun updateRemindersCountInfoText() {
        val remindersCount = reminderTimesAdapter.itemCount
        val goalText = if (currentSetGlassesGoal > 0) currentSetGlassesGoal.toString() else "N/A"
        textViewSetRemindersInfo.text = "Reminders set: $remindersCount / $goalText planned"
    }

    private fun updateEmptyViewVisibility() {
        if (reminderTimesAdapter.itemCount == 0) {
            textViewSetNoReminders.visibility = View.VISIBLE
            recyclerViewSetReminderTimes.visibility = View.GONE
        } else {
            textViewSetNoReminders.visibility = View.GONE
            recyclerViewSetReminderTimes.visibility = View.VISIBLE
        }
    }

    private fun showTimePickerDialog() {
        if (currentSetGlassesGoal > 0 && reminderTimesAdapter.itemCount >= currentSetGlassesGoal) {
            Toast.makeText(this, "You have set $currentSetGlassesGoal reminders, matching your goal.", Toast.LENGTH_SHORT).show()
            return
        }
        // Allow adding reminders even if goal is 0 for now; save button validates.

        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)

        val timePickerDialog = TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                val formattedTime = formatTime(hourOfDay, minute)
                if (!reminderTimesList.contains(formattedTime)) {
                    reminderTimesAdapter.addTime(formattedTime)
                    updateEmptyViewVisibility()
                    updateRemindersCountInfoText()
                } else {
                    Toast.makeText(this, "This reminder time already exists.", Toast.LENGTH_SHORT).show()
                }
            },
            currentHour,
            currentMinute,
            false // Use false for 12-hour AM/PM format
        )
        timePickerDialog.show()
    }

    private fun formatTime(hour: Int, minute: Int): String {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return sdf.format(calendar.time)
    }

    private fun maybeShowBatteryOptimizationWarning() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            val packageName = packageName
            val ignoring = pm.isIgnoringBatteryOptimizations(packageName)
            val dismissed = sharedPreferences.getBoolean("battery_optimization_warning_dismissed", false)
            if (!ignoring && !dismissed) {
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(getString(R.string.battery_optimization_title))
                    .setMessage(getString(R.string.battery_optimization_message))
                    .setPositiveButton(getString(R.string.open_settings)) { _, _ ->
                        try {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = android.net.Uri.parse("package:" + packageName)
                            }
                            startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(this, getString(R.string.unable_to_open_settings), Toast.LENGTH_LONG).show()
                        }
                    }
                    .setNegativeButton(getString(R.string.dismiss)) { _, _ ->
                        sharedPreferences.edit().putBoolean("battery_optimization_warning_dismissed", true).apply()
                    }
                    .show()
            }
        } catch (e: Exception) {
            Log.w(TAG, "maybeShowBatteryOptimizationWarning: ${e.message}")
        }
    }

    private fun saveCustomMessageToPrefs() {
        val message = editTextCustomHydrationMessage.text?.toString()?.trim() ?: ""
        sharedPreferences.edit().putString("custom_hydration_message", message).apply()
    }

    private fun loadCustomMessageToUI() {
        val message = sharedPreferences.getString("custom_hydration_message", "") ?: ""
        if (message.isNotEmpty()) {
            editTextCustomHydrationMessage.setText(message)
        }
    }

    private fun loadNotificationOptionsToUI() {
        val soundEnabled = sharedPreferences.getBoolean("reminder_sound_enabled", true)
        val vibrationEnabled = sharedPreferences.getBoolean("reminder_vibration_enabled", true)
        switchReminderSound.isChecked = soundEnabled
        switchReminderVibration.isChecked = vibrationEnabled
    }

    private fun loadSmartRemindersToUI() {
        val enabled = sharedPreferences.getBoolean("smart_reminders_enabled", false)
        val mins = sharedPreferences.getInt("no_drink_threshold_minutes", 60)
        switchEnableSmartReminders.isChecked = enabled
        editTextNoDrinkThresholdMinutes.setText(mins.toString())
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here if any.
        return super.onOptionsItemSelected(item)
    }
}
