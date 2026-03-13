package com.example.mealcamera.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.Index

@Entity(
    tableName = "recipes",
    indices = [
        Index(value = ["authorId"]),
        Index(value = ["isPublic"])
    ]
)
data class Recipe(
    @PrimaryKey(autoGenerate = true)
    val recipeId: Long = 0,

    @ColumnInfo(name = "firestoreId")
    val firestoreId: String? = null,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "description")
    val description: String,

    @ColumnInfo(name = "imagePath")
    val imagePath: String,

    @ColumnInfo(name = "category")
    val category: String,

    @ColumnInfo(name = "prepTime")
    val prepTime: String,

    @ColumnInfo(name = "popularityScore")
    val popularityScore: Int = 0,

    @ColumnInfo(name = "cuisine")
    val cuisine: String = "Русская",

    @ColumnInfo(name = "cuisineCode")
    val cuisineCode: String = "RU",

    // Новые поля для авторства
    @ColumnInfo(name = "authorId", defaultValue = "admin")
    val authorId: String = "admin",

    @ColumnInfo(name = "isPublic", defaultValue = "1")
    val isPublic: Boolean = true
)