package com.example.wellness_pro

import android.content.Intent // Added import
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.example.wellness_pro.navbar.BaseBottomNavActivity
import com.example.wellness_pro.ui.SettingsActivity // Added import
import com.example.wellness_pro.util.UserProgressUtil

class ProfileScreen : BaseBottomNavActivity() {

    override val currentNavControllerItemId: Int
        get() = R.id.navButtonProfile

    override val layoutId: Int
        get() = R.layout.activity_profile_screen

    // avatar removed from layout
    private lateinit var textViewName: TextView
    // subtitle removed from layout; keep only title
    // private lateinit var textViewEmail: TextView

    // Progress-related views were removed from the layout. Profile progress display
    // is handled elsewhere or intentionally omitted now.

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            textViewName = findViewById(R.id.textViewScreenTitle)

            // Progress views removed from layout; skip binding them.

        } catch (e: NullPointerException) {
            Log.e("ProfileScreen", "Error finding UI elements. Check IDs in XML.", e)
            Toast.makeText(this, "Error loading profile components.", Toast.LENGTH_LONG).show()
            return
        }

        setPlaceholderProfileData()
        loadAndDisplayAppUserProgress()
        setupWindowInsets()



        // New: Edit Profile and Share Progress actions
        findViewById<View?>(R.id.buttonEditProfile)?.setOnClickListener {
            try {
                startActivity(Intent(this, SettingsActivity::class.java))
            } catch (e: Exception) {
                Toast.makeText(this, "Unable to open settings.", Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<View?>(R.id.buttonShareProgress)?.setOnClickListener {
            try {
                val userProgress = UserProgressUtil.loadUserProgress(applicationContext)
                val xpToNextDisplay = if (userProgress.xpToNextLevel == Int.MAX_VALUE) "MAX" else userProgress.xpToNextLevel.toString()
                val summary = "My wellness progress: Level ${userProgress.currentLevel}, XP ${userProgress.currentXp}/${xpToNextDisplay}. Logged with Wellness Pro."
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, summary)
                    type = "text/plain"
                }
                startActivity(Intent.createChooser(shareIntent, getString(R.string.share_mood_history)))
            } catch (e: Exception) {
                Toast.makeText(this, "Unable to share progress.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setPlaceholderProfileData()
        loadAndDisplayAppUserProgress()
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
    }

    private fun loadAndDisplayAppUserProgress() {
        val userProgress = UserProgressUtil.loadUserProgress(applicationContext)
        // Progress UI was removed from the profile layout. Keep the data loading here
        // in case other components need it in the future.
        // No UI updates are performed for progress elements.
    }
}
