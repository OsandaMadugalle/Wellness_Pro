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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.wellness_pro.R
import com.example.wellness_pro.db.AppDatabase // For Chart ViewModel
import com.example.wellness_pro.db.MoodEntry as DbMoodEntry // Alias for clarity
import com.example.wellness_pro.navbar.BaseBottomNavActivity
import com.example.wellness_pro.viewmodel.MoodViewModel // For Chart ViewModel
import com.example.wellness_pro.viewmodel.MoodViewModelFactory // For Chart ViewModel
import android.widget.CalendarView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
// Gson and TypeToken no longer needed for RecyclerView
import kotlinx.coroutines.flow.collectLatest // Changed to collectLatest for RecyclerView updates
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
    private lateinit var textViewAverageMoodSummary: TextView
    private lateinit var moodEntriesAdapter: MoodEntriesAdapter
    private lateinit var fabLogNewMood: FloatingActionButton
    private lateinit var buttonShareMoodHistory: ImageButton

    // Common ViewModel for both Chart and RecyclerView
    private lateinit var moodViewModel: MoodViewModel

    // Calendar related (embedded)
    private var calendarViewMoodHistory: CalendarView? = null
    private lateinit var toggleGroupCalendarFilter: com.google.android.material.button.MaterialButtonToggleGroup
    private lateinit var buttonFilter1d: com.google.android.material.button.MaterialButton
    private lateinit var buttonFilter7d: com.google.android.material.button.MaterialButton
    private lateinit var buttonFilter30d: com.google.android.material.button.MaterialButton
    private lateinit var buttonFilterAll: com.google.android.material.button.MaterialButton

    // chip filters removed — default to showing all entries
    private val daysWindowState = MutableStateFlow<Int?>(null) // null means all

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
    textViewAverageMoodSummary = findViewById(R.id.textViewAverageMoodSummary)
        fabLogNewMood = findViewById(R.id.fabLogNewMood)
        buttonShareMoodHistory = findViewById(R.id.buttonShareMoodHistory)
        // Embedded calendar view (toggle with header button)
        calendarViewMoodHistory = findViewById(R.id.calendarViewMoodHistory)
    toggleGroupCalendarFilter = findViewById(R.id.toggleGroupCalendarFilter)
    buttonFilter1d = findViewById(R.id.buttonFilter1d)
    buttonFilter7d = findViewById(R.id.buttonFilter7d)
    buttonFilter30d = findViewById(R.id.buttonFilter30d)
    buttonFilterAll = findViewById(R.id.buttonFilterAll)
        val buttonCalendarOpen: ImageButton? = findViewById(R.id.buttonCalendarOpen)
        buttonCalendarOpen?.setOnClickListener {
            // Toggle calendar visibility since it's now embedded in this layout
            try {
                calendarViewMoodHistory?.let { cal ->
                    cal.visibility = if (cal.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                    // scroll to top when showing
                    if (cal.visibility == View.VISIBLE) recyclerViewMoodHistory.scrollToPosition(0)
                }
            } catch (e: Exception) {
                Log.e("MoodHistoryActivity", "Failed to toggle embedded calendar", e)
            }
        }

        setupRecyclerView()
        observeMoodDataForRecyclerView()

    setupFilterToggleGroup()

        // Calendar selection -> filter RecyclerView to that day
        calendarViewMoodHistory?.setOnDateChangeListener { _, year, month, dayOfMonth ->
            lifecycleScope.launch {
                try {
                    val startCal = java.util.Calendar.getInstance().apply {
                        set(java.util.Calendar.YEAR, year)
                        set(java.util.Calendar.MONTH, month)
                        set(java.util.Calendar.DAY_OF_MONTH, dayOfMonth)
                        set(java.util.Calendar.HOUR_OF_DAY, 0)
                        set(java.util.Calendar.MINUTE, 0)
                        set(java.util.Calendar.SECOND, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                    }
                    val endCal = java.util.Calendar.getInstance().apply {
                        timeInMillis = startCal.timeInMillis
                        set(java.util.Calendar.HOUR_OF_DAY, 23)
                        set(java.util.Calendar.MINUTE, 59)
                        set(java.util.Calendar.SECOND, 59)
                        set(java.util.Calendar.MILLISECOND, 999)
                    }

                    val allEntries = moodViewModel.allMoodEntriesSorted.first()
                    val filtered = allEntries.filter { it.timestamp in startCal.timeInMillis..endCal.timeInMillis }
                    moodEntriesAdapter.updateData(filtered)
                    // Update average summary for selected day
                    updateAverageSummary(filtered, getString(R.string.mood_filter_1d))
                    if (filtered.isEmpty()) {
                        textViewNoMoods.visibility = View.VISIBLE
                        recyclerViewMoodHistory.visibility = View.GONE
                    } else {
                        textViewNoMoods.visibility = View.GONE
                        recyclerViewMoodHistory.visibility = View.VISIBLE
                    }
                } catch (e: Exception) {
                    Log.e("MoodHistoryActivity", "Error filtering by selected date", e)
                }
            }
        }

        fabLogNewMood.setOnClickListener {
            startActivity(Intent(this, MoodLogActivity::class.java))
        }

        buttonShareMoodHistory.setOnClickListener {
            shareMoodSummary()
        }

        // chipGroupFilters removed: we use 'daysWindowState' set to null to show all entries by default

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
                // update average summary for current filter window
                val windowLabel = when (daysWindowState.value) {
                    null -> getString(R.string.mood_filter_all)
                    1 -> getString(R.string.mood_filter_1d)
                    7 -> getString(R.string.mood_filter_7d)
                    14 -> getString(R.string.mood_filter_14d)
                    30 -> getString(R.string.mood_filter_30d)
                    else -> getString(R.string.mood_filter_7d)
                }
                updateAverageSummary(filteredEntries, windowLabel)

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

    private fun updateAverageSummary(entries: List<DbMoodEntry>, label: String) {
        try {
            if (entries.isEmpty()) {
                textViewAverageMoodSummary.visibility = View.GONE
                return
            }
            val avg = entries.map { it.moodLevel }.average()
            val rounded = avg.toInt().coerceIn(1, 5)
            val emoji = moodEntriesAdapter.getEmojiForMoodLevel(rounded)
            val summary = getString(R.string.average_mood_summary_format, emoji, label)
            textViewAverageMoodSummary.text = summary
            textViewAverageMoodSummary.visibility = View.VISIBLE
        } catch (e: Exception) {
            Log.e("MoodHistoryActivity", "Error updating average summary", e)
            textViewAverageMoodSummary.visibility = View.GONE
        }
    }

    private fun setupFilterToggleGroup() {
        try {
            // Load persisted filter choice
            val prefs = getSharedPreferences("mood_prefs", MODE_PRIVATE)
            val saved = prefs.getString("calendar_filter_choice", "All") ?: "All"
            // Map saved to button id selection
            val idToCheck = when (saved) {
                "1d" -> R.id.buttonFilter1d
                "7d" -> R.id.buttonFilter7d
                "30d" -> R.id.buttonFilter30d
                else -> R.id.buttonFilterAll
            }
            toggleGroupCalendarFilter.check(idToCheck)

            toggleGroupCalendarFilter.addOnButtonCheckedListener { group, checkedId, isChecked ->
                if (!isChecked) return@addOnButtonCheckedListener
                val choice = when (checkedId) {
                    R.id.buttonFilter1d -> { daysWindowState.value = 1; "1d" }
                    R.id.buttonFilter7d -> { daysWindowState.value = 7; "7d" }
                    R.id.buttonFilter30d -> { daysWindowState.value = 30; "30d" }
                    else -> { daysWindowState.value = null; "All" }
                }
                // persist
                prefs.edit().putString("calendar_filter_choice", choice).apply()
            }
        } catch (e: Exception) {
            Log.e("MoodHistoryActivity", "Failed to setup filter toggle group", e)
        }
    }

    private fun showCalendar(visible: Boolean) {
        try {
            val cal = calendarViewMoodHistory ?: return
            if (visible) {
                cal.animate().alpha(1f).setDuration(200).withStartAction { cal.visibility = View.VISIBLE }
            } else {
                cal.animate().alpha(0f).setDuration(200).withEndAction { cal.visibility = View.GONE }
            }
        } catch (e: Exception) {
            Log.e("MoodHistoryActivity", "Error toggling calendar visibility", e)
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

    // Chart-related code removed; chart now lives in Profile screen
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
