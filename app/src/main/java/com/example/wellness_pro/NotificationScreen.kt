package com.example.wellness_pro

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button // Added for potential Clear All button
import android.widget.ImageButton // Added for potential Clear All button in Toolbar
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog // Added for confirmation dialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.wellness_pro.db.AppNotification
import com.example.wellness_pro.ui.AppNotificationAdapter
import com.example.wellness_pro.viewmodel.NotificationViewModel

class NotificationScreen : AppCompatActivity() {

    private lateinit var buttonBack: ImageView
    private lateinit var recyclerViewNotifications: RecyclerView
    private lateinit var textViewNoNotifications: TextView
    private lateinit var notificationAdapter: AppNotificationAdapter
    private lateinit var notificationViewModel: NotificationViewModel
    private lateinit var buttonClearAll: ImageButton // Assuming an ImageButton with ID buttonClearAll; change type if you use a standard Button

    companion object {
        private const val TAG = "NotificationScreen"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_notification_screen)

        buttonBack = findViewById(R.id.buttonBackTop2)
        recyclerViewNotifications = findViewById(R.id.recyclerViewNotifications)
        textViewNoNotifications = findViewById(R.id.textViewNoNotifications)

        // Make sure you have a Button or ImageButton with this ID in your XML layout
        buttonClearAll = findViewById(R.id.buttonClearAll) // Example ID, replace with your actual ID

        // Initialize RecyclerView and ViewModel first so observers are ready before UI interactions
        setupRecyclerView()
        setupViewModel() // This will now connect to your ViewModel and observe data

        buttonBack.setOnClickListener {
            val intent = Intent(this, DashboardScreen::class.java)
            startActivity(intent)
            // finish() // Optional: if you want to remove this screen from backstack
        }

        // Safe click - ViewModel is initialized above
        buttonClearAll.setOnClickListener {
            showClearAllConfirmationDialog()
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupRecyclerView() {
        notificationAdapter = AppNotificationAdapter(emptyList()) { notification ->
            Log.d(TAG, "Clicked on notification: ${notification.title}")
            // Handle notification click: mark as read, navigate, etc.
            // Example: notificationViewModel.markNotificationAsRead(notification.id)
        }
        recyclerViewNotifications.layoutManager = LinearLayoutManager(this)
        recyclerViewNotifications.adapter = notificationAdapter
        Log.d(TAG, "RecyclerView setup complete.")
    }

    private fun setupViewModel() {
        notificationViewModel = ViewModelProvider(this).get(NotificationViewModel::class.java)
        Log.d(TAG, "NotificationViewModel initialized.")

        notificationViewModel.allNotifications.observe(this, Observer { notifications ->
            notifications?.let {
                Log.d(TAG, "Observed notifications list. Count: ${it.size}")

                // Enable or disable the Clear All button depending on whether there are notifications
                buttonClearAll.isEnabled = it.isNotEmpty()

                if (it.isEmpty()) {
                    recyclerViewNotifications.isVisible = false
                    textViewNoNotifications.isVisible = true
                    Log.d(TAG, "No notifications to display. Showing 'No notifications' text.")
                } else {
                    recyclerViewNotifications.isVisible = true
                    textViewNoNotifications.isVisible = false
                    notificationAdapter.updateData(it)
                    Log.d(TAG, "Updating adapter with ${it.size} notifications.")
                }
            } ?: run {
                // If null, treat as empty list
                buttonClearAll.isEnabled = false
                recyclerViewNotifications.isVisible = false
                textViewNoNotifications.isVisible = true
                Log.d(TAG, "Observed null notifications list. Showing 'No notifications' text.")
            }
        })
        Log.d(TAG, "ViewModel observers set up.")
    }

    private fun showClearAllConfirmationDialog() {
        val builder = AlertDialog.Builder(this)
            .setTitle("Clear All Notifications")
            .setMessage("Are you sure you want to delete all notifications? This action cannot be undone.")
            .setPositiveButton("Clear All", null) // We'll override later to control dismissal
            .setNegativeButton("Cancel") { dialog, _ ->
                Log.d(TAG, "Clear All cancelled by user.")
                try {
                    if (!isFinishing && !isDestroyed) dialog.dismiss()
                } catch (e: Exception) {
                    Log.w(TAG, "Error dismissing cancel dialog", e)
                }
            }

        val dialog = builder.create()
        dialog.setOnShowListener {
            val positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positive.setOnClickListener {
                // Prevent double clicks
                positive.isEnabled = false
                buttonClearAll.isEnabled = false

                try {
                    notificationViewModel.clearAllNotifications()
                    Log.i(TAG, "Clear All confirmed by user.")
                } catch (e: Exception) {
                    Log.e(TAG, "Error while requesting clearAll on ViewModel", e)
                }

                // Safely dismiss the dialog if the activity is still valid
                try {
                    if (!isFinishing && !isDestroyed) dialog.dismiss()
                } catch (e: Exception) {
                    Log.w(TAG, "Error dismissing clearAll dialog", e)
                }
            }
        }

        try {
            if (!isFinishing && !isDestroyed) dialog.show()
        } catch (e: Exception) {
            Log.w(TAG, "Could not show Clear All dialog", e)
        }
    }
}
