package com.example.mealcamera.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recipes",
    indices = [
        Index(value = ["authorId"], name = "index_recipes_authorId"),
        Index(value = ["isPublic"], name = "index_recipes_isPublic")
    ]
)
data class Recipe(
    @PrimaryKey(autoGenerate = true)
    val recipeId: Long = 0,
    val firestoreId: String? = null,
    val name: String,
    val description: String,
    val imagePath: String,
    val category: String,
    val prepTime: String,
    val popularityScore: Int = 0,
    val cuisine: String = "Русская",
    val cuisineCode: String = "RU",
    
    @ColumnInfo(name = "authorId", defaultValue = "admin")
    val createdByUserId: String? = null,
    
    @ColumnInfo(name = "isPublic", defaultValue = "1")
    val isPublicRecipe: Boolean? = true
)