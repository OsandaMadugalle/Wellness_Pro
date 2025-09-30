package com.example.wellness_pro

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.wellness_pro.navbar.BaseBottomNavActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HabitsScreen : BaseBottomNavActivity() {

    override val layoutId: Int
        get() = R.layout.activity_habits_screen

    override val currentNavControllerItemId: Int
        get() = R.id.navButtonHabits

    private lateinit var buttonNavigateToSetHabitScreen: Button
    private lateinit var habitItemsContainer: LinearLayout
    private lateinit var habitStatusLayout: View
    private lateinit var textViewHabitStatusTitle: TextView
    private lateinit var progressBarHabit: ProgressBar
    private lateinit var textViewProgressPercentage: TextView
    private lateinit var textViewDuration: TextView
    private lateinit var textViewXp: TextView
    private lateinit var dayOfWeekImageViews: List<ImageView>

    private var habitsList: MutableList<Habit> = mutableListOf()
    private var selectedHabit: Habit? = null

    // Removed "Hydration" from this list
    private val countableHabitTypes = listOf("Steps", "Reading", "Workout")

    private val stepsUpdatedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == DashboardScreen.ACTION_STEPS_UPDATED) {
                val steps = intent.getIntExtra(DashboardScreen.EXTRA_CURRENT_STEPS, -1)
                val stepsDate = intent.getStringExtra(DashboardScreen.EXTRA_STEPS_DATE)
                val todayString = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

                Log.d("HabitsScreen", "Received ACTION_STEPS_UPDATED: $steps steps for $stepsDate")

                if (steps != -1 && stepsDate == todayString) {
                    val stepsHabit = habitsList.find { it.type.equals("Steps", ignoreCase = true) && !it.isArchived }
                    stepsHabit?.let { habit ->
                        if (habit.currentValue != steps) {
                            Log.i("HabitsScreen", "Broadcast updating Steps. Old: ${habit.currentValue}, New: $steps")
                            val wasCompleted = isHabitCompletedToday(habit)
                            habit.currentValue = steps
                            if (habit.currentValue >= habit.targetValue && !wasCompleted) {
                                markHabitAsCompleteBySystem(habit)
                            } else if (habit.currentValue < habit.targetValue && wasCompleted) {
                                unmarkHabitBySystem(habit)
                            }
                            saveHabits() // Save after any potential modification
                            if (selectedHabit?.id == habit.id) updateBottomSummaryPanel()
                            updateSpecificHabitItemUI(habit)
                        }
                    }
                } else {
                    Log.w("HabitsScreen", "Steps update ignored: date mismatch ($stepsDate vs $todayString) or invalid steps ($steps).")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState) // Base class handles setContentView
        initializeViews()
        setupInsets()
        setupNavigation()

        dayOfWeekImageViews = listOfNotNull(
            findViewById(R.id.imageViewDayStatusS), findViewById(R.id.imageViewDayStatusM),
            findViewById(R.id.imageViewDayStatusT), findViewById(R.id.imageViewDayStatusW),
            findViewById(R.id.imageViewDayStatusTh), findViewById(R.id.imageViewDayStatusF),
            findViewById(R.id.imageViewDayStatusSa)
        )
        if (dayOfWeekImageViews.size != 7) {
            Log.e("HabitsScreen", "Could not find all 7 dayOfWeekImageViews in layout. Found: ${dayOfWeekImageViews.size}")
        }
        Log.d("HabitsScreen", "onCreate completed.")
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            stepsUpdatedReceiver, IntentFilter(DashboardScreen.ACTION_STEPS_UPDATED)
        )
        Log.d("HabitsScreen", "Registered stepsUpdatedReceiver.")

        loadHabits() // This will also update streaks
        updateStepsHabitFromDashboardData() // Sync with dashboard potentially before display
        displayHabits() // This now uses potentially updated habits list
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(stepsUpdatedReceiver)
        Log.d("HabitsScreen", "Unregistered stepsUpdatedReceiver.")
    }

    private fun initializeViews() {
        try {
            habitItemsContainer = findViewById(R.id.habitItemsContainer)
            buttonNavigateToSetHabitScreen = findViewById(R.id.buttonSetHabit)
            habitStatusLayout = findViewById(R.id.habitStatusLayout)
            textViewHabitStatusTitle = findViewById(R.id.textViewHabitStatusTitle)
            progressBarHabit = findViewById(R.id.progressBarHabit)
            textViewProgressPercentage = findViewById(R.id.textViewProgressPercentage)
            textViewDuration = findViewById(R.id.textViewDuration)
            textViewXp = findViewById(R.id.textViewXp)

            if (!this::buttonNavigateToSetHabitScreen.isInitialized) {
                Log.e("HabitsScreen", "ERROR: buttonNavigateToSetHabitScreen NOT initialized after findViewById. Check R.id.buttonSetHabit in XML.")
            } else {
                Log.d("HabitsScreen", "buttonNavigateToSetHabitScreen initialized successfully.")
            }
        } catch (e: Exception) {
            Log.e("HabitsScreen", "Error in initializeViews: ${e.message}", e)
            Toast.makeText(this, "Error initializing screen layout. Critical views might be missing.", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupInsets() {
        val mainContent = findViewById<View?>(R.id.main_content_habits)

        mainContent?.let {
            findViewById<View?>(R.id.headerLayout)?.let { header ->
                if (header.getTag(R.id.tag_padding_top) == null) header.setTag(R.id.tag_padding_top, header.paddingTop)
            }
            findViewById<View?>(R.id.navBarContainer)?.let { navBar -> // This ID might be navBarContainerBottom if you standardized
                if (navBar.getTag(R.id.tag_padding_bottom) == null) navBar.setTag(R.id.tag_padding_bottom, navBar.paddingBottom)
            }

            ViewCompat.setOnApplyWindowInsetsListener(it) { _, windowInsets ->
                val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                val imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime())

                findViewById<View?>(R.id.headerLayout)?.apply {
                    val originalTopPadding = getTag(R.id.tag_padding_top) as? Int ?: paddingTop
                    updatePadding(top = systemBars.top + originalTopPadding)
                }

                findViewById<View?>(R.id.scrollViewHabits)?.apply {
                    updatePadding(bottom = kotlin.comparisons.maxOf(systemBars.bottom, imeInsets.bottom))
                }
                WindowInsetsCompat.CONSUMED
            }
        } ?: Log.e("HabitsScreen", "View for insets (R.id.main_content_habits) not found. Add this ID to your root layout in activity_habits_screen.xml.")
    }


    private fun setupNavigation() {
        if (this::buttonNavigateToSetHabitScreen.isInitialized) {
            buttonNavigateToSetHabitScreen.setOnClickListener {
                Log.d("HabitsScreen", "buttonNavigateToSetHabitScreen CLICKED!")
                startActivity(Intent(this, SetHabitScreen::class.java))
            }
        } else {
            Log.e("HabitsScreen", "setupNavigation: buttonNavigateToSetHabitScreen was NOT initialized. Listener NOT set.")
        }
    }

    private fun normalizeTimestampToDay(timestamp: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    private fun updateStepsHabitFromDashboardData() {
        val stepsHabit = habitsList.find { it.type.equals("Steps", ignoreCase = true) && !it.isArchived }
        stepsHabit?.let { habit ->
            try {
                val dashboardStepPrefs = getSharedPreferences(DashboardScreen.STEP_PREFS_NAME, Context.MODE_PRIVATE)
                val todayString = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val lastStepSaveDateByDashboard = dashboardStepPrefs.getString(DashboardScreen.KEY_LAST_STEP_SAVE_DATE, "")

                if (todayString == lastStepSaveDateByDashboard) {
                    val currentDailyStepsFromDashboard = dashboardStepPrefs.getInt(DashboardScreen.KEY_CURRENT_DAILY_STEPS, 0)
                    if (habit.currentValue != currentDailyStepsFromDashboard) {
                        Log.d("HabitsScreen", "Syncing Steps from Prefs. Old: ${habit.currentValue}, New: $currentDailyStepsFromDashboard")
                        val wasCompleted = isHabitCompletedToday(habit)
                        habit.currentValue = currentDailyStepsFromDashboard
                        if (habit.currentValue >= habit.targetValue && !wasCompleted) {
                            markHabitAsCompleteBySystem(habit)
                        } else if (habit.currentValue < habit.targetValue && wasCompleted) {
                            unmarkHabitBySystem(habit)
                        } else {
                            saveHabits()
                        }
                    }
                } else {
                    if (habit.currentValue != 0 || isHabitCompletedToday(habit)) {
                        Log.d("HabitsScreen", "Dashboard step data not for today ($lastStepSaveDateByDashboard). Resetting Steps habit currentValue to 0 and unmarking if needed.")
                        habit.currentValue = 0
                        if (isHabitCompletedToday(habit)) {
                            unmarkHabitBySystem(habit)
                        } else {
                            saveHabits()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("HabitsScreen", "Error in updateStepsHabitFromDashboardData: ", e)
            }
        }
    }

    private fun markHabitAsCompleteBySystem(habit: Habit) {
        val todayNormalized = normalizeTimestampToDay(System.currentTimeMillis())
        if (habit.completionHistory[todayNormalized] != true) {
            habit.lastCompletionTimestamp = System.currentTimeMillis()
            habit.completionHistory[todayNormalized] = true
            if (habit.targetValue > 0) {
                habit.currentValue = habit.targetValue
            }
            updateStreak(habit)
            saveHabits()
            Log.d("HabitsScreen", "Habit '${habit.type}' marked complete by system. Streak: ${habit.streak}")
        }
    }

    private fun unmarkHabitBySystem(habit: Habit) {
        val todayNormalized = normalizeTimestampToDay(System.currentTimeMillis())
        if (habit.completionHistory[todayNormalized] == true) {
            habit.completionHistory.remove(todayNormalized)
            if (habit.targetValue > 0) {
                habit.currentValue = 0 // For countable habits like steps, reset to 0 if unmarked.
            }
            updateStreak(habit)
            saveHabits()
            Log.d("HabitsScreen", "Habit '${habit.type}' unmarked by system. Streak: ${habit.streak}")
        }
    }

    private fun loadHabits() {
        try {
            val prefs = getSharedPreferences("PlayPalHabits", Context.MODE_PRIVATE)
            val gson = Gson()
            val json = prefs.getString("habits_list_json", null)
            val typeToken = object : TypeToken<MutableList<Habit>>() {}.type

            val loadedHabits = if (json != null) {
                gson.fromJson<MutableList<Habit>>(json, typeToken)?.mapNotNull { habit ->
                    val history = habit.completionHistory ?: mutableMapOf()
                    val updatedHabit = habit.copy(completionHistory = history)
                    updateStreak(updatedHabit)
                    if (updatedHabit.targetValue > 0) {
                        if (isHabitCompletedToday(updatedHabit)) {
                            if (updatedHabit.currentValue < updatedHabit.targetValue) updatedHabit.currentValue = updatedHabit.targetValue
                        } else {
                            // Removed special handling for "Steps" here as it's now handled by updateStepsHabitFromDashboardData or general logic
                        }
                    }
                    updatedHabit
                }?.toMutableList() ?: mutableListOf()
            } else {
                mutableListOf()
            }
            // Filter out archived habits AND habits of type "Hydration"
            habitsList = loadedHabits
                .filter { !it.isArchived && !it.type.equals("Hydration", ignoreCase = true) }
                .toMutableList()

            Log.d("HabitsScreen", "Loaded ${habitsList.size} active (and non-hydration) habits.")

            if (selectedHabit != null && !habitsList.any { it.id == selectedHabit!!.id }) {
                // If current selectedHabit was Hydration (or archived) and is now filtered out, reset selection
                selectedHabit = null
            }
            if (selectedHabit == null && habitsList.isNotEmpty()) {
                selectedHabit = habitsList.firstOrNull()
            }

        } catch (e: Exception) {
            Log.e("HabitsScreen", "Error loading habits", e)
            Toast.makeText(this, "Could not load habits.", Toast.LENGTH_SHORT).show()
            habitsList.clear()
            selectedHabit = null
        }
    }

    private fun incrementHabitProgress(habit: Habit) {
        if (habit.targetValue <= 0 || habit.currentValue >= habit.targetValue) {
            return
        }

        habit.currentValue++
        Log.d("HabitsScreen", "Incremented '${habit.type}' to ${habit.currentValue}/${habit.targetValue}")

        if (habit.currentValue >= habit.targetValue) {
            if (!isHabitCompletedToday(habit)) {
                val todayNorm = normalizeTimestampToDay(System.currentTimeMillis())
                habit.completionHistory[todayNorm] = true
                habit.lastCompletionTimestamp = System.currentTimeMillis()
                Log.d("HabitsScreen", "Habit '${habit.type}' auto-completed by incrementing to target.")
                updateStreak(habit)
            }
        }
        saveHabits()
        updateSpecificHabitItemUI(habit)
        if (selectedHabit?.id == habit.id) {
            updateBottomSummaryPanel()
        }
    }

    private fun displayHabits() {
        habitItemsContainer.removeAllViews()
        val daysOfWeekLayout = findViewById<View?>(R.id.daysOfWeekLayout)

        if (habitsList.isEmpty()) {
            val noHabitsTv = TextView(this).apply {
                text = getString(R.string.no_habits_yet_message)
                setTextColor(ContextCompat.getColor(this@HabitsScreen, R.color.text_white))
                textSize = 16f
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                setPadding(0, 60, 0, 60)
            }
            habitItemsContainer.addView(noHabitsTv)
            habitStatusLayout.isVisible = false
            daysOfWeekLayout?.isVisible = false
            selectedHabit = null
            return
        }

        daysOfWeekLayout?.isVisible = true
        habitStatusLayout.isVisible = true

        val inflater = LayoutInflater.from(this)
        habitsList.forEach { habit -> // Hydration habits are already filtered out from habitsList
            val habitItemView = inflater.inflate(R.layout.list_item_habit, habitItemsContainer, false)
            habitItemView.tag = habit.id

            val emojiTextView = habitItemView.findViewById<TextView>(R.id.textViewHabitEmoji)
            val titleTextView = habitItemView.findViewById<TextView>(R.id.textViewHabitTitle)
            val descriptionTextView = habitItemView.findViewById<TextView>(R.id.textViewHabitDescription)
            val progressIcon = habitItemView.findViewById<ImageView>(R.id.imageViewHabitIcon)
            val editButton = habitItemView.findViewById<ImageButton>(R.id.buttonEditHabit)
            val deleteButton = habitItemView.findViewById<ImageButton>(R.id.buttonDeleteHabit)
            val incrementButton = habitItemView.findViewById<ImageButton>(R.id.buttonIncrementHabit)

            emojiTextView.text = habit.getEmojiForType()
            titleTextView.text = habit.type
            descriptionTextView.text = habit.getDescriptionText()
            updateHabitItemCompletionIcon(progressIcon, habit)

            // Generic increment button logic for countable habits (excluding Hydration now)
            if (countableHabitTypes.any { it.equals(habit.type, ignoreCase = true) } && habit.targetValue > 0) {
                 if (!isHabitCompletedToday(habit) && habit.currentValue < habit.targetValue) {
                    incrementButton.visibility = View.VISIBLE
                    incrementButton.setOnClickListener { view ->
                        incrementHabitProgress(habit)
                        if (isHabitCompletedToday(habit) || habit.currentValue >= habit.targetValue) {
                            view.visibility = View.GONE
                        }
                    }
                } else {
                    incrementButton.visibility = View.GONE
                }
            } else {
                incrementButton.visibility = View.GONE
            }

            habitItemView.setOnClickListener {
                selectedHabit = habit
                updateBottomSummaryPanel()
            }

            progressIcon.setOnClickListener { // Checkmark icon
                // Simplified logic as Hydration specific toast is no longer needed here if Hydration is filtered
                if (habit.type.equals("Steps", ignoreCase = true)) {
                    if (isHabitCompletedToday(habit) || habit.currentValue >= habit.targetValue) {
                        performToggleAction(habit, progressIcon)
                    } else {
                        Toast.makeText(this@HabitsScreen, "Walk ${habit.targetValue - habit.currentValue} more steps to complete!", Toast.LENGTH_LONG).show()
                    }
                } else {
                    performToggleAction(habit, progressIcon)
                }
                 // Re-check increment button visibility for the toggled habit
                if (countableHabitTypes.any { it.equals(habit.type, ignoreCase = true) } && habit.targetValue > 0) {
                    if (!isHabitCompletedToday(habit) && habit.currentValue < habit.targetValue) {
                        incrementButton.visibility = View.VISIBLE
                    } else {
                        incrementButton.visibility = View.GONE
                    }
                }
            }

            editButton.setOnClickListener {
                val intent = Intent(this, SetHabitScreen::class.java)
                intent.putExtra(SetHabitScreen.EXTRA_HABIT_ID, habit.id)
                startActivity(intent)
            }
            deleteButton.setOnClickListener { showDeleteConfirmationDialog(habit) }
            habitItemsContainer.addView(habitItemView)
        }

        if (selectedHabit == null && habitsList.isNotEmpty()) {
            selectedHabit = habitsList.firstOrNull()
        }
        updateBottomSummaryPanel()
    }

    private fun performToggleAction(habit: Habit, progressIconOrView: View) {
        toggleHabitCompletion(habit)
        val iconImageView = if (progressIconOrView is ImageView) progressIconOrView else progressIconOrView.findViewById(R.id.imageViewHabitIcon)
        iconImageView?.let { updateHabitItemCompletionIcon(it, habit) }
        if (habit.id == selectedHabit?.id) {
             updateBottomSummaryPanel()
        }
        // Update the specific item in the list directly as its state (and potentially description) changed
        updateSpecificHabitItemUI(habit)
    }

    private fun showDeleteConfirmationDialog(habitToDelete: Habit) {
        AlertDialog.Builder(this, R.style.AlertDialogCustom)
            .setTitle("Delete Habit")
            .setMessage("Are you sure you want to delete the habit '${habitToDelete.type}'?")
            .setPositiveButton("Delete") { dialog, _ ->
                deleteHabit(habitToDelete)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteHabit(habitToDelete: Habit) {
        val originalSelectedHabitId = selectedHabit?.id
        val allHabitsPrefs = getSharedPreferences("PlayPalHabits", Context.MODE_PRIVATE)
        val gson = Gson()
        val json = allHabitsPrefs.getString("habits_list_json", null)
        val typeToken = object : TypeToken<MutableList<Habit>>() {}.type
        var allHabitsListFromPrefs = gson.fromJson<MutableList<Habit>>(json, typeToken) ?: mutableListOf()

        val habitInFullList = allHabitsListFromPrefs.find { it.id == habitToDelete.id }
        if (habitInFullList != null) {
            habitInFullList.isArchived = true
            allHabitsPrefs.edit().putString("habits_list_json", gson.toJson(allHabitsListFromPrefs)).apply()
            Log.d("HabitsScreen", "Habit '${habitToDelete.type}' marked as archived in SharedPreferences.")
        } else {
            Log.w("HabitsScreen", "Could not find habit '${habitToDelete.type}' in SharedPreferences to archive.")
        }

        // Reload habits which will apply the Hydration filter and archived filter
        loadHabits()
        // If the deleted habit was the selected one, select the first available, or none if list is empty
        if (originalSelectedHabitId == habitToDelete.id) {
            selectedHabit = habitsList.firstOrNull()
        }
        displayHabits() // Refresh the entire UI
        Toast.makeText(this, "'${habitToDelete.type}' deleted", Toast.LENGTH_SHORT).show() // Moved after displayHabits for better UX
    }

    private fun updateHabitItemCompletionIcon(iconView: ImageView, habit: Habit) {
        if (isHabitCompletedToday(habit)) {
            iconView.setImageResource(R.drawable.ic_check_circle_filled_green)
            iconView.colorFilter = null
            iconView.contentDescription = getString(R.string.habit_status_completed)
        } else {
            iconView.setImageResource(R.drawable.ic_check_circle_outline)
            iconView.colorFilter = PorterDuffColorFilter(
                ContextCompat.getColor(this, R.color.icon_grey_light),
                PorterDuff.Mode.SRC_IN
            )
            iconView.contentDescription = getString(R.string.habit_status_incomplete)
        }
    }

    private fun updateBottomSummaryPanel() {
        val currentHabit = selectedHabit
        val daysOfWeekLayout = findViewById<View?>(R.id.daysOfWeekLayout)

        if (currentHabit == null) {
            habitStatusLayout.isVisible = false
            daysOfWeekLayout?.isVisible = false
            return
        }
        habitStatusLayout.isVisible = true
        daysOfWeekLayout?.isVisible = true

        // Since Hydration is filtered out, no need for special title handling for it here.
        var title = getString(R.string.habit_type_status, currentHabit.type)
        textViewHabitStatusTitle.text = title

        val progress = if (currentHabit.targetValue > 0) {
            (currentHabit.currentValue.toFloat() / currentHabit.targetValue.toFloat() * 100).toInt()
        } else if (isHabitCompletedToday(currentHabit)) 100 else 0
        progressBarHabit.progress = progress.coerceIn(0, 100)
        textViewProgressPercentage.text = getString(R.string.percentage_format, progress)
        textViewDuration.text = if (currentHabit.streak > 0) getString(R.string.day_streak_format, currentHabit.streak) else getString(R.string.no_active_streak)

        textViewXp.text = getString(R.string.xp_format_habit, currentHabit.streak * 5)

        updateDaysOfWeekDisplay(currentHabit)
    }

    private fun updateDaysOfWeekDisplay(habit: Habit) {
        if (dayOfWeekImageViews.isEmpty()) {
            Log.w("HabitsScreen", "dayOfWeekImageViews list is empty in updateDaysOfWeekDisplay.")
            return
        }
        val startOfWeekCal = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.SUNDAY
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }

        for (i in 0..6) {
            val dayToCheckCal = (startOfWeekCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, i) }
            val isDayDone = wasHabitCompletedOn(habit, dayToCheckCal)
            val viewIdx = mapCalendarDayToViewIndex(dayToCheckCal.get(Calendar.DAY_OF_WEEK))

            if (viewIdx in dayOfWeekImageViews.indices) {
                val imgView = dayOfWeekImageViews[viewIdx]
                if (isDayDone) {
                    imgView.setImageResource(R.drawable.fill_circle)
                    imgView.colorFilter = PorterDuffColorFilter(ContextCompat.getColor(this, R.color.button_blue), PorterDuff.Mode.SRC_IN)
                } else {
                    imgView.setImageResource(R.drawable.empty_circle)
                    imgView.colorFilter = PorterDuffColorFilter(ContextCompat.getColor(this, R.color.icon_grey_light), PorterDuff.Mode.SRC_IN)
                }

                val todayCal = Calendar.getInstance()
                imgView.alpha = when {
                    isSameDay(dayToCheckCal, todayCal) -> 1.0f
                    dayToCheckCal.before(todayCal) -> 0.6f
                    else -> 0.8f
                }
            } else {
                Log.w("HabitsScreen", "Invalid viewIndex $viewIdx for dayOfWeekImageViews size ${dayOfWeekImageViews.size} for day ${dayToCheckCal.get(Calendar.DAY_OF_WEEK_IN_MONTH)}")
            }
        }
    }

    private fun mapCalendarDayToViewIndex(calendarDayOfWeek: Int): Int {
        return when (calendarDayOfWeek) {
            Calendar.SUNDAY -> 0
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            else -> {
                Log.e("HabitsScreen", "Invalid Calendar.DAY_OF_WEEK: $calendarDayOfWeek, defaulting to 0")
                0
            }
        }
    }

    private fun isHabitCompletedToday(habit: Habit): Boolean {
        return habit.completionHistory[normalizeTimestampToDay(System.currentTimeMillis())] == true
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun toggleHabitCompletion(habit: Habit) {
        val todayNorm = normalizeTimestampToDay(System.currentTimeMillis())
        val wasDone = habit.completionHistory[todayNorm] == true

        if (wasDone) { // Becoming incomplete
            habit.completionHistory.remove(todayNorm)
            if (habit.targetValue > 0) {
                // For countable habits, reset to 0 unless it's Steps (which gets updated by broadcast)
                if (!habit.type.equals("Steps", ignoreCase = true)) {
                    habit.currentValue = 0
                }
            }
            Log.d("HabitsScreen", "Habit '${habit.type}' UNMARKED for today. CurrentValue updated to ${habit.currentValue}")
        } else { // Becoming complete
            habit.completionHistory[todayNorm] = true
            habit.lastCompletionTimestamp = System.currentTimeMillis()
            if (habit.targetValue > 0) {
                if (habit.currentValue < habit.targetValue) habit.currentValue = habit.targetValue 
            } else { // For non-countable habits, mark as 1 if it was 0, or keep if already > 0
                if (habit.currentValue == 0) habit.currentValue = 1
            }
            Log.d("HabitsScreen", "Habit '${habit.type}' MARKED for today. CurrentValue set/kept at ${habit.currentValue}")
        }
        updateStreak(habit)
        saveHabits()
    }

    private fun updateStreak(habit: Habit) {
        val history = habit.completionHistory ?: mutableMapOf()
        if (history.isEmpty()) {
            habit.streak = 0
            return
        }

        var currentStreak = 0
        val completedDaysTimestamps = history.filterValues { it }.keys.sortedDescending()

        if (completedDaysTimestamps.isEmpty()) {
            habit.streak = 0
            return
        }

        val todayNormalized = normalizeTimestampToDay(System.currentTimeMillis())
        var expectedPreviousDay = todayNormalized

        // Check if today is completed
        if (completedDaysTimestamps.firstOrNull() == expectedPreviousDay) {
            currentStreak = 1
            // Check consecutive previous days
            for (i in 1 until completedDaysTimestamps.size) {
                expectedPreviousDay = normalizeTimestampToDay(expectedPreviousDay - (24 * 60 * 60 * 1000L))
                if (completedDaysTimestamps[i] == expectedPreviousDay) {
                    currentStreak++
                } else {
                    break // Streak broken
                }
            }
        } else {
            // If today is not completed, check if yesterday was, for a streak ending yesterday
            expectedPreviousDay = normalizeTimestampToDay(todayNormalized - (24 * 60 * 60 * 1000L))
            if (completedDaysTimestamps.firstOrNull() == expectedPreviousDay) {
                currentStreak = 1
                // Check consecutive previous days from yesterday
                for (i in 1 until completedDaysTimestamps.size) {
                    expectedPreviousDay = normalizeTimestampToDay(expectedPreviousDay - (24 * 60 * 60 * 1000L))
                    if (completedDaysTimestamps[i] == expectedPreviousDay) {
                        currentStreak++
                    } else {
                        break // Streak broken
                    }
                }
            } else {
                // No completion today or yesterday that's part of a streak
                currentStreak = 0
            }
        }
        habit.streak = currentStreak
        Log.d("HabitsScreen", "Updated streak for '${habit.type}': ${habit.streak}")
    }

    private fun wasHabitCompletedOn(habit: Habit, dayCal: Calendar): Boolean {
        return habit.completionHistory[normalizeTimestampToDay(dayCal.timeInMillis)] == true
    }

    private fun saveHabits() {
        try {
            val prefs = getSharedPreferences("PlayPalHabits", Context.MODE_PRIVATE)
            val fullJson = prefs.getString("habits_list_json", null)
            val typeToken = object : TypeToken<MutableList<Habit>>() {}.type
            val masterHabitList = if (fullJson != null) {
                Gson().fromJson<MutableList<Habit>>(fullJson, typeToken) ?: mutableListOf()
            } else {
                mutableListOf()
            }

            habitsList.forEach { currentHabit -> // habitsList now excludes Hydration
                val indexInMaster = masterHabitList.indexOfFirst { it.id == currentHabit.id }
                if (indexInMaster != -1) {
                    masterHabitList[indexInMaster] = currentHabit
                } else {
                    if (!currentHabit.isArchived) { // Should not add Hydration habits back if they were filtered
                         // This safeguard might re-add a non-Hydration habit if it was somehow missed during load and is now in habitsList
                        masterHabitList.add(currentHabit)
                        Log.w("HabitsScreen", "Safeguard: Added non-archived habit '${currentHabit.type}' to master list during save, as it was missing.")
                    }
                }
            }
            // Note: This save process does NOT remove Hydration from the master list in SharedPreferences,
            // it only stops saving updates for it from this screen if it's filtered out of habitsList.
            // Archived Hydration habits also remain in masterHabitList unless explicitly removed elsewhere.
            prefs.edit().putString("habits_list_json", Gson().toJson(masterHabitList)).apply()
            Log.d("HabitsScreen", "Habits (master) list saved. Count: ${masterHabitList.size}")

        } catch (e: Exception) {
            Log.e("HabitsScreen", "Error saving habits", e)
            Toast.makeText(this, "Error saving habits.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateSpecificHabitItemUI(changedHabit: Habit) {
        // If changedHabit is Hydration, this might not find it in habitItemsContainer if it was filtered out
        // However, if called for a non-Hydration habit, it should work fine.
        for (i in 0 until habitItemsContainer.childCount) {
            val view = habitItemsContainer.getChildAt(i)
            if (view.tag == changedHabit.id) {
                val progressIcon = view.findViewById<ImageView?>(R.id.imageViewHabitIcon)
                val descriptionTextView = view.findViewById<TextView?>(R.id.textViewHabitDescription)
                val incrementButton = view.findViewById<ImageButton?>(R.id.buttonIncrementHabit)

                progressIcon?.let { updateHabitItemCompletionIcon(it, changedHabit) }
                descriptionTextView?.text = changedHabit.getDescriptionText()
                
                // Generic increment button logic update for visible habits
                if (countableHabitTypes.any { it.equals(changedHabit.type, ignoreCase = true) } && changedHabit.targetValue > 0) {
                    if (!isHabitCompletedToday(changedHabit) && changedHabit.currentValue < changedHabit.targetValue) {
                        incrementButton?.visibility = View.VISIBLE
                    } else {
                        incrementButton?.visibility = View.GONE
                    }
                } else {
                    incrementButton?.visibility = View.GONE
                }

                if (changedHabit.id == selectedHabit?.id) {
                    updateBottomSummaryPanel()
                }
                Log.d("HabitsScreen", "UI updated for specific habit: ${changedHabit.type}")
                break
            }
        }
    }
}
