package com.quocdat.lingolens.model

data class User(
    val name: String,
    val targetLevel: String = "B1", // "B1" or "B2"
    val streakDays: Int = 0,
    val wordsLearnedCount: Int = 0,
    val dailyGoalCount: Int = 5
)
