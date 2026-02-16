package com.example.mealcamera.data.model

import androidx.room.Embedded

data class IngredientWithDetails(
    @Embedded val ingredient: Ingredient,
    val quantity: String,
    val unit: String
)