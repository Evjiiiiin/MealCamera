package com.example.mealcamera.ui.cooking

import androidx.lifecycle.ViewModel
import com.example.mealcamera.data.RecipeRepository
import com.example.mealcamera.data.local.AppStatsManager
import com.example.mealcamera.data.model.CookingStepWithIngredients
import com.example.mealcamera.data.remote.FirestoreService
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.Flow

class CookingViewModel(
    private val repository: RecipeRepository,
    private val statsManager: AppStatsManager,
    private val firestoreService: FirestoreService
) : ViewModel() {

    private val auth = FirebaseAuth.getInstance()

    fun getStepsWithIngredients(recipeId: Long, portions: Int): Flow<List<CookingStepWithIngredients>> =
        repository.getCookingStepsWithIngredients(recipeId, portions)

    suspend fun saveToHistory(recipeId: Long, recipeName: String) {
        if (recipeId <= 0L) return
        
        val userId = auth.currentUser?.uid
        
        // 1. Увеличиваем популярность в локальной БД
        repository.incrementRecipePopularity(recipeId)
        
        // 2. Регистрируем приготовление в локальной статистике (для мгновенного обновления UI)
        statsManager.registerCookedRecipe(userId, recipeId, recipeName)
        
        // 3. Сохраняем историю в облако (Firebase), если пользователь авторизован
        if (userId != null) {
            firestoreService.saveCookingHistory(userId, recipeId, recipeName)
        }
    }
}
