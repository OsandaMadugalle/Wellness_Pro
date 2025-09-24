package com.example.wellness_pro.models

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class MoodEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val moodEmoji: String, // e.g., "😊", "😢", "😠"
    val note: String? = null
) {
    // Helper function to get a formatted date string if needed
    fun getFormattedDate(): String {
        val date = Date(timestamp)
        // Simple date format, you can customize this
        val format = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        return format.format(date)
    }
}