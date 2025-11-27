package com.example.mealcamera.data

import com.example.mealcamera.data.dao.RecipeDao
import com.example.mealcamera.data.model.Ingredient
import com.example.mealcamera.data.model.IngredientWithDetails
import com.example.mealcamera.data.model.Recipe
import com.example.mealcamera.data.model.RecipeResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class RecipeRepository(private val recipeDao: RecipeDao) {

    val allRecipes: Flow<List<Recipe>> = recipeDao.getAllRecipes()

    fun getRecipeById(recipeId: Long): Flow<Recipe> = recipeDao.getRecipeById(recipeId)

    suspend fun incrementRecipePopularity(recipeId: Long) = recipeDao.incrementPopularity(recipeId)

    fun getIngredientsForRecipe(recipeId: Long) = flow {
        val recipeWithDetails = recipeDao.getRecipeWithIngredientsById(recipeId)

        val ingredientsWithDetails = recipeWithDetails.ingredients.map { ingredient ->
            val crossRef = recipeDao.getCrossRef(recipeId, ingredient.ingredientId)
            IngredientWithDetails(
                ingredient = ingredient,
                quantity = crossRef?.quantity ?: "",
                unit = crossRef?.unit ?: ""
            )
        }
        emit(ingredientsWithDetails)
    }

    suspend fun filterRecipesByAvailableIngredients(
        scannedIngredientNames: List<String>
    ): Triple<List<RecipeResult>, List<RecipeResult>, List<RecipeResult>> {
        val allRecipesWithIngredients = recipeDao.getAllRecipesWithIngredients()
        val allDbIngredients = recipeDao.getAllIngredients()

        val alwaysAvailableIds = allDbIngredients
            .filter { it.isAlwaysAvailable }
            .map { it.ingredientId }
            .toSet()

        val scannedIds = allDbIngredients
            .filter { dbIngredient ->
                scannedIngredientNames.any { scannedName ->
                    dbIngredient.name.trim().equals(scannedName.trim(), ignoreCase = true)
                }
            }
            .map { it.ingredientId }
            .toSet()

        if (scannedIds.isEmpty() && scannedIngredientNames.none { it.equals("вода", ignoreCase = true) }) {
            return Triple(emptyList(), emptyList(), emptyList())
        }

        val perfectMatches = mutableListOf<RecipeResult>()
        val oneMissingMatches = mutableListOf<RecipeResult>()
        val twoMissingMatches = mutableListOf<RecipeResult>()

        for (recipeWithIngredients in allRecipesWithIngredients) {
            val recipe = recipeWithIngredients.recipe

            // --- ИСПРАВЛЕНО: Добавляем "защиту от заглушек" ---
            // Пропускаем рецепты, если у них пустое имя или имя-заглушка.
            if (recipe.name.isBlank() || recipe.name.contains("Новый рецепт", ignoreCase = true)) {
                continue // Переходим к следующему рецепту
            }

            val ingredients = recipeWithIngredients.ingredients

            val missingIngredients = ingredients.filter { ingredient ->
                val id = ingredient.ingredientId
                id !in scannedIds && id !in alwaysAvailableIds
            }

            val missingCount = missingIngredients.size
            val missingNames = missingIngredients.map { it.name }

            when (missingCount) {
                0 -> perfectMatches.add(RecipeResult(recipe, emptyList()))
                1 -> oneMissingMatches.add(RecipeResult(recipe, missingNames))
                2 -> twoMissingMatches.add(RecipeResult(recipe, missingNames))
            }
        }

        return Triple(perfectMatches, oneMissingMatches, twoMissingMatches)
    }
}
