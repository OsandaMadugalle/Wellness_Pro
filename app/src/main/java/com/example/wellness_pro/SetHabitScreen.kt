package com.example.wellness_pro

import android.Manifest
import android.app.AlarmManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
// import androidx.coordinatorlayout.widget.CoordinatorLayout // Not used
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.ArrayAdapter
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
// import android.widget.Switch // No longer using this specific import
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import com.example.wellness_pro.navbar.BaseBottomNavActivity
// import com.example.wellness_pro.reminders.HydrationReminderManager // No longer needed if removing this feature
// import com.google.android.material.switchmaterial.SwitchMaterial // No longer needed
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Calendar
import java.util.Locale
import java.util.UUID

class SetHabitScreen : BaseBottomNavActivity() {

    override val layoutId: Int
        get() = R.layout.activity_set_habitscreen

    private lateinit var textViewScreenTitle: TextView
    private lateinit var buttonSetHabit: Button
    private lateinit var itemContainerSpinner: TextInputLayout
    private lateinit var textViewSelectedItem: MaterialAutoCompleteTextView
    private lateinit var textViewValueUnit: TextView
    private lateinit var editTextValueAmount: EditText
    private lateinit var buttonDaily: Button
    private lateinit var buttonWeekly: Button
    private lateinit var buttonWeekdays: Button
    private lateinit var scheduleButtons: List<Button>
    private var selectedSchedule: String = "Daily" // This holds the UI selected schedule type
    
    private var selectedHour: Int = -1
    private var selectedMinute: Int = -1

    

    
    private val habitTypes = listOf("Steps", "Meditation", "Workout", "Reading")
    private var currentSelectedHabitType: String = if (habitTypes.isNotEmpty()) habitTypes[0] else ""

    private var existingHabitId: String? = null
    private var isEditMode: Boolean = false
    private lateinit var allHabitsList: MutableList<Habit>


    companion object {
        const val EXTRA_HABIT_ID = "extra_habit_id"
        const val LIFECYCLE_TAG = "SetHabitScreen_Lifecycle"
        const val DEBUG_TAG = "SetHabitScreen_Debug"
    }

