package com.example.mealcamera.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mealcamera.data.RecipeRepository
import com.example.mealcamera.data.model.IngredientWithDetails
import com.example.mealcamera.data.model.Recipe
import com.example.mealcamera.data.model.RecipeStep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RecipeDetailViewModel(private val repository: RecipeRepository) : ViewModel() {

    private val _recipe = MutableStateFlow<Recipe?>(null)
    val recipe: StateFlow<Recipe?> = _recipe.asStateFlow()

    private val _ingredients = MutableStateFlow<List<IngredientWithDetails>>(emptyList())
    val ingredients: StateFlow<List<IngredientWithDetails>> = _ingredients.asStateFlow()

    private val _steps = MutableStateFlow<List<RecipeStep>>(emptyList())
    val steps: StateFlow<List<RecipeStep>> = _steps.asStateFlow()

    private val _portions = MutableStateFlow(1)
    val portions: StateFlow<Int> = _portions.asStateFlow()

    fun setPortions(count: Int) {
        if (count in 1..10) {
            _portions.value = count
        }
    }

    fun loadRecipe(recipeId: Long) {
        viewModelScope.launch {
            repository.getRecipeById(recipeId).collect { _recipe.value = it }
        }
        viewModelScope.launch {
            repository.getIngredientsForRecipe(recipeId).collect { _ingredients.value = it }
        }
        viewModelScope.launch {
            repository.getStepsForRecipe(recipeId).collect { _steps.value = it }
        }
        viewModelScope.launch {
            repository.incrementRecipePopularity(recipeId)
        }
    }
}
