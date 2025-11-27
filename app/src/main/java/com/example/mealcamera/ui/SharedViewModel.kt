package com.example.mealcamera.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mealcamera.data.RecipeRepository
import com.example.mealcamera.data.model.ScannedIngredient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.*

class SharedViewModel(private val repository: RecipeRepository) : ViewModel() {

    private val _scannedIngredients = MutableStateFlow<List<ScannedIngredient>>(emptyList())
    val scannedIngredients = _scannedIngredients.asStateFlow()

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
}
