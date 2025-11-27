package com.example.mealcamera.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mealcamera.data.RecipeRepository
import com.example.mealcamera.data.model.IngredientWithDetails
import com.example.mealcamera.data.model.Recipe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RecipeDetailViewModel(private val repository: RecipeRepository) : ViewModel() {
    private val _recipe = MutableStateFlow<Recipe?>(null)
    val recipe = _recipe.asStateFlow()

    private val _ingredients = MutableStateFlow<List<IngredientWithDetails>>(emptyList())
    val ingredients = _ingredients.asStateFlow()

    fun loadRecipe(recipeId: Long) {
        // --- ИСПРАВЛЕНО: Используем правильные, существующие методы репозитория ---

        // 1. Загружаем основную информацию о рецепте
        viewModelScope.launch {
            repository.getRecipeById(recipeId).collect { recipe ->
                _recipe.value = recipe
            }
        }

        // 2. Загружаем список ингредиентов для этого рецепта
        viewModelScope.launch {
            repository.getIngredientsForRecipe(recipeId).collect { details ->
                _ingredients.value = details
            }
        }

        // 3. Увеличиваем счетчик популярности рецепта
        viewModelScope.launch {
            repository.incrementRecipePopularity(recipeId)
        }
    }
}
