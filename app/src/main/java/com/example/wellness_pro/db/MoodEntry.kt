package com.example.wellness_pro.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mood_entries")
data class MoodEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val timestamp: Long,
    val moodLevel: Int, // e.g., 1 (very bad) to 5 (very good)
    val notes: String? = null
)