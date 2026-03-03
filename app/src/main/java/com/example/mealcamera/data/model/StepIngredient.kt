package com.example.mealcamera.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "step_ingredients",
    foreignKeys = [
        ForeignKey(
            entity = RecipeStep::class,
            parentColumns = ["stepId"],
            childColumns = ["stepId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Ingredient::class,
            parentColumns = ["ingredientId"],
            childColumns = ["ingredientId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("stepId"), Index("ingredientId")]
)
data class StepIngredient(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val stepId: Long,
    val ingredientId: Long,
    val quantity: String,
    val unit: String
)