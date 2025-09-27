package com.example.wellness_pro.ui

import android.content.Intent
import android.graphics.Color // For Chart
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat // For loading colors
import androidx.lifecycle.ViewModelProvider // For Chart ViewModel
import androidx.lifecycle.lifecycleScope // For Chart ViewModel
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.wellness_pro.R
import com.example.wellness_pro.db.AppDatabase // For Chart ViewModel
import com.example.wellness_pro.navbar.BaseBottomNavActivity
import com.example.wellness_pro.viewmodel.MoodViewModel // For Chart ViewModel
import com.example.wellness_pro.viewmodel.MoodViewModelFactory // For Chart ViewModel
import com.github.mikephil.charting.charts.LineChart // For Chart
import com.github.mikephil.charting.components.XAxis // For Chart
import com.github.mikephil.charting.data.Entry // For Chart
import com.github.mikephil.charting.data.LineData // For Chart
import com.github.mikephil.charting.data.LineDataSet // For Chart
import com.github.mikephil.charting.formatter.ValueFormatter // For Chart
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch // For Chart ViewModel
import java.text.SimpleDateFormat // For Chart
import java.util.Date // For Chart
import java.util.Locale // For Chart

// Note: RecyclerView uses models.MoodEntry from SharedPreferences
// Chart will use db.MoodEntry from Room via ViewModel

class MoodHistoryActivity : BaseBottomNavActivity() {

    override val currentNavControllerItemId: Int
        get() = R.id.navButtonMoodJournal

    override val layoutId: Int
        get() = R.layout.activity_mood_history

    // RecyclerView related (uses SharedPreferences models.MoodEntry)
    private lateinit var recyclerViewMoodHistory: RecyclerView
    private lateinit var textViewNoMoods: TextView // For RecyclerView
    private lateinit var moodEntriesAdapter: MoodEntriesAdapter
    private val moodEntriesList = mutableListOf<com.example.wellness_pro.models.MoodEntry>() // Prefs model
    private val moodEntriesKey = "mood_entries_list_json"
    private lateinit var fabLogNewMood: FloatingActionButton
    private lateinit var buttonShareMoodHistory: ImageButton

    // Chart related (uses Room db.MoodEntry via ViewModel)
    private lateinit var moodViewModel: MoodViewModel
    private lateinit var moodChartHistory: LineChart
    private lateinit var textViewNoMoodsChartHistory: TextView // For Chart

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // RecyclerView Init
        recyclerViewMoodHistory = findViewById(R.id.recyclerViewMoodHistory)
        textViewNoMoods = findViewById(R.id.textViewNoMoods) // For RecyclerView empty state
        fabLogNewMood = findViewById(R.id.fabLogNewMood)
        buttonShareMoodHistory = findViewById(R.id.buttonShareMoodHistory)

        setupRecyclerView()

        fabLogNewMood.setOnClickListener {
            startActivity(Intent(this, MoodLogActivity::class.java))
        }

        buttonShareMoodHistory.setOnClickListener {
            shareMoodSummary()
        }

