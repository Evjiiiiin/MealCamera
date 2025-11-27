package com.example.mealcamera.data.model

data class IngredientWithDetails(
    val ingredient: Ingredient, // Сам объект Ingredient (id, name, isAlwaysAvailable...)
    val quantity: String,       // Количество, необходимое для рецепта (например, "200")
    val unit: String            // Мера измерения (например, "гр", "шт")
)
