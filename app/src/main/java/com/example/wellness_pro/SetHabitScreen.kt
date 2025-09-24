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
import com.example.wellness_pro.navbar.BaseActivity
import com.example.wellness_pro.reminders.HydrationReminderManager
import com.google.android.material.switchmaterial.SwitchMaterial // <<< IMPORT THIS
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Calendar
import java.util.Locale

class SetHabitScreen : BaseActivity() {

    override val layoutId: Int
        get() = R.layout.activity_set_habitscreen

    private lateinit var textViewScreenTitle: TextView
    private lateinit var buttonBackTop: ImageView
    private lateinit var buttonSetHabit: Button
    private lateinit var itemContainerSpinner: ConstraintLayout
    private lateinit var textViewSelectedItem: TextView
    private lateinit var textViewValueUnit: TextView
    private lateinit var editTextValueAmount: EditText
    private lateinit var buttonDaily: Button
    private lateinit var buttonWeekly: Button
    private lateinit var buttonWeekdays: Button
    private lateinit var scheduleButtons: List<Button>
    private var selectedSchedule: String = "Daily"
    private lateinit var textViewRemindMePrompt: TextView
    private lateinit var textViewReminderTime: TextView
    private var selectedHour: Int = -1
    private var selectedMinute: Int = -1

    private lateinit var hydrationReminderSettingsContainer: ConstraintLayout
    private lateinit var switchHydrationReminder: SwitchMaterial // <<< CORRECTED TYPE HERE
    private lateinit var editTextReminderIntervalMinutes: EditText

    private val habitTypes = listOf("Hydration", "Steps", "Meditation", "Workout", "Reading")
    private var currentSelectedHabitType: String = habitTypes[0]

    private var existingHabitId: String? = null
    private var isEditMode: Boolean = false
    private lateinit var allHabitsList: MutableList<Habit>