        // Chart Init
        try {
            val moodDao = AppDatabase.getInstance().moodDao()
            val factory = MoodViewModelFactory(moodDao)
            moodViewModel = ViewModelProvider(this, factory)[MoodViewModel::class.java]

            moodChartHistory = findViewById(R.id.moodChartHistory)
            textViewNoMoodsChartHistory = findViewById(R.id.textViewNoMoodsChartHistory)

            setupMoodChartStyle()
            observeMoodDataForChart()
        } catch (e: Exception) {
            Log.e("MoodHistoryActivity", "Error initializing chart components", e)
            Toast.makeText(this, "Error loading mood trend chart.", Toast.LENGTH_LONG).show()
            findViewById<View?>(R.id.moodChartHistory)?.visibility = View.GONE
            findViewById<View?>(R.id.textViewNoMoodsChartHistory)?.apply {
                visibility = View.VISIBLE
                (this as? TextView)?.text = "Chart unavailable."
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadMoodEntriesForRecyclerView() // Load data for RecyclerView from SharedPreferences
        // Chart data is observed via ViewModel and will update automatically if LiveData/Flow is used correctly
    }

    private fun setupRecyclerView() {
        moodEntriesAdapter = MoodEntriesAdapter(moodEntriesList)
        recyclerViewMoodHistory.layoutManager = LinearLayoutManager(this)
        recyclerViewMoodHistory.adapter = moodEntriesAdapter
    }

    private fun loadMoodEntriesForRecyclerView() { // Renamed to clarify its purpose
        try {
            val prefs = getSharedPreferences("WellnessProPrefs", MODE_PRIVATE)
            val gson = Gson()
            val json = prefs.getString(moodEntriesKey, null)
            val typeToken = object : TypeToken<List<com.example.wellness_pro.models.MoodEntry>>() {}.type

            if (json != null) {
                val entries: List<com.example.wellness_pro.models.MoodEntry> = gson.fromJson(json, typeToken)
                moodEntriesList.clear()
                moodEntriesList.addAll(entries.sortedByDescending { it.timestamp })
                moodEntriesAdapter.notifyDataSetChanged()
            }

            if (moodEntriesList.isEmpty()) {
                textViewNoMoods.visibility = View.VISIBLE // Controls RecyclerView empty state
                recyclerViewMoodHistory.visibility = View.GONE
            } else {
                textViewNoMoods.visibility = View.GONE
                recyclerViewMoodHistory.visibility = View.VISIBLE
            }
            Log.d("MoodHistoryActivity", "Loaded ${moodEntriesList.size} mood entries for RecyclerView.")

        } catch (e: Exception) {
            Log.e("MoodHistoryActivity", "Error loading mood entries for RecyclerView", e)
            textViewNoMoods.visibility = View.VISIBLE
            textViewNoMoods.text = "Error loading mood history."
            recyclerViewMoodHistory.visibility = View.GONE
        }
    }

    private fun shareMoodSummary() {
        if (moodEntriesList.isEmpty()) { // Uses RecyclerView data for sharing
            Toast.makeText(this, "No moods to share yet!", Toast.LENGTH_SHORT).show()
            return
        }
        val latestMood = moodEntriesList.first()
        val summary = """My latest mood: ${latestMood.moodEmoji} on ${latestMood.getFormattedDate()}.
Track your wellness with Wellness Pro!"""
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, summary)
            type = "text/plain"
        }
        startActivity(Intent.createChooser(shareIntent, "Share your mood summary"))
    }

    // --- Chart Specific Methods ---
    private fun setupMoodChartStyle() {
        moodChartHistory.description.isEnabled = false
        moodChartHistory.legend.isEnabled = true
        moodChartHistory.legend.textColor = Color.WHITE
        moodChartHistory.setTouchEnabled(true)
        moodChartHistory.setPinchZoom(true)

        val xAxis = moodChartHistory.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.valueFormatter = object : ValueFormatter() {
            private val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
            override fun getFormattedValue(value: Float): String {
                return dateFormat.format(Date(value.toLong()))
            }
        }
        xAxis.granularity = 1f // Ensures labels are for distinct days
        xAxis.textColor = Color.WHITE
        xAxis.axisLineColor = Color.DKGRAY
        xAxis.gridColor = ContextCompat.getColor(this, R.color.chart_grid_line) // Use color resource

        val yAxisLeft = moodChartHistory.axisLeft
        yAxisLeft.textColor = Color.WHITE
        yAxisLeft.axisMinimum = 0.5f // To give some space below 1
        yAxisLeft.axisMaximum = 5.5f // To give some space above 5
        yAxisLeft.granularity = 1f
        yAxisLeft.setLabelCount(6, true) // For labels like 1, 2, 3, 4, 5
        yAxisLeft.axisLineColor = Color.DKGRAY
        yAxisLeft.gridColor = ContextCompat.getColor(this, R.color.chart_grid_line) // Use color resource

        moodChartHistory.axisRight.isEnabled = false
        moodChartHistory.setBackgroundColor(Color.TRANSPARENT)
        moodChartHistory.setNoDataText("Log your moods to see your trend!")
        moodChartHistory.setNoDataTextColor(Color.LTGRAY)
    }

    private fun observeMoodDataForChart() {
        lifecycleScope.launch {
            // Assuming moodViewModel.weeklyMoodTrend provides List<com.example.wellness_pro.db.MoodEntry>
            moodViewModel.weeklyMoodTrend.collect { moodEntriesFromDb ->
                updateMoodChartData(moodEntriesFromDb)
            }
        }
    }

    private fun updateMoodChartData(moodEntries: List<com.example.wellness_pro.db.MoodEntry>) {
        if (!::moodChartHistory.isInitialized || !::textViewNoMoodsChartHistory.isInitialized) {
            Log.w("MoodHistoryActivity", "Chart views not initialized, cannot update chart.")
            return
        }

        if (moodEntries.isEmpty()) {
            moodChartHistory.visibility = View.GONE
            textViewNoMoodsChartHistory.visibility = View.VISIBLE
            textViewNoMoodsChartHistory.text = "Not enough mood entries for a trend yet."
            moodChartHistory.clear() // Clear any old data
            moodChartHistory.invalidate() // Refresh chart view
            return
        }

        moodChartHistory.visibility = View.VISIBLE
        textViewNoMoodsChartHistory.visibility = View.GONE

        val entries = ArrayList<Entry>()
        // Sort entries by timestamp to ensure the line chart draws correctly
        val sortedMoodEntries = moodEntries.sortedBy { it.timestamp }
        sortedMoodEntries.forEach { moodEntry -> // This is db.MoodEntry
            entries.add(Entry(moodEntry.timestamp.toFloat(), moodEntry.moodLevel.toFloat()))
        }

        val dataSet = LineDataSet(entries, "Weekly Mood Trend")
        dataSet.color = ContextCompat.getColor(this, R.color.chart_line_blue)
        dataSet.valueTextColor = Color.WHITE
        dataSet.setCircleColor(ContextCompat.getColor(this, R.color.chart_circle_color))
        dataSet.circleRadius = 4f
        dataSet.valueTextSize = 10f
        dataSet.setDrawValues(true) // Show values on points
        dataSet.lineWidth = 2f

        // Optional: Add a fill color below the line
        // dataSet.setDrawFilled(true)
        // dataSet.fillColor = ContextCompat.getColor(this, R.color.chart_fill_color) // Define this color
        // dataSet.fillAlpha = 50 // Transparency of the fill

        val lineData = LineData(dataSet)
        moodChartHistory.data = lineData
        moodChartHistory.invalidate() // Refresh the chart
    }
}

