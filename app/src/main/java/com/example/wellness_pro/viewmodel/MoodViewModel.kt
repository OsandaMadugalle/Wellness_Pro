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
import kotlinx.coroutines.launch
import java.util.Calendar

class MoodViewModel(private val moodDao: MoodDao) : ViewModel() {

    // refreshTrigger ensures data is refetched when new entries are added
    private val refreshTrigger: MutableStateFlow<Long> = MutableStateFlow(System.currentTimeMillis())

    // Flow for the weekly mood trend (last 7 days) - for the chart
    val weeklyMoodTrend: StateFlow<List<MoodEntry>> = refreshTrigger.flatMapLatest { endTime ->
        val startTime = getStartTimeForWindow(endTime, 7)
        moodDao.getMoodEntriesBetween(startTime, endTime) 
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    // Flow for all mood entries, sorted by timestamp - for the RecyclerView history
    val allMoodEntriesSorted: StateFlow<List<MoodEntry>> = refreshTrigger.flatMapLatest { 
        moodDao.getAllMoodEntries() // CORRECTED: Was getAllEntriesSortedByTimestamp()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L), 
        initialValue = emptyList() 
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
            refreshTrigger.value = entryTimestamp // Update trigger to refetch data for both flows
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
