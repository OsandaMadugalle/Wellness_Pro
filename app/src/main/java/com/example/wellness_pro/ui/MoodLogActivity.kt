package com.example.wellness_pro.ui

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.wellness_pro.R
import com.example.wellness_pro.db.AppDatabase
import com.example.wellness_pro.db.MoodEntry // Room Entity
import com.example.wellness_pro.navbar.BaseBottomNavActivity
import com.example.wellness_pro.viewmodel.MoodViewModel
import com.example.wellness_pro.viewmodel.MoodViewModelFactory
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MoodLogActivity : BaseBottomNavActivity() {

    override val currentNavControllerItemId: Int
        get() = R.id.navButtonMoodJournal

    override val layoutId: Int
        get() = R.layout.activity_mood_log

    private lateinit var moodViewModel: MoodViewModel

    private lateinit var moodButtons: List<Button>
    private lateinit var editTextMoodNotes: EditText
    private lateinit var buttonSaveMood: Button
    private lateinit var moodChart: LineChart
    private lateinit var textViewNoMoodsChart: TextView

    private var selectedMoodLevel: Int = 0 // 0 means no selection, 1-5 for mood levels

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize ViewModel
        try {
            val moodDao = AppDatabase.getInstance().moodDao()
            val factory = MoodViewModelFactory(moodDao)
            moodViewModel = ViewModelProvider(this, factory)[MoodViewModel::class.java]
        } catch (e: IllegalStateException) {
            Log.e("MoodLogActivity", "Error initializing MoodViewModel. AppDatabase might not be initialized.", e)
            Toast.makeText(this, "Error initializing mood tracking components.", Toast.LENGTH_LONG).show()
            // Potentially finish() or disable mood tracking features if critical
            return
        }

        // Initialize UI Elements
        try {
            editTextMoodNotes = findViewById(R.id.editTextMoodNotes)
            buttonSaveMood = findViewById(R.id.buttonSaveMood)
            moodChart = findViewById(R.id.moodChart)
            textViewNoMoodsChart = findViewById(R.id.textViewNoMoodsChart)

            moodButtons = listOf(
                findViewById(R.id.buttonMood1),
                findViewById(R.id.buttonMood2),
                findViewById(R.id.buttonMood3),
                findViewById(R.id.buttonMood4),
                findViewById(R.id.buttonMood5)
            )
        } catch (e: NullPointerException) {
            Log.e("MoodLogActivity", "Error finding UI elements. Check IDs in XML.", e)
            Toast.makeText(this, "Error loading mood logging UI.", Toast.LENGTH_LONG).show()
            return
        }

        setupMoodInputListeners()
        setupMoodChartStyle()
        observeMoodData()

        buttonSaveMood.setOnClickListener {
            saveMoodEntryViaViewModel()
        }
    }

    private fun setupMoodInputListeners() {
        moodButtons.forEachIndexed { index, button ->
            button.setOnClickListener {
                selectedMoodLevel = index + 1
                updateMoodButtonSelection(selectedMoodLevel)
            }
        }
    }

    private fun updateMoodButtonSelection(selectedLevel: Int) {
        moodButtons.forEachIndexed { index, button ->
            if ((index + 1) == selectedLevel) {
                // Example: Highlight selected - You might want to define a specific style
                button.setBackgroundColor(Color.CYAN)
                button.setTextColor(Color.BLACK)
            } else {
                // Example: Default/unselected style
                button.setBackgroundColor(Color.TRANSPARENT) // Or your outlined button default
                button.setTextColor(Color.WHITE) // Or your default text color for outlined buttons
            }
        }
    }

    private fun saveMoodEntryViaViewModel() {
        if (selectedMoodLevel == 0) {
            Toast.makeText(this, "Please select your current mood (1-5)", Toast.LENGTH_SHORT).show()
            return
        }
        val notes = editTextMoodNotes.text.toString().trim()
        moodViewModel.insertMoodEntry(selectedMoodLevel, if (notes.isNotEmpty()) notes else null)
        Toast.makeText(this, "Mood saved!", Toast.LENGTH_SHORT).show()

        // Reset input fields
        editTextMoodNotes.text.clear()
        selectedMoodLevel = 0
        updateMoodButtonSelection(0) // Reset button highlights
        // Do not call finish() here, as this screen is now the main Mood Journal screen.
    }

    private fun setupMoodChartStyle() {
        moodChart.description.isEnabled = false
        moodChart.legend.isEnabled = true
        moodChart.legend.textColor = Color.WHITE
        moodChart.setTouchEnabled(true)
        moodChart.setPinchZoom(true)

        val xAxis = moodChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.valueFormatter = object : ValueFormatter() {
            private val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
            override fun getFormattedValue(value: Float): String {
                return dateFormat.format(Date(value.toLong()))
            }
        }
        xAxis.granularity = 1f
        xAxis.textColor = Color.WHITE
        xAxis.axisLineColor = Color.DKGRAY
        xAxis.gridColor = Color.GRAY

        val yAxisLeft = moodChart.axisLeft
        yAxisLeft.textColor = Color.WHITE
        yAxisLeft.axisMinimum = 0.5f
        yAxisLeft.axisMaximum = 5.5f
        yAxisLeft.granularity = 1f
        yAxisLeft.setLabelCount(6, true) // For labels 0,1,2,3,4,5 (or 1-5 if min is 1)
        yAxisLeft.axisLineColor = Color.DKGRAY
        yAxisLeft.gridColor = Color.GRAY

        moodChart.axisRight.isEnabled = false
        moodChart.setBackgroundColor(Color.TRANSPARENT) // Or your app background color
        moodChart.setNoDataText("Log your moods to see your trend!")
        moodChart.setNoDataTextColor(Color.LTGRAY)
    }

    private fun observeMoodData() {
        lifecycleScope.launch {
            moodViewModel.weeklyMoodTrend.collect { moodEntries ->
                if (::moodChart.isInitialized) {
                    updateMoodChartData(moodEntries)
                }
            }
        }
    }

    private fun updateMoodChartData(moodEntries: List<MoodEntry>) {
        if (moodEntries.isEmpty()) {
            moodChart.visibility = View.GONE
            textViewNoMoodsChart.visibility = View.VISIBLE
            textViewNoMoodsChart.text = "Log your first mood to see your trend!"
            moodChart.clear() // Clear any old data
            moodChart.invalidate() // Refresh chart view
            return
        }

        moodChart.visibility = View.VISIBLE
        textViewNoMoodsChart.visibility = View.GONE

        val entries = ArrayList<Entry>()
        moodEntries.forEach {
            // Ensure moodLevel is treated as float for y-axis
            entries.add(Entry(it.timestamp.toFloat(), it.moodLevel.toFloat()))
        }

        val dataSet = LineDataSet(entries, "Weekly Mood Trend")
        dataSet.color = Color.MAGENTA
        dataSet.valueTextColor = Color.WHITE
        dataSet.setCircleColor(Color.YELLOW)
        dataSet.circleRadius = 4f
        dataSet.valueTextSize = 10f
        dataSet.setDrawValues(true)
        dataSet.lineWidth = 2f

        val lineData = LineData(dataSet)
        moodChart.data = lineData
        moodChart.invalidate() // Refresh the chart
    }
}
