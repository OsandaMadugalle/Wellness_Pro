package com.example.wellness_pro.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MoodDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(moodEntry: MoodEntry)

    // Get mood entries between two timestamps (e.g., for the last 7 days)
    @Query("SELECT * FROM mood_entries WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    fun getMoodEntriesBetween(startTime: Long, endTime: Long): Flow<List<MoodEntry>>

    // Optional: Get all mood entries
    @Query("SELECT * FROM mood_entries ORDER BY timestamp DESC")
    fun getAllMoodEntries(): Flow<List<MoodEntry>>
}