package com.example.mealcamera.data.remote

data class RemoteRecipe(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val imagePath: String = "",
    val category: String = "",
    val prepTime: String = "",
    val popularityScore: Int = 0,
    val cuisine: String = "Русская",
    val cuisineCode: String = "RU",
    val authorId: String = "admin",
    val isPublic: Boolean = true,
    
    // КБЖУ
    val calories: Int = 0,
    val proteins: Double = 0.0,
    val fats: Double = 0.0,
    val carbs: Double = 0.0
)