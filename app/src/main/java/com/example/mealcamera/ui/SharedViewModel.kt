package com.example.mealcamera.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mealcamera.data.RecipeRepository
import com.example.mealcamera.data.model.EditableIngredient
import com.example.mealcamera.data.model.ScannedIngredient
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SharedViewModel(private val repository: RecipeRepository) : ViewModel() {

    // Временная корзина для ScanActivity (не сохраняется между сессиями)
    private val _temporaryIngredients = MutableStateFlow<List<ScannedIngredient>>(emptyList())
    val temporaryIngredients = _temporaryIngredients.asStateFlow()

    // Активная сессия для ResultActivity (сохраняется)
    private val _activeSession = MutableStateFlow<SessionData?>(null)
    val activeSession = _activeSession.asStateFlow()

    private val _favoriteChanged = MutableSharedFlow<Pair<Long, Boolean>>()
    val favoriteChanged = _favoriteChanged.asSharedFlow()

    data class SessionData(
        val ingredients: List<EditableIngredient>,
        val portions: Int,
        val lastUpdated: Long = System.currentTimeMillis()
    )

    // ========== МЕТОДЫ ДЛЯ ВРЕМЕННОЙ КОРЗИНЫ (ScanActivity) ==========

    fun addToTemporary(newIngredients: List<ScannedIngredient>) {
        val current = _temporaryIngredients.value.toMutableList()

        newIngredients.forEach { newIng ->
            if (current.none { it.timestamp == newIng.timestamp }) {
                current.add(newIng)
            }
        }

        _temporaryIngredients.value = current
    }

    fun removeFromTemporary(ingredient: ScannedIngredient) {
        _temporaryIngredients.value = _temporaryIngredients.value.filter { it.timestamp != ingredient.timestamp }
    }

    fun clearTemporary() {
        _temporaryIngredients.value = emptyList()
    }

    // ========== МЕТОДЫ ДЛЯ АКТИВНОЙ СЕССИИ (ResultActivity) ==========

    fun startSession(ingredients: List<ScannedIngredient>) {
        // Преобразуем ScannedIngredient в EditableIngredient для сессии
        val editableIngredients = ingredients.mapIndexed { index, scanned ->
            EditableIngredient(
                id = scanned.timestamp, // Используем timestamp как ID
                name = scanned.name,
                quantity = "1",
                unit = scanned.unit
            )
        }

        _activeSession.value = SessionData(
            ingredients = editableIngredients,
            portions = 1
        )

        // Очищаем временную корзину после старта сессии
        clearTemporary()
    }

    fun updateSession(ingredients: List<EditableIngredient>, portions: Int) {
        _activeSession.value = SessionData(
            ingredients = ingredients,
            portions = portions,
            lastUpdated = System.currentTimeMillis()
        )
    }

    fun addToSession(newIngredients: List<EditableIngredient>) {
        val currentSession = _activeSession.value ?: return
        val currentIngredients = currentSession.ingredients.toMutableList()

        // Добавляем только новые ингредиенты (по имени)
        newIngredients.forEach { newIng ->
            if (currentIngredients.none { it.name == newIng.name }) {
                currentIngredients.add(newIng)
            }
        }

        _activeSession.value = currentSession.copy(
            ingredients = currentIngredients,
            lastUpdated = System.currentTimeMillis()
        )
    }

    fun endSession() {
        _activeSession.value = null
        clearTemporary()
    }

    fun isSessionActive(): Boolean = _activeSession.value != null

    fun getSessionAge(): Long {
        return _activeSession.value?.let {
            System.currentTimeMillis() - it.lastUpdated
        } ?: Long.MAX_VALUE
    }

    // ========== МЕТОДЫ ДЛЯ ПРОВЕРКИ СОСТОЯНИЯ ==========

    fun shouldResetSession(): Boolean {
        // Сбрасываем сессию если она старше 30 минут
        return getSessionAge() > 30 * 60 * 1000
    }

    // ========== ИЗБРАННОЕ ==========

    fun notifyFavoriteChanged(recipeId: Long, isFavorite: Boolean) {
        viewModelScope.launch {
            _favoriteChanged.emit(Pair(recipeId, isFavorite))
        }
    }
}