    companion object {
        const val EXTRA_HABIT_ID = "extra_habit_id"
        const val LIFECYCLE_TAG = "SetHabitScreen_Lifecycle"
        const val DEBUG_TAG = "SetHabitScreen_Debug"
    }

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.i(LIFECYCLE_TAG, "POST_NOTIFICATIONS permission granted.")
                Toast.makeText(this, "Notification permission granted.", Toast.LENGTH_SHORT).show()
                if (currentSelectedHabitType.equals("Hydration", ignoreCase = true) && this::switchHydrationReminder.isInitialized && switchHydrationReminder.isChecked) {
                    Toast.makeText(this, "Try saving the habit again to set the reminder.", Toast.LENGTH_LONG).show()
                }
            } else {
                Log.w(LIFECYCLE_TAG, "POST_NOTIFICATIONS permission denied.")
                Toast.makeText(this, "Notifications permission denied. Reminders will not work.", Toast.LENGTH_LONG).show()
                if (this::switchHydrationReminder.isInitialized) {
                    switchHydrationReminder.isChecked = false
                }
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
            textViewSelectedItem.text = currentSelectedHabitType
            updateValueContainer(currentSelectedHabitType)
            updateScheduleButtonStates(buttonDaily)
            updateHydrationReminderVisibility(currentSelectedHabitType)
            if (currentSelectedHabitType.equals("Hydration", ignoreCase = true)) {
                if(this::switchHydrationReminder.isInitialized) switchHydrationReminder.isChecked = false
                if(this::editTextReminderIntervalMinutes.isInitialized) {
                    editTextReminderIntervalMinutes.setText("60")
                    editTextReminderIntervalMinutes.isEnabled = false
                }
            }
            Log.d(LIFECYCLE_TAG, "onCreate - NEW habit mode setup COMPLETED.")
        }
        Log.d(LIFECYCLE_TAG, "onCreate - END")
    }

    private fun initializeViews() {
        Log.d(LIFECYCLE_TAG, "initializeViews - START")
        try {
            textViewScreenTitle = findViewById(R.id.textViewScreenTitle)
            buttonBackTop = findViewById(R.id.buttonBackTop)
            buttonSetHabit = findViewById(R.id.buttonSetHabit)
            itemContainerSpinner = findViewById(R.id.itemContainerSpinner)
            textViewSelectedItem = findViewById(R.id.textViewSelectedItem)
            textViewValueUnit = findViewById(R.id.textViewValueUnit)
            editTextValueAmount = findViewById(R.id.editTextValueAmount)
            buttonDaily = findViewById(R.id.buttonDaily)
            buttonWeekly = findViewById(R.id.buttonWeekly)
            buttonWeekdays = findViewById(R.id.buttonWeekdays)
            scheduleButtons = listOf(buttonDaily, buttonWeekly, buttonWeekdays)
            textViewRemindMePrompt = findViewById(R.id.textViewRemindMePrompt)
            textViewReminderTime = findViewById(R.id.textViewReminderTime)

            hydrationReminderSettingsContainer = findViewById(R.id.hydrationReminderSettingsContainer)
            switchHydrationReminder = findViewById(R.id.switchHydrationReminder) // This line should no longer crash
            editTextReminderIntervalMinutes = findViewById(R.id.editTextReminderIntervalMinutes)
            Log.d(LIFECYCLE_TAG, "initializeViews - All findViewById calls attempted.")
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


    private fun updateHydrationReminderVisibility(habitType: String) {
        Log.d(LIFECYCLE_TAG, "updateHydrationReminderVisibility - START. Habit type: $habitType")
        if (!this::hydrationReminderSettingsContainer.isInitialized) {
            Log.w(LIFECYCLE_TAG, "updateHydrationReminderVisibility - hydrationReminderSettingsContainer not initialized yet.")
            return
        }
        hydrationReminderSettingsContainer.isVisible = habitType.equals("Hydration", ignoreCase = true)
        Log.d(LIFECYCLE_TAG, "updateHydrationReminderVisibility - hydrationReminderSettingsContainer.isVisible: ${hydrationReminderSettingsContainer.isVisible}")
        if (!hydrationReminderSettingsContainer.isVisible) {
            if(this::switchHydrationReminder.isInitialized) switchHydrationReminder.isChecked = false
            if(this::editTextReminderIntervalMinutes.isInitialized) {
                editTextReminderIntervalMinutes.isEnabled = false
                editTextReminderIntervalMinutes.text.clear()
            }
        }
        Log.d(LIFECYCLE_TAG, "updateHydrationReminderVisibility - END")
    }

    private fun setupClickListeners() {
        Log.d(LIFECYCLE_TAG, "setupClickListeners - START")
        buttonBackTop.setOnClickListener { navigateToHabitsScreen() }
        buttonSetHabit.setOnClickListener {
            Log.d(DEBUG_TAG, "buttonSetHabit clicked.")
            if (currentSelectedHabitType.equals("Hydration", ignoreCase = true) && switchHydrationReminder.isChecked) {
                Log.d(DEBUG_TAG, "Hydration habit with reminder enabled.")
                if (!hasNotificationPermission()) {
                    Log.d(DEBUG_TAG, "Notification permission NOT granted. Requesting...")
                    requestNotificationPermission()
                    return@setOnClickListener
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val alarmManagerService = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                    if (alarmManagerService != null && !alarmManagerService.canScheduleExactAlarms()) {
                        Log.d(DEBUG_TAG, "Exact alarm permission NOT granted. Opening settings...")
                        Toast.makeText(this, "App needs permission to schedule exact alarms for precise reminders. Please enable it in settings.", Toast.LENGTH_LONG).show()
                        try {
                            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = android.net.Uri.parse("package:$packageName")
                            })
                        } catch (e: Exception) {
                            Log.e(DEBUG_TAG, "Could not open exact alarm settings", e)
                            Toast.makeText(this, "Could not open settings. Please enable 'Alarms & reminders' manually for PlayPal.", Toast.LENGTH_LONG).show()
                        }
                        return@setOnClickListener
                    }
                }
            }
            Log.d(DEBUG_TAG, "Processing and saving habit data...")
            if (processAndSaveHabitData()) {
                Log.d(DEBUG_TAG, "processAndSaveHabitData successful. Navigating to HabitsScreen.")
                navigateToHabitsScreen()
            } else {
                Log.w(DEBUG_TAG, "processAndSaveHabitData FAILED.")
            }
        }
        itemContainerSpinner.setOnClickListener { anchorView -> showHabitTypePopupMenu(anchorView) }
        buttonDaily.setOnClickListener { updateScheduleButtonStates(it as Button) }
        buttonWeekly.setOnClickListener { updateScheduleButtonStates(it as Button) }
        buttonWeekdays.setOnClickListener { updateScheduleButtonStates(it as Button) }
        textViewReminderTime.setOnClickListener { showTimePickerDialog() }
        textViewRemindMePrompt.setOnClickListener { showTimePickerDialog() }

        switchHydrationReminder.setOnCheckedChangeListener { _, isChecked ->
            Log.d(DEBUG_TAG, "switchHydrationReminder - isChecked: $isChecked")
            editTextReminderIntervalMinutes.isEnabled = isChecked
            if (isChecked) {
                if (!hasNotificationPermission()) {
                    requestNotificationPermission()
                }
                if (editTextReminderIntervalMinutes.text.isBlank()) {
                    editTextReminderIntervalMinutes.setText("60")
                }
            } else {
                editTextReminderIntervalMinutes.error = null
            }
        }
        Log.d(LIFECYCLE_TAG, "setupClickListeners - END")
    }

    private fun hasNotificationPermission(): Boolean {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true // For Android 10 (API 29) and below, this is effectively true as permission is not runtime.
        Log.d(DEBUG_TAG, "hasNotificationPermission on API ${Build.VERSION.SDK_INT}: $hasPermission")
        return hasPermission
    }

    private fun requestNotificationPermission() {
        Log.d(DEBUG_TAG, "requestNotificationPermission - START")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                Log.d(DEBUG_TAG, "Showing rationale for POST_NOTIFICATIONS.")
                Toast.makeText(this, "Notification permission is needed for hydration reminders to function.", Toast.LENGTH_LONG).show()
            }
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            Log.d(DEBUG_TAG, "requestNotificationPermission - Not needed for API ${Build.VERSION.SDK_INT}")
        }
        Log.d(DEBUG_TAG, "requestNotificationPermission - END (launcher called if applicable or not needed)")
    }

    private fun populateFieldsForEdit() {
        Log.d(DEBUG_TAG, "populateFieldsForEdit - START. existingHabitId: $existingHabitId")
        Log.d(DEBUG_TAG, "populateFieldsForEdit - allHabitsList size: ${allHabitsList.size}")
        allHabitsList.forEachIndexed { index, habit ->
            Log.v(DEBUG_TAG, "populateFieldsForEdit - Checking Habit $index: ID=${habit.id}, Type=${habit.type}, Archived=${habit.isArchived}")
        }

        val habitToEdit = allHabitsList.find { it.id == existingHabitId && !it.isArchived }

        if (habitToEdit == null) {
            Log.w(DEBUG_TAG, "populateFieldsForEdit - habitToEdit is NULL for ID '$existingHabitId'. Will call finish().")
            Toast.makeText(this, "Habit not found or is archived.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        Log.d(DEBUG_TAG, "populateFieldsForEdit - Habit found: ${habitToEdit.type}. Populating fields.")
        currentSelectedHabitType = habitToEdit.type
        textViewSelectedItem.text = habitToEdit.type
        updateValueContainer(habitToEdit.type, habitToEdit.targetValue.toString())
        updateHydrationReminderVisibility(habitToEdit.type)

        selectedSchedule = habitToEdit.schedule
        scheduleButtons.find { it.text.toString().equals(habitToEdit.schedule, ignoreCase = true) }
            ?.let {
                Log.d(DEBUG_TAG, "populateFieldsForEdit - Found schedule button: ${it.text}")
                updateScheduleButtonStates(it)
            } ?: run {
            Log.w(DEBUG_TAG, "populateFieldsForEdit - Could not find schedule button for '${habitToEdit.schedule}', defaulting to Daily.")
            updateScheduleButtonStates(buttonDaily)
        }

        selectedHour = habitToEdit.reminderTimeHour ?: -1
        selectedMinute = habitToEdit.reminderTimeMinute ?: -1
        if (selectedHour != -1 && selectedMinute != -1) {
            textViewReminderTime.text = String.format(Locale.getDefault(), "%02d:%02d", selectedHour, selectedMinute)
        } else {
            textViewReminderTime.text = getString(R.string.time)
        }
        Log.d(DEBUG_TAG, "populateFieldsForEdit - Reminder time set to: ${textViewReminderTime.text}")

        if (habitToEdit.type.equals("Hydration", ignoreCase = true)) {
            switchHydrationReminder.isChecked = habitToEdit.isReminderEnabled
            editTextReminderIntervalMinutes.setText(habitToEdit.reminderIntervalMinutes?.toString() ?: "60")
            editTextReminderIntervalMinutes.isEnabled = habitToEdit.isReminderEnabled
            Log.d(DEBUG_TAG, "populateFieldsForEdit - Hydration habit: reminderEnabled=${habitToEdit.isReminderEnabled}, interval=${editTextReminderIntervalMinutes.text}")
        } else {
            switchHydrationReminder.isChecked = false
            editTextReminderIntervalMinutes.setText("60")
            editTextReminderIntervalMinutes.isEnabled = false
            Log.d(DEBUG_TAG, "populateFieldsForEdit - Not a hydration habit. Reminder switch off.")
        }
        Log.d(DEBUG_TAG, "populateFieldsForEdit - END")
    }


    private fun showHabitTypePopupMenu(anchorView: View) {
        Log.d(DEBUG_TAG, "showHabitTypePopupMenu - START")
        val popupMenu = PopupMenu(this, anchorView)
        habitTypes.forEach { habitType -> popupMenu.menu.add(habitType) }
        popupMenu.setOnMenuItemClickListener { menuItem ->
            val newType = menuItem.title.toString()
            Log.d(DEBUG_TAG, "showHabitTypePopupMenu - MenuItem clicked: $newType")
            if (newType != currentSelectedHabitType) {
                currentSelectedHabitType = newType
                textViewSelectedItem.text = currentSelectedHabitType
                updateValueContainer(currentSelectedHabitType)
                updateHydrationReminderVisibility(currentSelectedHabitType)

                if (currentSelectedHabitType.equals("Hydration", ignoreCase = true)) {
                    val habitBeingEdited = if(isEditMode) allHabitsList.find { it.id == existingHabitId } else null
                    if (habitBeingEdited == null || !habitBeingEdited.type.equals("Hydration", ignoreCase = true)) {
                        Log.d(DEBUG_TAG, "showHabitTypePopupMenu - Switched to Hydration (new or from non-hydration). Resetting reminder fields.")
                        switchHydrationReminder.isChecked = false
                        editTextReminderIntervalMinutes.setText("60")
                        editTextReminderIntervalMinutes.isEnabled = false
                    } else {
                        Log.d(DEBUG_TAG, "showHabitTypePopupMenu - Switched to Hydration (was already Hydration). Keeping existing reminder fields.")
                    }
                }
            }
            true
        }
        popupMenu.show()
        Log.d(DEBUG_TAG, "showHabitTypePopupMenu - END")
    }

    private fun updateValueContainer(habitType: String, defaultValueOverride: String? = null) {
        Log.d(LIFECYCLE_TAG, "updateValueContainer - START. Habit type: $habitType, Override: $defaultValueOverride")
        val (unitResId, defaultVal) = when (habitType) {
            "Hydration" -> Pair(R.string.glasses_day, "8")
            "Steps" -> Pair(R.string.steps_unit, "10000")
            "Meditation" -> Pair(R.string.minutes_unit, "15")
            "Workout" -> Pair(R.string.duration_unit, "30")
            "Reading" -> Pair(R.string.pages_unit, "20")
            else -> {
                Log.w(LIFECYCLE_TAG, "updateValueContainer - Unknown habit type: $habitType. Defaulting unit and value.")
                Pair(0, "1")
            }
        }
        textViewValueUnit.text = if (unitResId != 0) getString(unitResId) else "units"
        editTextValueAmount.setText(defaultValueOverride ?: defaultVal)
        Log.d(LIFECYCLE_TAG, "updateValueContainer - Unit: ${textViewValueUnit.text}, Value: ${editTextValueAmount.text}")
        Log.d(LIFECYCLE_TAG, "updateValueContainer - END")
    }

    private fun updateScheduleButtonStates(selectedButton: Button) {
        Log.d(LIFECYCLE_TAG, "updateScheduleButtonStates - START. Selected button text: ${selectedButton.text}")
        scheduleButtons.forEach { button ->
            button.backgroundTintList = ContextCompat.getColorStateList(
                this,
                if (button.id == selectedButton.id) R.color.button_blue else R.color.button_grey
            )
        }
        selectedSchedule = selectedButton.text.toString()
        Log.d(LIFECYCLE_TAG, "updateScheduleButtonStates - selectedSchedule: $selectedSchedule, END")
    }

    private fun showTimePickerDialog() {
        Log.d(DEBUG_TAG, "showTimePickerDialog - START")
        val calendar = Calendar.getInstance()
        val initialHour = if (selectedHour != -1) selectedHour else calendar.get(Calendar.HOUR_OF_DAY)
        val initialMinute = if (selectedMinute != -1) selectedMinute else calendar.get(Calendar.MINUTE)

        TimePickerDialog(this, { _, hourOfDay, minute ->
            selectedHour = hourOfDay
            selectedMinute = minute
            textViewReminderTime.text = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute)
            Log.d(DEBUG_TAG, "showTimePickerDialog - Time selected: $selectedHour:$selectedMinute")
        }, initialHour, initialMinute, false).show()
        Log.d(DEBUG_TAG, "showTimePickerDialog - Dialog shown. END")
    }


    private fun navigateToHabitsScreen() {
        Log.d(LIFECYCLE_TAG, "navigateToHabitsScreen - START")
        val intent = Intent(this, HabitsScreen::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        finish()
        Log.d(LIFECYCLE_TAG, "navigateToHabitsScreen - END (Activity finished)")
    }

    private fun processAndSaveHabitData(): Boolean {
        Log.d(DEBUG_TAG, "processAndSaveHabitData - START")
        val habitType = currentSelectedHabitType
        val habitValueStr = editTextValueAmount.text.toString().trim()
        if (habitValueStr.isBlank()) {
            editTextValueAmount.error = "Value cannot be empty"
            Toast.makeText(this, "Please enter a target value.", Toast.LENGTH_SHORT).show()
            Log.w(DEBUG_TAG, "processAndSaveHabitData - Validation FAILED: Value empty.")
            return false
        }
        val habitValue = habitValueStr.toIntOrNull()
        if (habitValue == null || habitValue <= 0) {
            editTextValueAmount.error = "Invalid value (must be a positive number)"
            Toast.makeText(this, "Please enter a valid positive number for the target value.", Toast.LENGTH_SHORT).show()
            Log.w(DEBUG_TAG, "processAndSaveHabitData - Validation FAILED: Invalid value '$habitValueStr'.")
            return false
        }
        editTextValueAmount.error = null

        val schedule = selectedSchedule
        val unit = textViewValueUnit.text.toString()
        Log.d(DEBUG_TAG, "processAndSaveHabitData - Basic data: Type=$habitType, Value=$habitValue, Unit=$unit, Schedule=$schedule")

        var reminderEnabledForHydration = false
        var reminderIntervalForHydration: Int? = null

        if (habitType.equals("Hydration", ignoreCase = true)) {
            reminderEnabledForHydration = switchHydrationReminder.isChecked
            Log.d(DEBUG_TAG, "processAndSaveHabitData - Hydration habit. Switch isChecked: $reminderEnabledForHydration") // CHECKPOINT 1
            if (reminderEnabledForHydration) {
                val intervalStr = editTextReminderIntervalMinutes.text.toString().trim()
                reminderIntervalForHydration = intervalStr.toIntOrNull()
                if (reminderIntervalForHydration == null || reminderIntervalForHydration <= 0) {
                    editTextReminderIntervalMinutes.error = "Invalid interval (e.g., 30, 60)"
                    Toast.makeText(this, "Please enter a valid positive number of minutes for reminder interval.", Toast.LENGTH_LONG).show()
                    Log.w(DEBUG_TAG, "processAndSaveHabitData - Validation FAILED: Invalid reminder interval '$intervalStr'.")
                    return false
                }
                editTextReminderIntervalMinutes.error = null
                Log.d(DEBUG_TAG, "processAndSaveHabitData - Hydration reminder interval from EditText: $reminderIntervalForHydration minutes.") // CHECKPOINT 2
            }
        }

        var savedOrUpdatedHabit: Habit? = null
        var previousHabitTypeIfEdit: String? = null

        if (isEditMode && existingHabitId != null) {
            Log.d(DEBUG_TAG, "processAndSaveHabitData - EDIT mode. Habit ID: $existingHabitId")
            val habitIndex = allHabitsList.indexOfFirst { it.id == existingHabitId }
            if (habitIndex != -1) {
                val oldHabit = allHabitsList[habitIndex]
                previousHabitTypeIfEdit = oldHabit.type
                Log.d(DEBUG_TAG, "processAndSaveHabitData - Old habit found at index $habitIndex. Type was: $previousHabitTypeIfEdit")

                val updatedHabit = oldHabit.copy(
                    type = habitType,
                    targetValue = habitValue,
                    unit = unit,
                    schedule = schedule,
                    reminderTimeHour = if (selectedHour != -1) selectedHour else null,
                    reminderTimeMinute = if (selectedMinute != -1) selectedMinute else null,
                    isReminderEnabled = if (habitType.equals("Hydration", ignoreCase = true)) reminderEnabledForHydration else false, // CHECKPOINT 3 (for edit)
                    reminderIntervalMinutes = if (habitType.equals("Hydration", ignoreCase = true)) reminderIntervalForHydration else null, // CHECKPOINT 4 (for edit)
                    currentValue = if (oldHabit.type.equals(habitType, ignoreCase = true)) oldHabit.currentValue else 0,
                    completionHistory = if (oldHabit.type.equals(habitType, ignoreCase = true)) oldHabit.completionHistory else mutableMapOf(),
                    streak = if (oldHabit.type.equals(habitType, ignoreCase = true)) oldHabit.streak else 0
                )
                allHabitsList[habitIndex] = updatedHabit
                savedOrUpdatedHabit = updatedHabit
                Log.i(DEBUG_TAG, "processAndSaveHabitData - Habit '${updatedHabit.type}' (ID: ${updatedHabit.id}) updated locally. isReminderEnabled: ${updatedHabit.isReminderEnabled}, reminderInterval: ${updatedHabit.reminderIntervalMinutes}")
                Toast.makeText(this, "'${updatedHabit.type}' habit updated!", Toast.LENGTH_SHORT).show()
            } else {
                Log.e(DEBUG_TAG, "processAndSaveHabitData - ERROR: Habit with ID '$existingHabitId' not found in allHabitsList for update.")
                Toast.makeText(this, "Error finding habit to update. It might have been deleted.", Toast.LENGTH_SHORT).show()
                return false
            }
        } else {
            Log.d(DEBUG_TAG, "processAndSaveHabitData - NEW habit mode.")
            val newHabit = Habit(
                type = habitType,
                targetValue = habitValue,
                unit = unit,
                schedule = schedule,
                reminderTimeHour = if (selectedHour != -1) selectedHour else null,
                reminderTimeMinute = if (selectedMinute != -1) selectedMinute else null,
                isReminderEnabled = if (habitType.equals("Hydration", ignoreCase = true)) reminderEnabledForHydration else false, // CHECKPOINT 3 (for new)
                reminderIntervalMinutes = if (habitType.equals("Hydration", ignoreCase = true)) reminderIntervalForHydration else null // CHECKPOINT 4 (for new)
            )
            allHabitsList.add(newHabit)
            savedOrUpdatedHabit = newHabit
            Log.i(DEBUG_TAG, "processAndSaveHabitData - New habit '${newHabit.type}' (ID: ${newHabit.id}) created locally. isReminderEnabled: ${newHabit.isReminderEnabled}, reminderInterval: ${newHabit.reminderIntervalMinutes}")
            Toast.makeText(this, "'${newHabit.type}' habit set!", Toast.LENGTH_SHORT).show()
        }

        val success = saveAllHabitsToPrefs()
        if (success && savedOrUpdatedHabit != null) {
            Log.d(DEBUG_TAG, "processAndSaveHabitData - Habit data saved to Prefs. Processing reminders for habit ID: ${savedOrUpdatedHabit.id}, Type: ${savedOrUpdatedHabit.type}") // CHECKPOINT 5
            if (savedOrUpdatedHabit.type.equals("Hydration", ignoreCase = true)) {
                Log.d(DEBUG_TAG, "processAndSaveHabitData - Checking conditions for Hydration reminder. isReminderEnabled: ${savedOrUpdatedHabit.isReminderEnabled}, interval: ${savedOrUpdatedHabit.reminderIntervalMinutes}") // CHECKPOINT 6
                if (savedOrUpdatedHabit.isReminderEnabled && (savedOrUpdatedHabit.reminderIntervalMinutes ?: 0) > 0) {
                    Log.i(DEBUG_TAG, "SCHEDULING Hydration reminder for habit ID: ${savedOrUpdatedHabit.id}, Interval: ${savedOrUpdatedHabit.reminderIntervalMinutes} min. Habit details: $savedOrUpdatedHabit") // CHECKPOINT 7
                    HydrationReminderManager.scheduleOrUpdateReminder(applicationContext, savedOrUpdatedHabit)
                } else {
                    Log.w(DEBUG_TAG, "CANCELLING Hydration reminder for habit ID: ${savedOrUpdatedHabit.id}. Reason: isReminderEnabled=${savedOrUpdatedHabit.isReminderEnabled}, interval=${savedOrUpdatedHabit.reminderIntervalMinutes}") // CHECKPOINT 8
                    HydrationReminderManager.cancelSpecificHabitReminder(applicationContext, savedOrUpdatedHabit.id)
                }
            } else if (isEditMode && previousHabitTypeIfEdit?.equals("Hydration", ignoreCase = true) == true) {
                // This case is when a habit was Hydration and is changed to something else.
                Log.d(DEBUG_TAG, "Habit type changed FROM Hydration. Cancelling any old reminder for ${savedOrUpdatedHabit.id}")
                HydrationReminderManager.cancelSpecificHabitReminder(applicationContext, savedOrUpdatedHabit.id)
            } else {
                Log.d(DEBUG_TAG, "Not a Hydration habit or not an edit from Hydration. No reminder action needed here. Habit type: ${savedOrUpdatedHabit.type}")
            }
        } else if (!success) {
            Log.e(DEBUG_TAG, "processAndSaveHabitData - Failed to save habits to prefs. Reminders not processed.")
        }
        Log.d(DEBUG_TAG, "processAndSaveHabitData - END. Success: $success")
        return success
    }

    private fun loadAllHabits() {
        Log.d(LIFECYCLE_TAG, "loadAllHabits - START")
        try {
            val prefs = getSharedPreferences("PlayPalHabits", Context.MODE_PRIVATE)
            val gson = Gson()
            val json = prefs.getString("habits_list_json", null)
            val typeToken = object : TypeToken<MutableList<Habit>>() {}.type
            allHabitsList = gson.fromJson<MutableList<Habit>>(json, typeToken)?.mapNotNull { habit ->
                habit.copy(completionHistory = habit.completionHistory ?: mutableMapOf())
            }?.toMutableList() ?: mutableListOf()
            Log.i(LIFECYCLE_TAG, "loadAllHabits - Loaded ${allHabitsList.size} habits into allHabitsList.")
        } catch (e: Exception) {
            Log.e(LIFECYCLE_TAG, "loadAllHabits - ERROR loading full habits list", e)
            Toast.makeText(this, "Could not load existing habit data.", Toast.LENGTH_SHORT).show()
            allHabitsList = mutableListOf()
        }
        Log.d(LIFECYCLE_TAG, "loadAllHabits - END")
    }

    private fun saveAllHabitsToPrefs(): Boolean {
        Log.d(DEBUG_TAG, "saveAllHabitsToPrefs - START. Saving ${allHabitsList.size} habits.")
        try {
            val prefs = getSharedPreferences("PlayPalHabits", Context.MODE_PRIVATE)
            prefs.edit().putString("habits_list_json", Gson().toJson(allHabitsList)).apply()
            Log.i(DEBUG_TAG, "saveAllHabitsToPrefs - Saved all ${allHabitsList.size} habits to SharedPreferences.")
            return true
        } catch (e: Exception) {
            Log.e(DEBUG_TAG, "saveAllHabitsToPrefs - ERROR saving habits to SharedPreferences", e)
            Toast.makeText(this, "Failed to save habit data.", Toast.LENGTH_SHORT).show()
            return false
        }
    }

    override fun onStart() {
        Log.d(LIFECYCLE_TAG, "onStart - START")
        super.onStart()
        Log.d(LIFECYCLE_TAG, "onStart - END")
    }

    override fun onResume() {
        Log.d(LIFECYCLE_TAG, "onResume - START")
        super.onResume()
        Log.d(LIFECYCLE_TAG, "onResume - END")
    }

    override fun onPause() {
        Log.d(LIFECYCLE_TAG, "onPause - START")
        super.onPause()
        Log.d(LIFECYCLE_TAG, "onPause - END")
    }

    override fun onStop() {
        Log.d(LIFECYCLE_TAG, "onStop - START")
        super.onStop()
        Log.d(LIFECYCLE_TAG, "onStop - END")
    }

    override fun onDestroy() {
        Log.d(LIFECYCLE_TAG, "onDestroy - START")
        super.onDestroy()
        Log.d(LIFECYCLE_TAG, "onDestroy - END")
    }
}
