package com.example.wellness_pro.ui

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
// Removed: import androidx.appcompat.app.AppCompatActivity - No longer needed directly
import com.example.wellness_pro.R
import com.example.wellness_pro.models.MoodEntry
import com.example.wellness_pro.navbar.BaseBottomNavActivity // Added import
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class MoodLogActivity : BaseBottomNavActivity() { // Changed superclass

    // This property tells BaseBottomNavActivity which nav item to highlight
    override val currentNavControllerItemId: Int
        get() = R.id.navButtonMoodJournal

    // Add this override to provide the layout for BaseBottomNavActivity
    override val layoutId: Int
        get() = R.layout.activity_mood_log

    private lateinit var emojiHappy: TextView
    private lateinit var emojiSad: TextView
    private lateinit var emojiAngry: TextView
    private lateinit var emojiNeutral: TextView
    private lateinit var editTextMoodNote: EditText
    private lateinit var buttonSaveMood: Button

    private var selectedEmoji: String? = null
    private val moodEntriesKey = "mood_entries_list_json" // SharedPreferences key

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // setContentView(R.layout.activity_mood_log) // Handled by BaseBottomNavActivity via layoutId

        emojiHappy = findViewById(R.id.emojiHappy)
        emojiSad = findViewById(R.id.emojiSad)
        emojiAngry = findViewById(R.id.emojiAngry)
        emojiNeutral = findViewById(R.id.emojiNeutral)
        editTextMoodNote = findViewById(R.id.editTextMoodNote)
        buttonSaveMood = findViewById(R.id.buttonSaveMood)

        setupEmojiClickListeners()

        buttonSaveMood.setOnClickListener {
            saveMoodEntry()
        }
    }

    private fun setupEmojiClickListeners() {
        val emojiMap = mapOf(
            emojiHappy to "😊",
            emojiSad to "😢",
            emojiAngry to "😠",
            emojiNeutral to "😐"
        )

        emojiMap.forEach { (textView, emoji) ->
            textView.setOnClickListener {
                selectedEmoji = emoji
                highlightSelectedEmoji(textView)
                Log.d("MoodLogActivity", "Selected emoji: $selectedEmoji")
            }
        }
    }

    private fun highlightSelectedEmoji(selectedView: TextView) {
        emojiHappy.alpha = if (emojiHappy == selectedView) 1.0f else 0.5f
        emojiSad.alpha = if (emojiSad == selectedView) 1.0f else 0.5f
        emojiAngry.alpha = if (emojiAngry == selectedView) 1.0f else 0.5f
        emojiNeutral.alpha = if (emojiNeutral == selectedView) 1.0f else 0.5f
    }

    private fun saveMoodEntry() {
        if (selectedEmoji == null) {
            Toast.makeText(this, "Please select how you are feeling.", Toast.LENGTH_SHORT).show()
            return
        }

        val note = editTextMoodNote.text.toString().trim()
        val moodEntry = MoodEntry(
            moodEmoji = selectedEmoji!!,
            note = if (note.isNotEmpty()) note else null
        )

        try {
            val prefs = getSharedPreferences("WellnessProPrefs", MODE_PRIVATE)
            val gson = Gson()
            val json = prefs.getString(moodEntriesKey, null)
            val typeToken = object : TypeToken<MutableList<MoodEntry>>() {}.type
            val moodEntries: MutableList<MoodEntry> = if (json != null) {
                gson.fromJson(json, typeToken)
            } else {
                mutableListOf()
            }

            moodEntries.add(0, moodEntry)

            val editor = prefs.edit()
            editor.putString(moodEntriesKey, gson.toJson(moodEntries))
            editor.apply()

            Toast.makeText(this, "Mood logged!", Toast.LENGTH_SHORT).show()
            Log.i("MoodLogActivity", "Mood entry saved: ${moodEntry.id}")
            // Navigate to MoodHistoryActivity after saving, or just finish if user should stay on the current screen's underlying view (e.g. dashboard)
            // For now, let's assume MoodLogActivity is on top of another screen from the nav bar.
            // If MoodLogActivity IS the 'Mood Journal' screen, it shouldn't finish, or it should navigate to MoodHistory.
            // For simplicity, let's keep finish() for now. If you want to go to history, replace finish() with:
            // startActivity(Intent(this, MoodHistoryActivity::class.java))
            // finish()
            finish() 

        } catch (e: Exception) {
            Log.e("MoodLogActivity", "Error saving mood entry", e)
            Toast.makeText(this, "Error saving mood.", Toast.LENGTH_SHORT).show()
        }
    }
}
