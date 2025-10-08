package com.example.wellness_pro.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat // For loading colors
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModelProvider // For Chart ViewModel
import androidx.lifecycle.lifecycleScope // For Chart ViewModel
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.wellness_pro.R
import com.example.wellness_pro.db.AppDatabase // For Chart ViewModel
import com.example.wellness_pro.db.MoodEntry as DbMoodEntry // Alias for clarity
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
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
// Gson and TypeToken no longer needed for RecyclerView
import kotlinx.coroutines.flow.collectLatest // Changed to collectLatest for RecyclerView updates
import kotlinx.coroutines.launch // For Chart ViewModel
import java.text.SimpleDateFormat // For Chart and RecyclerView
import java.util.Date // For Chart and RecyclerView
import java.util.Locale // For Chart and RecyclerView

class MoodHistoryActivity : BaseBottomNavActivity() {

    override val currentNavControllerItemId: Int
        get() = R.id.navButtonMoodJournal

    override val layoutId: Int
        get() = R.layout.activity_mood_history

    // RecyclerView related (now uses Room db.MoodEntry via ViewModel)
    private lateinit var recyclerViewMoodHistory: RecyclerView
    private lateinit var textViewNoMoods: TextView // For RecyclerView empty state
    private lateinit var moodEntriesAdapter: MoodEntriesAdapter
    private lateinit var fabLogNewMood: FloatingActionButton
    private lateinit var buttonShareMoodHistory: ImageButton

    // Common ViewModel for both Chart and RecyclerView
    private lateinit var moodViewModel: MoodViewModel

    // Chart related
    private lateinit var moodChartHistory: LineChart
    private lateinit var textViewNoMoodsChartHistory: TextView // For Chart

    private lateinit var chipGroupFilters: ChipGroup
    private val daysWindowState = MutableStateFlow<Int?>(7) // default 7 days; null means all

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ViewModel Initialization (common for both chart and RecyclerView)
        try {
            // Ensure AppDatabase is initialized if your getInstance() relies on prior async init
            // For simplicity here, assuming getInstance() handles its own synchronization/initialization check
            val moodDao = AppDatabase.getInstance().moodDao() // If getInstance() can throw, handle it.
            val factory = MoodViewModelFactory(moodDao)
            moodViewModel = ViewModelProvider(this, factory)[MoodViewModel::class.java]
        } catch (e: Exception) {
            Log.e("MoodHistoryActivity", "Error initializing MoodViewModel", e)
            Toast.makeText(this, getString(R.string.error_loading_mood_data_components), Toast.LENGTH_LONG).show()
            finish() // Critical component failed, cannot proceed
            return
        }

        // RecyclerView Init
        recyclerViewMoodHistory = findViewById(R.id.recyclerViewMoodHistory)
        textViewNoMoods = findViewById(R.id.textViewNoMoods)
        fabLogNewMood = findViewById(R.id.fabLogNewMood)
        buttonShareMoodHistory = findViewById(R.id.buttonShareMoodHistory)

        setupRecyclerView()
        observeMoodDataForRecyclerView()

        fabLogNewMood.setOnClickListener {
            startActivity(Intent(this, MoodLogActivity::class.java))
        }

        buttonShareMoodHistory.setOnClickListener {
            shareMoodSummary()
        }

        chipGroupFilters = findViewById(R.id.chipGroupFilters)
        chipGroupFilters.setOnCheckedChangeListener { _, checkedId ->
            val days = when (checkedId) {
                R.id.chipDaily -> 1
                R.id.chipLast7 -> 7
                R.id.chipLast14 -> 14
                R.id.chipLast30 -> 30
                R.id.chipAll -> null
                else -> 7
            }
            Log.d("MoodHistoryActivity", "ChipGroup selection changed: checkedId=$checkedId -> days=$days")
            daysWindowState.value = days
        }

