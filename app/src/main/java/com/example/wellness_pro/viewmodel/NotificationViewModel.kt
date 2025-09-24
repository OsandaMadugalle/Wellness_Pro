package com.example.wellness_pro.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
// import androidx.lifecycle.Transformations // Remove this import
import androidx.lifecycle.switchMap // Add this import for the extension function
import androidx.lifecycle.asLiveData
import com.example.wellness_pro.db.AppDatabase
import com.example.wellness_pro.db.AppNotification

class NotificationViewModel(application: Application) : AndroidViewModel(application) {

    // This LiveData will be switched based on database initialization status
    val allNotifications: LiveData<List<AppNotification>>

    init {
        val appDatabase = AppDatabase // Companion object

        // Observe the isInitialized StateFlow from AppDatabase
        // Convert StateFlow to LiveData for easier observation and transformation
        val dbInitializedLiveData: LiveData<Boolean> = appDatabase.isInitialized.asLiveData()

        // When dbInitializedLiveData becomes true, switch to the actual DAO's LiveData
        // Otherwise, provide an empty list or null (here, an empty LiveData source)
        allNotifications = dbInitializedLiveData.switchMap { isInitialized -> // Use the switchMap extension function
            if (isInitialized) {
                // Database is ready, get DAO and return its LiveData
                AppDatabase.getInstance().appNotificationDao().getAllNotifications()
            } else {
                // Database not ready, return a LiveData with an empty list
                // Or you could return a LiveData that never emits until initialized
                MutableLiveData(emptyList()) // Or null if your UI handles it
            }
        }
    }

    // You can add other methods here if needed
}
