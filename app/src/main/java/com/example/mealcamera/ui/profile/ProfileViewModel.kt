package com.example.mealcamera.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mealcamera.data.FavoriteRepository
import com.example.mealcamera.data.RecipeRepository
import com.example.mealcamera.data.local.AppStatsManager
import com.example.mealcamera.data.model.Recipe
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ProfileStats(
    val cookedCount: Int = 0,
    val uniqueCount: Int = 0,
    val recentRecipes: List<AppStatsManager.RecentCookedRecipe> = emptyList()
)
class ProfileViewModel(
    application: Application,
    private val repository: RecipeRepository,
    private val favoriteRepository: FavoriteRepository
) : AndroidViewModel(application) {

    private val statsManager = AppStatsManager(application)
    private val auth = FirebaseAuth.getInstance()
    private val userId = auth.currentUser?.uid

    // 1. Statistics State
    private val _stats = MutableStateFlow(ProfileStats())
    val stats: StateFlow<ProfileStats> = _stats.asStateFlow()

    // 2. Favorite Recipes State
    private val _favoriteRecipes = MutableStateFlow<List<Recipe>>(emptyList())
    val favoriteRecipes: StateFlow<List<Recipe>> = _favoriteRecipes.asStateFlow()

    // 3. User-created Recipes State
    val myRecipes: StateFlow<List<Recipe>> = repository.allRecipes
        .map { recipes ->
            recipes.filter { it.createdByUserId == userId }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _deleteStatus = MutableSharedFlow<Boolean>()
    val deleteStatus = _deleteStatus.asSharedFlow()

    init {
        refreshStats()
        loadFavorites()
    }

    fun refreshStats() {
        val currentUserId = auth.currentUser?.uid
        _stats.value = ProfileStats(
            cookedCount = statsManager.getCookedRecipesCount(currentUserId),
            uniqueCount = statsManager.getUniqueCookedRecipesCount(currentUserId),
            recentRecipes = statsManager.getRecentCookedRecipes(currentUserId)
        )
    }

    fun loadFavorites() {
        viewModelScope.launch {
            favoriteRepository.getFavoriteRecipes()?.collect { recipes ->
                _favoriteRecipes.value = recipes
            }
        }
    }

    fun toggleFavorite(recipe: Recipe) {
        viewModelScope.launch {
            favoriteRepository.toggleFavorite(recipe)
            // Local update if needed, though Flow from repository should handle it
        }
    }

    fun deleteRecipe(recipe: Recipe) {
        viewModelScope.launch {
            val success = repository.deleteUserRecipe(recipe.recipeId, recipe.firestoreId)
            _deleteStatus.emit(success)
        }
    }
}