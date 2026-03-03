package com.example.mealcamera

import android.app.Application
import com.example.mealcamera.data.AppDatabase
import com.example.mealcamera.data.FavoriteRepository
import com.example.mealcamera.data.PrepopulateManager
import com.example.mealcamera.data.RecipeRepository
import com.example.mealcamera.data.remote.FirestoreService
import com.example.mealcamera.ui.SharedViewModel
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MealCameraApplication : Application() {

    private val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
    private val firestoreService: FirestoreService by lazy { FirestoreService() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    val recipeRepository: RecipeRepository by lazy {
        RecipeRepository(database.recipeDao(), database.shoppingListDao(), firestoreService)
    }

    val favoriteRepository: FavoriteRepository by lazy {
        FavoriteRepository(database.favoriteDao(), firestore)
    }

    val sharedViewModel: SharedViewModel by lazy {
        SharedViewModel(recipeRepository)
    }

    val viewModelFactory: ViewModelFactory by lazy {
        ViewModelFactory(this, recipeRepository, favoriteRepository, sharedViewModel)
    }

    private val _isAppInitialized = MutableStateFlow(false)
    val isAppInitialized: StateFlow<Boolean> = _isAppInitialized.asStateFlow()

    override fun onCreate() {
        super.onCreate()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                PrepopulateManager(this@MealCameraApplication, firestoreService)
                    .prepopulateIfNeeded(database.recipeDao())

                recipeRepository.syncRecipesFromCloud()

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isAppInitialized.value = true
            }
        }
    }
}