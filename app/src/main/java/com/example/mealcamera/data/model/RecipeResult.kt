package com.example.mealcamera.data.model

data class RecipeResult(
    val recipe: Recipe,
    val missingIngredients: List<String> = emptyList()
) {
    val canCook: Boolean
        get() = missingIngredients.isEmpty()
}
