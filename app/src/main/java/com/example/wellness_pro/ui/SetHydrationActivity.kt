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
        ViewCompat.setOnApplyWindowInsetsListener(headerLayoutSetHydration) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = insets.top)
            WindowInsetsCompat.CONSUMED
        }

        editTextSetGlassesGoal = findViewById(R.id.editTextSetGlassesGoal)
        textViewSetRemindersInfo = findViewById(R.id.textViewSetRemindersInfo)
        recyclerViewSetReminderTimes = findViewById(R.id.recyclerViewSetReminderTimes)
        textViewSetNoReminders = findViewById(R.id.textViewSetNoReminders)
        buttonSetAddTime = findViewById(R.id.buttonSetAddTime)
        buttonSaveHydrationSettings = findViewById(R.id.buttonSaveHydrationSettings)

        setupRecyclerView()
        setupGlassesGoalListener()
        loadHydrationSettingsToUI()
        updateRemindersCountInfoText()
        updateEmptyViewVisibility()

        buttonSetAddTime.setOnClickListener {
            showTimePickerDialog()
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here if any.
        return super.onOptionsItemSelected(item)
    }
}
