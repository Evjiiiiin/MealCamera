package com.example.mealcamera

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.example.mealcamera.data.AppDatabase
import com.example.mealcamera.data.PrepopulateManager
import com.example.mealcamera.data.RecipeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// --- ИСПРАВЛЕНО: Реализуем ViewModelStoreOwner ---
class MealCameraApplication : Application(), ViewModelStoreOwner {

    // Создаем хранилище для ViewModel, которое будет жить вместе с приложением
    override val viewModelStore: ViewModelStore by lazy {
        ViewModelStore()
    }

    private val applicationScope = CoroutineScope(Dispatchers.Main)
    lateinit var repository: RecipeRepository
    lateinit var viewModelFactory: ViewModelFactory

    override fun onCreate() {
        super.onCreate()

        val database = AppDatabase.getDatabase(this)
        repository = RecipeRepository(database.recipeDao())

        // ViewModelFactory теперь использует Application контекст
        viewModelFactory = ViewModelFactory(this, repository)

        val prepopulateManager = PrepopulateManager(this)
        applicationScope.launch {
            prepopulateManager.prepopulateIfNeeded(database.recipeDao())
        }
    }
}
