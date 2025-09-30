package com.example.wellness_pro.ui

import android.app.TimePickerDialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout // Added for headerLayoutHydration
import android.widget.TextView
import android.widget.Toast
// import androidx.appcompat.widget.Toolbar // No longer using Toolbar directly here
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.wellness_pro.R
import com.example.wellness_pro.navbar.BaseBottomNavActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HydrationActivity : BaseBottomNavActivity() {

    override val layoutId: Int
        get() = R.layout.activity_hydration

    override val currentNavControllerItemId: Int
        get() = R.id.navButtonHydration // Ensure this ID exists in your nav menu and layout_reusable_nav_bar.xml

    private lateinit var recyclerViewReminderTimes: RecyclerView
    private lateinit var textViewNoReminders: TextView
    private lateinit var buttonAddTime: Button
    private lateinit var buttonSaveReminders: Button
    private lateinit var editTextGlassesGoal: EditText
    private lateinit var textViewRemindersSetInfo: TextView
    private lateinit var headerLayoutHydration: LinearLayout // For applying insets to the new header

    private lateinit var reminderTimesAdapter: ReminderTimesAdapter
    private val reminderTimesList = mutableListOf<String>()
    private var currentGlassesGoal: Int = 0

    companion object {
        private const val PREFS_NAME = "HydrationSettingsPrefs"
        private const val KEY_GLASSES_GOAL = "glassesGoal"
        private const val KEY_REMINDER_TIMES = "reminderTimesSet"
    }

    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        headerLayoutHydration = findViewById(R.id.headerLayoutHydration) // Initialize new header

        // Apply insets for Edge-to-Edge display to the new header
        ViewCompat.setOnApplyWindowInsetsListener(headerLayoutHydration) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Apply top padding to the header to account for the status bar
            view.updatePadding(top = insets.top)
            // Return CONSUMED to prevent other listeners from overriding this
            WindowInsetsCompat.CONSUMED
        }

        // Toolbar and ActionBar setup removed
        // findViewById<Toolbar>(R.id.toolbar_hydration) // old toolbar ID, no longer used in this manner
        // setSupportActionBar(toolbar) // Removed
        // supportActionBar?.setDisplayHomeAsUpEnabled(true) // Removed
        // supportActionBar?.setDisplayShowHomeEnabled(true) // Removed

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
            if (goalToSave <= 0) {
                Toast.makeText(this, "Please set a valid daily glasses goal.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            currentGlassesGoal = goalToSave
            val timesToSave = reminderTimesAdapter.getTimes()
            saveHydrationSettings(currentGlassesGoal, timesToSave)

            if (timesToSave.isNotEmpty()) {
                Toast.makeText(this, "Goal and reminders saved!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Goal saved! No reminders were set.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveHydrationSettings(goal: Int, times: List<String>) {
        val editor = sharedPreferences.edit()
        editor.putInt(KEY_GLASSES_GOAL, goal)
        editor.putStringSet(KEY_REMINDER_TIMES, times.toSet())
        editor.apply()
    }

    private fun loadHydrationSettings() {
        currentGlassesGoal = sharedPreferences.getInt(KEY_GLASSES_GOAL, 0)
        if (currentGlassesGoal > 0) {
            editTextGlassesGoal.setText(currentGlassesGoal.toString())
        } else {
            editTextGlassesGoal.setText("")
        }

        val savedTimes = sharedPreferences.getStringSet(KEY_REMINDER_TIMES, emptySet()) ?: emptySet()
        reminderTimesList.clear()
        reminderTimesList.addAll(savedTimes.sorted())

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

        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)

        val timePickerDialog = TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                val formattedTime = formatTime(hourOfDay, minute)
                reminderTimesAdapter.addTime(formattedTime)
                updateEmptyViewVisibility()
                updateRemindersSetInfoText()
            },
            currentHour,
            currentMinute,
            false
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
        // android.R.id.home handling removed as the new header doesn't have an up button by default
        // If you add other menu items to an options menu, handle them here.
        return super.onOptionsItemSelected(item)
    }
}
