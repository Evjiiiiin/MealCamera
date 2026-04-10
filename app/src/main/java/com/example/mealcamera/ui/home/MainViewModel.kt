package com.example.mealcamera.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mealcamera.data.FavoriteRepository
import com.example.mealcamera.data.RecipeRepository
import com.example.mealcamera.data.model.Recipe
import com.example.mealcamera.data.remote.FirestoreService
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class MainUiState(
    val recipes: List<Recipe> = emptyList(),
    val favoriteIds: Set<Long> = emptySet(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val searchQuery: String = "",
    val categoryFilter: String = "Все",
    val cuisineFilter: String = "Все кухни",
    val error: String? = null
)

class MainViewModel(
    private val repository: RecipeRepository,
    private val favoriteRepository: FavoriteRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _categoryFilter = MutableStateFlow("Все")
    private val _cuisineFilter = MutableStateFlow("Все кухни")
    private val _isRefreshing = MutableStateFlow(false)
    private val _userAllergens = MutableStateFlow<List<String>>(emptyList())

    private val firestoreService = FirestoreService()
    private val auth = FirebaseAuth.getInstance()

    init {
        observeUserAllergens()
    }

    private fun observeUserAllergens() {
        val userId = auth.currentUser?.uid ?: return
        firestoreService.getUserAllergensFlow(userId)
            .onEach { allergens ->
                _userAllergens.value = allergens
            }
            .launchIn(viewModelScope)
    }

    private fun normalize(text: String): String = text.lowercase().trim().replace("ё", "е")

    private val filteredRecipesFlow: Flow<List<Recipe>> = combine(
        repository.allRecipesWithIngredientsFlow(),
        _searchQuery,
        _categoryFilter,
        _cuisineFilter,
        _userAllergens
    ) { recipesWithIngs, query, category, cuisine, allergens ->
        
        val expandedAllergens = repository.expandAllergens(allergens).map { normalize(it) }

        recipesWithIngs.asSequence()
            .filter { item ->
                if (expandedAllergens.isEmpty()) true
                else {
                    val inMainInfo = expandedAllergens.any { allergen -> 
                        normalize(item.recipe.name).contains(allergen) || 
                        normalize(item.recipe.description).contains(allergen)
                    }
                    val inIngredients = item.ingredients.any { ing ->
                        val normalizedIng = normalize(ing.name)
                        expandedAllergens.any { allergen -> normalizedIng.contains(allergen) }
                    }
                    !(inMainInfo || inIngredients)
                }
            }
            .map { it.recipe }
            .filter { recipe ->
                val matchesQuery = normalize(recipe.name).contains(normalize(query))
                val matchesCategory = category == "Все" || recipe.category.equals(category, ignoreCase = true)
                val matchesCuisine = cuisine == "Все кухни" || recipe.cuisine.equals(cuisine, ignoreCase = true)
                matchesQuery && matchesCategory && matchesCuisine
            }
            .distinctBy { "${it.name.lowercase()}|${it.category.lowercase()}" }
            .toList()
    }

    val uiState: StateFlow<MainUiState> = combine(
        filteredRecipesFlow,
        favoriteRepository.getFavoriteRecipeIds(),
        _isRefreshing,
        _searchQuery,
        _categoryFilter,
        _cuisineFilter
    ) { args: Array<Any> ->
        @Suppress("UNCHECKED_CAST")
        MainUiState(
            recipes = args[0] as List<Recipe>,
            favoriteIds = (args[1] as List<Long>).toSet(),
            isRefreshing = args[2] as Boolean,
            searchQuery = args[3] as String,
            categoryFilter = args[4] as String,
            cuisineFilter = args[5] as String
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState()
    )

    fun toggleFavorite(recipe: Recipe, isFavorite: Boolean) {
        viewModelScope.launch {
            if (isFavorite) favoriteRepository.addFavorite(recipe)
            else favoriteRepository.removeFavorite(recipe.recipeId)
        }
    }

    fun refreshRecipes() {
        viewModelScope.launch {
            _isRefreshing.value = true
            repository.syncRecipesFromCloud()
            _isRefreshing.value = false
        }
    }

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setCategoryFilter(category: String) { _categoryFilter.value = category }
    fun setCuisineFilter(cuisine: String) { _cuisineFilter.value = cuisine }
}