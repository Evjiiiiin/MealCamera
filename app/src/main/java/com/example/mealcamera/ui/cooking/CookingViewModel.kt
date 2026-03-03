package com.example.mealcamera.ui.cooking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mealcamera.data.RecipeRepository
import com.example.mealcamera.data.model.StepWithIngredients
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class CookingViewModel(
    private val repository: RecipeRepository
) : ViewModel() {

    fun getStepsWithIngredients(recipeId: Long): Flow<List<StepWithIngredients>> =
        repository.getStepsWithIngredients(recipeId)

    suspend fun saveToHistory(recipeId: Long, recipeName: String) {
        // TODO: Добавить сохранение в историю
        // Это будет в следующей задаче
    }
}