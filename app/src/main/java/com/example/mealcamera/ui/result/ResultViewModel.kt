package com.example.mealcamera.ui.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mealcamera.data.RecipeRepository
import com.example.mealcamera.data.model.EditableIngredient
import com.example.mealcamera.data.model.RecipeResult
import com.example.mealcamera.data.util.UnitHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ResultViewModel(private val repository: RecipeRepository) : ViewModel() {

    private val _editableIngredients = MutableStateFlow<List<EditableIngredient>>(emptyList())
    val editableIngredients: StateFlow<List<EditableIngredient>> = _editableIngredients.asStateFlow()

    private val _perfectRecipes = MutableStateFlow<List<RecipeResult>>(emptyList())
    val perfectRecipes: StateFlow<List<RecipeResult>> = _perfectRecipes.asStateFlow()

    private val _oneMissingRecipes = MutableStateFlow<List<RecipeResult>>(emptyList())
    val oneMissingRecipes: StateFlow<List<RecipeResult>> = _oneMissingRecipes.asStateFlow()

    private val _twoMissingRecipes = MutableStateFlow<List<RecipeResult>>(emptyList())
    val twoMissingRecipes: StateFlow<List<RecipeResult>> = _twoMissingRecipes.asStateFlow()

    fun setInitialIngredients(initialIngredients: List<EditableIngredient>) {
        if (_editableIngredients.value.isEmpty()) {
            _editableIngredients.value = initialIngredients
            findRecipes(initialIngredients)
        }
    }

    fun findRecipes(updatedIngredients: List<EditableIngredient>) {
        _editableIngredients.value = updatedIngredients

        viewModelScope.launch {
            try {
                val userIngredientsMap = updatedIngredients
                    .associateBy { it.name.trim().lowercase() }

                val allRecipesWithIngredients = repository.getAllRecipesWithIngredients()
                val alwaysAvailableIds = repository.getAllDbIngredients()
                    .filter { it.isAlwaysAvailable }
                    .map { it.ingredientId }
                    .toSet()

                val perfect = mutableListOf<RecipeResult>()
                val oneMissing = mutableListOf<RecipeResult>()
                val twoMissing = mutableListOf<RecipeResult>()

                allRecipesWithIngredients.forEach { recipeWithIngredients ->
                    val recipe = recipeWithIngredients.recipe
                    if (recipe.name.isBlank() || recipe.name.contains("Новый рецепт", ignoreCase = true)) {
                        return@forEach
                    }

                    val currentRecipeDetailedMissingIngredients = mutableListOf<String>()
                    var actualMissingCount = 0

                    recipeWithIngredients.ingredients.forEach { ingredientInRecipe ->
                        val recipeIngredientName = ingredientInRecipe.name.trim().lowercase()

                        // Получаем RecipeIngredientCrossRef для данного ингредиента в этом рецепте
                        val crossRef = repository.getRecipeIngredientCrossRef(recipe.recipeId, ingredientInRecipe.ingredientId)
                        val recipeRequiredQuantity = crossRef?.quantity?.toDoubleOrNull() ?: 0.0

                        if (ingredientInRecipe.ingredientId !in alwaysAvailableIds) {
                            val userProvidedIngredient = userIngredientsMap[recipeIngredientName]

                            if (userProvidedIngredient == null) {
                                currentRecipeDetailedMissingIngredients.add(ingredientInRecipe.name)
                                actualMissingCount++
                            } else {
                                val userProvidedQuantity = userProvidedIngredient.quantity.toDoubleOrNull() ?: 0.0

                                if (userProvidedQuantity < recipeRequiredQuantity) {
                                    val difference = recipeRequiredQuantity - userProvidedQuantity
                                    val unitToDisplay = userProvidedIngredient.unit.ifBlank {
                                        UnitHelper.getUnitForIngredient(userProvidedIngredient.name)
                                    }
                                    currentRecipeDetailedMissingIngredients.add(
                                        "не хватает ${UnitHelper.formatQuantity(difference)} ${ingredientInRecipe.name} (${unitToDisplay})"
                                    )
                                    actualMissingCount++
                                }
                            }
                        }
                    }

                    when (actualMissingCount) {
                        0 -> perfect.add(RecipeResult(recipe, currentRecipeDetailedMissingIngredients))
                        1 -> oneMissing.add(RecipeResult(recipe, currentRecipeDetailedMissingIngredients))
                        2 -> twoMissing.add(RecipeResult(recipe, currentRecipeDetailedMissingIngredients))
                    }
                }

                _perfectRecipes.value = perfect
                _oneMissingRecipes.value = oneMissing
                _twoMissingRecipes.value = twoMissing
            } catch (e: Exception) {
                e.printStackTrace()
                _perfectRecipes.value = emptyList()
                _oneMissingRecipes.value = emptyList()
                _twoMissingRecipes.value = emptyList()
            }
        }
    }
}
