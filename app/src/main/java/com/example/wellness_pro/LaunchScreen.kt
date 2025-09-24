package com.example.wellness_pro

import android.content.Context // For SharedPreferences
import android.content.Intent // For starting activities
import android.content.SharedPreferences // For SharedPreferences
import android.os.Bundle
import android.util.Log // Ensure this is imported
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LaunchScreen : AppCompatActivity() {

    private lateinit var loadingProgressBar: ProgressBar
    private lateinit var loadingTextView: TextView
    private lateinit var sharedPreferences: SharedPreferences

    companion object {
        const val PREFS_NAME = "PlayPalPrefs" // Your app's preference file name
        // const val KEY_ONBOARDING_COMPLETE = "onboarding_complete" // Removed as Login/Onboarding are deleted
        const val LIFECYCLE_TAG = "LaunchScreen_Lifecycle" // Define a common tag
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(LIFECYCLE_TAG, "onCreate - START")
        super.onCreate(savedInstanceState)
        Log.d(LIFECYCLE_TAG, "onCreate - super.onCreate CALLED")
        enableEdgeToEdge()
        Log.d(LIFECYCLE_TAG, "onCreate - enableEdgeToEdge CALLED")
        setContentView(R.layout.activity_launch_screen)
        Log.d(LIFECYCLE_TAG, "onCreate - setContentView CALLED. Current tag 'LaunchScreen' logs will follow.")

        Log.d("LaunchScreen", "onCreate called (existing log)")

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        Log.d(LIFECYCLE_TAG, "onCreate - SharedPreferences INITIALIZED")

        try {
            loadingProgressBar = findViewById(R.id.loading)
            loadingTextView = findViewById(R.id.textLoading)
            Log.d(LIFECYCLE_TAG, "onCreate - ProgressBar and TextView INITIALIZED")
        } catch (e: Exception) {
            Log.e(LIFECYCLE_TAG, "onCreate - Error finding ProgressBar or TextView. Check IDs in XML.", e)
            Toast.makeText(this, "UI Error in Launch Screen. Check layout.", Toast.LENGTH_LONG).show()
        }

        val mainLayout = findViewById<View>(R.id.main)
        if (mainLayout == null) {
            Log.e(LIFECYCLE_TAG, "onCreate - R.id.main (root layout ID) not found in activity_launch_screen.xml!")
        }
        mainLayout?.let {
            ViewCompat.setOnApplyWindowInsetsListener(it) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
                insets
            }
        }
        Log.d(LIFECYCLE_TAG, "onCreate - WindowInsetsListener SET (if mainLayout found)")

        loadAndDecideNavigation()
        Log.d(LIFECYCLE_TAG, "onCreate - loadAndDecideNavigation CALLED")
        Log.d(LIFECYCLE_TAG, "onCreate - END")
    }

    override fun onStart() {
        Log.d(LIFECYCLE_TAG, "onStart - START")
        super.onStart()
        Log.d(LIFECYCLE_TAG, "onStart - END")
    }

    override fun onResume() {
        Log.d(LIFECYCLE_TAG, "onResume - START")
        super.onResume()
        Log.d(LIFECYCLE_TAG, "onResume - END")
    }

    override fun onPause() {
        Log.d(LIFECYCLE_TAG, "onPause - START")
        super.onPause()
        Log.d(LIFECYCLE_TAG, "onPause - END")
    }

    override fun onStop() {
        Log.d(LIFECYCLE_TAG, "onStop - START")
        super.onStop()
        Log.d(LIFECYCLE_TAG, "onStop - END")
    }

    private fun showLoading(isLoading: Boolean) {
        if (::loadingProgressBar.isInitialized && ::loadingTextView.isInitialized) {
            loadingProgressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            loadingTextView.visibility = if (isLoading) View.VISIBLE else View.GONE
        } else {
            Log.w(LIFECYCLE_TAG, "showLoading called but ProgressBar or TextView not initialized.")
        }
    }

    // Removed isOnboardingComplete() method as it's no longer needed

    private fun loadAndDecideNavigation() {
        showLoading(true)
        Log.d(LIFECYCLE_TAG, "loadAndDecideNavigation started. Simulating delay.")

        CoroutineScope(Dispatchers.Main).launch {
            delay(1000) // Simulate a 1-second loading period (adjust as needed)
            Log.d(LIFECYCLE_TAG, "Delay finished. Navigating directly to DashboardScreen.")

            val targetActivity: Class<*> = DashboardScreen::class.java

            val intent = Intent(this@LaunchScreen, targetActivity)
            startActivity(intent)
            finish()
            Log.d(LIFECYCLE_TAG, "Navigated to ${targetActivity.simpleName} and LaunchScreen finished.")
        }
    }

    override fun onDestroy() {
        Log.d(LIFECYCLE_TAG, "onDestroy - START")
        super.onDestroy()
        Log.d("LaunchScreen", "onDestroy called (existing log)")
        Log.d(LIFECYCLE_TAG, "onDestroy - END")
    }
}