// MoodEntriesAdapter remains the same, using models.MoodEntry for RecyclerView
class MoodEntriesAdapter(private val moodEntries: List<com.example.wellness_pro.models.MoodEntry>) :
    RecyclerView.Adapter<MoodEntriesAdapter.MoodEntryViewHolder>() {

    class MoodEntryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val emojiTextView: TextView = itemView.findViewById(R.id.textViewMoodItemEmoji)
        val timestampTextView: TextView = itemView.findViewById(R.id.textViewMoodItemTimestamp)
        val noteTextView: TextView = itemView.findViewById(R.id.textViewMoodItemNote)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MoodEntryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_mood_entry, parent, false)
        return MoodEntryViewHolder(view)
    }

    override fun onBindViewHolder(holder: MoodEntryViewHolder, position: Int) {
        val moodEntry = moodEntries[position] // models.MoodEntry
        holder.emojiTextView.text = moodEntry.moodEmoji

        try {
            holder.timestampTextView.text = moodEntry.getFormattedDate()
        } catch (e: Exception) {
            holder.timestampTextView.text = "Invalid date"
            Log.e("MoodEntriesAdapter", "Error formatting date for mood entry: ${moodEntry.id}", e)
        }

        if (moodEntry.note != null && moodEntry.note.isNotBlank()) {
            holder.noteTextView.text = moodEntry.note
            holder.noteTextView.visibility = View.VISIBLE
        } else {
            holder.noteTextView.visibility = View.GONE
        }
    }

    override fun getItemCount() = moodEntries.size
}
