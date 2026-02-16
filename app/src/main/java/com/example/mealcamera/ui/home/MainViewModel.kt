package com.example.mealcamera.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mealcamera.data.RecipeRepository
import com.example.mealcamera.data.model.Recipe
import kotlinx.coroutines.flow.*

data class MainUiState(
    val recipes: List<Recipe> = emptyList(),
    val isLoading: Boolean = false
)

class MainViewModel(private val repository: RecipeRepository) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _categoryFilter = MutableStateFlow("Все")

    val uiState: StateFlow<MainUiState> = combine(
        repository.allRecipes,
        _searchQuery,
        _categoryFilter
    ) { recipes, query, category ->
        val filtered = recipes.filter { recipe ->
            val matchesQuery = recipe.name.contains(query, ignoreCase = true)
            val matchesCategory = category == "Все" || recipe.category == category
            matchesQuery && matchesCategory
        }
        MainUiState(recipes = filtered)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState()
    )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setCategoryFilter(category: String) {
        _categoryFilter.value = category
    }
}
