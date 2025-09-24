package com.example.wellness_pro.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_notifications")
data class AppNotification(
    @PrimaryKey val id: String,
    val title: String,
    val message: String,
    val timestamp: Long,
    val isRead: Boolean = false,
    val relatedHabitId: String? = null // Added field
)
