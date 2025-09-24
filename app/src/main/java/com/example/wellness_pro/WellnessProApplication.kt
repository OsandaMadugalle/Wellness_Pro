package com.example.wellness_pro

import android.app.Application
import android.util.Log
import com.example.wellness_pro.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WellnessProApplication : Application() {

    companion object {
        private const val TAG = "WellnessProApplication"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: Initializing database...")
        // Initialize the database off the main thread
        CoroutineScope(Dispatchers.IO).launch {
            AppDatabase.initialize(applicationContext)
            Log.d(TAG, "onCreate: Database initialization launched.")
        }
    }
}
