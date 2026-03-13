package com.example.mealcamera.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mealcamera.data.FavoriteRepository
import com.example.mealcamera.data.RecipeRepository
import com.example.mealcamera.data.model.IngredientWithDetails
import com.example.mealcamera.data.model.Recipe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class RecipeDetailViewModel(
    private val recipeRepository: RecipeRepository,
    private val favoriteRepository: FavoriteRepository
) : ViewModel() {

    fun getRecipeById(recipeId: Long): Flow<Recipe> = recipeRepository.getRecipeById(recipeId)

    fun getIngredientsForRecipe(recipeId: Long): Flow<List<IngredientWithDetails>> =
        recipeRepository.getIngredientsForRecipe(recipeId)

    fun toggleFavorite(recipeId: Long) {
        viewModelScope.launch {
            val recipe = recipeRepository.getRecipeById(recipeId).firstOrNull() ?: return@launch

            val isFavorite = favoriteRepository.isFavorite(recipeId)
            if (isFavorite) {
                favoriteRepository.removeFromFavorites(recipeId)
            } else {
                favoriteRepository.addToFavorites(recipeId)
            }

            recipeRepository.incrementRecipePopularity(recipe.recipeId)
        }
    }

    suspend fun getRecipeByIdOnce(recipeId: Long): Recipe? =
        recipeRepository.getRecipeById(recipeId).firstOrNull()
}