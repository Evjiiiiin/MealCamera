package com.example.mealcamera

import android.app.Application
import com.example.mealcamera.data.AppDatabase
import com.example.mealcamera.data.PrepopulateManager
import com.example.mealcamera.data.RecipeRepository
import com.example.mealcamera.data.remote.FirestoreService
import com.example.mealcamera.ui.SharedViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MealCameraApplication : Application() {

    private val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
    private val firestoreService: FirestoreService by lazy { FirestoreService() }

    val recipeRepository: RecipeRepository by lazy {
        RecipeRepository(database.recipeDao(), database.shoppingListDao(), firestoreService)
    }

    val viewModelFactory: ViewModelFactory by lazy {
        ViewModelFactory(this, recipeRepository)
    }

    val sharedViewModel: SharedViewModel by lazy {
        SharedViewModel(recipeRepository)
    }

    private val _isAppInitialized = MutableStateFlow(false)
    val isAppInitialized: StateFlow<Boolean> = _isAppInitialized.asStateFlow()

    override fun onCreate() {
        super.onCreate()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Сначала заполняем базу локальными рецептами, если она пуста
                PrepopulateManager(this@MealCameraApplication)
                    .prepopulateIfNeeded(database.recipeDao())

                // 2. Затем синхронизируем данные из Firebase
                recipeRepository.syncRecipesFromCloud()

                recipeRepository.getAllDbIngredients()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isAppInitialized.value = true
            }
        }
    }
}