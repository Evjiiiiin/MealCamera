package com.example.mealcamera.ui.addrecipe

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mealcamera.data.RecipeRepository
import com.example.mealcamera.data.remote.CloudRecipe
import com.example.mealcamera.data.remote.StepData
import com.example.mealcamera.util.FirebaseStorageHelper
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed class AddRecipeState {
    object Idle : AddRecipeState()
    object Loading : AddRecipeState()
    object Success : AddRecipeState()
    data class Error(val message: String) : AddRecipeState()
    data class Loaded(val recipe: CloudRecipe) : AddRecipeState()
}

class AddRecipeViewModel(
    private val repository: RecipeRepository,
    private val storageHelper: FirebaseStorageHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow<AddRecipeState>(AddRecipeState.Idle)
    val uiState = _uiState.asStateFlow()

    private var editingRecipeId: Long? = null
    private var editingFirestoreId: String? = null

    fun loadRecipe(recipeId: Long) {
        viewModelScope.launch {
            _uiState.value = AddRecipeState.Loading
            try {
                val recipe = repository.getFullRecipeForEditing(recipeId)
                if (recipe != null) {
                    editingRecipeId = recipeId
                    
                    // Получаем firestoreId из локального объекта Recipe
                    val localRecipe = repository.getRecipeById(recipeId).first()
                    editingFirestoreId = localRecipe.firestoreId
                    
                    _uiState.value = AddRecipeState.Loaded(recipe)
                } else {
                    _uiState.value = AddRecipeState.Error("Не удалось загрузить рецепт")
                }
            } catch (e: Exception) {
                _uiState.value = AddRecipeState.Error("Ошибка загрузки: ${e.localizedMessage}")
            }
        }
    }

    fun saveRecipe(
        name: String,
        description: String,
        category: String,
        cuisine: String,
        cuisineCode: String,
        prepTime: String,
        ingredients: List<com.example.mealcamera.data.remote.CloudIngredient>,
        steps: List<StepData>,
        mainImage: Bitmap?,
        stepImages: Map<Int, Bitmap?>,
        userId: String,
        isPublic: Boolean,
        currentImagePath: String = ""
    ) {
        if (!validate(name, description, ingredients, steps)) return

        viewModelScope.launch {
            _uiState.value = AddRecipeState.Loading
            try {
                // 1. Загружаем главное изображение
                val mainImagePath = mainImage?.let {
                    storageHelper.uploadRecipeImage(it)
                } ?: currentImagePath

                // 2. Загружаем изображения шагов параллельно
                val updatedSteps = steps.mapIndexed { index, step ->
                    val stepBitmap = stepImages[index]
                    if (stepBitmap != null) {
                        async {
                            val path = storageHelper.uploadRecipeImage(stepBitmap) ?: ""
                            step.copy(imagePath = path)
                        }
                    } else {
                        async { step }
                    }
                }.awaitAll()

                // 3. Создаем объект рецепта
                val recipe = CloudRecipe(
                    name = name,
                    description = description,
                    imagePath = mainImagePath,
                    category = category,
                    prepTime = prepTime,
                    cuisine = cuisine,
                    cuisineCode = cuisineCode,
                    ingredients = ingredients,
                    steps = updatedSteps,
                    authorId = userId,
                    isPublic = isPublic
                )

                // 4. Сохраняем или обновляем
                val result = if (editingRecipeId != null) {
                    repository.updateUserRecipe(editingRecipeId!!, editingFirestoreId, recipe, userId, isPublic)
                } else {
                    repository.addUserRecipe(recipe, userId, isPublic) != null
                }

                if (result) {
                    _uiState.value = AddRecipeState.Success
                } else {
                    _uiState.value = AddRecipeState.Error("Не удалось сохранить изменения")
                }

            } catch (e: Exception) {
                _uiState.value = AddRecipeState.Error("Ошибка: ${e.localizedMessage}")
            }
        }
    }

    private fun validate(
        name: String,
        description: String,
        ingredients: List<com.example.mealcamera.data.remote.CloudIngredient>,
        steps: List<StepData>
    ): Boolean {
        if (name.isBlank()) {
            _uiState.value = AddRecipeState.Error("Название не может быть пустым")
            return false
        }
        if (description.isBlank()) {
            _uiState.value = AddRecipeState.Error("Описание не может быть пустым")
            return false
        }
        if (ingredients.isEmpty()) {
            _uiState.value = AddRecipeState.Error("Добавьте хотя бы один ингредиент")
            return false
        }
        if (steps.isEmpty()) {
            _uiState.value = AddRecipeState.Error("Добавьте хотя бы один шаг")
            return false
        }
        return true
    }
}
