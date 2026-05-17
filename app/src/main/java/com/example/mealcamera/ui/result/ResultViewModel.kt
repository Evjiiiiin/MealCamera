package com.example.mealcamera.ui.result

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mealcamera.data.RecipeRepository
import com.example.mealcamera.data.FavoriteRepository
import com.example.mealcamera.data.model.*
import com.example.mealcamera.data.remote.FirestoreService
import com.example.mealcamera.data.util.UnitHelper
import com.example.mealcamera.ui.SharedViewModel
import com.example.mealcamera.util.IngredientTranslator
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

    // Список ингредиентов, которые всегда считаются доступными (кухонный минимум)
    private val alwaysAvailableDefaults = setOf(
        "соль", "сахар", "вода", "перец", "сода", "уксус", "ванилин", 
        "корица", "разрыхлитель", "лимонная кислота", "лавровый лист", "гвоздика", "молотый перец"
    )

    private fun normalize(text: String): String = text.trim().lowercase(Locale.ROOT).replace("ё", "е")

    private fun capitalize(text: String): String {
        return text.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    private fun isSimilar(name1: String, name2: String): Boolean {
        val n1 = normalize(name1)
        val n2 = normalize(name2)
        if (n1 == n2) return true
        if (n1.contains(n2) || n2.contains(n1)) return true
        
        val minLen = minOf(n1.length, n2.length)
        if (minLen >= 4) {
            val rootLen = (minLen * 0.75).toInt().coerceAtLeast(4)
            if (n1.substring(0, rootLen) == n2.substring(0, rootLen)) return true
        }
        return false
    }

    fun restoreSession(ingredients: List<EditableIngredient>, savedPortions: Int) {
        val capitalized = ingredients.map { it.copy(name = capitalize(it.name)) }
        _editableIngredients.value = capitalized
        _portions.value = savedPortions
        findRecipes(capitalized)
    }

    fun updateIngredient(updatedIngredient: EditableIngredient) {
        val capitalized = updatedIngredient.copy(name = capitalize(updatedIngredient.name))
        val currentList = _editableIngredients.value.map {
            if (it.id == capitalized.id) capitalized else it
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
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _isSearching.value = true
            delay(200)
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
                Log.e("ResultViewModel", "Search error", e)
                _errorEvents.emit("Ошибка поиска")
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
        val normalizedUserIngredients = userIngredients.map { it.copy(name = normalize(it.name)) }

        val allDbIngredients = repository.getAllDbIngredients()
        val alwaysAvailableInDb = allDbIngredients.filter { it.isAlwaysAvailable }.map { normalize(it.name) }.toSet()
        val combinedAlwaysAvailable = alwaysAvailableInDb + alwaysAvailableDefaults

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

            val normRecipeName = normalize(recipe.name)
            
            // Фильтрация по аллергенам
            val hasAllergen = expandedAllergens.any { allergen -> 
                normRecipeName.contains(allergen) || 
                recipeWithIngs.ingredients.any { ing -> normalize(ing.name).contains(allergen) }
            }
            if (hasAllergen) continue

            var missingCount = 0
            val missingStrings = mutableListOf<String>()
            val structuredMissing = mutableListOf<MissingIngredientData>()
            val crossRefs = allCrossRefs[recipe.recipeId] ?: emptyList()

            for (ing in recipeWithIngs.ingredients) {
                val normIngName = normalize(ing.name)
                
                // Пропуск базовых продуктов
                if (combinedAlwaysAvailable.any { normIngName.contains(it) || it.contains(normIngName) }) continue

                // Поиск совпадения у пользователя
                val userIng = normalizedUserIngredients.find { isSimilar(it.name, normIngName) }

                val cr = crossRefs.find { it.ingredientId == ing.ingredientId }
                val reqQtyStr = cr?.quantity ?: ""
                val reqUnit = cr?.unit?.ifBlank { UnitHelper.getDefaultUnit(ing.name) } ?: UnitHelper.getDefaultUnit(ing.name)
                val reqQty = reqQtyStr.toDoubleOrNull() ?: 0.0

                if (userIng == null) {
                    missingStrings.add(ing.name)
                    structuredMissing.add(MissingIngredientData(ing.name, reqQty * currentPortions, reqUnit))
                    missingCount++
                } else {
                    if (reqQtyStr.lowercase(Locale.ROOT).contains("по вкусу")) continue

                    val userQty = userIng.quantity.toDoubleOrNull() ?: 0.0
                    val userUnit = userIng.unit.ifBlank { UnitHelper.getDefaultUnit(userIng.name) }

                    // Строгая проверка достаточности с учетом конвертации
                    val sufficient = when {
                        UnitHelper.areDiscreteUnitsCompatible(userUnit, reqUnit) -> {
                            UnitHelper.isDiscreteAmountSufficient(userQty, userUnit, reqQty * currentPortions, reqUnit)
                        }
                        else -> {
                            val uBase = UnitHelper.toBaseUnit(userQty, userUnit)
                            val rBase = UnitHelper.toBaseUnit(reqQty * currentPortions, reqUnit)
                            
                            if (uBase.isNaN() || rBase.isNaN()) {
                                userQty >= (reqQty * currentPortions)
                            } else {
                                uBase >= (rBase - 0.01)
                            }
                        }
                    }

                    if (!sufficient) {
                        val diffDesc = buildDiffDescription(userQty, userUnit, reqQty * currentPortions, reqUnit, ing.name)
                        missingStrings.add(diffDesc)
                        
                        // Считаем сколько реально не хватает для списка покупок
                        val diffQty = if (UnitHelper.areDiscreteUnitsCompatible(userUnit, reqUnit)) {
                            (reqQty * currentPortions) - userQty
                        } else {
                            val uBase = UnitHelper.toBaseUnit(userQty, userUnit)
                            val rBase = UnitHelper.toBaseUnit(reqQty * currentPortions, reqUnit)
                            if (uBase.isNaN() || rBase.isNaN()) (reqQty * currentPortions) - userQty
                            else {
                                // Конвертируем недостачу обратно в единицу рецепта
                                val missingInBase = rBase - uBase
                                UnitHelper.convert(missingInBase, "г", reqUnit).takeIf { !it.isNaN() } ?: ((reqQty * currentPortions) - userQty)
                            }
                        }
                        
                        structuredMissing.add(MissingIngredientData(ing.name, diffQty, reqUnit))
                        missingCount++
                    }
                }
                if (missingCount > 2) break
            }

            val res = RecipeResult(recipe, missingStrings, structuredMissing)
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
        val formattedNeed = UnitHelper.formatQuantity(needQty)
        val formattedHave = UnitHelper.formatQuantity(haveQty)
        return "нужно $formattedNeed $needUnit (есть $formattedHave $haveUnit) $ingredientName"
    }

    fun addToShoppingList(missing: List<MissingIngredientData>) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            missing.forEach { item ->
                repository.addScaledIngredientsToShoppingListExplicit(userId, item.name, item.quantity, item.unit)
            }
            _errorEvents.emit("Добавлено в список покупок")
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
        val capitalized = updated.map { it.copy(name = capitalize(it.name)) }
        _editableIngredients.value = capitalized
        findRecipes(capitalized)
        sharedViewModel.updateSession(capitalized, _portions.value)
    }

    fun resetIngredientsOnly() {
        _editableIngredients.value = emptyList()
        sharedViewModel.endSession()
    }

    private data class SearchResults(val perfect: List<RecipeResult>, val oneMissing: List<RecipeResult>, val twoMissing: List<RecipeResult>)
}
