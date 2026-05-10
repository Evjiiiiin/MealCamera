package com.example.mealcamera.ui.scan

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mealcamera.data.model.ScannedIngredient
import com.example.mealcamera.data.util.UnitHelper
import com.example.mealcamera.ml.DetectedFood
import com.example.mealcamera.util.ImageStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class ScanViewModel(application: Application) : AndroidViewModel(application) {

    private val imageStorage = ImageStorage(application)

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()

    private val _latestDetections = MutableStateFlow<List<DetectedFood>>(emptyList())
    val latestDetections = _latestDetections.asStateFlow()

    // Temporal Smoothing: храним историю количества детекций по типам для стабильности
    // Map<Label, List<Int>> где List - история количества в последних 5 кадрах
    private val detectionHistory = mutableMapOf<String, Int>()
    private val frameCountHistory = mutableMapOf<String, MutableList<Int>>()
    private val WINDOW_SIZE = 10
    private val MIN_CONFIRMATION = 4

    fun updateDetections(newDetections: List<DetectedFood>) {
        val currentCounts = newDetections.groupBy { it.originalLabel }.mapValues { it.value.size }
        val allLabels = (frameCountHistory.keys + currentCounts.keys).toSet()
        val stableDetections = mutableListOf<DetectedFood>()

        for (label in allLabels) {
            val history = frameCountHistory.getOrPut(label) { mutableListOf() }
            val countInThisFrame = currentCounts[label] ?: 0
            
            history.add(countInThisFrame)
            if (history.size > WINDOW_SIZE) history.removeAt(0)

            // Считаем медианное или среднее количество объектов этого типа
            val averageCount = Math.round(history.average()).toInt()
            
            if (averageCount > 0) {
                // Берем детекции из текущего кадра или создаем виртуальные для UI, если в этом кадре пропуск
                val existing = newDetections.filter { it.originalLabel == label }
                if (existing.isNotEmpty()) {
                    stableDetections.addAll(existing.take(averageCount))
                } else if (history.count { it > 0 } >= MIN_CONFIRMATION) {
                    // Если объект часто мелькал, но на этом кадре пропал - оставляем "фантомную" метку для плавности
                    repeat(averageCount) {
                        stableDetections.add(DetectedFood(
                            name = label,
                            originalLabel = label,
                            confidence = 0.5f
                        ))
                    }
                }
            }
        }

        _latestDetections.value = stableDetections
    }

    private fun normalize(name: String): String {
        return name.trim().lowercase().replace("ё", "е")
    }

    fun processCapturedDetections(
        bitmap: Bitmap,
        detections: List<DetectedFood>,
        onResult: (List<ScannedIngredient>) -> Unit
    ) {
        viewModelScope.launch {
            _isProcessing.value = true

            val newIngredients = withContext(Dispatchers.IO) {
                if (detections.isEmpty()) return@withContext emptyList()

                val fileName = "scan_${UUID.randomUUID()}.jpg"
                val imagePath = imageStorage.saveImage(bitmap, fileName)

                // Группируем по имени, чтобы посчитать количество, если это одинаковые продукты
                detections.groupBy { it.name }.map { (name, detectedList) ->
                    val normalizedName = normalize(name)
                    ScannedIngredient(
                        name = normalizedName,
                        imagePath = imagePath,
                        quantity = detectedList.size.toString(),
                        unit = UnitHelper.getDefaultUnit(normalizedName),
                        timestamp = System.currentTimeMillis() + name.hashCode()
                    )
                }
            }

            onResult(newIngredients)
            _isProcessing.value = false
        }
    }
}