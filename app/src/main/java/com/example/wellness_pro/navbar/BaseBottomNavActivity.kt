// file: com/example/playpal/navbar/BaseBottomNavActivity.kt
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
import com.example.wellness_pro.ui.MoodLogActivity // Keep this for later use from MoodHistoryActivity
import com.example.wellness_pro.ui.MoodHistoryActivity // Added import
import com.example.wellness_pro.ProfileScreen
import com.example.wellness_pro.R
import com.google.android.material.button.MaterialButton

abstract class BaseBottomNavActivity : BaseActivity() { // Extends our updated BaseActivity
    @get:IdRes
    abstract val currentNavControllerItemId: Int

    private lateinit var navButtonDashboard: MaterialButton
    private lateinit var navButtonHabits: MaterialButton
    private lateinit var navButtonMoodJournal: MaterialButton // Added
    private lateinit var navButtonProfile: MaterialButton
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
            navButtonMoodJournal = findViewById(R.id.navButtonMoodJournal) // Added
            navButtonProfile = findViewById(R.id.navButtonProfile)
            // Updated list to include all initialized buttons
            navButtons = listOf(
                navButtonDashboard, navButtonHabits, navButtonMoodJournal, navButtonProfile // Added navButtonMoodJournal
            )
        } catch (e: Exception) {
            Log.e("BaseBottomNav", "Error initializing nav buttons. Check IDs in XML.", e)
            throw IllegalStateException("Nav button ID missing or layout issue.", e)
        }
    }

    private fun setupClickListeners() {
        navButtonDashboard.setOnClickListener { navigateTo(DashboardScreen::class.java, R.id.navButtonDashboard) }
        navButtonHabits.setOnClickListener { navigateTo(HabitsScreen::class.java, R.id.navButtonHabits) }
        // Changed to navigate to MoodHistoryActivity
        navButtonMoodJournal.setOnClickListener { navigateTo(MoodHistoryActivity::class.java, R.id.navButtonMoodJournal) } 
        navButtonProfile.setOnClickListener { navigateTo(ProfileScreen::class.java, R.id.navButtonProfile) }
    }

    private fun setupReusableBottomNavigationBar() {
        initializeNavButtons()
        setupClickListeners()
        updateNavButtonStates()
    }

    private fun <T : AppCompatActivity> navigateTo(activityClass: Class<T>, @IdRes destinationButtonId: Int) {
        if (this::class.java == activityClass && currentNavControllerItemId == destinationButtonId) {
            return // Already on the target screen
        }
        val intent = Intent(this, activityClass).apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    private fun updateNavButtonStates() {
        if (!::navButtons.isInitialized) {
            try { initializeNavButtons() } catch (e: Exception) {
                Log.e("BaseBottomNav", "Failed to re-init buttons for state update. Resources exist?",e);
                return
            }
        }
        navButtons.forEach { button ->
            val isActive = (button.id == currentNavControllerItemId)
            button.isSelected = isActive
            val iconTintColor = if (isActive) R.color.active_nav_icon_color else R.color.inactive_nav_icon_color
            val textColorVal = if (isActive) R.color.active_nav_text_color else R.color.inactive_nav_text_color
            try {
                button.setIconTintResource(iconTintColor)
                button.setTextColor(ContextCompat.getColor(this, textColorVal))
            } catch (e: Exception) {
                Log.e("BaseBottomNav", "Error setting button colors. Resources exist? Button ID: ${button.id}", e)
            }
        }
    }

    private fun setupNavBarInsets() {
        findViewById<FrameLayout?>(R.id.navBarContainerBottom)?.let { container ->
            ViewCompat.setOnApplyWindowInsetsListener(container) { view, windowInsets ->
                val navInsets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
                view.updatePadding(bottom = navInsets.bottom)
                windowInsets
            }
        } ?: Log.e("BaseBottomNav", "navBarContainerBottom (expected for nav bar insets) not found.")
    }
}
