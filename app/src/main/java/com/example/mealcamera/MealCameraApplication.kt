package com.example.mealcamera

import android.app.Application
import com.example.mealcamera.data.AppDatabase
import com.example.mealcamera.data.RecipeRepository
import com.example.mealcamera.ui.SharedViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MealCameraApplication : Application() {

    private val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }

    val recipeRepository: RecipeRepository by lazy {
        RecipeRepository(database.recipeDao(), database.shoppingListDao())
    }

    val viewModelFactory: ViewModelFactory by lazy {
        ViewModelFactory(this, recipeRepository)
    }

    val sharedViewModel: SharedViewModel by lazy {
        SharedViewModel(recipeRepository)
    }

    // НОВОЕ: Состояние инициализации приложения, доступное для всего приложения
    private val _isAppInitialized = MutableStateFlow(false)
    val isAppInitialized: StateFlow<Boolean> = _isAppInitialized.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        // Запускаем тяжелую инициализацию в фоновом потоке.
        // Используем CoroutineScope для Application, т.к. Application не является LifecycleOwner.
        // Операции внутри launch не будут блокировать главный поток.
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Эта строка вызывает ленивую инициализацию database и recipeRepository
                // и выполнит все необходимые операции Room (например, миграции) в фоновом потоке.
                recipeRepository.getAllDbIngredients()

                // Здесь также может быть любая другая тяжелая инициализация,
                // например, загрузка ML-моделей или сетевые запросы,
                // которые нужно выполнить перед запуском основного UI.
                // Пример: MyMLModelLoader.loadModel(applicationContext)

            } catch (e: Exception) {
                e.printStackTrace()
                // Логирование или обработка ошибок инициализации
            } finally {
                _isAppInitialized.value = true // Сообщаем, что инициализация завершена
            }
        }
    }
}
