package com.example.mealcamera

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.mealcamera.data.FavoriteRepository
import com.example.mealcamera.data.RecipeRepository
import com.example.mealcamera.ui.SharedViewModel
import com.example.mealcamera.ui.cooking.CookingViewModel
import com.example.mealcamera.ui.detail.RecipeDetailViewModel
import com.example.mealcamera.ui.home.MainViewModel
import com.example.mealcamera.ui.profile.FavoritesViewModel
import com.example.mealcamera.ui.result.ResultViewModel
import com.example.mealcamera.ui.scan.ScanViewModel

class ViewModelFactory(
    private val application: Application,
    private val recipeRepository: RecipeRepository,
    private val favoriteRepository: FavoriteRepository,
    private val sharedViewModel: SharedViewModel
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(MainViewModel::class.java) ->
                MainViewModel(recipeRepository) as T

            modelClass.isAssignableFrom(RecipeDetailViewModel::class.java) ->
                RecipeDetailViewModel(recipeRepository, favoriteRepository, sharedViewModel) as T

            modelClass.isAssignableFrom(ResultViewModel::class.java) ->
                ResultViewModel(recipeRepository, sharedViewModel) as T  // ← ИСПРАВЛЕНО: добавлен sharedViewModel

            modelClass.isAssignableFrom(ScanViewModel::class.java) ->
                ScanViewModel(application) as T

            modelClass.isAssignableFrom(SharedViewModel::class.java) ->
                sharedViewModel as T

            modelClass.isAssignableFrom(FavoritesViewModel::class.java) ->
                FavoritesViewModel(favoriteRepository) as T

            modelClass.isAssignableFrom(CookingViewModel::class.java) ->
                CookingViewModel(recipeRepository) as T

            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}