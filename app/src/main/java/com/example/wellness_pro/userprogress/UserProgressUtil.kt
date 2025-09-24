package com.example.wellness_pro.util

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.example.wellness_pro.model.UserProgress // ***** CORRECTED IMPORT *****
import com.google.gson.Gson

object UserProgressUtil {

    // Use constants from ChallengesScreen or define them uniquely here if they differ
    // For consistency, it's better if these are shared or the util is the SOLE manager of these keys
    private const val PREFS_USER_PROGRESS_NAME = "PlayPalUserPrefs"
    private const val KEY_USER_PROGRESS = "userProgress_v3" // Use a new key if structure changes or to reset
    private val gson = Gson()

    // Make MAX_LEVEL accessible if needed by other classes, e.g. ChallengesScreen for display
    const val MAX_LEVEL = 99 // Keep MAX_LEVEL consistent

    fun loadUserProgress(context: Context): UserProgress {
        val prefs = context.getSharedPreferences(PREFS_USER_PROGRESS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_USER_PROGRESS, null)
        return if (!jsonString.isNullOrEmpty()) {
            try {
                val progress = gson.fromJson(jsonString, UserProgress::class.java)
                // Ensure xpToNextLevel is correctly initialized if it was Int.MAX_VALUE and loaded as such
                // or if it's 0 due to an older version not having it.
                if (progress.currentLevel < MAX_LEVEL && (progress.xpToNextLevel == Int.MAX_VALUE || progress.xpToNextLevel == 0)) {
                    progress.xpToNextLevel = calculateXpForNextLevel(progress.currentLevel)
                } else if (progress.currentLevel >= MAX_LEVEL) {
                    progress.xpToNextLevel = Int.MAX_VALUE
                }
                progress ?: UserProgress().also {
                    // If creating a new UserProgress, ensure xpToNextLevel is set
                    it.xpToNextLevel = calculateXpForNextLevel(it.currentLevel)
                }
            } catch (e: Exception) {
                Log.e("UserProgressUtil", "Error parsing user progress. Resetting.", e)
                UserProgress().also {
                    it.xpToNextLevel = calculateXpForNextLevel(it.currentLevel)
                }
            }
        } else {
            UserProgress().also {
                it.xpToNextLevel = calculateXpForNextLevel(it.currentLevel)
            }
        }
    }

    fun saveUserProgress(context: Context, userProgress: UserProgress) {
        // Ensure xpToNextLevel is correctly set before saving, especially if max level
        if (userProgress.currentLevel >= MAX_LEVEL) {
            userProgress.xpToNextLevel = Int.MAX_VALUE
        } else if (userProgress.xpToNextLevel == 0 || userProgress.xpToNextLevel == Int.MAX_VALUE) {
            // Recalculate if it seems unset or incorrectly set to MAX_VALUE for a non-max level
            userProgress.xpToNextLevel = calculateXpForNextLevel(userProgress.currentLevel)
        }

        val prefs = context.getSharedPreferences(PREFS_USER_PROGRESS_NAME, Context.MODE_PRIVATE).edit()
        val jsonString = gson.toJson(userProgress)
        prefs.putString(KEY_USER_PROGRESS, jsonString)
        prefs.apply()
        Log.d("UserProgressUtil", "User progress saved: Level ${userProgress.currentLevel}, XP ${userProgress.currentXp}/${userProgress.xpToNextLevel}")
    }

    private fun calculateXpForNextLevel(level: Int): Int {
        if (level < 1) return 100 // Base XP for level 1 to reach level 2
        if (level >= MAX_LEVEL) return Int.MAX_VALUE
        // Example: L1 needs 100 (100 + 0*50), L2 needs 150 (100 + 1*50) to reach L3
        return 100 + (level - 1) * 50
    }

    private fun checkForLevelUp(context: Context, userProgress: UserProgress): Boolean {
        var leveledUp = false
        // Ensure xpToNextLevel is correctly calculated if it's currently Int.MAX_VALUE but not max level
        if (userProgress.currentLevel < MAX_LEVEL && userProgress.xpToNextLevel == Int.MAX_VALUE) {
            userProgress.xpToNextLevel = calculateXpForNextLevel(userProgress.currentLevel)
        }

        while (userProgress.currentXp >= userProgress.xpToNextLevel && userProgress.currentLevel < MAX_LEVEL) {
            userProgress.currentXp -= userProgress.xpToNextLevel
            userProgress.currentLevel++
            userProgress.xpToNextLevel = calculateXpForNextLevel(userProgress.currentLevel) // This will be MAX_VALUE if new level is MAX_LEVEL
            Log.i("UserProgressUtil", "LEVEL UP! New Level: ${userProgress.currentLevel}. XP for next: ${userProgress.xpToNextLevel}")
            leveledUp = true
        }
        if (leveledUp) {
            Toast.makeText(context, "Level Up! You are now Level ${userProgress.currentLevel}!", Toast.LENGTH_LONG).show()
        }
        return leveledUp
    }

    private fun checkForDeLevel(context: Context, userProgress: UserProgress): Boolean {
        var deLeveled = false
        while (userProgress.currentLevel > 1 && userProgress.currentXp < 0) {
            userProgress.currentLevel--
            val xpForPreviousLevelMax = calculateXpForNextLevel(userProgress.currentLevel)
            userProgress.currentXp += xpForPreviousLevelMax
            userProgress.xpToNextLevel = xpForPreviousLevelMax
            Log.i("UserProgressUtil", "DE-LEVELED! New Level: ${userProgress.currentLevel}. XP: ${userProgress.currentXp}/${userProgress.xpToNextLevel}")
            deLeveled = true
        }
        if (userProgress.currentLevel == 1 && userProgress.currentXp < 0) {
            userProgress.currentXp = 0
        }
        if (deLeveled) {
            Toast.makeText(context, "Level Down. You are now Level ${userProgress.currentLevel}.", Toast.LENGTH_LONG).show()
        }
        return deLeveled
    }

    fun addXp(context: Context, amount: Int): UserProgress {
        if (amount <= 0) return loadUserProgress(context)

        val currentProgress = loadUserProgress(context)
        if (currentProgress.currentLevel >= MAX_LEVEL) {
            Log.d("UserProgressUtil", "Max level reached. No XP added.")
            return currentProgress // No more XP if max level
        }

        currentProgress.currentXp += amount
        Log.d("UserProgressUtil", "XP Added: $amount. Current XP before level check: ${currentProgress.currentXp}, Level: ${currentProgress.currentLevel}")

        checkForLevelUp(context.applicationContext, currentProgress)
        saveUserProgress(context, currentProgress)
        return currentProgress
    }

    fun reduceXp(context: Context, amount: Int): UserProgress {
        if (amount <= 0) return loadUserProgress(context)

        val currentProgress = loadUserProgress(context)
        currentProgress.currentXp -= amount

        Log.d("UserProgressUtil", "XP Reduced: $amount. Current XP before de-level check: ${currentProgress.currentXp}")
        checkForDeLevel(context.applicationContext, currentProgress)
        if (currentProgress.currentLevel == 1 && currentProgress.currentXp < 0) {
            currentProgress.currentXp = 0
        }
        saveUserProgress(context, currentProgress)
        return currentProgress
    }
}
