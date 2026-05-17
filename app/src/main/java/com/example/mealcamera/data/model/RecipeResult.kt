package com.example.mealcamera.data.model

data class RecipeResult(
    val recipe: Recipe,
    val missingIngredients: List<String> = emptyList(),
    val structuredMissingIngredients: List<MissingIngredientData> = emptyList()
)

data class MissingIngredientData(
    val name: String,
    val quantity: Double,
    val unit: String
)
