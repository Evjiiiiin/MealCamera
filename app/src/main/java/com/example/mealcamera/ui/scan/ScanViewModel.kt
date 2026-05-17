package com.example.mealcamera.ui.scan

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mealcamera.data.RecipeRepository
import com.example.mealcamera.data.model.ScannedIngredient
import com.example.mealcamera.data.remote.FirestoreService
import com.example.mealcamera.data.util.UnitHelper
import com.example.mealcamera.ml.DetectedFood
import com.example.mealcamera.util.ImageStorage
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

class ScanViewModel(
    application: Application,
    private val repository: RecipeRepository
) : AndroidViewModel(application) {

    private val imageStorage = ImageStorage(application)
    private val firestoreService = FirestoreService()
    private val auth = FirebaseAuth.getInstance()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()

    private val _latestDetections = MutableStateFlow<List<DetectedFood>>(emptyList())
    val latestDetections = _latestDetections.asStateFlow()

    private val _userAllergens = MutableStateFlow<List<String>>(emptyList())
    val userAllergens = _userAllergens.asStateFlow()

    private val detectionHistory = mutableMapOf<String, MutableList<Int>>()
    private val lastConfirmedDetections = mutableMapOf<String, List<DetectedFood>>()
    private val historyWindowSize = 8
    private val minConfirmationFrames = 3

    init {
        observeUserAllergens()
    }

    private fun observeUserAllergens() {
        val userId = auth.currentUser?.uid ?: return
        firestoreService.getUserAllergensFlow(userId)
            .onEach { allergens ->
                _userAllergens.value = repository.expandAllergens(allergens).map { it.lowercase() }
            }
            .launchIn(viewModelScope)
    }

    fun updateDetections(newDetections: List<DetectedFood>) {
        val detectionsByLabel = newDetections.groupBy { normalizeLabel(it.originalLabel) }
        val allLabels = (detectionHistory.keys + detectionsByLabel.keys).toSet()
        val stableDetections = mutableListOf<DetectedFood>()

        for (label in allLabels) {
            val history = detectionHistory.getOrPut(label) { mutableListOf() }
            val currentDetections = detectionsByLabel[label].orEmpty().sortedByDescending { it.confidence }

            history.add(currentDetections.size)
            if (history.size > historyWindowSize) history.removeAt(0)

            if (currentDetections.isNotEmpty()) {
                lastConfirmedDetections[label] = currentDetections
            }

            val visibleFrames = history.count { it > 0 }
            val smoothedCount = history.average().roundToInt().coerceAtLeast(0)
            val shouldShow = visibleFrames >= minConfirmationFrames || currentDetections.isNotEmpty()
            if (!shouldShow || smoothedCount == 0) continue

            val source = if (currentDetections.isNotEmpty()) currentDetections else lastConfirmedDetections[label].orEmpty()
            stableDetections.addAll(source.take(smoothedCount))
        }

        val activeLabels = detectionHistory.filterValues { history -> history.any { it > 0 } }.keys
        lastConfirmedDetections.keys.retainAll(activeLabels)
        detectionHistory.keys.retainAll(activeLabels + detectionsByLabel.keys)

        _latestDetections.value = stableDetections.sortedByDescending { it.confidence }
    }

    private fun normalizeLabel(name: String): String {
        return name.trim().lowercase().replace("ё", "е")
    }

    private fun capitalize(text: String): String {
        return text.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
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

                detections.groupBy { it.name }.map { (name, detectedList) ->
                    val unit = UnitHelper.getDefaultUnit(name)
                    val estimatedQuantity = estimateQuantity(detectedList.size, unit)
                    ScannedIngredient(
                        name = capitalize(name),
                        imagePath = imagePath,
                        quantity = UnitHelper.formatQuantity(estimatedQuantity),
                        unit = unit,
                        timestamp = System.currentTimeMillis() + name.hashCode()
                    )
                }
            }

            onResult(newIngredients)
            _isProcessing.value = false
        }
    }

    private fun estimateQuantity(count: Int, unit: String): Double {
        return when (unit) {
            "шт" -> count.toDouble()
            "мл" -> 200.0 * count
            else -> 100.0 * count
        }
    }
}
