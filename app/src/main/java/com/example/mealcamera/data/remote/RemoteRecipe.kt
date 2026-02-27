package com.example.mealcamera.data.remote

import com.google.firebase.firestore.PropertyName

data class RemoteRecipe(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val imagePath: String = "", // Ссылка на Firebase Storage
    val category: String = "Все",
    val prepTime: String = "",
    val calories: Int = 0,      // Задел на КБЖУ
    val protein: Double = 0.0,
    val fat: Double = 0.0,
    val carbs: Double = 0.0,
    val ingredients: List<RemoteIngredient> = emptyList(),
    val steps: List<String> = emptyList(),
    val authorId: String = "admin", // Чтобы понимать, чей рецепт
    val isPublic: Boolean = true
)

data class RemoteIngredient(
    val name: String = "",
    val quantity: String = "",
    val unit: String = ""
)