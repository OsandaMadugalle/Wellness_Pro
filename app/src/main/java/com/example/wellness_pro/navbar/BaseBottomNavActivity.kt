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
import com.example.wellness_pro.ui.HydrationActivity // ADDED
import com.example.wellness_pro.ui.MoodHistoryActivity
import com.example.wellness_pro.ProfileScreen
import com.example.wellness_pro.R
import com.google.android.material.button.MaterialButton

abstract class BaseBottomNavActivity : BaseActivity() {
    @get:IdRes
    abstract val currentNavControllerItemId: Int

    private lateinit var navButtonDashboard: MaterialButton
    private lateinit var navButtonHabits: MaterialButton
    private lateinit var navButtonHydration: MaterialButton // ADDED
    private lateinit var navButtonMoodJournal: MaterialButton
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
            navButtonHydration = findViewById(R.id.navButtonHydration) // ADDED
            navButtonMoodJournal = findViewById(R.id.navButtonMoodJournal)
            navButtonProfile = findViewById(R.id.navButtonProfile)

            navButtons = listOf( // UPDATED list
                navButtonDashboard, navButtonHabits, navButtonHydration, navButtonMoodJournal, navButtonProfile
            )
        } catch (e: Exception) {
            Log.e("BaseBottomNav", "Error initializing nav buttons. Check IDs in XML (navButtonHydration might be new).", e)
            throw IllegalStateException("Nav button ID missing or layout issue.", e)
        }
    }

    private fun setupClickListeners() {
        navButtonDashboard.setOnClickListener { navigateTo(DashboardScreen::class.java, R.id.navButtonDashboard) }
        navButtonHabits.setOnClickListener { navigateTo(HabitsScreen::class.java, R.id.navButtonHabits) }
        navButtonHydration.setOnClickListener { navigateTo(HydrationActivity::class.java, R.id.navButtonHydration) } // ADDED
        navButtonMoodJournal.setOnClickListener { navigateTo(MoodHistoryActivity::class.java, R.id.navButtonMoodJournal) }
        navButtonProfile.setOnClickListener { navigateTo(ProfileScreen::class.java, R.id.navButtonProfile) }
    }

    private fun setupReusableBottomNavigationBar() {
        initializeNavButtons()
        setupClickListeners()
        updateNavButtonStates()
    }

    private fun <T : AppCompatActivity> navigateTo(activityClass: Class<T>, @IdRes destinationButtonId: Int) {
        // Check if the current activity IS the target activity AND the currentNavControllerItemId matches the button pressed.
        // This prevents re-navigating if already on the correct screen AND the correct tab is "active".
        // If currentNavControllerItemId doesn't match, it means we are on the activity, but a different tab was last "active", so we allow navigation to "refresh" the state.
        if (this::class.java == activityClass && currentNavControllerItemId == destinationButtonId) {
            Log.d("BaseBottomNav", "Already on ${activityClass.simpleName} and its designated tab ID $destinationButtonId is active. No navigation.")
            return
        }
        val intent = Intent(this, activityClass).apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT // or Intent.FLAG_ACTIVITY_SINGLE_TOP
            // If you want to ensure the activity is always brought to front without creating a new instance
            // and potentially clear activities on top of it in the task, consider:
            // flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
        // If you want to finish the current activity when navigating to a top-level tab, you might add finish() here.
        // However, for REORDER_TO_FRONT, this is often not needed.
        // If activityClass is one of the main tabs, and you want to clear other non-main activities above it:
        // overridePendingTransition(0, 0) // Optional: disable transition animation
    }

    private fun updateNavButtonStates() {
        if (!::navButtons.isInitialized) {
            try {
                initializeNavButtons() // Attempt to re-initialize if not ready
            } catch (e: Exception) {
                Log.e("BaseBottomNav", "Failed to re-init buttons for state update. Resources exist? Nav buttons list was uninitialized.",e);
                return // Exit if still cannot initialize
            }
        }

        var foundActiveButtonInLoop = false
        navButtons.forEach { button ->
            val isActive = (button.id == currentNavControllerItemId)
            if(isActive) foundActiveButtonInLoop = true

            button.isSelected = isActive // Standard Android state for selection
            // Custom styling for active/inactive state
            val iconTintColor = if (isActive) R.color.active_nav_icon_color else R.color.inactive_nav_icon_color
            val textColorVal = if (isActive) R.color.active_nav_text_color else R.color.inactive_nav_text_color
            try {
                button.setIconTintResource(iconTintColor)
                button.setTextColor(ContextCompat.getColor(this, textColorVal))
            } catch (e: Exception) {
                Log.e("BaseBottomNav", "Error setting button colors for button ID ${button.id}. Resources exist? Current Item ID: $currentNavControllerItemId", e)
                // It's possible for resources to be unavailable during certain lifecycle moments or if IDs are wrong.
            }
        }
         if (!foundActiveButtonInLoop && navButtons.isNotEmpty()) { // Added check for navButtons.isNotEmpty()
            Log.w("BaseBottomNav", "currentNavControllerItemId ($currentNavControllerItemId) did not match any button in navButtons. No button will be highlighted as active.")
        }
    }

    private fun setupNavBarInsets() {
        findViewById<FrameLayout?>(R.id.navBarContainerBottom)?.let { container ->
            ViewCompat.setOnApplyWindowInsetsListener(container) { view, windowInsets ->
                val navInsets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
                view.updatePadding(bottom = navInsets.bottom.coerceAtLeast(0)) // Ensure padding is not negative
                windowInsets
            }
        } ?: Log.e("BaseBottomNav", "navBarContainerBottom (expected for nav bar insets) not found in the current layout. Insets not applied.")
    }
}
