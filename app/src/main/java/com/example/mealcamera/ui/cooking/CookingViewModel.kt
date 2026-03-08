package com.example.mealcamera.ui.cooking

import androidx.lifecycle.ViewModel
import com.example.mealcamera.data.RecipeRepository
import com.example.mealcamera.data.model.CookingStepWithIngredients
import kotlinx.coroutines.flow.Flow

class CookingViewModel(
    private val repository: RecipeRepository
) : ViewModel() {

    fun getStepsWithIngredients(recipeId: Long, portions: Int): Flow<List<CookingStepWithIngredients>> =
        repository.getCookingStepsWithIngredients(recipeId, portions)

    suspend fun saveToHistory(recipeId: Long, _recipeName: String) {
        if (recipeId <= 0L) return
        repository.incrementRecipePopularity(recipeId)
    }
}