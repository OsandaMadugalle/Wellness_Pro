package com.example.wellness_pro.ui

import android.Manifest // Added
import android.app.TimePickerDialog
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager // Added
import android.os.Build // Added
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log // Added
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts // Added
import androidx.core.content.ContextCompat // Added
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.wellness_pro.R
import com.example.wellness_pro.navbar.BaseBottomNavActivity
import com.example.wellness_pro.reminders.HydrationReminderManager // Added
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HydrationActivity : BaseBottomNavActivity() {

    override val layoutId: Int
        get() = R.layout.activity_hydration

    override val currentNavControllerItemId: Int
        get() = R.id.navButtonHydration

    private lateinit var recyclerViewReminderTimes: RecyclerView
    private lateinit var textViewNoReminders: TextView
    private lateinit var buttonAddTime: Button
    private lateinit var buttonSaveReminders: Button
    private lateinit var editTextGlassesGoal: EditText
    private lateinit var textViewRemindersSetInfo: TextView
    private lateinit var headerLayoutHydration: LinearLayout

    private lateinit var reminderTimesAdapter: ReminderTimesAdapter
    private val reminderTimesList = mutableListOf<String>()
    private var currentGlassesGoal: Int = 0

    companion object {
        private const val TAG = "HydrationActivity"
        private const val PREFS_NAME = "HydrationSettingsPrefs"
        private const val KEY_GLASSES_GOAL = "glassesGoal"
        private const val KEY_REMINDER_TIMES = "reminderTimesSet"
    }

    private lateinit var sharedPreferences: SharedPreferences

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d(TAG, "POST_NOTIFICATIONS permission granted.")
            // Permission is granted. Proceed with scheduling reminders.
            val timesToSave = reminderTimesAdapter.getTimes()
            if (timesToSave.isNotEmpty()) {
                 HydrationReminderManager.scheduleOrUpdateAllReminders(this)
            } // No need to call cancel here as it's handled before permission check
        } else {
            Log.w(TAG, "POST_NOTIFICATIONS permission denied.")
            Toast.makeText(this, "Reminders will not work without notification permission.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        headerLayoutHydration = findViewById(R.id.headerLayoutHydration)

        ViewCompat.setOnApplyWindowInsetsListener(headerLayoutHydration) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = insets.top)
            WindowInsetsCompat.CONSUMED
        }

        editTextGlassesGoal = findViewById(R.id.editTextGlassesGoal)
        textViewRemindersSetInfo = findViewById(R.id.textViewRemindersSetInfo)
        recyclerViewReminderTimes = findViewById(R.id.recyclerViewReminderTimes)
        textViewNoReminders = findViewById(R.id.textViewNoReminders)
        buttonAddTime = findViewById(R.id.buttonAddTime)
        buttonSaveReminders = findViewById(R.id.buttonSaveReminders)

        setupRecyclerView()
        setupGlassesGoalListener()
        loadHydrationSettings()
        updateRemindersSetInfoText()
        updateEmptyViewVisibility()

        buttonAddTime.setOnClickListener {
            showTimePickerDialog()
        }

        buttonSaveReminders.setOnClickListener {
            val goalToSave = editTextGlassesGoal.text.toString().toIntOrNull() ?: 0
            if (goalToSave <= 0 && reminderTimesAdapter.getTimes().isNotEmpty()) {
                // Allow saving reminders even if goal is 0, but not if goal is invalid and reminders exist
                 editTextGlassesGoal.error = "Set a goal if adding reminders"
                 Toast.makeText(this, "Please set a valid daily glasses goal if you have reminders.", Toast.LENGTH_SHORT).show()
                 return@setOnClickListener
            } else if (goalToSave < 0) { // Goal specifically < 0 is invalid
                 editTextGlassesGoal.error = "Goal cannot be negative"
                 Toast.makeText(this, "Goal cannot be negative.", Toast.LENGTH_SHORT).show()
                 return@setOnClickListener
            }

            currentGlassesGoal = goalToSave // can be 0 if no reminders
            val timesToSave = reminderTimesAdapter.getTimes()
            saveHydrationSettings(currentGlassesGoal, timesToSave)

            if (timesToSave.isNotEmpty()) {
                Toast.makeText(this, "Goal and reminders saved!", Toast.LENGTH_SHORT).show()
                checkAndRequestNotificationPermissionThenSchedule()
            } else {
                Toast.makeText(this, "Goal saved! All reminders cleared.", Toast.LENGTH_SHORT).show()
                HydrationReminderManager.cancelAllReminders(this)
            }
        }
    }

    private fun checkAndRequestNotificationPermissionThenSchedule() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    Log.d(TAG, "POST_NOTIFICATIONS permission already granted.")
                    HydrationReminderManager.scheduleOrUpdateAllReminders(this)
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    // Show an educational UI to the user asking for permission
                    // For simplicity, we'll show a toast and then request.
                    Toast.makeText(this, "Notification permission is required for hydration reminders.", Toast.LENGTH_LONG).show()
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                else -> {
                    // Directly request the permission
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            // No runtime permission needed for pre-Tiramisu
            HydrationReminderManager.scheduleOrUpdateAllReminders(this)
        }
    }

    private fun saveHydrationSettings(goal: Int, times: List<String>) {
        val editor = sharedPreferences.edit()
        editor.putInt(KEY_GLASSES_GOAL, goal)
        editor.putStringSet(KEY_REMINDER_TIMES, times.toSet())
        editor.apply()
        Log.d(TAG, "Hydration settings saved. Goal: $goal, Times: ${times.joinToString()}")
    }

    private fun loadHydrationSettings() {
        currentGlassesGoal = sharedPreferences.getInt(KEY_GLASSES_GOAL, 0)
        if (currentGlassesGoal > 0) {
            editTextGlassesGoal.setText(currentGlassesGoal.toString())
        } else {
            editTextGlassesGoal.setText("") // Clear if goal is 0 or not set
        }

        val savedTimes = sharedPreferences.getStringSet(KEY_REMINDER_TIMES, emptySet()) ?: emptySet()
        reminderTimesList.clear()
        reminderTimesList.addAll(savedTimes.sorted())
        Log.d(TAG, "Hydration settings loaded. Goal: $currentGlassesGoal, Times: ${reminderTimesList.joinToString()}")

        if (::reminderTimesAdapter.isInitialized) {
            reminderTimesAdapter.notifyDataSetChanged()
        }
    }

    private fun setupRecyclerView() {
        reminderTimesAdapter = ReminderTimesAdapter(reminderTimesList) { position ->
            reminderTimesAdapter.removeTime(position)
            updateEmptyViewVisibility()
            updateRemindersSetInfoText()
        }
        recyclerViewReminderTimes.adapter = reminderTimesAdapter
        recyclerViewReminderTimes.layoutManager = LinearLayoutManager(this)
    }

    private fun setupGlassesGoalListener() {
        editTextGlassesGoal.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                currentGlassesGoal = s.toString().toIntOrNull() ?: 0
                updateRemindersSetInfoText()
            }
        })
    }

    private fun updateEmptyViewVisibility() {
        if (reminderTimesAdapter.itemCount == 0) {
            textViewNoReminders.visibility = View.VISIBLE
            recyclerViewReminderTimes.visibility = View.GONE
        } else {
            textViewNoReminders.visibility = View.GONE
            recyclerViewReminderTimes.visibility = View.VISIBLE
        }
    }

    private fun updateRemindersSetInfoText() {
        val currentCount = reminderTimesAdapter.itemCount
        val goalText = if (currentGlassesGoal > 0) currentGlassesGoal.toString() else "-"
        textViewRemindersSetInfo.text = "Reminders: $currentCount / $goalText"
    }

    private fun showTimePickerDialog() {
        if (currentGlassesGoal > 0 && reminderTimesAdapter.itemCount >= currentGlassesGoal) {
            Toast.makeText(this, "You\'ve already set $currentGlassesGoal reminders to meet your goal.", Toast.LENGTH_SHORT).show()
            return
        }
        if (currentGlassesGoal <= 0 && reminderTimesAdapter.getTimes().isEmpty()){
             Toast.makeText(this, "Please set a daily glasses goal first.", Toast.LENGTH_SHORT).show()
            return
        }

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
                    updateRemindersSetInfoText()
                } else {
                    Toast.makeText(this, "This reminder time already exists.", Toast.LENGTH_SHORT).show()
                }
            },
            currentHour,
            currentMinute,
            false // False for 12-hour format with AM/PM
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return super.onOptionsItemSelected(item)
    }
}
