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
import com.example.wellness_pro.ProfileScreen
import com.example.wellness_pro.R
import com.google.android.material.button.MaterialButton

abstract class BaseBottomNavActivity : BaseActivity() { // Extends our updated BaseActivity
    @get:IdRes
    abstract val currentNavControllerItemId: Int

    private lateinit var navButtonDashboard: MaterialButton
    // private lateinit var navButtonExercise: MaterialButton // Removed
    private lateinit var navButtonHabits: MaterialButton
    // private lateinit var navButtonChallenges: MaterialButton // Removed
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
            navButtonProfile = findViewById(R.id.navButtonProfile)
            // Updated list to only include initialized buttons
            navButtons = listOf(
                navButtonDashboard, navButtonHabits, navButtonProfile
            )
        } catch (e: Exception) {
            Log.e("BaseBottomNav", "Error initializing nav buttons. Check IDs in XML.", e)
            throw IllegalStateException("Nav button ID missing or layout issue.", e)
        }
    }

    private fun setupClickListeners() {
        navButtonDashboard.setOnClickListener { navigateTo(DashboardScreen::class.java, R.id.navButtonDashboard) }
        navButtonHabits.setOnClickListener { navigateTo(HabitsScreen::class.java, R.id.navButtonHabits) }
        navButtonProfile.setOnClickListener { navigateTo(ProfileScreen::class.java, R.id.navButtonProfile) }
        // Click listeners for navButtonExercise and navButtonChallenges would be removed here if they existed
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
        // Navigate even if it's the same activity but a different conceptual destination (button ID)
        // Or if it's a different activity altogether.
        val intent = Intent(this, activityClass).apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        // Consider adding finish() if you don't want the previous activity in the back stack,
        // or overridePendingTransition for custom animations.
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
        // Assuming navBarContainer is an ID in the layout file of the Activity that includes the nav bar.
        // If navBarContainer is part of the included layout_reusable_nav_bar.xml itself, this is fine.
        findViewById<FrameLayout?>(R.id.navBarContainerBottom)?.let { container -> // Changed ID to navBarContainerBottom to match Dashboard XML
            ViewCompat.setOnApplyWindowInsetsListener(container) { view, windowInsets ->
                val navInsets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
                view.updatePadding(bottom = navInsets.bottom)
                windowInsets
            }
        } ?: Log.e("BaseBottomNav", "navBarContainerBottom (expected for nav bar insets) not found.")
    }
}
