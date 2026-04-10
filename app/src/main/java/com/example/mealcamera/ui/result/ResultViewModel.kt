package com.example.mealcamera.ui.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mealcamera.data.RecipeRepository
import com.example.mealcamera.data.model.EditableIngredient
import com.example.mealcamera.data.model.RecipeResult
import com.example.mealcamera.data.remote.FirestoreService
import com.example.mealcamera.data.util.UnitHelper
import com.example.mealcamera.ui.SharedViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

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

    private val _errorEvents = MutableSharedFlow<String>()
    val errorEvents = _errorEvents.asSharedFlow()

    private val firestoreService = FirestoreService()
    private val auth = FirebaseAuth.getInstance()
    private val processedRecipeNames = mutableSetOf<String>()

    private fun normalize(text: String): String = text.trim().lowercase(Locale.ROOT).replace("ё", "е")

    fun restoreSession(ingredients: List<EditableIngredient>, savedPortions: Int) {
        _editableIngredients.value = ingredients.map { it.copy() }
        _portions.value = savedPortions
        findRecipes(_editableIngredients.value)
    }

    fun updateIngredient(updatedIngredient: EditableIngredient) {
        val currentList = _editableIngredients.value.map {
            if (it.id == updatedIngredient.id) updatedIngredient.copy() else it
        }
        _editableIngredients.value = currentList
        findRecipes(currentList)
        sharedViewModel.updateSession(currentList, _portions.value)
    }

    fun setPortions(newValue: Int) {
        if (newValue in 1..10) {
            _portions.value = newValue
            findRecipes(_editableIngredients.value)
            sharedViewModel.updateSession(_editableIngredients.value, newValue)
        }
    }

    fun findRecipes(updatedIngredients: List<EditableIngredient>) {
        processedRecipeNames.clear()
        viewModelScope.launch {
            try {
                val userId = auth.currentUser?.uid
                val userAllergens = if (userId != null) {
                    try { firestoreService.getUserAllergens(userId) } catch (_: Exception) { emptyList() }
                } else emptyList()
                
                val expandedAllergens = repository.expandAllergens(userAllergens).map { normalize(it) }
                val userIngredientsMap = updatedIngredients.associateBy { normalize(it.name) }
                
                val allDbIngredients = repository.getAllDbIngredients()
                val alwaysAvailableNames = allDbIngredients
                    .filter { it.isAlwaysAvailable }
                    .map { normalize(it.name) }
                    .toSet()

                val allRecipesWithIngredients = repository.getAllRecipesWithIngredients()

                val perfect = mutableListOf<RecipeResult>()
                val oneMissing = mutableListOf<RecipeResult>()
                val twoMissing = mutableListOf<RecipeResult>()

                allRecipesWithIngredients.forEach recipes@{ recipeWithIngredients ->
                    val recipe = recipeWithIngredients.recipe
                    val recipeKey = "${recipe.name.lowercase()}|${recipe.category.lowercase()}"
                    
                    if (processedRecipeNames.contains(recipeKey)) return@recipes
                    if (recipe.name.isBlank()) return@recipes

                    val hasAllergen = expandedAllergens.any { allergen -> 
                        normalize(recipe.name).contains(allergen) || 
                        recipeWithIngredients.ingredients.any { normalize(it.name).contains(allergen) }
                    }
                    if (hasAllergen) return@recipes

                    processedRecipeNames.add(recipeKey)
                    val missingDescriptions = mutableListOf<String>()
                    var missingCount = 0

                    recipeWithIngredients.ingredients.forEach ingredients@{ recipeIng ->
                        val normalizedRecipeIngName = normalize(recipeIng.name)
                        
                        if (alwaysAvailableNames.contains(normalizedRecipeIngName)) return@ingredients

                        val userIng = userIngredientsMap[normalizedRecipeIngName]
                        if (userIng == null) {
                            missingDescriptions.add(recipeIng.name)
                            missingCount++
                        } else {
                            val crossRef = repository.getRecipeIngredientCrossRef(recipe.recipeId, recipeIng.ingredientId)
                            val baseQtyStr = crossRef?.quantity ?: "0"
                            val baseQty = baseQtyStr.toDoubleOrNull() ?: getApproximateWeight(normalizedRecipeIngName)
                            val requiredQty = baseQty * _portions.value
                            val recipeUnit = crossRef?.unit?.ifBlank { "г" } ?: "г"

                            val userQtyNum = userIng.quantity.toDoubleOrNull() ?: 1.0
                            val userQtyBase = UnitHelper.toBaseUnit(userQtyNum, userIng.unit)
                            val requiredQtyBase = UnitHelper.toBaseUnit(requiredQty, recipeUnit)

                            val isEnough = if (userIng.unit == "шт" && (recipeUnit != "шт" && recipeUnit != "штука")) {
                                // Если у пользователя штуки, а в рецепте вес
                                (userQtyNum * getApproximateWeight(normalizedRecipeIngName)) >= requiredQtyBase
                            } else {
                                userQtyBase >= (requiredQtyBase - 0.01)
                            }

                            if (!isEnough) {
                                val diffBase = requiredQtyBase - userQtyBase
                                val diffFormatted = UnitHelper.formatQuantity(UnitHelper.convert(diffBase, "г", recipeUnit))
                                missingDescriptions.add("нужно еще $diffFormatted $recipeUnit ${recipeIng.name}")
                                missingCount++
                            }
                        }
                    }

                    when (missingCount) {
                        0 -> perfect.add(RecipeResult(recipe, missingDescriptions))
                        1 -> oneMissing.add(RecipeResult(recipe, missingDescriptions))
                        2 -> twoMissing.add(RecipeResult(recipe, missingDescriptions))
                    }
                }

                _perfectRecipes.value = perfect.sortedByDescending { it.recipe.popularityScore }
                _oneMissingRecipes.value = oneMissing.sortedByDescending { it.recipe.popularityScore }
                _twoMissingRecipes.value = twoMissing.sortedByDescending { it.recipe.popularityScore }

            } catch (_: Exception) {
                _errorEvents.emit("Ошибка при поиске рецептов")
            }
        }
    }

    private fun getApproximateWeight(name: String): Double {
        return when {
            name.contains("яйцо") -> 50.0
            name.contains("чеснок") -> 5.0
            name.contains("лук") -> 100.0
            name.contains("картофель") -> 150.0
            name.contains("томат") || name.contains("помидор") -> 120.0
            name.contains("лимон") -> 100.0
            name.contains("яблоко") -> 180.0
            else -> 100.0
        }
    }

    fun removeIngredient(ingredient: EditableIngredient) {
        val updatedList = _editableIngredients.value.filter { it.id != ingredient.id }
        _editableIngredients.value = updatedList
        
        sharedViewModel.temporaryIngredients.value.find { normalize(it.name) == normalize(ingredient.name) }?.let {
            sharedViewModel.removeFromTemporary(it)
        }
        
        findRecipes(updatedList)
        sharedViewModel.updateSession(updatedList, _portions.value)
    }

    fun addIngredients(updatedIngredients: List<EditableIngredient>) {
        _editableIngredients.value = updatedIngredients
        findRecipes(updatedIngredients)
        sharedViewModel.updateSession(updatedIngredients, _portions.value)
    }
}