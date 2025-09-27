package com.example.wellness_pro.navbar

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.FrameLayout
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.example.wellness_pro.DashboardScreen
import com.example.wellness_pro.HabitsScreen
import com.example.wellness_pro.ui.MoodHistoryActivity
import com.example.wellness_pro.ProfileScreen
import com.example.wellness_pro.R
// import com.example.wellness_pro.ui.SettingsActivity // REMOVED import
import com.google.android.material.button.MaterialButton

abstract class BaseBottomNavActivity : BaseActivity() {
    @get:IdRes
    abstract val currentNavControllerItemId: Int

    private lateinit var navButtonDashboard: MaterialButton
    private lateinit var navButtonHabits: MaterialButton
    private lateinit var navButtonMoodJournal: MaterialButton
    private lateinit var navButtonProfile: MaterialButton
    // private lateinit var navButtonSettings: MaterialButton // REMOVED

    private lateinit var navButtons: List<MaterialButton>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupReusableBottomNavigationBar()
        setupNavBarInsets()
    }

    override fun onResume() {
        super.onResume()
        updateNavButtonStates()
    }

    private fun initializeNavButtons() {
        try {
            navButtonDashboard = findViewById(R.id.navButtonDashboard)
            navButtonHabits = findViewById(R.id.navButtonHabits)
            navButtonMoodJournal = findViewById(R.id.navButtonMoodJournal)
            navButtonProfile = findViewById(R.id.navButtonProfile)
            // navButtonSettings = findViewById(R.id.navButtonSettings) // REMOVED initialization

            navButtons = listOf( // UPDATED list
                navButtonDashboard, navButtonHabits, navButtonMoodJournal, navButtonProfile
            )
        } catch (e: Exception) {
            Log.e("BaseBottomNav", "Error initializing nav buttons. Check IDs in XML.", e)
            throw IllegalStateException("Nav button ID missing or layout issue.", e)
        }
    }

    private fun setupClickListeners() {
        navButtonDashboard.setOnClickListener { navigateTo(DashboardScreen::class.java, R.id.navButtonDashboard) }
        navButtonHabits.setOnClickListener { navigateTo(HabitsScreen::class.java, R.id.navButtonHabits) }
        navButtonMoodJournal.setOnClickListener { navigateTo(MoodHistoryActivity::class.java, R.id.navButtonMoodJournal) }
        navButtonProfile.setOnClickListener { navigateTo(ProfileScreen::class.java, R.id.navButtonProfile) }
        // navButtonSettings.setOnClickListener { navigateTo(SettingsActivity::class.java, R.id.navButtonSettings) } // REMOVED listener
    }

    private fun setupReusableBottomNavigationBar() {
        initializeNavButtons()
        setupClickListeners()
        updateNavButtonStates()
    }

    private fun <T : AppCompatActivity> navigateTo(activityClass: Class<T>, @IdRes destinationButtonId: Int) {
        if (this::class.java == activityClass && currentNavControllerItemId == destinationButtonId) {
            Log.d("BaseBottomNav", "Already on ${activityClass.simpleName} which matches button ID $destinationButtonId. No navigation.")
            return
        }
        val intent = Intent(this, activityClass).apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    private fun updateNavButtonStates() {
        if (!::navButtons.isInitialized) {
            try {
                initializeNavButtons()
            } catch (e: Exception) {
                Log.e("BaseBottomNav", "Failed to re-init buttons for state update. Resources exist? Nav buttons list was uninitialized.",e);
                return
            }
        }

        var foundActiveButtonInLoop = false
        navButtons.forEach { button ->
            val isActive = (button.id == currentNavControllerItemId)
            if(isActive) foundActiveButtonInLoop = true

            button.isSelected = isActive
            val iconTintColor = if (isActive) R.color.active_nav_icon_color else R.color.inactive_nav_icon_color
            val textColorVal = if (isActive) R.color.active_nav_text_color else R.color.inactive_nav_text_color
            try {
                button.setIconTintResource(iconTintColor)
                button.setTextColor(ContextCompat.getColor(this, textColorVal))
            } catch (e: Exception) {
                Log.e("BaseBottomNav", "Error setting button colors for button ID ${button.id}. Resources exist? Current Item ID: $currentNavControllerItemId", e)
            }
        }
         if (!foundActiveButtonInLoop) {
            Log.w("BaseBottomNav", "currentNavControllerItemId ($currentNavControllerItemId) did not match any button in navButtons. No button will be highlighted as active.")
        }
    }

    private fun setupNavBarInsets() {
        findViewById<FrameLayout?>(R.id.navBarContainerBottom)?.let { container ->
            ViewCompat.setOnApplyWindowInsetsListener(container) { view, windowInsets ->
                val navInsets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
                view.updatePadding(bottom = navInsets.bottom.coerceAtLeast(0))
                windowInsets
            }
        } ?: Log.e("BaseBottomNav", "navBarContainerBottom (expected for nav bar insets) not found in the current layout. Insets not applied.")
    }
}