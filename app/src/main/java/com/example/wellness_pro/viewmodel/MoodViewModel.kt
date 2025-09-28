package com.example.wellness_pro.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wellness_pro.db.MoodDao
import com.example.wellness_pro.db.MoodEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
// import kotlinx.coroutines.flow.flowOf // No longer needed for hardcoded empty list
import kotlinx.coroutines.launch
import java.util.Calendar

class MoodViewModel(private val moodDao: MoodDao) : ViewModel() {

    private val refreshTrigger: MutableStateFlow<Long> = MutableStateFlow(System.currentTimeMillis())

    val weeklyMoodTrend: StateFlow<List<MoodEntry>> = refreshTrigger.flatMapLatest { endTime -> // Renamed currentTime to endTime for clarity
        val startTime = getStartTimeForWindow(endTime, 7) // Fetch data for the last 7 days
        moodDao.getMoodEntriesBetween(startTime, endTime)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList<MoodEntry>()
    )

    fun insertMoodEntry(moodLevel: Int, notes: String? = null) {
        viewModelScope.launch {
            val entryTimestamp = System.currentTimeMillis()
            val moodEntry = MoodEntry(
                timestamp = entryTimestamp,
                moodLevel = moodLevel,
                notes = notes
            )
            moodDao.insert(moodEntry)
            refreshTrigger.value = entryTimestamp // Trigger a refresh to update the flow
        }
    }

    private fun getStartTimeForWindow(endTime: Long, daysToSubtract: Int): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = endTime
        calendar.add(Calendar.DAY_OF_YEAR, -daysToSubtract)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}
