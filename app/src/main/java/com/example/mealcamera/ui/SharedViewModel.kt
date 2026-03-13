package com.example.mealcamera.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mealcamera.data.RecipeRepository
import com.example.mealcamera.data.model.EditableIngredient
import com.example.mealcamera.data.model.ScannedIngredient
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SharedViewModel(private val repository: RecipeRepository) : ViewModel() {

    // Временная корзина для ScanActivity (не сохраняется между сессиями)
    private val _temporaryIngredients = MutableStateFlow<List<ScannedIngredient>>(emptyList())
    val temporaryIngredients = _temporaryIngredients.asStateFlow()

    // Активная сессия для ResultActivity (привязана к userId)
    private val _activeSession = MutableStateFlow<SessionData?>(null)
    val activeSession = _activeSession.asStateFlow()

    private val _favoriteChanged = MutableSharedFlow<Pair<Long, Boolean>>()
    val favoriteChanged = _favoriteChanged.asSharedFlow()

    data class SessionData(
        val userId: String,                    // Добавлено: привязка к пользователю
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
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: "guest"

        // Преобразуем ScannedIngredient в EditableIngredient для сессии
        val editableIngredients = ingredients.mapIndexed { index, scanned ->
            EditableIngredient(
                id = scanned.timestamp,
                name = scanned.name,
                quantity = "1",
                unit = scanned.unit
            )
        }

        _activeSession.value = SessionData(
            userId = currentUserId,
            ingredients = editableIngredients,
            portions = 1
        )

        clearTemporary()
    }

    fun updateSession(ingredients: List<EditableIngredient>, portions: Int) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: "guest"

        _activeSession.value = SessionData(
            userId = currentUserId,
            ingredients = ingredients,
            portions = portions,
            lastUpdated = System.currentTimeMillis()
        )
    }

    fun addToSession(newIngredients: List<EditableIngredient>) {
        val currentSession = _activeSession.value ?: return
        val currentIngredients = currentSession.ingredients.toMutableList()

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

    fun isSessionActive(): Boolean {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: "guest"
        val session = _activeSession.value

        // Сессия активна только если принадлежит текущему пользователю
        return session != null && session.userId == currentUserId && !shouldResetSession()
    }

    fun getSessionAge(): Long {
        return _activeSession.value?.let {
            System.currentTimeMillis() - it.lastUpdated
        } ?: Long.MAX_VALUE
    }

    fun shouldResetSession(): Boolean {
        // Сбрасываем сессию если она старше 30 минут
        return getSessionAge() > 30 * 60 * 1000
    }

    // Проверка соответствия пользователя
    fun isSessionForCurrentUser(): Boolean {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: "guest"
        return _activeSession.value?.userId == currentUserId
    }

    // ========== ИЗБРАННОЕ ==========

    fun notifyFavoriteChanged(recipeId: Long, isFavorite: Boolean) {
        viewModelScope.launch {
            _favoriteChanged.emit(Pair(recipeId, isFavorite))
        }
    }
}