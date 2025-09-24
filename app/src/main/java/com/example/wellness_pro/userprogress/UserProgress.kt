package com.example.wellness_pro.model // Or whatever package you chose

data class UserProgress(
    var currentXp: Int = 0,
    var currentLevel: Int = 1,
    var xpToNextLevel: Int = 100
)