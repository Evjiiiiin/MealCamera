package com.example.mealcamera.ui.profile

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mealcamera.data.FavoriteRepository
import com.example.mealcamera.data.RecipeRepository
import com.example.mealcamera.data.local.AppStatsManager
import com.example.mealcamera.data.model.Recipe
import com.example.mealcamera.data.remote.FirestoreService
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ProfileStats(
    val cookedCount: Int = 0,
    val uniqueCount: Int = 0,
    val recentRecipes: List<AppStatsManager.RecentCookedRecipe> = emptyList(),
    val isLoading: Boolean = false
)

class ProfileViewModel(
    application: Application,
    private val repository: RecipeRepository,
    private val favoriteRepository: FavoriteRepository,
    private val firestoreService: FirestoreService
) : AndroidViewModel(application) {

    private val statsManager = AppStatsManager(application)
    private val auth = FirebaseAuth.getInstance()
    private val userId = auth.currentUser?.uid

    private val _stats = MutableStateFlow(ProfileStats(isLoading = true))
    val stats: StateFlow<ProfileStats> = _stats.asStateFlow()

    val myRecipes: StateFlow<List<Recipe>> = repository.allRecipes
        .map { recipes ->
            recipes.filter { it.createdByUserId == userId }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _favoriteRecipes = MutableStateFlow<List<Recipe>>(emptyList())
    val favoriteRecipes: StateFlow<List<Recipe>> = _favoriteRecipes.asStateFlow()

    private val _deleteStatus = MutableSharedFlow<Boolean>()
    val deleteStatus = _deleteStatus.asSharedFlow()

    init {
        refreshStats()
        loadFavorites()
    }

    fun refreshStats() {
        val currentUserId = auth.currentUser?.uid

        val localRecipes = statsManager.getRecentCookedRecipes(currentUserId)
        
        _stats.value = ProfileStats(
            cookedCount = statsManager.getCookedRecipesCount(currentUserId),
            uniqueCount = statsManager.getUniqueCookedRecipesCount(currentUserId),
            recentRecipes = localRecipes,
            isLoading = true
        )

        if (currentUserId == null) {
            _stats.update { it.copy(isLoading = false) }
            return
        }

        viewModelScope.launch {
            try {
                val cloudHistory = firestoreService.getCookingHistory(currentUserId)
                val cloudRecent = cloudHistory.map { map ->
                    AppStatsManager.RecentCookedRecipe(
                        recipeId = (map["recipeId"] as? Number)?.toLong() ?: -1L,
                        name = map["recipeName"] as? String ?: "Блюдо",
                        cookedAtMillis = (map["cookedAt"] as? Number)?.toLong() ?: 0L
                    )
                }

                val combined = (localRecipes + cloudRecent)
                    .distinctBy { "${it.name}_${it.cookedAtMillis / 60000}" }
                    .sortedByDescending { it.cookedAtMillis }

                val uniqueIds = combined.map { it.recipeId }.distinct().size

                _stats.value = ProfileStats(
                    cookedCount = combined.size,
                    uniqueCount = uniqueIds,
                    recentRecipes = combined,
                    isLoading = false
                )
                
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Sync error: ${e.message}")
                _stats.update { it.copy(isLoading = false) }
            }
        }
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
        }
    }

    fun deleteRecipe(recipe: Recipe) {
        viewModelScope.launch {
            val success = repository.deleteUserRecipe(recipe.recipeId, recipe.firestoreId)
            _deleteStatus.emit(success)
        }
    }
}
