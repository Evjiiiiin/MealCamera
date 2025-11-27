package com.example.mealcamera.ui.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mealcamera.data.RecipeRepository
import com.example.mealcamera.data.model.EditableIngredient
import com.example.mealcamera.data.model.RecipeResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ResultViewModel(private val repository: RecipeRepository) : ViewModel() {

    // Хранилище для ингредиентов, которые мы редактируем
    private val _editableIngredients = MutableStateFlow<List<EditableIngredient>>(emptyList())
    val editableIngredients = _editableIngredients.asStateFlow()

    // Хранилища для результатов поиска
    private val _perfectRecipes = MutableStateFlow<List<RecipeResult>>(emptyList())
    val perfectRecipes = _perfectRecipes.asStateFlow()

    private val _oneMissingRecipes = MutableStateFlow<List<RecipeResult>>(emptyList())
    val oneMissingRecipes = _oneMissingRecipes.asStateFlow()

    private val _twoMissingRecipes = MutableStateFlow<List<RecipeResult>>(emptyList())
    val twoMissingRecipes = _twoMissingRecipes.asStateFlow()

    /**
     * Вызывается один раз при создании Activity для установки начального списка.
     * Проверяет, не был ли список уже установлен (для сохранения состояния).
     */
    fun setInitialIngredients(initialIngredients: List<EditableIngredient>) {
        if (_editableIngredients.value.isEmpty()) {
            _editableIngredients.value = initialIngredients
        }
    }

    /**
     * Вызывается по нажатию кнопки "Подобрать рецепты".
     * Принимает обновленный список от адаптера и запускает поиск.
     */
    fun findRecipes(updatedIngredients: List<EditableIngredient>) {
        // Сохраняем обновленные данные
        _editableIngredients.value = updatedIngredients

        viewModelScope.launch {
            try {
                // Извлекаем только имена для поиска
                val ingredientNames = updatedIngredients.map { it.name }
                val (perfect, oneMissing, twoMissing) = repository.filterRecipesByAvailableIngredients(ingredientNames)

                _perfectRecipes.value = perfect
                _oneMissingRecipes.value = oneMissing
                _twoMissingRecipes.value = twoMissing
            } catch (e: Exception) {
                // Обработка ошибок, если необходимо
                e.printStackTrace()
                _perfectRecipes.value = emptyList()
                _oneMissingRecipes.value = emptyList()
                _twoMissingRecipes.value = emptyList()
            }
        }
    }
}
