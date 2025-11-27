package com.example.mealcamera

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.mealcamera.data.RecipeRepository
import com.example.mealcamera.ui.SharedViewModel
import com.example.mealcamera.ui.detail.RecipeDetailViewModel
import com.example.mealcamera.ui.home.MainViewModel
import com.example.mealcamera.ui.result.ResultViewModel
import com.example.mealcamera.ui.scan.ScanViewModel

class ViewModelFactory(
    private val application: Application, // <-- Application все еще нужна
    private val repository: RecipeRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(MainViewModel::class.java) ->
                MainViewModel(repository) as T

            modelClass.isAssignableFrom(RecipeDetailViewModel::class.java) ->
                RecipeDetailViewModel(repository) as T

            modelClass.isAssignableFrom(ResultViewModel::class.java) ->
                ResultViewModel(repository) as T

            modelClass.isAssignableFrom(ScanViewModel::class.java) ->
                ScanViewModel(application) as T

            modelClass.isAssignableFrom(SharedViewModel::class.java) ->
                SharedViewModel(repository) as T

            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
