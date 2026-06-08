package com.example.mealcamera.ui.cooking

import androidx.lifecycle.ViewModel
import com.example.mealcamera.data.RecipeRepository
import com.example.mealcamera.data.local.AppStatsManager
import com.example.mealcamera.data.model.CookingStepWithIngredients
import com.example.mealcamera.data.remote.FirestoreService
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withTimeoutOrNull

class CookingViewModel(
    private val repository: RecipeRepository,
    private val statsManager: AppStatsManager,
    private val firestoreService: FirestoreService
) : ViewModel() {

    companion object {
        private const val CLOUD_SAVE_TIMEOUT_MS = 1_500L
    }

    private val auth = FirebaseAuth.getInstance()

    fun getStepsWithIngredients(recipeId: Long, portions: Int): Flow<List<CookingStepWithIngredients>> =
        repository.getCookingStepsWithIngredients(recipeId, portions)

    suspend fun saveToHistory(recipeId: Long, recipeName: String) {
        if (recipeId <= 0L) return

        val userId = auth.currentUser?.uid

        repository.incrementRecipePopularity(recipeId)

        statsManager.registerCookedRecipe(userId, recipeId, recipeName)

        if (userId != null) {
            runCatching {
                withTimeoutOrNull(CLOUD_SAVE_TIMEOUT_MS) {
                    firestoreService.saveCookingHistory(userId, recipeId, recipeName)
                }
            }
        }
    }
}