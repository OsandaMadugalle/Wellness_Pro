package com.example.wellness_pro

import android.content.Intent // Added import
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.example.wellness_pro.navbar.BaseBottomNavActivity
import com.example.wellness_pro.ui.SettingsActivity // Added import
import com.example.wellness_pro.util.UserProgressUtil
import com.example.wellness_pro.ui.HydrationActivity

class ProfileScreen : BaseBottomNavActivity() {

    override val currentNavControllerItemId: Int
        get() = R.id.navButtonProfile

    override val layoutId: Int
        get() = R.layout.activity_profile_screen

    // Header views
    private lateinit var imageViewAvatar: ImageView
    private lateinit var textViewName: TextView

    // Stats row views
    private lateinit var tileHabits: View
    private lateinit var tileHydration: View
    private lateinit var textViewHabitsSummary: TextView
    private lateinit var textViewHydrationSummary: TextView

    // Progress-related views were removed from the layout. Profile progress display
    // is handled elsewhere or intentionally omitted now.

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {

            imageViewAvatar = findViewById(R.id.imageViewAvatar)
            textViewName = findViewById(R.id.textViewScreenTitle)

            // Bind stats-related views
            tileHabits = findViewById(R.id.tileHabits)
            tileHydration = findViewById(R.id.tileHydration)
            textViewHabitsSummary = findViewById(R.id.textViewHabitsSummary)
            textViewHydrationSummary = findViewById(R.id.textViewHydrationSummary)

            // Progress views removed from layout; skip binding them.

        } catch (e: NullPointerException) {
            Log.e("ProfileScreen", "Error finding UI elements. Check IDs in XML.", e)
            Toast.makeText(this, "Error loading profile components.", Toast.LENGTH_LONG).show()
            return
        }

        setPlaceholderProfileData()
        loadAndDisplayAppUserProgress()
    loadAndDisplayProfileStats()
        setupWindowInsets()



        // New: Edit Profile and Share Progress actions
        findViewById<View?>(R.id.buttonEditProfile)?.setOnClickListener {
            try {
                startActivity(Intent(this, SettingsActivity::class.java))
            } catch (e: Exception) {
                Toast.makeText(this, "Unable to open settings.", Toast.LENGTH_SHORT).show()
            }
        }
        // Tile click handlers - navigate to the corresponding screens
        tileHabits.setOnClickListener {
            try {
                startActivity(Intent(this, com.example.wellness_pro.HabitsScreen::class.java))
            } catch (e: Exception) {
                Toast.makeText(this, "Unable to open Habits.", Toast.LENGTH_SHORT).show()
            }
        }

        tileHydration.setOnClickListener {
            try {
                startActivity(Intent(this, com.example.wellness_pro.ui.HydrationActivity::class.java))
            } catch (e: Exception) {
                Toast.makeText(this, "Unable to open Hydration.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setPlaceholderProfileData()
        loadAndDisplayAppUserProgress()
        loadAndDisplayProfileStats()
    }

    private fun loadAndDisplayProfileStats() {
        // Load habits summary from SharedPreferences (PlayPalHabits)
        try {
            val prefs = getSharedPreferences("PlayPalHabits", Context.MODE_PRIVATE)
            val gson = Gson()
            val json = prefs.getString("habits_list_json", null)
            var activeHabitsCount = 0
            if (!json.isNullOrEmpty()) {
                val type = object : TypeToken<MutableList<com.example.wellness_pro.Habit>>() {}.type
                val list: MutableList<com.example.wellness_pro.Habit> = gson.fromJson(json, type) ?: mutableListOf()
                // Exclude hydration habits and archived ones
                activeHabitsCount = list.count { !it.type.equals("Hydration", ignoreCase = true) && !it.isArchived }
            }
            textViewHabitsSummary.text = getString(R.string.profile_habits_summary, activeHabitsCount)
        } catch (e: Exception) {
            Log.w("ProfileScreen", "Error loading habits for profile stats: ${e.message}")
            textViewHabitsSummary.text = getString(R.string.profile_habits_summary, 0)
        }

        // Load hydration summary from Hydration prefs
        try {
            val prefs = getSharedPreferences(HydrationActivity.PREFS_NAME, Context.MODE_PRIVATE)
            val goal = prefs.getInt(HydrationActivity.KEY_GLASSES_GOAL, 0)
            val todayKey = HydrationActivity.KEY_HYDRATION_INTAKE_PREFIX + java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
            val intake = prefs.getInt(todayKey, 0)
            val hydrationSummary = if (goal > 0) "$intake/$goal" else getString(R.string.no_goal_set_short)
            textViewHydrationSummary.text = getString(R.string.profile_hydration_summary, hydrationSummary)
        } catch (e: Exception) {
            Log.w("ProfileScreen", "Error loading hydration for profile stats: ${e.message}")
            textViewHydrationSummary.text = getString(R.string.profile_hydration_summary, getString(R.string.no_goal_set_short))
        }
    }

    private fun setupWindowInsets() {
        // Preserve original header top padding and add status bar inset on top of it
        findViewById<View?>(R.id.headerLayout)?.let { header ->
            if (header.getTag(R.id.tag_padding_top) == null) header.setTag(R.id.tag_padding_top, header.paddingTop)
            ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
                val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
                val originalTop = v.getTag(R.id.tag_padding_top) as? Int ?: v.paddingTop
                v.updatePadding(top = originalTop + statusBarInsets.top)
                insets
            }
        }

        // Apply left/right/bottom insets to main content but don't override its top padding
        val mainContent = findViewById<View>(R.id.main)
        if (mainContent != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainContent) { view, windowInsets ->
                val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.updatePadding(
                    left = systemBars.left,
                    right = systemBars.right,
                    bottom = systemBars.bottom
                )
                windowInsets
            }
        } else {
            Log.e("ProfileScreen", "View with ID R.id.main not found. Insets will not be applied.")
        }
    }

    private fun setPlaceholderProfileData() {
        val prefs = getSharedPreferences("AppSettingsPrefs", android.content.Context.MODE_PRIVATE)
        val displayName = prefs.getString("display_name", null)
        if (::textViewName.isInitialized) textViewName.text = displayName?.takeIf { it.isNotBlank() } ?: getString(R.string.name)
        // Optionally, we could generate initials and overlay them on the avatar. Keep default drawable for now.
    }

    private fun loadAndDisplayAppUserProgress() {
        val userProgress = UserProgressUtil.loadUserProgress(applicationContext)
        // Progress UI was removed from the profile layout. Keep the data loading here
        // in case other components need it in the future.
        // No UI updates are performed for progress elements.
    }
}
