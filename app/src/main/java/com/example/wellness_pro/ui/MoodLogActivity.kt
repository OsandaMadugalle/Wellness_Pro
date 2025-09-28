package com.example.wellness_pro.ui

import android.content.Intent // ADDED
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView 
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.wellness_pro.R
import com.example.wellness_pro.db.AppDatabase
import com.example.wellness_pro.navbar.BaseBottomNavActivity
import com.example.wellness_pro.viewmodel.MoodViewModel
import com.example.wellness_pro.viewmodel.MoodViewModelFactory

class MoodLogActivity : BaseBottomNavActivity() {

    override val currentNavControllerItemId: Int
        get() = R.id.navButtonMoodJournal

    override val layoutId: Int
        get() = R.layout.activity_mood_log

    private lateinit var moodViewModel: MoodViewModel

    private lateinit var moodSelectionLayouts: List<View>
    private lateinit var editTextMoodNotes: EditText
    private lateinit var buttonSaveMood: Button
    private lateinit var textViewMotivationalMessage: TextView

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
            finish() 
            return
        }

        try {
            editTextMoodNotes = findViewById(R.id.editTextMoodNotes)
            buttonSaveMood = findViewById(R.id.buttonSaveMood)
            textViewMotivationalMessage = findViewById(R.id.textViewMotivationalMessage)

            moodSelectionLayouts = listOf(
                findViewById(R.id.moodOption1),
                findViewById(R.id.moodOption2),
                findViewById(R.id.moodOption3),
                findViewById(R.id.moodOption4),
                findViewById(R.id.moodOption5)
            )
        } catch (e: NullPointerException) {
            Log.e("MoodLogActivity", "Error finding UI elements. Check IDs in XML.", e)
            Toast.makeText(this, "Error loading mood logging UI.", Toast.LENGTH_LONG).show()
            finish() 
            return
        }

        setupMoodInputListeners()
        updateMoodSelectionVisuals(0) 

        buttonSaveMood.setOnClickListener {
            saveMoodEntryViaViewModel()
        }
    }

    private fun setupMoodInputListeners() {
        moodSelectionLayouts.forEachIndexed { index, layout ->
            layout.setOnClickListener {
                selectedMoodLevel = index + 1 
                updateMoodSelectionVisuals(selectedMoodLevel)
            }
        }
    }

    private fun updateMoodSelectionVisuals(selectedLevel: Int) {
        moodSelectionLayouts.forEachIndexed { index, layout ->
            val isSelected = (index + 1) == selectedLevel
            if (isSelected) {
                layout.setBackgroundColor(ContextCompat.getColor(this, R.color.button_blue_selected))
            } else {
                layout.setBackgroundColor(Color.TRANSPARENT) 
            }
        }

        when (selectedLevel) {
            1 -> {
                textViewMotivationalMessage.text = "It's okay to not be okay. Be gentle with yourself today."
                textViewMotivationalMessage.visibility = View.VISIBLE
            }
            2 -> {
                textViewMotivationalMessage.text = "Tough times don't last, but tough people do. You've got this."
                textViewMotivationalMessage.visibility = View.VISIBLE
            }
            3 -> {
                textViewMotivationalMessage.text = "Taking a moment to pause is a strength. What's one small thing you can do for yourself?"
                textViewMotivationalMessage.visibility = View.VISIBLE
            }
            4 -> {
                textViewMotivationalMessage.text = "Good to see you're feeling okay! Keep that positive momentum going."
                textViewMotivationalMessage.visibility = View.VISIBLE
            }
            5 -> {
                textViewMotivationalMessage.text = "Awesome! Glad you're feeling happy. Spread that positivity!"
                textViewMotivationalMessage.visibility = View.VISIBLE
            }
            else -> {
                textViewMotivationalMessage.visibility = View.GONE
                textViewMotivationalMessage.text = "" 
            }
        }
    }

    private fun saveMoodEntryViaViewModel() {
        if (selectedMoodLevel == 0) {
            Toast.makeText(this, "Please select your current mood", Toast.LENGTH_SHORT).show()
            return
        }
        val notes = editTextMoodNotes.text.toString().trim()
        moodViewModel.insertMoodEntry(selectedMoodLevel, if (notes.isNotEmpty()) notes else null)
        Toast.makeText(this, "Mood saved!", Toast.LENGTH_SHORT).show()

        // Navigate to MoodHistoryActivity
        val intent = Intent(this, MoodHistoryActivity::class.java)
        startActivity(intent)
        finish() // Finish MoodLogActivity
    }
}
