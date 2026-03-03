package com.example.mealcamera.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mealcamera.data.RecipeRepository
import com.example.mealcamera.data.model.ScannedIngredient
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SharedViewModel(private val repository: RecipeRepository) : ViewModel() {

    private val _scannedIngredients = MutableStateFlow<List<ScannedIngredient>>(emptyList())
    val scannedIngredients = _scannedIngredients.asStateFlow()

    private val _favoriteChanged = MutableSharedFlow<Pair<Long, Boolean>>()
    val favoriteChanged = _favoriteChanged.asSharedFlow()

    fun addIngredients(newIngredients: List<ScannedIngredient>) {
        _scannedIngredients.value += newIngredients
    }

    fun addIngredientManually(name: String) {
        val newIngredient = ScannedIngredient(name, "", "1", "шт")
        addIngredients(listOf(newIngredient))
    }

    fun removeIngredient(ingredient: ScannedIngredient) {
        _scannedIngredients.value = _scannedIngredients.value.filter { it.timestamp != ingredient.timestamp }
    }

    fun notifyFavoriteChanged(recipeId: Long, isFavorite: Boolean) {
        viewModelScope.launch {
            _favoriteChanged.emit(Pair(recipeId, isFavorite))
        }
    }
}