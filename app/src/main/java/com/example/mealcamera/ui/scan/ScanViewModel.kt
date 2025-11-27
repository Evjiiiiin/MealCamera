package com.example.mealcamera.ui.scan

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mealcamera.data.model.ScannedIngredient
import com.example.mealcamera.ml.DetectedFood
import com.example.mealcamera.ml.TFLiteFoodDetector
import com.example.mealcamera.util.ImageStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class ScanViewModel(application: Application) : AndroidViewModel(application) {

    private val detector = TFLiteFoodDetector(application)
    private val imageStorage = ImageStorage(application)

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()

    fun processImageWithBitmap(bitmap: Bitmap, onResult: (List<DetectedFood>) -> Unit) {
        viewModelScope.launch {
            setProcessing(true)
            try {
                val detectedFoods = detector.detectFood(bitmap)
                onResult(detectedFoods)
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(emptyList())
            }
            // setProcessing(false) будет вызван в Activity после handleDetectionResult
        }
    }

    // --- ИСПРАВЛЕНО: Добавлен недостающий метод ---
    /**
     * Преобразует результат детекции в список ScannedIngredient.
     * @param detectedFoods Список распознанных моделью объектов.
     * @param capturedBitmap Снимок для создания миниатюры.
     * @param onResult Лямбда, которая вернет готовый список.
     */
    fun getIngredientsFromDetection(
        detectedFoods: List<DetectedFood>,
        capturedBitmap: Bitmap,
        onResult: (List<ScannedIngredient>) -> Unit
    ) {
        viewModelScope.launch {
            val newIngredients = detectedFoods.map { food ->
                val imagePath = imageStorage.saveImage(capturedBitmap, "thumb_${UUID.randomUUID()}.jpg")
                ScannedIngredient(
                    name = food.name,
                    imagePath = imagePath,
                    quantity = "1", // Значение по умолчанию
                    unit = "шт"     // Значение по умолчанию
                )
            }
            onResult(newIngredients)
        }
    }

    fun setProcessing(isProcessing: Boolean) {
        _isProcessing.value = isProcessing
    }
}
