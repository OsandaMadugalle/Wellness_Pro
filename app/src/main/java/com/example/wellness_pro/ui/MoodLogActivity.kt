package com.example.wellness_pro.ui

import android.graphics.Color
import android.os.Bundle
import android.util.Log
// import android.view.View // No longer needed
import android.widget.Button
import android.widget.EditText
// import android.widget.TextView // No longer needed
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
// import androidx.lifecycle.lifecycleScope // No longer needed
import com.example.wellness_pro.R
import com.example.wellness_pro.db.AppDatabase
// import com.example.wellness_pro.db.MoodEntry // Still used by ViewModel indirectly
import com.example.wellness_pro.navbar.BaseBottomNavActivity
import com.example.wellness_pro.viewmodel.MoodViewModel
import com.example.wellness_pro.viewmodel.MoodViewModelFactory
// No MPAndroidChart imports needed
// import kotlinx.coroutines.launch // No longer needed
// No Date/Time imports needed for chart

class MoodLogActivity : BaseBottomNavActivity() {

    override val currentNavControllerItemId: Int
        get() = R.id.navButtonMoodJournal

    override val layoutId: Int
        get() = R.layout.activity_mood_log

    private lateinit var moodViewModel: MoodViewModel

    private lateinit var moodButtons: List<Button>
    private lateinit var editTextMoodNotes: EditText
    private lateinit var buttonSaveMood: Button
    // private lateinit var moodChart: LineChart // REMOVED
    // private lateinit var textViewNoMoodsChart: TextView // REMOVED

    private var selectedMoodLevel: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            val moodDao = AppDatabase.getInstance().moodDao()
            val factory = MoodViewModelFactory(moodDao)
            moodViewModel = ViewModelProvider(this, factory)[MoodViewModel::class.java]
        } catch (e: IllegalStateException) {
            Log.e("MoodLogActivity", "Error initializing MoodViewModel. AppDatabase might not be initialized.", e)
            Toast.makeText(this, "Error initializing mood tracking components.", Toast.LENGTH_LONG).show()
            return
        }

        try {
            editTextMoodNotes = findViewById(R.id.editTextMoodNotes)
            buttonSaveMood = findViewById(R.id.buttonSaveMood)

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
                button.setBackgroundColor(Color.CYAN) // Example highlight
                button.setTextColor(Color.BLACK)
            } else {
                button.setBackgroundColor(Color.TRANSPARENT) // Example default
                button.setTextColor(Color.WHITE)
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

        editTextMoodNotes.text.clear()
        selectedMoodLevel = 0
        updateMoodButtonSelection(0)
    }
}
