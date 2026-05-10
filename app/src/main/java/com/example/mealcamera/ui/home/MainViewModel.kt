package com.example.mealcamera.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mealcamera.data.FavoriteRepository
import com.example.mealcamera.data.RecipeRepository
import com.example.mealcamera.data.model.FilterState
import com.example.mealcamera.data.model.Ingredient
import com.example.mealcamera.data.model.Recipe
import com.example.mealcamera.data.model.RecipeWithIngredients
import com.example.mealcamera.data.remote.FirestoreService
import com.example.mealcamera.util.PrepTimeParser
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
    private val _filterState = MutableStateFlow(FilterState())
    private val _isRefreshing = MutableStateFlow(false)
    private val _userAllergens = MutableStateFlow<List<String>>(emptyList())

    // Публичный геттер для текущего состояния фильтров (используется в HomeFragment)
    val currentFilterState: FilterState get() = _filterState.value

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
        _filterState,
        _userAllergens
    ) { recipesWithIngs: List<RecipeWithIngredients>, query: String, filter: FilterState, allergens: List<String> ->

        val expandedAllergens = repository.expandAllergens(allergens).map { normalize(it) }
        val normalizedQuery = normalize(query)

        recipesWithIngs.asSequence()
            .filter { item: RecipeWithIngredients ->
                if (expandedAllergens.isEmpty()) true
                else {
                    val inMainInfo = expandedAllergens.any { allergen: String ->
                        normalize(item.recipe.name).contains(allergen as CharSequence) ||
                                normalize(item.recipe.description).contains(allergen as CharSequence)
                    }
                    val inIngredients = item.ingredients.any { ing: Ingredient ->
                        val normalizedIng = normalize(ing.name)
                        expandedAllergens.any { allergen: String -> normalizedIng.contains(allergen as CharSequence) }
                    }
                    !(inMainInfo || inIngredients)
                }
            }
            .filter { item: RecipeWithIngredients ->
                val matchesQuery = if (normalizedQuery.isEmpty()) true else {
                    val inName = normalize(item.recipe.name).contains(normalizedQuery as CharSequence)
                    val inIngredients = item.ingredients.any { ing: Ingredient -> normalize(ing.name).contains(normalizedQuery as CharSequence) }
                    inName || inIngredients
                }
                matchesQuery
            }
            .filter { item: RecipeWithIngredients ->
                val matchesCategory = filter.selectedCategories.isEmpty() ||
                        filter.selectedCategories.any { it.equals(item.recipe.category, ignoreCase = true) }
                matchesCategory
            }
            .filter { item: RecipeWithIngredients ->
                val matchesCuisine = filter.selectedCuisines.isEmpty() ||
                        filter.selectedCuisines.any { it.equals(item.recipe.cuisine, ignoreCase = true) }
                matchesCuisine
            }
            .filter { item: RecipeWithIngredients ->
                val prepMinutes = PrepTimeParser.parseToMinutes(item.recipe.prepTime)
                filter.prepTimeRange?.let { range ->
                    prepMinutes in range.start.toInt()..range.endInclusive.toInt()
                } ?: true
            }
            .filter { item: RecipeWithIngredients ->
                filter.caloriesRange?.let { range ->
                    item.recipe.calories in range.start.toInt()..range.endInclusive.toInt()
                } ?: true
            }
            .map { it.recipe }
            .distinctBy { "${it.name.lowercase()}|${it.category.lowercase()}" }
            .toList()
    }

    val uiState: StateFlow<MainUiState> = combine(
        filteredRecipesFlow,
        favoriteRepository.getFavoriteRecipeIds(),
        _isRefreshing,
        _searchQuery,
        _filterState.map { it.selectedCategories.firstOrNull() ?: "Все" },
        _filterState.map { it.selectedCuisines.firstOrNull() ?: "Все кухни" }
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

    fun setFilters(filterState: FilterState) {
        _filterState.value = filterState
    }

    fun toggleFavorite(recipe: Recipe, isFavorite: Boolean) {
        viewModelScope.launch {
            if (isFavorite) favoriteRepository.addFavorite(recipe)
            else favoriteRepository.removeFavorite(recipe)
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
    fun setCategoryFilter(category: String) {
        val newFilters = _filterState.value.copy(selectedCategories = if (category == "Все") emptySet() else setOf(category))
        _filterState.value = newFilters
    }
    fun setCuisineFilter(cuisine: String) {
        val newFilters = _filterState.value.copy(selectedCuisines = if (cuisine == "Все кухни") emptySet() else setOf(cuisine))
        _filterState.value = newFilters
    }
}