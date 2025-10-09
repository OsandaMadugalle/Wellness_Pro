package com.example.wellness_pro.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.example.wellness_pro.R
import com.example.wellness_pro.navbar.BaseBottomNavActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HydrationActivity : BaseBottomNavActivity() {

    override val layoutId: Int
        get() = R.layout.activity_hydration

    override val currentNavControllerItemId: Int
        get() = R.id.navButtonHydration

    private lateinit var headerLayoutHydration: LinearLayout
    private lateinit var progressBarHydrationStatus: ProgressBar
    private lateinit var textViewHydrationProgressInfo: TextView
    private lateinit var textViewNoGoalSetHydration: TextView
    private lateinit var textViewNextReminderLabel: TextView
    private lateinit var buttonAddOneGlass: Button
    private lateinit var buttonRemoveOneGlass: Button
    private lateinit var buttonGoToSetHydrationSettings: Button
    private lateinit var textViewWeeklySummary: TextView

    private var currentDailyGoal: Int = 0
    private var currentIntakeToday: Int = 0

    companion object {
        private const val TAG = "HydrationActivity"
        const val PREFS_NAME = SetHydrationActivity.PREFS_NAME
        const val KEY_GLASSES_GOAL = SetHydrationActivity.KEY_GLASSES_GOAL
        const val KEY_HYDRATION_INTAKE_PREFIX = "intake_"
        private const val KEY_REMINDER_TIMES = "reminderTimesSet"
    }

    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Initialize views
        headerLayoutHydration = findViewById(R.id.headerLayoutHydration)
        progressBarHydrationStatus = findViewById(R.id.progressBarHydrationStatus)
        textViewHydrationProgressInfo = findViewById(R.id.textViewHydrationProgressInfo)
        textViewNoGoalSetHydration = findViewById(R.id.textViewNoGoalSetHydration)
        textViewNextReminderLabel = findViewById(R.id.textViewNextReminderLabel)
        buttonAddOneGlass = findViewById(R.id.buttonAddOneGlass)
        buttonRemoveOneGlass = findViewById(R.id.buttonRemoveOneGlass)
        buttonGoToSetHydrationSettings = findViewById(R.id.buttonGoToSetHydrationSettings)
        textViewWeeklySummary = findViewById(R.id.textViewWeeklySummary)

        buttonAddOneGlass.setOnClickListener {
            logOneGlassConsumed()
        }

        buttonRemoveOneGlass.setOnClickListener {
            removeOneGlassConsumed()
        }

        buttonGoToSetHydrationSettings.setOnClickListener {
            startActivity(Intent(this, SetHydrationActivity::class.java))
        }

        // Preserve original top padding and add system bars inset (matches HabitsScreen behavior)
        if (headerLayoutHydration.getTag(R.id.tag_padding_top) == null) {
            headerLayoutHydration.setTag(R.id.tag_padding_top, headerLayoutHydration.paddingTop)
        }
        ViewCompat.setOnApplyWindowInsetsListener(headerLayoutHydration) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val originalTopPadding = (view.getTag(R.id.tag_padding_top) as? Int) ?: view.paddingTop
            view.updatePadding(top = insets.top + originalTopPadding)
            WindowInsetsCompat.CONSUMED
        }
    }

    override fun onResume() {
        super.onResume()
        loadHydrationStatus()
        updateHydrationProgressDisplay()
        displayNextReminderTime()
        displayWeeklySummary()
    }

    private fun getCurrentHydrationDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun loadHydrationStatus() {
        currentDailyGoal = sharedPreferences.getInt(KEY_GLASSES_GOAL, 0)
        val todayDateString = getCurrentHydrationDateString()
        val intakeKey = KEY_HYDRATION_INTAKE_PREFIX + todayDateString
        currentIntakeToday = sharedPreferences.getInt(intakeKey, 0)
        Log.d(TAG, "Hydration status loaded. Goal: $currentDailyGoal, Today's Intake: $currentIntakeToday")
    }

    private fun updateHydrationProgressDisplay() {
        if (currentDailyGoal > 0) {
            textViewNoGoalSetHydration.visibility = View.GONE
            progressBarHydrationStatus.visibility = View.VISIBLE
            textViewHydrationProgressInfo.visibility = View.VISIBLE
            buttonAddOneGlass.isEnabled = true
            buttonRemoveOneGlass.isEnabled = currentIntakeToday > 0


            progressBarHydrationStatus.max = currentDailyGoal
            progressBarHydrationStatus.progress = currentIntakeToday.coerceIn(0, currentDailyGoal)
            textViewHydrationProgressInfo.text = "$currentIntakeToday/$currentDailyGoal glasses"

            if (currentIntakeToday >= currentDailyGoal) {
                textViewHydrationProgressInfo.append(" - Goal Reached! 🎉")
                markGoalMetForToday()
            }

        } else {
            textViewNoGoalSetHydration.visibility = View.VISIBLE
            progressBarHydrationStatus.visibility = View.GONE
            textViewHydrationProgressInfo.visibility = View.GONE
            textViewHydrationProgressInfo.text = "0/0 glasses"
            buttonAddOneGlass.isEnabled = false // Disable logging if no goal is set
            buttonRemoveOneGlass.isEnabled = false
        }
    }

    private fun displayNextReminderTime() {
        val reminderTimes = sharedPreferences.getStringSet(KEY_REMINDER_TIMES, emptySet())?.toList()?.sorted()
        if (reminderTimes.isNullOrEmpty()) {
            textViewNextReminderLabel.visibility = View.GONE
            return
        }

        val currentTime = Calendar.getInstance()

        // helper: parse strings like "05:30 AM" or "5:30 pm" into hour/minute (24h)
        fun parseHourMinute(timeStr: String): Pair<Int, Int>? {
            // Matches H or HH : MM with optional AM/PM
            val regex = Regex("\\s*(\\d{1,2}):(\\d{2})\\s*([APap][Mm])?\\s*")
            val match = regex.matchEntire(timeStr)
            if (match == null) return null
            var hour = match.groupValues[1].toIntOrNull() ?: return null
            val minute = match.groupValues[2].toIntOrNull() ?: return null
            val ampm = match.groupValues[3].uppercase()
            if (ampm.isNotEmpty()) {
                if (ampm == "AM" && hour == 12) hour = 0
                else if (ampm == "PM" && hour != 12) hour += 12
            }
            // bounds check
            if (hour !in 0..23 || minute !in 0..59) return null
            return Pair(hour, minute)
        }

        // Build a sorted list of (timeString -> millisToday) so ordering is by actual time
        val timesWithMillis = mutableListOf<Pair<String, Long>>()
        for (time in reminderTimes) {
            val hm = parseHourMinute(time) ?: continue
            val reminderCalendar = Calendar.getInstance()
            reminderCalendar.set(Calendar.HOUR_OF_DAY, hm.first)
            reminderCalendar.set(Calendar.MINUTE, hm.second)
            reminderCalendar.set(Calendar.SECOND, 0)
            reminderCalendar.set(Calendar.MILLISECOND, 0)
            timesWithMillis.add(Pair(time, reminderCalendar.timeInMillis))
        }

        if (timesWithMillis.isEmpty()) {
            textViewNextReminderLabel.visibility = View.GONE
            return
        }

        timesWithMillis.sortBy { it.second }

        var nextReminderTime: String? = null
        for ((timeStr, millis) in timesWithMillis) {
            if (millis > currentTime.timeInMillis) {
                nextReminderTime = timeStr
                break
            }
        }

        if (nextReminderTime != null) {
            textViewNextReminderLabel.text = "Next reminder at: $nextReminderTime"
            textViewNextReminderLabel.visibility = View.VISIBLE
        } else {
            // If all reminders for today have passed, show the first reminder for tomorrow (earliest by time)
            val earliestTomorrow = timesWithMillis.first().first
            textViewNextReminderLabel.text = "Next reminder at: $earliestTomorrow (tomorrow)"
            textViewNextReminderLabel.visibility = View.VISIBLE
        }
    }

    private fun logOneGlassConsumed() {
        if (currentDailyGoal <= 0) {
            Toast.makeText(this, "Please set a hydration goal first in settings.", Toast.LENGTH_SHORT).show()
            return
        }

        val editor = sharedPreferences.edit()
        val todayDateString = getCurrentHydrationDateString()
        val intakeKey = KEY_HYDRATION_INTAKE_PREFIX + todayDateString

        currentIntakeToday++
        editor.putInt(intakeKey, currentIntakeToday)
        editor.putLong("last_drink_timestamp", System.currentTimeMillis())
        editor.apply()

        Log.d(TAG, "Logged one glass. Today's intake for $todayDateString: $currentIntakeToday")
        Toast.makeText(this, "Water intake: $currentIntakeToday/$currentDailyGoal glasses", Toast.LENGTH_SHORT).show()

        updateHydrationProgressDisplay()
        displayWeeklySummary()
    }

    private fun removeOneGlassConsumed() {
        if (currentIntakeToday > 0) {
            val editor = sharedPreferences.edit()
            val todayDateString = getCurrentHydrationDateString()
            val intakeKey = KEY_HYDRATION_INTAKE_PREFIX + todayDateString

            currentIntakeToday--
            editor.putInt(intakeKey, currentIntakeToday)
            editor.apply()

            Log.d(TAG, "Removed one glass. Today's intake for $todayDateString: $currentIntakeToday")
            Toast.makeText(this, "Water intake: $currentIntakeToday/$currentDailyGoal glasses", Toast.LENGTH_SHORT).show()

            updateHydrationProgressDisplay()
            displayWeeklySummary()
        }
    }

    private fun markGoalMetForToday() {
        if (currentDailyGoal <= 0) return
        val todayDateString = getCurrentHydrationDateString()
        val goalMetKey = getGoalMetKey(todayDateString)
        if (!sharedPreferences.getBoolean(goalMetKey, false)) {
            sharedPreferences.edit().putBoolean(goalMetKey, true).apply()
            Log.d(TAG, "Marked goal met for $todayDateString")
        }
    }

    private fun getGoalMetKey(dateString: String): String {
        return "goal_met_" + dateString
    }

    private fun displayWeeklySummary() {
        val days = 7
        val count = getGoalMetCountForPastDays(days)
        textViewWeeklySummary.text = getString(R.string.weekly_goal_summary, count, days)
        textViewWeeklySummary.visibility = View.VISIBLE
    }

    private fun getGoalMetCountForPastDays(days: Int): Int {
        var count = 0
        val cal = Calendar.getInstance()
        for (i in 0 until days) {
            val dateString = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
            if (sharedPreferences.getBoolean(getGoalMetKey(dateString), false)) count++
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return count
    }
}
