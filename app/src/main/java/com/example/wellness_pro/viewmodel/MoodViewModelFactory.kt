package com.example.wellness_pro.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.wellness_pro.db.MoodDao

class MoodViewModelFactory(private val moodDao: MoodDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MoodViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MoodViewModel(moodDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
