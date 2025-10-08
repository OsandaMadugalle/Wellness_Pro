package com.example.wellness_pro.ui

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.CalendarView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.wellness_pro.R
import com.example.wellness_pro.db.AppDatabase
import com.example.wellness_pro.db.MoodEntry as DbMoodEntry
import com.example.wellness_pro.viewmodel.MoodViewModel
import com.example.wellness_pro.ui.MoodEntriesAdapter
import com.example.wellness_pro.viewmodel.MoodViewModelFactory
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Simple calendar screen (Option A) - uses built-in CalendarView.
 * Selecting a date queries the DAO for entries on that day and shows them in a RecyclerView below.
 */
class MoodCalendarActivity : AppCompatActivity() {

    private lateinit var calendarView: CalendarView
    private lateinit var recyclerView: RecyclerView
    private lateinit var textViewNoEntries: TextView
    private lateinit var moodViewModel: MoodViewModel
    private lateinit var adapter: MoodEntriesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recyclerView.layoutManager = LinearLayoutManager(this)
    adapter = MoodEntriesAdapter(emptyList())
        recyclerView.adapter = adapter

        try {
            val moodDao = AppDatabase.getInstance().moodDao()
            val factory = MoodViewModelFactory(moodDao)
            moodViewModel = ViewModelProvider(this, factory)[MoodViewModel::class.java]
        } catch (e: Exception) {
            Log.e("MoodCalendarActivity", "Error initializing MoodViewModel", e)
            Toast.makeText(this, getString(R.string.error_loading_mood_data_components), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Default to today
        val today = System.currentTimeMillis()
        queryForDate(today)

        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val cal = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, dayOfMonth)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val start = cal.timeInMillis
            val end = cal.apply { add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis - 1
            queryForRange(start, end)
        }
    }

    private fun queryForDate(epochMillis: Long) {
        val cal = Calendar.getInstance().apply { timeInMillis = epochMillis }
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        val end = cal.apply { add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis - 1
        queryForRange(start, end)
    }

    private fun queryForRange(start: Long, end: Long) {
        lifecycleScope.launch {
            moodViewModel.weeklyMoodTrend // trigger VM initialization
            moodViewModel // ensure initialized
            // use DAO via ViewModel's flows by directly asking the DAO: simpler here
            try {
                val moodDao = AppDatabase.getInstance().moodDao()
                moodDao.getMoodEntriesBetween(start, end).collectLatest { entries ->
                    updateList(entries)
                }
            } catch (e: Exception) {
                Log.e("MoodCalendarActivity", "Error querying mood entries for range", e)
                Toast.makeText(this@MoodCalendarActivity, "Error loading entries", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateList(entries: List<DbMoodEntry>) {
        runOnUiThread {
            adapter.updateData(entries)
            if (entries.isEmpty()) {
                textViewNoEntries.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            } else {
                textViewNoEntries.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
            }
        }
    }

}
