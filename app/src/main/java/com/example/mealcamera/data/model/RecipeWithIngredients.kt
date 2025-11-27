package com.example.mealcamera.data.model

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

/**
 * Сущность для получения рецепта со списком базовых ингредиентов (без quantity/unit)
 * Используется для общих запросов, где нужны только ID ингредиентов
 */
data class RecipeWithIngredients(
    @Embedded val recipe: Recipe,

    @Relation(
        parentColumn = "recipeId",
        entityColumn = "ingredientId",
        associateBy = Junction(RecipeIngredientCrossRef::class)
    )
    val ingredients: List<Ingredient>
)