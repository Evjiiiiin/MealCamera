package com.example.mealcamera.ui.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mealcamera.data.RecipeRepository
import com.example.mealcamera.data.FavoriteRepository
import com.example.mealcamera.data.model.*
import com.example.mealcamera.data.remote.FirestoreService
import com.example.mealcamera.data.util.UnitHelper
import com.example.mealcamera.ui.SharedViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.Locale

class ResultViewModel(
    private val repository: RecipeRepository,
    private val favoriteRepository: FavoriteRepository,
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

    private val _isSearching = MutableStateFlow(false)
    val isSearching = _isSearching.asStateFlow()

    private val _errorEvents = MutableSharedFlow<String>()
    val errorEvents = _errorEvents.asSharedFlow()

    val favoriteRecipeIds: StateFlow<Set<Long>> = favoriteRepository.getFavoriteRecipeIds()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val firestoreService = FirestoreService()
    private val auth = FirebaseAuth.getInstance()

    private var searchJob: Job? = null

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

    fun toggleFavorite(recipe: Recipe) {
        viewModelScope.launch {
            favoriteRepository.toggleFavorite(recipe)
        }
    }

    fun findRecipes(updatedIngredients: List<EditableIngredient>) {
        if (updatedIngredients.isEmpty()) {
            return
        }

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _isSearching.value = true
            delay(300)
            try {
                val results = withContext(Dispatchers.Default) {
                    performSearch(updatedIngredients, _portions.value)
                }
                _perfectRecipes.value = results.perfect
                _oneMissingRecipes.value = results.oneMissing
                _twoMissingRecipes.value = results.twoMissing
            } catch (e: CancellationException) {
                // ignore
            } catch (e: Exception) {
                _errorEvents.emit("Ошибка поиска: ${e.message}")
            } finally {
                _isSearching.value = false
            }
        }
    }

    private suspend fun performSearch(
        userIngredients: List<EditableIngredient>,
        currentPortions: Int
    ): SearchResults {
        val userId = auth.currentUser?.uid
        val userAllergens = if (userId != null) {
            try { firestoreService.getUserAllergens(userId) } catch (_: Exception) { emptyList() }
        } else emptyList()

        val expandedAllergens = repository.expandAllergens(userAllergens).map { normalize(it) }
        val userIngredientsMap = userIngredients.associateBy { normalize(it.name) }

        val allDbIngredients = repository.getAllDbIngredients()
        val alwaysAvailableNames = allDbIngredients.filter { it.isAlwaysAvailable }.map { normalize(it.name) }.toSet()

        val allRecipesWithIngredients = repository.getAllRecipesWithIngredients()
        val allCrossRefs = repository.getAllCrossRefs().groupBy { it.recipeId }

        val perfect = mutableListOf<RecipeResult>()
        val oneMissing = mutableListOf<RecipeResult>()
        val twoMissing = mutableListOf<RecipeResult>()
        val processedKeys = mutableSetOf<String>()

        for (recipeWithIngs in allRecipesWithIngredients) {
            val recipe = recipeWithIngs.recipe
            val key = "${normalize(recipe.name)}|${normalize(recipe.category)}"
            if (processedKeys.contains(key) || recipe.name.isBlank()) continue
            processedKeys.add(key)

            val normName = normalize(recipe.name)
            if (expandedAllergens.any { normName.contains(it) || recipeWithIngs.ingredients.any { ing -> normalize(ing.name).contains(it) } }) continue

            var missingCount = 0
            val missingDesc = mutableListOf<String>()
            val crossRefs = allCrossRefs[recipe.recipeId] ?: emptyList()

            for (ing in recipeWithIngs.ingredients) {
                val normIngName = normalize(ing.name)
                if (alwaysAvailableNames.contains(normIngName)) continue

                val userIng = userIngredientsMap[normIngName]
                if (userIng == null) {
                    missingDesc.add(ing.name)
                    missingCount++
                } else {
                    val cr = crossRefs.find { it.ingredientId == ing.ingredientId }
                    val reqQtyStr = cr?.quantity ?: ""
                    val reqUnit = cr?.unit?.ifBlank { UnitHelper.getDefaultUnit(ing.name) } ?: UnitHelper.getDefaultUnit(ing.name)

                    // "по вкусу" всегда достаточно
                    if (reqQtyStr.lowercase(Locale.ROOT).contains("по вкусу")) continue

                    val reqQty = reqQtyStr.toDoubleOrNull()
                    val userQty = userIng.quantity.toDoubleOrNull()

                    if (reqQty == null || userQty == null) {
                        // Невозможно сравнить, считаем что не хватает
                        missingDesc.add(ing.name)
                        missingCount++
                        continue
                    }

                    val userUnit = userIng.unit.ifBlank { UnitHelper.getDefaultUnit(userIng.name) }

                    // Сравнение
                    val sufficient = when {
                        UnitHelper.areDiscreteUnitsCompatible(userUnit, reqUnit) -> {
                            UnitHelper.isDiscreteAmountSufficient(userQty, userUnit, reqQty * currentPortions, reqUnit)
                        }
                        else -> {
                            val uBase = UnitHelper.toBaseUnit(userQty, userUnit)
                            val rBase = UnitHelper.toBaseUnit(reqQty * currentPortions, reqUnit)
                            if (uBase.isNaN() || rBase.isNaN()) false
                            else uBase >= rBase - 0.01
                        }
                    }

                    if (!sufficient) {
                        val diffDesc = buildDiffDescription(userQty, userUnit, reqQty * currentPortions, reqUnit, ing.name)
                        missingDesc.add(diffDesc)
                        missingCount++
                    }
                }
                if (missingCount > 2) break
            }

            val res = RecipeResult(recipe, missingDesc)
            when (missingCount) {
                0 -> perfect.add(res)
                1 -> oneMissing.add(res)
                2 -> twoMissing.add(res)
            }
        }

        return SearchResults(
            perfect.sortedByDescending { it.recipe.popularityScore },
            oneMissing.sortedByDescending { it.recipe.popularityScore },
            twoMissing.sortedByDescending { it.recipe.popularityScore }
        )
    }

    private fun buildDiffDescription(haveQty: Double, haveUnit: String, needQty: Double, needUnit: String, ingredientName: String): String {
        return if (haveUnit == needUnit) {
            val diff = needQty - haveQty
            "еще ${UnitHelper.formatQuantity(diff)} $needUnit $ingredientName"
        } else {
            "нужно $needQty $needUnit (у вас $haveQty $haveUnit) $ingredientName"
        }
    }

    fun removeIngredient(ingredient: EditableIngredient) {
        val updated = _editableIngredients.value.filter { it.id != ingredient.id }
        _editableIngredients.value = updated
        sharedViewModel.temporaryIngredients.value.find { normalize(it.name) == normalize(ingredient.name) }?.let {
            sharedViewModel.removeFromTemporary(it)
        }
        findRecipes(updated)
        sharedViewModel.updateSession(updated, _portions.value)
    }

    fun addIngredients(updated: List<EditableIngredient>) {
        _editableIngredients.value = updated
        findRecipes(updated)
        sharedViewModel.updateSession(updated, _portions.value)
    }

    fun resetIngredientsOnly() {
        _editableIngredients.value = emptyList()
        sharedViewModel.endSession()
    }

    private data class SearchResults(val perfect: List<RecipeResult>, val oneMissing: List<RecipeResult>, val twoMissing: List<RecipeResult>)
}