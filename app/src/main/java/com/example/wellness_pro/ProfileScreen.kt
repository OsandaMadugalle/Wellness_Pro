package com.example.wellness_pro

// import android.content.Intent // No longer needed if EditProfileScreen is removed
import android.os.Bundle
import android.util.Log
import android.view.View
// import android.widget.ImageButton // No longer finding settingsButton by this type if listener is removed
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
// import com.bumptech.glide.Glide // Not strictly needed if only using placeholders
import com.example.wellness_pro.navbar.BaseBottomNavActivity
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
            Log.e("ProfileScreen", "Error finding profile UI elements. Check IDs in XML.", e)
            Toast.makeText(this, "Error loading profile components.", Toast.LENGTH_LONG).show()
        }

        setPlaceholderProfileData()
        loadAndDisplayAppUserProgress()
        setupWindowInsets()

        // Settings Button Click Listener - REMOVED
        // val settingsButton = findViewById<ImageButton>(R.id.buttonSettings)
        // settingsButton?.setOnClickListener {
        //     val intent = Intent(this, EditProfileScreen::class.java)
        //     startActivity(intent)
        // } ?: Log.w("ProfileScreen", "Button with ID R.id.buttonSettings not found or listener not set.")
        // You might want to hide or remove the settingsButton from the XML if it has no function
        val settingsButton = findViewById<View>(R.id.buttonSettings) // Find it as a generic View
        settingsButton?.setOnClickListener(null) // Remove any existing listener
        // settingsButton?.visibility = View.GONE // Optionally hide it

        val logoutButton = findViewById<View>(R.id.buttonLogout)
        logoutButton?.visibility = View.GONE
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
        if (::textViewName.isInitialized) textViewName.text = getString(R.string.name) 
        if (::textViewEmail.isInitialized) textViewEmail.text = getString(R.string.mail)
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