    // Provide the nav bar id so BaseBottomNavActivity can wire and highlight the proper tab
    override val currentNavControllerItemId: Int
        get() = R.id.navButtonHabits

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.i(LIFECYCLE_TAG, "POST_NOTIFICATIONS permission granted.")
                Toast.makeText(this, "Notification permission granted.", Toast.LENGTH_SHORT).show()
            } else {
                Log.w(LIFECYCLE_TAG, "POST_NOTIFICATIONS permission denied.")
                Toast.makeText(this, "Notifications permission denied. Reminders will not work.", Toast.LENGTH_LONG).show()
            }
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(LIFECYCLE_TAG, "onCreate - START")
        super.onCreate(savedInstanceState)
        Log.d(LIFECYCLE_TAG, "onCreate - super.onCreate CALLED (setContentView with R.layout.activity_set_habitscreen likely done by base)")

        Log.d(LIFECYCLE_TAG, "onCreate - Calling loadAllHabits...")
        loadAllHabits()
        Log.d(LIFECYCLE_TAG, "onCreate - loadAllHabits COMPLETED. allHabitsList size: ${allHabitsList.size}")

        Log.d(LIFECYCLE_TAG, "onCreate - Calling initializeViews...")
        initializeViews()
        Log.d(LIFECYCLE_TAG, "onCreate - initializeViews COMPLETED (or finish() was called if it failed)")

        if (isFinishing) {
            Log.w(LIFECYCLE_TAG, "onCreate - Activity isFinishing after initializeViews. Aborting further setup.")
            return
        }

        Log.d(LIFECYCLE_TAG, "onCreate - Calling setupWindowInsets...")
        setupWindowInsets()
        Log.d(LIFECYCLE_TAG, "onCreate - setupWindowInsets COMPLETED")

        Log.d(LIFECYCLE_TAG, "onCreate - Calling setupClickListeners...")
        setupClickListeners()
        Log.d(LIFECYCLE_TAG, "onCreate - setupClickListeners COMPLETED")

        existingHabitId = intent.getStringExtra(EXTRA_HABIT_ID)
        isEditMode = existingHabitId != null
        Log.d(LIFECYCLE_TAG, "onCreate - isEditMode: $isEditMode, existingHabitId: $existingHabitId")

        if (isEditMode) {
            Log.d(LIFECYCLE_TAG, "onCreate - Populating fields for EDIT mode...")
            textViewScreenTitle.text = getString(R.string.edit_habit_title)
            buttonSetHabit.text = getString(R.string.update_habit_button)
            populateFieldsForEdit()
            Log.d(LIFECYCLE_TAG, "onCreate - populateFieldsForEdit COMPLETED (or finish() was called if it failed)")
        } else {
            Log.d(LIFECYCLE_TAG, "onCreate - Setting up for NEW habit mode...")
            textViewScreenTitle.text = getString(R.string.set_new_habit_title)
            buttonSetHabit.text = getString(R.string.set_habit_button)
            if (habitTypes.isNotEmpty()) {
                currentSelectedHabitType = habitTypes[0]
                textViewSelectedItem.setText(currentSelectedHabitType, false)
                updateValueContainer(currentSelectedHabitType)
            } else {
                Log.e(LIFECYCLE_TAG, "Habit types list is empty! Cannot set default type.")
                textViewSelectedItem.setText("No types available", false)
                // Consider disabling parts of the UI or showing an error
            }
            updateScheduleButtonStates(buttonDaily) // Default to Daily
            Log.d(LIFECYCLE_TAG, "onCreate - NEW habit mode setup COMPLETED.")
        }
        Log.d(LIFECYCLE_TAG, "onCreate - END")
    }

    private fun initializeViews() {
        Log.d(LIFECYCLE_TAG, "initializeViews - START")
        try {
            textViewScreenTitle = findViewById(R.id.textViewScreenTitle)
            buttonSetHabit = findViewById(R.id.buttonSetHabit)
            itemContainerSpinner = findViewById(R.id.itemContainerSpinner)
            textViewSelectedItem = findViewById(R.id.textViewSelectedItem)
            textViewValueUnit = findViewById(R.id.textViewValueUnit)
            editTextValueAmount = findViewById(R.id.editTextValueAmount)
            buttonDaily = findViewById(R.id.buttonDaily)
            buttonWeekly = findViewById(R.id.buttonWeekly)
            buttonWeekdays = findViewById(R.id.buttonWeekdays)
            scheduleButtons = listOf(buttonDaily, buttonWeekly, buttonWeekdays)
            

            Log.d(LIFECYCLE_TAG, "initializeViews - All findViewById calls attempted.")
            // Setup dropdown adapter for habit types
            try {
                val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, habitTypes)
                textViewSelectedItem.setAdapter(adapter)
                textViewSelectedItem.setOnItemClickListener { parent, view, position, id ->
                    val selected = parent.getItemAtPosition(position).toString()
                    currentSelectedHabitType = selected
                    textViewSelectedItem.setText(selected, false)
                    updateValueContainer(selected)
                }
            } catch (e: Exception) {
                Log.w(LIFECYCLE_TAG, "initializeViews - could not setup dropdown adapter: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e(LIFECYCLE_TAG, "initializeViews - ERROR finding views: ${e.message}", e)
            Toast.makeText(this, "Error initializing screen components. Layout might be incorrect.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        Log.d(LIFECYCLE_TAG, "initializeViews - END (Successfully initialized or error handled)")
    }

    private fun setupWindowInsets() {
        Log.d(LIFECYCLE_TAG, "setupWindowInsets - START")
        val mainLayout = findViewById<View?>(R.id.main)
        if (mainLayout == null) {
            Log.e(LIFECYCLE_TAG, "setupWindowInsets - Root view with ID 'main' not found. Insets will not be applied.")
            return
        }

        findViewById<View?>(R.id.headerLayout)?.let { header ->
            if (header.getTag(R.id.tag_padding_top) == null) {
                header.setTag(R.id.tag_padding_top, header.paddingTop)
                Log.d(LIFECYCLE_TAG, "setupWindowInsets - Set initial top padding for headerLayout.")
            }
        }
        findViewById<View?>(R.id.bottomActionsContainer)?.let { bottomContainer ->
            if (bottomContainer.getTag(R.id.tag_padding_bottom) == null) {
                bottomContainer.setTag(R.id.tag_padding_bottom, bottomContainer.paddingBottom)
                Log.d(LIFECYCLE_TAG, "setupWindowInsets - Set initial bottom padding for bottomActionsContainer.")
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(mainLayout) { _, windowInsets ->
            Log.d(LIFECYCLE_TAG, "setupWindowInsets - OnApplyWindowInsetsListener triggered.")
            val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime())

            findViewById<View?>(R.id.headerLayout)?.apply {
                val initialPaddingTop = getTag(R.id.tag_padding_top) as? Int ?: this.paddingTop
                updatePadding(top = systemBars.top + initialPaddingTop)
            }

            findViewById<View?>(R.id.scrollView)?.apply {
                updatePadding(bottom = kotlin.math.max(systemBars.bottom, imeInsets.bottom))
            }

            findViewById<View?>(R.id.bottomActionsContainer)?.apply {
                val initialPaddingBottom = getTag(R.id.tag_padding_bottom) as? Int ?: this.paddingBottom
                val bottomPadding = kotlin.math.max(systemBars.bottom, imeInsets.bottom)
                updatePadding(bottom = bottomPadding + initialPaddingBottom)
            }
            WindowInsetsCompat.CONSUMED
        }
        Log.d(LIFECYCLE_TAG, "setupWindowInsets - END")
    }

    private fun setupClickListeners() {
        Log.d(LIFECYCLE_TAG, "setupClickListeners - START")
    // Back navigation handled after successful save via navigateToHabitsScreen().
        buttonSetHabit.setOnClickListener {
            Log.d(DEBUG_TAG, "buttonSetHabit clicked.")
            Log.d(DEBUG_TAG, "Processing and saving habit data...")
            if (processAndSaveHabitData()) {
                Log.d(DEBUG_TAG, "processAndSaveHabitData successful. Navigating to HabitsScreen.")
                navigateToHabitsScreen()
            } else {
                Log.w(DEBUG_TAG, "processAndSaveHabitData FAILED.")
            }
        }
    // Use the MaterialAutoCompleteTextView's dropdown; still allow tapping container to open it
    itemContainerSpinner.setOnClickListener { _ -> textViewSelectedItem.showDropDown() }
        buttonDaily.setOnClickListener { updateScheduleButtonStates(it as Button) }
        buttonWeekly.setOnClickListener { updateScheduleButtonStates(it as Button) }
        buttonWeekdays.setOnClickListener { updateScheduleButtonStates(it as Button) }
    
        Log.d(LIFECYCLE_TAG, "setupClickListeners - END")
    }

    private fun hasNotificationPermission(): Boolean {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
        Log.d(DEBUG_TAG, "hasNotificationPermission on API ${Build.VERSION.SDK_INT}: $hasPermission")
        return hasPermission
    }

    private fun requestNotificationPermission() {
        Log.d(DEBUG_TAG, "requestNotificationPermission - START")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                Log.d(DEBUG_TAG, "Showing rationale for POST_NOTIFICATIONS.")
                Toast.makeText(this, "Notification permission is needed for reminders.", Toast.LENGTH_LONG).show()
            }
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        Log.d(DEBUG_TAG, "requestNotificationPermission - END (request launched or not applicable)")
    }

    private fun loadAllHabits() {
        Log.d(LIFECYCLE_TAG, "loadAllHabits() - START")
        val prefs = getSharedPreferences("PlayPalHabits", Context.MODE_PRIVATE)
        val gson = Gson()
        val json = prefs.getString("habits_list_json", null)
        if (json != null) {
            val type = object : TypeToken<MutableList<Habit>>() {}.type
            allHabitsList = gson.fromJson(json, type) ?: mutableListOf()
            Log.d(LIFECYCLE_TAG, "Loaded ${allHabitsList.size} habits from SharedPreferences.")
        } else {
            allHabitsList = mutableListOf()
            Log.d(LIFECYCLE_TAG, "No habits found in SharedPreferences, initialized an empty list.")
        }
    }
    
    private fun updateValueContainer(habitType: String) {
        Log.d(DEBUG_TAG, "updateValueContainer called for type: $habitType")
        when (habitType) {
            
            "Steps" -> {
                textViewValueUnit.text = "steps"
                editTextValueAmount.hint = "e.g., 10000"
            }
            "Meditation", "Workout" -> {
                textViewValueUnit.text = "minutes"
                editTextValueAmount.hint = "e.g., 30"
            }
            "Reading" -> {
                textViewValueUnit.text = "pages"
                editTextValueAmount.hint = "e.g., 20"
            }
            else -> {
                // This will be the default if habitTypes becomes empty or an unexpected type is passed.
                // For newly created habits from the current habitTypes list, this 'else' won't be hit.
                textViewValueUnit.text = "units"
                editTextValueAmount.hint = "value"
                 if (!habitTypes.contains(habitType) && habitType.isNotEmpty()) {
                    Log.w(DEBUG_TAG, "updateValueContainer called with an unknown or filtered habit type: $habitType. Falling back to default units.")
                }
            }
        }
        editTextValueAmount.setText("") 
    }

    private fun showHabitTypePopupMenu(anchorView: View) {
        Log.d(DEBUG_TAG, "showHabitTypePopupMenu called")
        val popupMenu = PopupMenu(this, anchorView)
        if (habitTypes.isEmpty()) {
            popupMenu.menu.add("No habit types available").setEnabled(false)
        } else {
            habitTypes.forEach { habitType ->
                popupMenu.menu.add(habitType)
            }
        }
        popupMenu.setOnMenuItemClickListener { menuItem ->
            if (habitTypes.isEmpty() || !menuItem.isEnabled) return@setOnMenuItemClickListener false
                currentSelectedHabitType = menuItem.title.toString()
                textViewSelectedItem.setText(currentSelectedHabitType, false)
            updateValueContainer(currentSelectedHabitType)
            true
        }
        popupMenu.show()
    }

    private fun updateScheduleButtonStates(clickedButton: Button) {
        Log.d(DEBUG_TAG, "updateScheduleButtonStates called for: ${clickedButton.text}")
        selectedSchedule = clickedButton.text.toString()
        scheduleButtons.forEach { button ->
            val isSelected = (button == clickedButton)
            button.isSelected = isSelected
            val bgColor = if (isSelected) R.color.colorAccentGreen else R.color.cardBackgroundColor
            val textColor = if (isSelected) R.color.white else R.color.textColorPrimary
            try {
                button.backgroundTintList = ContextCompat.getColorStateList(this, bgColor)
            } catch (_: Exception) {
                // Ignore if backgroundTintList not supported here
            }
            button.setTextColor(ContextCompat.getColor(this, textColor))
        }
    }
    
    private fun showTimePickerDialog() {
        Log.d(DEBUG_TAG, "showTimePickerDialog called")
        val calendar = Calendar.getInstance()
        val hour = if (selectedHour != -1) selectedHour else calendar.get(Calendar.HOUR_OF_DAY)
        val minute = if (selectedMinute != -1) selectedMinute else calendar.get(Calendar.MINUTE)

        TimePickerDialog(this, { _, hourOfDay, minuteOfHour ->
            selectedHour = hourOfDay
            selectedMinute = minuteOfHour
            Log.d(DEBUG_TAG, "Reminder time selected: ${String.format(Locale.getDefault(), "%02d:%02d", selectedHour, selectedMinute)}")
            Toast.makeText(this, "Selected time: ${String.format(Locale.getDefault(), "%02d:%02d", selectedHour, selectedMinute)}", Toast.LENGTH_SHORT).show()
        }, hour, minute, true).show()
    }

    private fun populateFieldsForEdit() {
        Log.d(LIFECYCLE_TAG, "populateFieldsForEdit() - START")
        if (existingHabitId == null) {
            Log.e(LIFECYCLE_TAG, "populateFieldsForEdit called with null existingHabitId. Finishing.")
            Toast.makeText(this, "Error: Habit ID missing for edit.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val habitToEdit = allHabitsList.find { it.id == existingHabitId }
        if (habitToEdit == null) {
            Log.e(LIFECYCLE_TAG, "Habit with ID $existingHabitId not found for edit. Finishing.")
            Toast.makeText(this, "Error: Habit data not found.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // If the habit being edited is of a type no longer in habitTypes (e.g., Hydration),
        // we should probably prevent editing or handle it gracefully.
        // For now, it will load, but user won't be able to switch its type to another filtered type via the popup.
        if (!habitTypes.contains(habitToEdit.type) && habitTypes.isNotEmpty()) {
             Log.w(DEBUG_TAG, "Editing a habit of type '${habitToEdit.type}' which is no longer in the active habitTypes list. The type selector will not include it if changed.")
             // Potentially, you might want to default to the first available type or show a message
        }

        currentSelectedHabitType = habitToEdit.type
    textViewSelectedItem.setText(currentSelectedHabitType, false)
        editTextValueAmount.setText(habitToEdit.targetValue.toString())
        updateValueContainer(currentSelectedHabitType) 

        selectedSchedule = habitToEdit.schedule 
        when (selectedSchedule) {
            "Daily" -> updateScheduleButtonStates(buttonDaily)
            "Weekly" -> updateScheduleButtonStates(buttonWeekly)
            "Weekdays" -> updateScheduleButtonStates(buttonWeekdays)
            else -> updateScheduleButtonStates(buttonDaily) 
        }

        selectedHour = habitToEdit.reminderTimeHour ?: -1
        selectedMinute = habitToEdit.reminderTimeMinute ?: -1
        // Reminder time fields are kept in the model but not shown in the UI
        Log.d(LIFECYCLE_TAG, "populateFieldsForEdit() - END for habit: ${habitToEdit.type}")
    }

    private fun processAndSaveHabitData(): Boolean {
        Log.d(DEBUG_TAG, "processAndSaveHabitData() - START")
        val habitName = currentSelectedHabitType
        val targetValueStr = editTextValueAmount.text.toString()

        if (habitName.isEmpty() || (habitTypes.isEmpty() && !isEditMode)) {
            Toast.makeText(this, "No habit type selected or available.", Toast.LENGTH_SHORT).show()
            return false
        }

        if (targetValueStr.isBlank()) {
            editTextValueAmount.error = "Target value cannot be empty."
            Toast.makeText(this, "Please enter a target value.", Toast.LENGTH_SHORT).show()
            return false
        }
        val targetValue = targetValueStr.toIntOrNull()
        if (targetValue == null || targetValue <= 0) {
            editTextValueAmount.error = "Please enter a valid positive target value."
            Toast.makeText(this, "Please enter a valid positive target value.", Toast.LENGTH_SHORT).show()
            return false
        }
        
        val newOrUpdatedHabit = if (isEditMode && existingHabitId != null) {
            allHabitsList.find { it.id == existingHabitId }?.apply {
                this.type = habitName
                this.targetValue = targetValue
                this.schedule = selectedSchedule 
                this.reminderTimeHour = selectedHour
                this.reminderTimeMinute = selectedMinute
                this.unit = textViewValueUnit.text.toString().removeSuffix("/day").trim()
            } ?: run {
                Log.e(DEBUG_TAG, "Failed to find habit with ID $existingHabitId for update.")
                return false 
            }
        } else {
            Habit(
                id = UUID.randomUUID().toString(),
                type = habitName,
                targetValue = targetValue,
                schedule = selectedSchedule, 
                reminderTimeHour = selectedHour,
                reminderTimeMinute = selectedMinute,
                unit = textViewValueUnit.text.toString().removeSuffix("/day").trim()
            )
        }

        if (isEditMode) {
            val index = allHabitsList.indexOfFirst { it.id == newOrUpdatedHabit.id }
            if (index != -1) {
                allHabitsList[index] = newOrUpdatedHabit
            } else { 
                 Log.w(DEBUG_TAG, "processAndSaveHabitData: existing habit for edit mode not found in allHabitsList. Adding as new. ID: ${newOrUpdatedHabit.id}")
                allHabitsList.add(newOrUpdatedHabit)
            }
        } else {
            allHabitsList.add(newOrUpdatedHabit)
        }
        saveHabitsToPrefs()
    // Reminder time fields are kept but scheduling is not performed here.
        Log.d(DEBUG_TAG, "Habit data processed and saved to list. isEditMode: $isEditMode, Habit type: ${newOrUpdatedHabit.type}")
        return true
    }
    
    private fun saveHabitsToPrefs() {
        Log.d(DEBUG_TAG, "saveHabitsToPrefs() called")
        val prefs = getSharedPreferences("PlayPalHabits", Context.MODE_PRIVATE)
        val gson = Gson()
        val json = gson.toJson(allHabitsList)
        prefs.edit().putString("habits_list_json", json).apply()
        Log.i(DEBUG_TAG, "Habits saved to SharedPreferences. Count: ${allHabitsList.size}")
    }

    private fun navigateToHabitsScreen() {
        finish()
    }
}
