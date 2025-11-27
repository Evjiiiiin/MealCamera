package com.example.mealcamera.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ingredients")
data class Ingredient(
    @PrimaryKey(autoGenerate = true)
    val ingredientId: Long = 0,
    val name: String,
    val isAlwaysAvailable: Boolean = false,
    val isCoreIngredient: Boolean = true
)