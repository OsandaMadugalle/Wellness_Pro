package com.example.wellness_pro.db

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Database(entities = [AppNotification::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun appNotificationDao(): AppNotificationDao

    companion object {
        private const val TAG = "AppDatabase"
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val _isInitialized = MutableStateFlow(false)
        val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

        suspend fun initialize(context: Context) {
            if (INSTANCE == null) {
                synchronized(this) {
                    if (INSTANCE == null) {
                        Log.d(TAG, "Building database...")
                        INSTANCE = Room.databaseBuilder(
                            context.applicationContext,
                            AppDatabase::class.java,
                            "wellness_pro_database"
                        )
                        .fallbackToDestructiveMigration()
                        .build()
                        _isInitialized.value = true
                        Log.d(TAG, "Database built and initialized flag set.")
                    } else {
                        // Already initialized by another thread in the synchronized block
                        if (!_isInitialized.value) _isInitialized.value = true
                    }
                }
            } else {
                 // Instance was already created
                if (!_isInitialized.value) _isInitialized.value = true
            }
        }

        fun getInstance(): AppDatabase {
            return INSTANCE ?: throw IllegalStateException("AppDatabase.initialize() must complete before calling getInstance(). Observe AppDatabase.isInitialized StateFlow.")
        }
    }
}
