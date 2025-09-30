package com.example.wellness_pro

import java.util.UUID

data class Habit(
    val id: String = UUID.randomUUID().toString(),
    var type: String,
    var targetValue: Int,
    var unit: String,
    var schedule: String,
    var reminderTimeHour: Int? = null, // General reminder for the task itself
    var reminderTimeMinute: Int? = null,
    var isArchived: Boolean = false,
    val creationDate: Long = System.currentTimeMillis(),
    var currentValue: Int = 0,
    @Deprecated("Use completionHistory instead") var daysCompletedThisWeek: MutableSet<Int>? = mutableSetOf(), // Phasing out
    var lastCompletionTimestamp: Long = 0L,
    var streak: Int = 0,
    val completionHistory: MutableMap<Long, Boolean> = mutableMapOf(), // Ensure this is always initialized

    // Fields for Hydration Reminder specific settings
    var isReminderEnabled: Boolean = false, // Specifically for interval-based hydration reminders
    var reminderIntervalMinutes: Int? = 60, // Default to 1 hour, user can change
    var reminderStartTimeHour: Int? = null, // Optional: e.g., 8 (for 8 AM)
    var reminderEndTimeHour: Int? = null,     // Optional: e.g., 22 (for 10 PM)
    var reminderEndTimeMinute: Int? = null    // Optional: e.g., 00 or 30 for the end hour
) {
    fun getEmojiForType(): String {
        return when (type) {
            "Hydration" -> "💧"
            "Steps" -> "🚶"
            "Meditation" -> "🧘"
            "Workout" -> "🏋️"
            "Reading" -> "📚"
            else -> "🎯"
        }
    }

    fun getDescriptionText(): String {
        return "$targetValue $unit / ${schedule.lowercase()}"
    }
}
