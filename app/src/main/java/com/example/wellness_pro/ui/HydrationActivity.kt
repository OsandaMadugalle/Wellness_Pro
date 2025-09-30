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
    private lateinit var buttonLogOneGlass: Button
    private lateinit var buttonGoToSetHydrationSettings: Button

    private var currentDailyGoal: Int = 0
    private var currentIntakeToday: Int = 0

    companion object {
        private const val TAG = "HydrationActivity"
        // Re-using constants from SetHydrationActivity to ensure consistency
        const val PREFS_NAME = SetHydrationActivity.PREFS_NAME 
        const val KEY_GLASSES_GOAL = SetHydrationActivity.KEY_GLASSES_GOAL
        const val KEY_HYDRATION_INTAKE_PREFIX = "intake_" // Standard prefix
    }

    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        headerLayoutHydration = findViewById(R.id.headerLayoutHydration)
        progressBarHydrationStatus = findViewById(R.id.progressBarHydrationStatus)
        textViewHydrationProgressInfo = findViewById(R.id.textViewHydrationProgressInfo)
        textViewNoGoalSetHydration = findViewById(R.id.textViewNoGoalSetHydration)
        buttonLogOneGlass = findViewById(R.id.buttonLogOneGlass)
        buttonGoToSetHydrationSettings = findViewById(R.id.buttonGoToSetHydrationSettings)

        ViewCompat.setOnApplyWindowInsetsListener(headerLayoutHydration) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = insets.top)
            WindowInsetsCompat.CONSUMED
        }

        buttonLogOneGlass.setOnClickListener {
            logOneGlassConsumed()
        }

        buttonGoToSetHydrationSettings.setOnClickListener {
            startActivity(Intent(this, SetHydrationActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        loadHydrationStatus()
        updateHydrationProgressDisplay()
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
            buttonLogOneGlass.isEnabled = true

            progressBarHydrationStatus.max = currentDailyGoal
            progressBarHydrationStatus.progress = currentIntakeToday.coerceIn(0, currentDailyGoal)
            textViewHydrationProgressInfo.text = "$currentIntakeToday/$currentDailyGoal glasses"

            if (currentIntakeToday >= currentDailyGoal) {
                textViewHydrationProgressInfo.append(" - Goal Reached! 🎉")
            }

        } else {
            textViewNoGoalSetHydration.visibility = View.VISIBLE
            progressBarHydrationStatus.visibility = View.GONE
            textViewHydrationProgressInfo.visibility = View.GONE
            textViewHydrationProgressInfo.text = "0/0 glasses"
            buttonLogOneGlass.isEnabled = false // Disable logging if no goal is set
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
        editor.apply()

        Log.d(TAG, "Logged one glass. Today's intake for $todayDateString: $currentIntakeToday")
        Toast.makeText(this, "Water intake: $currentIntakeToday/$currentDailyGoal glasses", Toast.LENGTH_SHORT).show()
        
        updateHydrationProgressDisplay()
    }
}
