package com.example.mealcamera.ui.scan

import android.app.Application
import android.graphics.Bitmap
import android.graphics.RectF
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
    private val historyWindowSize = 10
    private val minConfirmationFrames = 5
    private val iouThreshold = 0.4f
    private val maxShownGroups = 3

    // DEMO PROFILE: финальные ограничения для стабильного списка.
    private val captureIouDedupThreshold = 0.55f
    private val captureMaxClasses = 4
    private val captureMaxObjectsTotal = 6

    private var allowedIngredients: Set<String> = emptySet()
    private var previousFrameDetections: List<DetectedFood> = emptyList()

    init {
        observeUserAllergens()
        warmUpAllowedIngredients()
    }

    private fun warmUpAllowedIngredients() {
        viewModelScope.launch(Dispatchers.IO) {
            allowedIngredients = repository.getAllDbIngredients()
                .map { normalizeLabel(it.name) }
                .toSet()
        }
    }

    private fun observeUserAllergens() {
        val userId = auth.currentUser?.uid ?: return

        firestoreService.getUserAllergensFlow(userId)
            .onEach { allergens ->
                _userAllergens.value =
                    repository.expandAllergens(allergens).map { it.lowercase() }
            }
            .launchIn(viewModelScope)
    }

    fun updateDetections(newDetections: List<DetectedFood>) {
        val filteredByWhitelist = filterByAllowedIngredients(newDetections)
        val trackedDetections = stabilizeWithIouTracking(filteredByWhitelist)

        val detectionsByLabel = trackedDetections.groupBy { normalizeLabel(it.name) }
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
            val shouldShow = visibleFrames >= minConfirmationFrames
            if (!shouldShow || smoothedCount == 0) continue

            val source = if (currentDetections.isNotEmpty()) currentDetections else lastConfirmedDetections[label].orEmpty()
            stableDetections.addAll(source.take(smoothedCount))
        }

        val activeLabels = detectionHistory.filterValues { history -> history.any { it > 0 } }.keys
        lastConfirmedDetections.keys.retainAll(activeLabels)
        detectionHistory.keys.retainAll(activeLabels + detectionsByLabel.keys)

        _latestDetections.value = stableDetections
            .groupBy { normalizeLabel(it.name) }
            .values
            .sortedByDescending { group -> group.maxOf { it.confidence } }
            .take(maxShownGroups)
            .flatMap { it.sortedByDescending { d -> d.confidence } }
    }

    private fun filterByAllowedIngredients(detections: List<DetectedFood>): List<DetectedFood> {
        val currentAllowed = allowedIngredients
        if (currentAllowed.isEmpty()) return detections

        return detections.filter {
            val normalizedTranslated = normalizeLabel(it.name)
            val normalizedOriginal = normalizeLabel(it.originalLabel)
            currentAllowed.contains(normalizedTranslated) || currentAllowed.contains(normalizedOriginal)
        }
    }

    private fun stabilizeWithIouTracking(currentDetections: List<DetectedFood>): List<DetectedFood> {
        if (previousFrameDetections.isEmpty()) {
            previousFrameDetections = currentDetections
            return currentDetections
        }

        val stabilized = currentDetections.map { current ->
            val currentBox = current.boundingBox ?: return@map current

            val bestPrevious = previousFrameDetections
                .mapNotNull { prev ->
                    val prevBox = prev.boundingBox ?: return@mapNotNull null
                    prev to calculateIoU(currentBox, prevBox)
                }
                .filter { it.second >= iouThreshold }
                .maxByOrNull { it.second }
                ?.first

            if (bestPrevious == null) return@map current

            val classChanged = !bestPrevious.name.equals(current.name, ignoreCase = true)
            val shouldKeepPreviousClass =
                classChanged &&
                        bestPrevious.confidence >= 0.7f &&
                        current.confidence < 0.7f

            if (shouldKeepPreviousClass) {
                current.copy(
                    name = bestPrevious.name,
                    originalLabel = bestPrevious.originalLabel
                )
            } else {
                current
            }
        }

        previousFrameDetections = stabilized
        return stabilized
    }

    private fun calculateIoU(first: RectF, second: RectF): Float {
        val left = maxOf(first.left, second.left)
        val top = maxOf(first.top, second.top)
        val right = minOf(first.right, second.right)
        val bottom = minOf(first.bottom, second.bottom)

        val intersection = if (right > left && bottom > top) (right - left) * (bottom - top) else 0f
        val firstArea = (first.right - first.left) * (first.bottom - first.top)
        val secondArea = (second.right - second.left) * (second.bottom - second.top)
        val union = firstArea + secondArea - intersection

        return if (union <= 0f) 0f else intersection / union
    }

    private fun normalizeLabel(name: String): String {
        return name.trim().lowercase().replace("ё", "е")
    }

    private fun capitalize(text: String): String {
        return text.trim()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
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

                val normalizedDetections = normalizeCaptureDetections(detections)

                normalizedDetections.groupBy { it.name }.map { (name, detectedList) ->
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


    private fun normalizeCaptureDetections(detections: List<DetectedFood>): List<DetectedFood> {
        if (detections.isEmpty()) return emptyList()

        val deduplicated = deduplicateByIou(detections, captureIouDedupThreshold)

        return deduplicated
            .groupBy { normalizeLabel(it.name) }
            .values
            .sortedByDescending { group -> group.maxOf { it.confidence } }
            .take(captureMaxClasses)
            .flatMap { classGroup -> classGroup.sortedByDescending { it.confidence } }
            .take(captureMaxObjectsTotal)
    }

    private fun deduplicateByIou(
        detections: List<DetectedFood>,
        threshold: Float
    ): List<DetectedFood> {
        val sorted = detections.sortedByDescending { it.confidence }
        val kept = mutableListOf<DetectedFood>()

        for (candidate in sorted) {
            val candidateBox = candidate.boundingBox
            if (candidateBox == null) {
                kept.add(candidate)
                continue
            }

            val isDuplicate = kept.any { keptItem ->
                val keptBox = keptItem.boundingBox ?: return@any false
                val sameClass = normalizeLabel(keptItem.name) == normalizeLabel(candidate.name)
                sameClass && calculateIoU(candidateBox, keptBox) >= threshold
            }

            if (!isDuplicate) kept.add(candidate)
        }

        return kept
    }

    private fun estimateQuantity(count: Int, unit: String): Double {
        return when (unit) {
            "шт" -> count.toDouble()
            "мл" -> 200.0 * count
            else -> 100.0 * count
        }
    }
}