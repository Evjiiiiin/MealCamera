package com.example.mealcamera

import android.app.Application
import android.util.Log
import com.example.mealcamera.data.AppDatabase
import com.example.mealcamera.data.FavoriteRepository
import com.example.mealcamera.data.PrepopulateManager
import com.example.mealcamera.data.RecipeRepository
import com.example.mealcamera.data.remote.FirestoreService
import com.example.mealcamera.ui.SharedViewModel
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MealCameraApplication : Application() {

    private val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
    val firestoreService: FirestoreService by lazy { FirestoreService() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    val recipeRepository: RecipeRepository by lazy {
        RecipeRepository(database.recipeDao(), database.shoppingListDao(), firestoreService)
    }

    val favoriteRepository: FavoriteRepository by lazy {
        FavoriteRepository(database.favoriteDao(), firestore)
    }

    private val _sharedViewModel by lazy { SharedViewModel(recipeRepository) }
    val sharedViewModel: SharedViewModel
        get() = _sharedViewModel

    val viewModelFactory: ViewModelFactory by lazy {
        ViewModelFactory(this, recipeRepository, favoriteRepository, sharedViewModel)
    }

    private val appScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        appScope.launch {
            try {
                Log.i("MealCameraApplication", "🚀 Initializing app...")

                PrepopulateManager(this@MealCameraApplication, firestoreService)
                    .prepopulateIfNeeded(database.recipeDao())

                recipeRepository.syncRecipesFromCloud()

                Log.i("MealCameraApplication", "✅ App initialization complete!")
            } catch (e: Exception) {
                Log.e("MealCameraApplication", "❌ Error during app initialization", e)
            }
        }
    }
}