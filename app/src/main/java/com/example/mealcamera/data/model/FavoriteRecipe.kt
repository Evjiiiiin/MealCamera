package com.example.mealcamera.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteRecipe(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: String,
    val recipeId: Long,
    val firestoreId: String?,
    val addedAt: Long = System.currentTimeMillis()
)