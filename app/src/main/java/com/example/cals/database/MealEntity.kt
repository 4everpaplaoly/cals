package com.example.cals.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "meals")
data class MealEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val date: String,          // "2025-11-20"
    val foodName: String,      // ex. "삼겹살"
    val calories: Double,
    val protein: Double,
    val carbs: Double,
    val sugar: Double
)
