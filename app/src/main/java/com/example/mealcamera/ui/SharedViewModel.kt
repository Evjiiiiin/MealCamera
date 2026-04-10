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

    private val _temporaryIngredients = MutableStateFlow<List<ScannedIngredient>>(emptyList())
    val temporaryIngredients = _temporaryIngredients.asStateFlow()

    private val _activeSession = MutableStateFlow<SessionData?>(null)
    val activeSession = _activeSession.asStateFlow()

    private val _favoriteChanged = MutableSharedFlow<Pair<Long, Boolean>>()
    val favoriteChanged = _favoriteChanged.asSharedFlow()

    private val _allergensChanged = MutableSharedFlow<Unit>()
    val allergensChanged = _allergensChanged.asSharedFlow()

    data class SessionData(
        val userId: String,
        val ingredients: List<EditableIngredient>,
        val portions: Int,
        val lastUpdated: Long = System.currentTimeMillis()
    )

    fun addToTemporary(newIngredients: List<ScannedIngredient>) {
        val current = _temporaryIngredients.value.toMutableList()
        newIngredients.forEach { newIng ->
            if (current.none { it.name.equals(newIng.name, ignoreCase = true) }) {
                current.add(newIng)
            }
        }
        _temporaryIngredients.value = current
        syncSessionWithTemporary()
    }

    fun setTemporaryIngredients(newList: List<ScannedIngredient>) {
        _temporaryIngredients.value = newList
        syncSessionWithTemporary()
    }

    fun removeFromTemporary(ingredient: ScannedIngredient) {
        _temporaryIngredients.value = _temporaryIngredients.value.filter { it.name != ingredient.name }
        syncSessionWithTemporary()
    }

    private fun syncSessionWithTemporary() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: "guest"
        val editable = _temporaryIngredients.value.map {
            EditableIngredient(it.timestamp, it.name, it.quantity.ifBlank { "1" }, it.unit.ifBlank { "г" })
        }
        _activeSession.value = SessionData(currentUserId, editable, _activeSession.value?.portions ?: 1)
    }

    fun clearTemporary() {
        _temporaryIngredients.value = emptyList()
        _activeSession.value = null
    }

    fun startSession(_ingredients: List<ScannedIngredient>) {
        // Используем текущий сейф для старта
        syncSessionWithTemporary()
    }

    fun updateSession(ingredients: List<EditableIngredient>, portions: Int) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: "guest"
        _activeSession.value = SessionData(currentUserId, ingredients, portions)
        
        // Синхронизируем обратно в temporary для камеры
        _temporaryIngredients.value = ingredients.map {
            ScannedIngredient(it.name, "", it.quantity, it.unit, it.id)
        }
    }

    fun endSession() {
        clearTemporary()
    }

    fun notifyFavoriteChanged(recipeId: Long, isFavorite: Boolean) {
        viewModelScope.launch {
            _favoriteChanged.emit(Pair(recipeId, isFavorite))
        }
    }

    fun notifyAllergensChanged() {
        viewModelScope.launch {
            _allergensChanged.emit(Unit)
        }
    }
}