        // Chart Init
        moodChartHistory = findViewById(R.id.moodChartHistory)
        textViewNoMoodsChartHistory = findViewById(R.id.textViewNoMoodsChartHistory)
        setupMoodChartStyle()
        observeMoodDataForChart()
        // Ensure consistent insets handling for header and main
        setupWindowInsets()
    }

    private fun setupWindowInsets() {
        // Preserve header original top padding and add status bar inset
        findViewById<View?>(R.id.headerLayoutMoodHistory)?.let { header ->
            if (header.getTag(R.id.tag_padding_top) == null) header.setTag(R.id.tag_padding_top, header.paddingTop)
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
                val statusBarInsets = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars())
                val originalTop = v.getTag(R.id.tag_padding_top) as? Int ?: v.paddingTop
                v.updatePadding(top = originalTop + statusBarInsets.top)
                insets
            }
        }

        // Apply left/right/bottom insets to main content
        findViewById<View?>(R.id.main)?.let { main ->
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(main) { v, insets ->
                val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                v.updatePadding(left = systemBars.left, right = systemBars.right, bottom = systemBars.bottom)
                insets
            }
        }
    }

    private fun setupRecyclerView() {
        moodEntriesAdapter = MoodEntriesAdapter(emptyList())
        recyclerViewMoodHistory.layoutManager = LinearLayoutManager(this)
        recyclerViewMoodHistory.adapter = moodEntriesAdapter
    }

    private fun observeMoodDataForRecyclerView() {
        lifecycleScope.launch {
            combine(
                moodViewModel.allMoodEntriesSorted,
                daysWindowState
            ) { entriesFromDb, daysFilter ->
                val filtered = when (daysFilter) {
                    null -> entriesFromDb
                    else -> {
                        val end = System.currentTimeMillis()
                        val startCal = java.util.Calendar.getInstance().apply {
                            timeInMillis = end
                            add(java.util.Calendar.DAY_OF_YEAR, -(daysFilter))
                            set(java.util.Calendar.HOUR_OF_DAY, 0)
                            set(java.util.Calendar.MINUTE, 0)
                            set(java.util.Calendar.SECOND, 0)
                            set(java.util.Calendar.MILLISECOND, 0)
                        }
                        entriesFromDb.filter { it.timestamp >= startCal.timeInMillis && it.timestamp <= end }
                    }
                }
                filtered
            }.collectLatest { filteredEntries ->
                moodEntriesAdapter.updateData(filteredEntries)
                if (filteredEntries.isEmpty()) {
                    textViewNoMoods.visibility = View.VISIBLE
                    recyclerViewMoodHistory.visibility = View.GONE
                } else {
                    textViewNoMoods.visibility = View.GONE
                    recyclerViewMoodHistory.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun shareMoodSummary() {
        val currentMoods = moodEntriesAdapter.getItems()
        if (currentMoods.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_moods_to_share_yet), Toast.LENGTH_SHORT).show()
            return
        }
        val latestMood = currentMoods.first()
        val moodEmoji = moodEntriesAdapter.getEmojiForMoodLevel(latestMood.moodLevel)
        val formattedDate = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(latestMood.timestamp))

        val summary = "My latest mood: $moodEmoji on $formattedDate. Note: ${latestMood.notes ?: "N/A"}\nTrack your wellness with Wellness Pro!" // Changed .note to .notes

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, summary)
            type = "text/plain"
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_your_mood_summary)))
    }

    private fun setupMoodChartStyle() {
        moodChartHistory.description.isEnabled = false
        moodChartHistory.legend.isEnabled = true
        moodChartHistory.legend.textColor = ContextCompat.getColor(this, R.color.textColorPrimary)
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
        xAxis.granularity = 1f
        xAxis.textColor = ContextCompat.getColor(this, R.color.textColorPrimary)
        xAxis.axisLineColor = ContextCompat.getColor(this, R.color.textColorSecondary)
        xAxis.gridColor = ContextCompat.getColor(this, R.color.chart_grid_line)

        val yAxisLeft = moodChartHistory.axisLeft
        yAxisLeft.textColor = ContextCompat.getColor(this, R.color.textColorPrimary)
        yAxisLeft.axisMinimum = 0.5f
        yAxisLeft.axisMaximum = 5.5f
        yAxisLeft.granularity = 1f
        yAxisLeft.setLabelCount(6, true)
        yAxisLeft.axisLineColor = ContextCompat.getColor(this, R.color.textColorSecondary)
        yAxisLeft.gridColor = ContextCompat.getColor(this, R.color.chart_grid_line)

        moodChartHistory.axisRight.isEnabled = false
        moodChartHistory.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent))
        moodChartHistory.setNoDataText(getString(R.string.log_your_moods_to_see_your_trend))
        moodChartHistory.setNoDataTextColor(ContextCompat.getColor(this, R.color.textColorSecondary))
    }

    private fun observeMoodDataForChart() {
        lifecycleScope.launch {
            combine(
                moodViewModel.allMoodEntriesSorted, // reuse same flow for simplicity
                daysWindowState
            ) { entriesFromDb, daysFilter ->
                val filtered = when (daysFilter) {
                    null -> entriesFromDb
                    else -> {
                        val end = System.currentTimeMillis()
                        val startCal = java.util.Calendar.getInstance().apply {
                            timeInMillis = end
                            add(java.util.Calendar.DAY_OF_YEAR, -(daysFilter))
                            set(java.util.Calendar.HOUR_OF_DAY, 0)
                            set(java.util.Calendar.MINUTE, 0)
                            set(java.util.Calendar.SECOND, 0)
                            set(java.util.Calendar.MILLISECOND, 0)
                        }
                        entriesFromDb.filter { it.timestamp >= startCal.timeInMillis && it.timestamp <= end }
                    }
                }
                filtered
            }.collectLatest { filteredForChart ->
                Log.d("MoodHistoryActivity", "Chart data combined -> filtered entries=${filteredForChart.size}, daysWindow=${daysWindowState.value}")
                updateMoodChartData(filteredForChart)
            }
        }
    }

    private fun updateMoodChartData(moodEntries: List<DbMoodEntry>) {
        if (!::moodChartHistory.isInitialized || !::textViewNoMoodsChartHistory.isInitialized) {
            Log.w("MoodHistoryActivity", "Chart views not initialized, cannot update chart.")
            return
        }

        Log.d("MoodHistoryActivity", "updateMoodChartData called with ${moodEntries.size} entries")

        if (moodEntries.isEmpty()) {
            moodChartHistory.visibility = View.GONE
            textViewNoMoodsChartHistory.visibility = View.VISIBLE
            textViewNoMoodsChartHistory.text = getString(R.string.not_enough_mood_entries_for_a_trend_yet)
            moodChartHistory.clear()
            moodChartHistory.invalidate()
            return
        }

        moodChartHistory.visibility = View.VISIBLE
        textViewNoMoodsChartHistory.visibility = View.GONE

        val entries = ArrayList<Entry>()
        val sortedMoodEntries = moodEntries.sortedBy { it.timestamp }
        sortedMoodEntries.forEach { moodEntry ->
            entries.add(Entry(moodEntry.timestamp.toFloat(), moodEntry.moodLevel.toFloat()))
        }

        val dataSet = LineDataSet(entries, getString(R.string.daily_mood_trend_chart_title))
        dataSet.color = ContextCompat.getColor(this, R.color.chart_line_blue)
        dataSet.valueTextColor = ContextCompat.getColor(this, R.color.textColorPrimary)
        dataSet.setCircleColor(ContextCompat.getColor(this, R.color.chart_circle_color))
        dataSet.circleRadius = 4f
        dataSet.valueTextSize = 10f
        dataSet.setDrawValues(true)
        dataSet.lineWidth = 2f
        dataSet.mode = LineDataSet.Mode.CUBIC_BEZIER
        dataSet.setDrawFilled(true)
        dataSet.fillColor = ContextCompat.getColor(this, R.color.chart_fill_color)
        dataSet.fillAlpha = 85

        val lineData = LineData(dataSet)
        moodChartHistory.data = lineData
        moodChartHistory.invalidate()
    }
}

