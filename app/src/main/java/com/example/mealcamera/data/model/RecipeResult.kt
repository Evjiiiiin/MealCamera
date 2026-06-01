package com.example.mealcamera.data.model

data class RecipeResult(
    val recipe: Recipe,
    val missingIngredients: List<String> = emptyList(),
    val structuredMissingIngredients: List<MissingIngredientData> = emptyList(),
    val availablePortions: Int? = null,
    val targetPortions: Int = 1
)

data class MissingIngredientData(
    val name: String,
    val quantity: Double,
    val unit: String
)