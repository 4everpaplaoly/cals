package com.example.cals.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface MealDao {

    @Insert
    suspend fun insertMeal(meal: MealEntity)

    // 오늘 날짜 데이터 가져오기
    @Query("SELECT * FROM meals WHERE date = :date")
    suspend fun getMealsByDate(date: String): List<MealEntity>

    // 모든 데이터(리포트용)
    @Query("SELECT * FROM meals")
    suspend fun getAllMeals(): List<MealEntity>
}