class MoodEntriesAdapter(private var moodEntries: List<DbMoodEntry>) :
    RecyclerView.Adapter<MoodEntriesAdapter.MoodEntryViewHolder>() {

    private val dateFormat = SimpleDateFormat("MMM d, yyyy 'at' hh:mm a", Locale.getDefault())

    fun getEmojiForMoodLevel(moodLevel: Int): String {
        return when (moodLevel) {
            1 -> "😭"
            2 -> "🙁"
            3 -> "😐"
            4 -> "🙂"
            5 -> "😄"
            else -> "❓"
        }
    }

    fun getItems(): List<DbMoodEntry> = moodEntries

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
        val moodEntry = moodEntries[position]
        holder.emojiTextView.text = getEmojiForMoodLevel(moodEntry.moodLevel)

        try {
            holder.timestampTextView.text = dateFormat.format(Date(moodEntry.timestamp))
        } catch (e: Exception) {
            holder.timestampTextView.text = holder.itemView.context.getString(R.string.invalid_date)
            Log.e("MoodEntriesAdapter", "Error formatting date for mood entry: ${moodEntry.id}", e)
        }

        if (moodEntry.notes != null && moodEntry.notes.isNotBlank()) { // Changed .note to .notes
            holder.noteTextView.text = moodEntry.notes // Changed .note to .notes
            holder.noteTextView.visibility = View.VISIBLE
        } else {
            holder.noteTextView.visibility = View.GONE
        }
    }

    override fun getItemCount() = moodEntries.size

    fun updateData(newEntries: List<DbMoodEntry>) {
        this.moodEntries = newEntries.sortedByDescending { it.timestamp }
        notifyDataSetChanged()
    }
}
