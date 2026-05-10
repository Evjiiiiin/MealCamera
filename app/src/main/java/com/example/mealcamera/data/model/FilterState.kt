package com.example.mealcamera.data.model

import java.io.Serializable

data class FilterState(
    val selectedCategories: Set<String> = emptySet(),
    val selectedCuisines: Set<String> = emptySet(),
    val prepTimeRange: ClosedRange<Float>? = null,
    val caloriesRange: ClosedRange<Float>? = null
) : Serializable