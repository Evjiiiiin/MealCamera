package com.example.mealcamera

import android.app.Application
import android.util.Log
import com.example.mealcamera.data.AppDatabase
import com.example.mealcamera.data.FavoriteRepository
import com.example.mealcamera.data.PrepopulateManager
import com.example.mealcamera.data.RecipeRepository
import com.example.mealcamera.data.remote.FirestoreService
import com.example.mealcamera.ui.SharedViewModel
import com.example.mealcamera.util.ThemeManager
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
        
        // Применяем сохраненную тему сразу при запуске
        ThemeManager.applyTheme(this)

        // Отложенный запуск тяжелых операций, чтобы не блокировать UI
        appScope.launch {
            try {
                Log.i("MealCameraApplication", "🚀 Начало инициализации данных...")
                val prepopulateManager = PrepopulateManager(this@MealCameraApplication, firestoreService)
                prepopulateManager.prepopulateIfNeeded(database.recipeDao())

                Log.i("MealCameraApplication", "🔄 Синхронизация рецептов с облаком...")
                recipeRepository.syncRecipesFromCloud()

                Log.i("MealCameraApplication", "✅ Инициализация завершена успешно!")
            } catch (e: Exception) {
                Log.e("MealCameraApplication", "❌ Ошибка при инициализации", e)
            }
        }
    }
}
