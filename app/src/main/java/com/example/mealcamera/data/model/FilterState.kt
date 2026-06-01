package com.example.mealcamera.data.model

import java.io.Serializable

const val DEFAULT_MAX_CALORIES_PER_PORTION = 2000f

data class FilterState(
    val selectedCategories: Set<String> = emptySet(),
    val selectedCuisines: Set<String> = emptySet(),
    val minPrepTime: Float = 0f,
    val maxPrepTime: Float = 240f,
    val minCalories: Float = 0f,
    val maxCalories: Float = DEFAULT_MAX_CALORIES_PER_PORTION
) : Serializable