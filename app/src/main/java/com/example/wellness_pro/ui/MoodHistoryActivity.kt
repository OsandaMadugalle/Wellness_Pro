package com.example.wellness_pro.ui

import android.content.Intent // Added import
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
// Removed: import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.wellness_pro.R
import com.example.wellness_pro.models.MoodEntry
import com.example.wellness_pro.navbar.BaseBottomNavActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton // Added import
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MoodHistoryActivity : BaseBottomNavActivity() { // Changed superclass

    // This property tells BaseBottomNavActivity which nav item to highlight
    override val currentNavControllerItemId: Int
        get() = R.id.navButtonMoodJournal

    // Add this override for layoutId
    override val layoutId: Int
        get() = R.layout.activity_mood_history

    private lateinit var recyclerViewMoodHistory: RecyclerView
    private lateinit var textViewNoMoods: TextView
    private lateinit var moodEntriesAdapter: MoodEntriesAdapter
    private val moodEntriesList = mutableListOf<MoodEntry>()
    private val moodEntriesKey = "mood_entries_list_json" // SharedPreferences key (same as in MoodLogActivity)
    private lateinit var fabLogNewMood: FloatingActionButton // Added FAB variable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // setContentView(R.layout.activity_mood_history) // This will likely be handled by BaseBottomNavActivity now

        recyclerViewMoodHistory = findViewById(R.id.recyclerViewMoodHistory)
        textViewNoMoods = findViewById(R.id.textViewNoMoods)
        fabLogNewMood = findViewById(R.id.fabLogNewMood) // Initialize FAB

        setupRecyclerView()
        // loadMoodEntries() // Moved to onResume for consistent refresh

        fabLogNewMood.setOnClickListener { // Set click listener for FAB
            startActivity(Intent(this, MoodLogActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        loadMoodEntries() // Load/refresh mood entries when the activity resumes
    }

    private fun setupRecyclerView() {
        moodEntriesAdapter = MoodEntriesAdapter(moodEntriesList)
        recyclerViewMoodHistory.layoutManager = LinearLayoutManager(this)
        recyclerViewMoodHistory.adapter = moodEntriesAdapter
    }

    private fun loadMoodEntries() {
        try {
            val prefs = getSharedPreferences("WellnessProPrefs", MODE_PRIVATE) // Context is available from Activity
            val gson = Gson()
            val json = prefs.getString(moodEntriesKey, null)
            val typeToken = object : TypeToken<List<MoodEntry>>() {}.type // Use List here for type safety

            if (json != null) {
                val entries: List<MoodEntry> = gson.fromJson(json, typeToken)
                moodEntriesList.clear()
                moodEntriesList.addAll(entries)
                moodEntriesAdapter.notifyDataSetChanged()
            }

            if (moodEntriesList.isEmpty()) {
                textViewNoMoods.visibility = View.VISIBLE
                recyclerViewMoodHistory.visibility = View.GONE
            } else {
                textViewNoMoods.visibility = View.GONE
                recyclerViewMoodHistory.visibility = View.VISIBLE
            }
            Log.d("MoodHistoryActivity", "Loaded ${moodEntriesList.size} mood entries.")

        } catch (e: Exception) {
            Log.e("MoodHistoryActivity", "Error loading mood entries", e)
            textViewNoMoods.visibility = View.VISIBLE
            textViewNoMoods.text = "Error loading mood history."
            recyclerViewMoodHistory.visibility = View.GONE
        }
    }
}

class MoodEntriesAdapter(private val moodEntries: List<MoodEntry>) :
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
        val moodEntry = moodEntries[position]
        holder.emojiTextView.text = moodEntry.moodEmoji

        try {
            val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            holder.timestampTextView.text = sdf.format(Date(moodEntry.timestamp))
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
