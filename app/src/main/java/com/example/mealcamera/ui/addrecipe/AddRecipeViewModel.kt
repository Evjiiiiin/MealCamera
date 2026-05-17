package com.example.mealcamera.ui.addrecipe

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mealcamera.data.RecipeRepository
import com.example.mealcamera.data.remote.CloudIngredient
import com.example.mealcamera.data.remote.CloudRecipe
import com.example.mealcamera.data.remote.StepData
import com.example.mealcamera.util.ImageStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

sealed class AddRecipeState {
    object Idle : AddRecipeState()
    object Loading : AddRecipeState()
    object Success : AddRecipeState()
    object Deleted : AddRecipeState()
    data class Error(val message: String) : AddRecipeState()
    data class Loaded(val recipe: CloudRecipe) : AddRecipeState()
}

class AddRecipeViewModel(
    private val repository: RecipeRepository,
    private val imageStorage: ImageStorage
) : ViewModel() {

    private val _uiState = MutableStateFlow<AddRecipeState>(AddRecipeState.Idle)
    val uiState = _uiState.asStateFlow()

    private var editingRecipeId: Long? = null
    private var editingFirestoreId: String? = null

    private fun capitalize(text: String): String {
        return text.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    fun loadRecipe(recipeId: Long) {
        if (recipeId <= 0) return

        viewModelScope.launch {
            _uiState.value = AddRecipeState.Loading
            Log.d("AddRecipeViewModel", "Загрузка рецепта с ID: $recipeId")
            try {
                val recipe = repository.getFullRecipeForEditing(recipeId)
                if (recipe != null) {
                    editingRecipeId = recipeId
                    val localRecipe = repository.getRecipeByIdSync(recipeId)
                    editingFirestoreId = localRecipe?.firestoreId
                    _uiState.value = AddRecipeState.Loaded(recipe)
                } else {
                    _uiState.value = AddRecipeState.Error("Рецепт не найден")
                }
            } catch (e: Exception) {
                Log.e("AddRecipeViewModel", "Ошибка загрузки", e)
                _uiState.value = AddRecipeState.Error("Ошибка загрузки: ${e.message}")
            }
        }
    }

    fun saveRecipe(
        name: String, description: String, category: String, cuisine: String,
        cuisineCode: String, prepTime: String, ingredients: List<CloudIngredient>,
        steps: List<StepData>, mainImage: Bitmap?, stepImages: Map<Int, Bitmap?>,
        userId: String, isPublic: Boolean, calories: Int, proteins: Double,
        fats: Double, carbs: Double, currentImagePath: String, totalWeight: Int
    ) {
        val capitalizedName = capitalize(name)
        val capitalizedIngredients = ingredients.map { it.copy(name = capitalize(it.name)) }
        val capitalizedSteps = steps.map { step ->
            step.copy(
                title = capitalize(step.title),
                ingredients = step.ingredients.map { it.copy(name = capitalize(it.name)) }
            )
        }

        // --- Валидация (синхронная) ---
        if (capitalizedName.isBlank()) {
            _uiState.value = AddRecipeState.Error("Введите название блюда")
            return
        }

        if (category.isBlank() || category == "Выберите категорию") {
            _uiState.value = AddRecipeState.Error("Выберите категорию блюда")
            return
        }
        if (cuisine.isBlank() || cuisine == "Выберите кухню") {
            _uiState.value = AddRecipeState.Error("Выберите кухню")
            return
        }

        if (description.isBlank()) {
            _uiState.value = AddRecipeState.Error("Добавьте описание рецепта")
            return
        }
        if (prepTime.isBlank() || prepTime == "0 мин") {
            _uiState.value = AddRecipeState.Error("Укажите время приготовления")
            return
        }
        if (totalWeight <= 0) {
            _uiState.value = AddRecipeState.Error("Укажите вес порции (г)")
            return
        }
        if (capitalizedIngredients.isEmpty()) {
            _uiState.value = AddRecipeState.Error("Добавьте хотя бы один основной ингредиент")
            return
        }
        if (capitalizedSteps.isEmpty()) {
            _uiState.value = AddRecipeState.Error("Добавьте хотя бы один шаг приготовления")
            return
        }

        viewModelScope.launch {
            _uiState.value = AddRecipeState.Loading
            
            // --- Валидация съедобности (асинхронная, так как требует доступа к БД) ---
            val allIngNames = capitalizedIngredients.map { it.name } + 
                              capitalizedSteps.flatMap { s -> s.ingredients.map { it.name } }
            
            for (ingName in allIngNames) {
                if (!repository.isEdible(ingName)) {
                    _uiState.value = AddRecipeState.Error("Можно вводить только продукты питания: $ingName")
                    return@launch
                }
            }

            try {
                val isEditing = editingRecipeId != null
                val recipeIdForSaving = editingRecipeId ?: System.currentTimeMillis()

                val finalMainImage = when {
                    mainImage != null -> imageStorage.saveRecipeImage(recipeIdForSaving, mainImage)
                    currentImagePath.isNotEmpty() && File(currentImagePath).exists() -> currentImagePath
                    else -> ""
                }

                val updatedSteps = capitalizedSteps.mapIndexed { index, step ->
                    val newBitmap = stepImages[index]
                    if (newBitmap != null) {
                        val newPath = imageStorage.saveStepImage(recipeIdForSaving, index, newBitmap)
                        step.copy(imagePath = newPath)
                    } else {
                        step
                    }
                }

                val recipe = CloudRecipe(
                    capitalizedName, description, finalMainImage, category, prepTime, 0, cuisine, cuisineCode,
                    capitalizedIngredients, updatedSteps, userId, isPublic, calories, proteins, fats, carbs, totalWeight
                )

                val result = if (isEditing) {
                    repository.updateUserRecipe(editingRecipeId!!, editingFirestoreId, recipe, userId, isPublic)
                } else {
                    repository.addUserRecipe(recipe, userId, isPublic) != null
                }

                if (result) _uiState.value = AddRecipeState.Success
                else _uiState.value = AddRecipeState.Error("Ошибка при сохранении")

            } catch (e: Exception) {
                Log.e("AddRecipeViewModel", "Ошибка сохранения", e)
                _uiState.value = AddRecipeState.Error("Ошибка: ${e.localizedMessage}")
            }
        }
    }

    fun deleteRecipe() {
        val id = editingRecipeId ?: return
        viewModelScope.launch {
            _uiState.value = AddRecipeState.Loading
            if (repository.deleteUserRecipe(id, editingFirestoreId)) {
                _uiState.value = AddRecipeState.Deleted
            } else {
                _uiState.value = AddRecipeState.Error("Ошибка удаления")
            }
        }
    }

    fun resetEditing() {
        editingRecipeId = null
        editingFirestoreId = null
        _uiState.value = AddRecipeState.Idle
    }
}
