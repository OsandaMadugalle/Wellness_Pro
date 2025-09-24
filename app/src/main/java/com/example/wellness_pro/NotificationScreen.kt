package com.example.wellness_pro

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.wellness_pro.db.AppNotification // Added this import
import com.example.wellness_pro.ui.AppNotificationAdapter
import com.example.wellness_pro.viewmodel.NotificationViewModel

class NotificationScreen : AppCompatActivity() {

    private lateinit var buttonBack: ImageView
    private lateinit var recyclerViewNotifications: RecyclerView
    private lateinit var textViewNoNotifications: TextView
    private lateinit var notificationAdapter: AppNotificationAdapter
    private lateinit var notificationViewModel: NotificationViewModel

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

        buttonBack.setOnClickListener {
            val intent = Intent(this, DashboardScreen::class.java)
            startActivity(intent)
            // finish() // Optional
        }

        setupRecyclerView()
        setupViewModel() // This will now connect to your ViewModel and observe data

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupRecyclerView() {
        // Ensure AppNotificationAdapter is correctly implemented and imported
        // The click listener here is an example; adapt as needed
        notificationAdapter = AppNotificationAdapter(emptyList()) { notification ->
            Log.d(TAG, "Clicked on notification: ${notification.title}")
            // Example: Mark as read or navigate based on the notification
            // notificationViewModel.markNotificationAsRead(notification.id)
            // Or start an activity related to this notification
        }
        recyclerViewNotifications.layoutManager = LinearLayoutManager(this)
        recyclerViewNotifications.adapter = notificationAdapter
        Log.d(TAG, "RecyclerView setup complete.")
    }

    private fun setupViewModel() {
        // Ensure NotificationViewModel, AppDatabase, and AppNotificationDao are created and working
        notificationViewModel = ViewModelProvider(this).get(NotificationViewModel::class.java)
        Log.d(TAG, "NotificationViewModel initialized.")

        notificationViewModel.allNotifications.observe(this, Observer { notifications ->
            // This block will be executed whenever the data in the database changes
            // (if your DAO returns LiveData)
            notifications?.let {
                Log.d(TAG, "Observed notifications list. Count: ${it.size}")
                if (it.isEmpty()) {
                    recyclerViewNotifications.isVisible = false
                    textViewNoNotifications.isVisible = true
                    Log.d(TAG, "No notifications to display. Showing 'No notifications' text.")
                } else {
                    recyclerViewNotifications.isVisible = true
                    textViewNoNotifications.isVisible = false
                    // The LiveData from Room usually handles sorting if defined in the @Query
                    // If not, you can sort here: it.sortedByDescending { n -> n.timestamp }
                    notificationAdapter.updateData(it) // This line should now be correct
                    Log.d(TAG, "Updating adapter with ${it.size} notifications.")
                }
            } ?: run {
                // This case might happen if LiveData initially emits null
                recyclerViewNotifications.isVisible = false
                textViewNoNotifications.isVisible = true
                Log.d(TAG, "Observed null notifications list. Showing 'No notifications' text.")
            }
        })
        Log.d(TAG, "ViewModel observers set up.")
    }
}
