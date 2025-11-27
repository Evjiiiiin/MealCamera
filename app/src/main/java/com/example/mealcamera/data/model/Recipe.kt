package com.example.mealcamera.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recipes")
data class Recipe(
    @PrimaryKey(autoGenerate = true)
    val recipeId: Long = 0,
    val name: String,
    val description: String,
    val imagePath: String,
    val category: String,
    val prepTime: String,
    val popularityScore: Int = 0 // Добавлено для сортировки
)