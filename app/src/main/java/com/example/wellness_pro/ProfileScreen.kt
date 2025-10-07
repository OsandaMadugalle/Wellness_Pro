package com.example.wellness_pro

import android.content.Intent // Added import
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
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

    private lateinit var imageViewAvatar: ImageView
    private lateinit var textViewName: TextView
    private lateinit var textViewEmail: TextView

    private lateinit var textViewLevel: TextView
    private lateinit var textViewXP: TextView
    private lateinit var progressBarLevel: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            imageViewAvatar = findViewById(R.id.imageViewAvatar)
            textViewName = findViewById(R.id.textViewScreenTitle)
            textViewEmail = findViewById(R.id.textViewScreenSubtitle)

            textViewLevel = findViewById(R.id.textViewLevel)
            textViewXP = findViewById(R.id.textViewXP)
            progressBarLevel = findViewById(R.id.progressBarLevel)

        } catch (e: NullPointerException) {
            Log.e("ProfileScreen", "Error finding UI elements. Check IDs in XML.", e)
            Toast.makeText(this, "Error loading profile components.", Toast.LENGTH_LONG).show()
            return
        }

        setPlaceholderProfileData()
        loadAndDisplayAppUserProgress()
        setupWindowInsets()

        val settingsButton = findViewById<View>(R.id.buttonSettings)
        settingsButton?.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

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
        val mainContent = findViewById<View>(R.id.main)
        if (mainContent != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainContent) { view, windowInsets ->
                val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.updatePadding(
                    left = systemBars.left,
                    top = systemBars.top,
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
        if (::textViewEmail.isInitialized) textViewEmail.visibility = View.GONE
        if (::imageViewAvatar.isInitialized) {
            imageViewAvatar.setImageResource(R.drawable.ic_avatar)
        }
    }

    private fun loadAndDisplayAppUserProgress() {
        val userProgress = UserProgressUtil.loadUserProgress(applicationContext)

        if (::textViewLevel.isInitialized) {
            textViewLevel.text = getString(R.string.level_format, userProgress.currentLevel)
        }
        if (::textViewXP.isInitialized) {
            val xpToNextDisplay = if (userProgress.xpToNextLevel == Int.MAX_VALUE) "MAX" else userProgress.xpToNextLevel.toString()
            textViewXP.text = getString(R.string.xp_format, userProgress.currentXp, xpToNextDisplay)
        }
        if (::progressBarLevel.isInitialized) {
            if (userProgress.xpToNextLevel > 0 && userProgress.xpToNextLevel != Int.MAX_VALUE) {
                progressBarLevel.max = userProgress.xpToNextLevel
                progressBarLevel.progress = userProgress.currentXp.coerceIn(0, userProgress.xpToNextLevel)
            } else {
                progressBarLevel.max = 1
                progressBarLevel.progress = if (userProgress.currentLevel >= UserProgressUtil.MAX_LEVEL) 1 else 0
            }
        }
    }
}
