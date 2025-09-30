package com.example.wellness_pro.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap 
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.wellness_pro.db.AppDatabase
import com.example.wellness_pro.db.AppNotification
import kotlinx.coroutines.launch

class NotificationViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "NotificationViewModel"
    }

    val allNotifications: LiveData<List<AppNotification>>

    init {
        Log.d(TAG, "Initializing NotificationViewModel...")
        val appDatabase = AppDatabase // Companion object

        val dbInitializedLiveData: LiveData<Boolean> = appDatabase.isInitialized.asLiveData()
        Log.d(TAG, "Observing AppDatabase.isInitialized. Current value (from StateFlow): ${appDatabase.isInitialized.value}")

        allNotifications = dbInitializedLiveData.switchMap { isInitialized ->
            Log.d(TAG, "dbInitializedLiveData emitted: $isInitialized")
            if (isInitialized) {
                Log.i(TAG, "Database IS INITIALIZED. Switching to DAO's getAllNotifications().")
                AppDatabase.getInstance().appNotificationDao().getAllNotifications()
            } else {
                Log.w(TAG, "Database IS NOT INITIALIZED. Switching to empty list LiveData.")
                MutableLiveData(emptyList<AppNotification>()) // Ensure type is explicit
            }
        }
        Log.d(TAG, "NotificationViewModel initialization complete.")
    }

    /**
     * Clears all notifications from the database.
     */
    fun clearAllNotifications() {
        viewModelScope.launch {
            try {
                AppDatabase.getInstance().appNotificationDao().clearAll()
                Log.i(TAG, "Successfully called clearAll on DAO.")
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing all notifications from database", e)
            }
        }
    }
}
