package com.example.mealcamera.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mealcamera.data.RecipeRepository
import com.example.mealcamera.data.model.Recipe
import kotlinx.coroutines.flow.*

data class MainUiState(val filteredRecipes: List<Recipe> = emptyList())

class MainViewModel(repository: RecipeRepository) : ViewModel() {
    private val _categoryFilter = MutableStateFlow<String?>("Все") // По умолчанию фильтр "Все"
    private val allRecipes: Flow<List<Recipe>> = repository.allRecipes

    val uiState: StateFlow<MainUiState> =
        combine(allRecipes, _categoryFilter) { recipes, category ->
            val filtered = if (category != null && category.lowercase() != "все") {
                recipes.filter { it.category.equals(category, ignoreCase = true) }
            } else {
                recipes // Если категория "Все" или null, показываем все
            }
            MainUiState(filteredRecipes = filtered)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = MainUiState()
        )

    fun setCategoryFilter(category: String?) {
        _categoryFilter.value = category
    }
}
