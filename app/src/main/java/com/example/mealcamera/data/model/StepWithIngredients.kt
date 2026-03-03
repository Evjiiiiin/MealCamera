package com.example.mealcamera.data.model

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class StepWithIngredients(
    @Embedded val step: RecipeStep,

    @Relation(
        parentColumn = "stepId",
        entityColumn = "ingredientId",
        associateBy = Junction(StepIngredient::class)
    )
    val ingredients: List<Ingredient>
)