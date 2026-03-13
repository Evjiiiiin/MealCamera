package com.example.mealcamera.ui.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mealcamera.data.RecipeRepository
import com.example.mealcamera.data.model.EditableIngredient
import com.example.mealcamera.data.model.RecipeResult
import com.example.mealcamera.data.model.ScannedIngredient
import com.example.mealcamera.data.util.UnitHelper
import com.example.mealcamera.ui.SharedViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ResultViewModel(
    private val repository: RecipeRepository,
    private val sharedViewModel: SharedViewModel
) : ViewModel() {

    private val _editableIngredients = MutableStateFlow<List<EditableIngredient>>(emptyList())
    val editableIngredients: StateFlow<List<EditableIngredient>> = _editableIngredients.asStateFlow()

    private val _perfectRecipes = MutableStateFlow<List<RecipeResult>>(emptyList())
    val perfectRecipes: StateFlow<List<RecipeResult>> = _perfectRecipes.asStateFlow()

    private val _oneMissingRecipes = MutableStateFlow<List<RecipeResult>>(emptyList())
    val oneMissingRecipes: StateFlow<List<RecipeResult>> = _oneMissingRecipes.asStateFlow()

    private val _twoMissingRecipes = MutableStateFlow<List<RecipeResult>>(emptyList())
    val twoMissingRecipes: StateFlow<List<RecipeResult>> = _twoMissingRecipes.asStateFlow()

    private val _portions = MutableStateFlow(1)
    val portions: StateFlow<Int> = _portions.asStateFlow()

    private val processedRecipeNames = mutableSetOf<String>()

    fun restoreSession(ingredients: List<EditableIngredient>, savedPortions: Int) {
        _editableIngredients.value = ingredients
        _portions.value = savedPortions
        findRecipes(ingredients)
    }

    fun addIngredients(updatedIngredients: List<EditableIngredient>) {
        _editableIngredients.value = updatedIngredients
        findRecipes(updatedIngredients)
        sharedViewModel.updateSession(updatedIngredients, _portions.value)
    }

    fun setPortions(newValue: Int) {
        if (newValue in 1..10) {
            _portions.value = newValue
            findRecipes(_editableIngredients.value)
            sharedViewModel.updateSession(_editableIngredients.value, newValue)
        }
    }

    fun updateIngredient(updatedIngredient: EditableIngredient) {
        val updatedList = _editableIngredients.value.map {
            if (it.id == updatedIngredient.id) updatedIngredient else it
        }
        _editableIngredients.value = updatedList
        findRecipes(updatedList)
        sharedViewModel.updateSession(updatedList, _portions.value)
    }

    fun removeIngredient(ingredient: EditableIngredient) {
        val updatedList = _editableIngredients.value.filter { it.id != ingredient.id }
        _editableIngredients.value = updatedList

        val scannedIngredients = sharedViewModel.temporaryIngredients.value
        val matchingIngredient = scannedIngredients.find { it.name == ingredient.name }

        matchingIngredient?.let {
            sharedViewModel.removeFromTemporary(it)
        }

        findRecipes(updatedList)
        sharedViewModel.updateSession(updatedList, _portions.value)
    }

    fun findRecipes(updatedIngredients: List<EditableIngredient>) {
        _editableIngredients.value = updatedIngredients.map { it.copy() }

        processedRecipeNames.clear()

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

                    val recipeKey = "${recipe.name.lowercase()}|${recipe.category.lowercase()}"
                    if (processedRecipeNames.contains(recipeKey)) {
                        return@forEach
                    }

                    if (recipe.name.isBlank() || recipe.name.contains("Новый рецепт", ignoreCase = true)) {
                        return@forEach
                    }

                    processedRecipeNames.add(recipeKey)

                    val currentRecipeDetailedMissingIngredients = mutableListOf<String>()
                    var actualMissingCount = 0

                    recipeWithIngredients.ingredients.forEach { ingredientInRecipe ->
                        val recipeIngredientName = ingredientInRecipe.name.trim().lowercase()

                        val crossRef = repository.getRecipeIngredientCrossRef(recipe.recipeId, ingredientInRecipe.ingredientId)

                        val baseRequiredQuantity = try {
                            crossRef?.quantity?.toDoubleOrNull() ?: 0.0
                        } catch (e: NumberFormatException) {
                            0.0
                        }

                        val recipeRequiredQuantity = baseRequiredQuantity * _portions.value

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
                                        UnitHelper.getDefaultUnit(userProvidedIngredient.name)
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