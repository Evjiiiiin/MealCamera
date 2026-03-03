package com.example.mealcamera.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mealcamera.data.RecipeRepository
import com.example.mealcamera.data.model.Recipe
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class MainUiState(
    val recipes: List<Recipe> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null
)

class MainViewModel(private val repository: RecipeRepository) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _categoryFilter = MutableStateFlow("Все")
    private val _cuisineFilter = MutableStateFlow("Все кухни")

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val uiState: StateFlow<MainUiState> = combine(
        repository.allRecipes,
        _searchQuery,
        _categoryFilter,
        _cuisineFilter,
        _isRefreshing,
        _error
    ) { recipes, query, category, cuisine, refreshing, error ->
        val filtered = recipes.filter { recipe ->
            val matchesQuery = recipe.name.contains(query, ignoreCase = true)
            val matchesCategory = category == "Все" || recipe.category == category
            val matchesCuisine = cuisine == "Все кухни" || (recipe.cuisine == cuisine)
            matchesQuery && matchesCategory && matchesCuisine
        }
        MainUiState(
            recipes = filtered,
            isRefreshing = refreshing,
            error = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState()
    )

    fun refreshRecipes() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _error.value = null

            try {
                repository.syncRecipesFromCloud()
            } catch (e: Exception) {
                _error.value = "Ошибка обновления: ${e.message}"
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setCategoryFilter(category: String) {
        _categoryFilter.value = category
    }

    fun setCuisineFilter(cuisine: String) {
        _cuisineFilter.value = cuisine
    }
}