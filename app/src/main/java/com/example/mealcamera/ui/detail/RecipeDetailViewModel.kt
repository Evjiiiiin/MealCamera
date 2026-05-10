package com.example.mealcamera.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mealcamera.data.FavoriteRepository
import com.example.mealcamera.data.RecipeRepository
import com.example.mealcamera.data.model.IngredientWithDetails
import com.example.mealcamera.data.model.Recipe
import com.example.mealcamera.data.model.RecipeStep
import com.example.mealcamera.ui.SharedViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class RecipeDetailViewModel(
    private val repository: RecipeRepository,
    private val favoriteRepository: FavoriteRepository,
    private val sharedViewModel: SharedViewModel
) : ViewModel() {

    private val _recipeId = MutableStateFlow<Long?>(null)
    private val _portions = MutableStateFlow(1)
    private val _isFavorite = MutableStateFlow(false)

    val recipe: StateFlow<Recipe?> = _recipeId
        .filterNotNull()
        .flatMapLatest { id -> repository.getRecipeById(id) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val portions: StateFlow<Int> = _portions.asStateFlow()

    val ingredients: StateFlow<List<IngredientWithDetails>> = combine(
        _recipeId.filterNotNull(),
        _portions
    ) { id, portions -> id to portions }
        .flatMapLatest { (id, portions) ->
            repository.getScaledIngredientsForRecipe(id, portions)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val steps: StateFlow<List<RecipeStep>> = _recipeId
        .filterNotNull()
        .flatMapLatest { id -> repository.getStepsForRecipe(id) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    fun loadRecipe(recipeId: Long) {
        _recipeId.value = recipeId
        viewModelScope.launch {
            _isFavorite.value = favoriteRepository.isFavorite(recipeId)
            repository.incrementRecipePopularity(recipeId)
        }
    }

    fun setPortions(count: Int) {
        if (count in 1..10) {
            _portions.value = count
        }
    }

    fun toggleFavorite(recipeId: Long) {
        viewModelScope.launch {
            val recipe = repository.getRecipeById(recipeId).firstOrNull() ?: return@launch
            val newState = !_isFavorite.value
            favoriteRepository.toggleFavorite(recipe)
            _isFavorite.value = newState
            sharedViewModel.notifyFavoriteChanged(recipeId, newState)
        }
    }
}