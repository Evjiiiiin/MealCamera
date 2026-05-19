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

    /**
     * История обнаружений по каждому классу для сглаживания результатов между кадрами.
     */
    private val detectionHistory = mutableMapOf<String, MutableList<Int>>()

    /**
     * Последние подтвержденные детекции по каждому классу.
     */
    private val lastConfirmedDetections = mutableMapOf<String, List<DetectedFood>>()

    /**
     * Размер окна истории (количество кадров).
     */
    private val historyWindowSize = 8

    /**
     * Минимальное количество кадров, в которых объект должен появиться,
     * чтобы считаться стабильным.
     */
    private val minConfirmationFrames = 1

    /**
     * Минимальная допустимая уверенность детектора.
     */
    private val minAcceptedConfidence = 0.40f

    /**
     * Максимальное количество различных групп продуктов,
     * отображаемых одновременно.
     */
    private val maxProductGroups = 6

    init {
        observeUserAllergens()
    }

    private fun observeUserAllergens() {
        val userId = auth.currentUser?.uid ?: return

        firestoreService.getUserAllergensFlow(userId)
            .onEach { allergens ->
                _userAllergens.value =
                    repository.expandAllergens(allergens)
                        .map { it.lowercase() }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Обновляет список текущих обнаружений и выполняет:
     * - фильтрацию по confidence;
     * - удаление не-пищевых объектов;
     * - сглаживание по истории кадров;
     * - выбор лучших групп продуктов.
     */
    fun updateDetections(newDetections: List<DetectedFood>) {
        val filteredDetections = newDetections
            .filter { it.confidence >= minAcceptedConfidence }
            .filter {
                repositorySafeFoodName(it.name) &&
                        repositorySafeFoodName(it.originalLabel)
            }

        val detectionsByLabel =
            filteredDetections.groupBy { normalizeLabel(it.originalLabel) }

        val allLabels =
            (detectionHistory.keys + detectionsByLabel.keys).toSet()

        val stableDetections = mutableListOf<DetectedFood>()

        for (label in allLabels) {
            val history =
                detectionHistory.getOrPut(label) { mutableListOf() }

            val currentDetections =
                detectionsByLabel[label]
                    .orEmpty()
                    .sortedByDescending { it.confidence }

            // Сохраняем количество найденных объектов в текущем кадре
            history.add(currentDetections.size)

            // Ограничиваем размер истории
            if (history.size > historyWindowSize) {
                history.removeAt(0)
            }

            // Запоминаем последние подтвержденные детекции
            if (currentDetections.isNotEmpty()) {
                lastConfirmedDetections[label] = currentDetections
            }

            // Количество кадров, в которых объект был виден
            val visibleFrames = history.count { it > 0 }

            // Усреднённое количество объектов
            val smoothedCount =
                history.average()
                    .roundToInt()
                    .coerceAtLeast(0)

            val shouldShow = visibleFrames >= minConfirmationFrames

            if (!shouldShow || smoothedCount == 0) {
                continue
            }

            // Используем текущие детекции, а если их нет —
            // последние подтвержденные
            val source =
                if (currentDetections.isNotEmpty()) {
                    currentDetections
                } else {
                    lastConfirmedDetections[label].orEmpty()
                }

            stableDetections.addAll(
                source.take(smoothedCount)
            )
        }

        // Оставляем только активные классы
        val activeLabels =
            detectionHistory
                .filterValues { history ->
                    history.any { it > 0 }
                }
                .keys

        lastConfirmedDetections.keys.retainAll(activeLabels)
        detectionHistory.keys.retainAll(
            activeLabels + detectionsByLabel.keys
        )

        // Сохраняем итоговые сглаженные результаты
        _latestDetections.value =
            keepTopProductGroupsWithAllVisibleItems(stableDetections)
    }

    /**
     * Оставляет несколько наиболее вероятных групп продуктов,
     * сохраняя все видимые экземпляры внутри каждой группы.
     */
    private fun keepTopProductGroupsWithAllVisibleItems(
        detections: List<DetectedFood>
    ): List<DetectedFood> {
        return detections
            .groupBy { normalizeLabel(it.originalLabel) }
            .values
            .sortedByDescending { group ->
                group.maxOf { it.confidence }
            }
            .take(maxProductGroups)
            .flatMap { group ->
                group.sortedByDescending { it.confidence }
            }
    }

    /**
     * Исключает очевидные не-пищевые объекты.
     */
    private fun repositorySafeFoodName(name: String): Boolean {
        val normalized = normalizeLabel(name)

        val blocked = setOf(
            "phone", "mobile phone", "cell phone", "телефон",
            "key", "keys", "ключ", "ключи",
            "table", "стол",
            "hand", "hands", "рука", "руки",
            "person", "people", "человек", "люди"
        )

        return normalized !in blocked
    }

    /**
     * Нормализация названия для сравнения.
     */
    private fun normalizeLabel(name: String): String {
        return name
            .trim()
            .lowercase()
            .replace("ё", "е")
    }

    /**
     * Приведение первой буквы к верхнему регистру.
     */
    private fun capitalize(text: String): String {
        return text
            .trim()
            .replaceFirstChar {
                if (it.isLowerCase()) {
                    it.titlecase(Locale.getDefault())
                } else {
                    it.toString()
                }
            }
    }

    /**
     * Преобразует детекции в список ScannedIngredient
     * и сохраняет общий снимок.
     */
    fun processCapturedDetections(
        bitmap: Bitmap,
        detections: List<DetectedFood>,
        onResult: (List<ScannedIngredient>) -> Unit
    ) {
        viewModelScope.launch {
            _isProcessing.value = true

            val newIngredients = withContext(Dispatchers.IO) {
                if (detections.isEmpty()) {
                    return@withContext emptyList()
                }

                val fileName = "scan_${UUID.randomUUID()}.jpg"
                val imagePath = imageStorage.saveImage(bitmap, fileName)

                detections
                    .groupBy { it.name }
                    .map { (name, detectedList) ->
                        val unit = UnitHelper.getDefaultUnit(name)
                        val estimatedQuantity =
                            estimateQuantity(
                                detectedList.size,
                                unit
                            )

                        ScannedIngredient(
                            name = capitalize(name),
                            imagePath = imagePath,
                            quantity = UnitHelper.formatQuantity(
                                estimatedQuantity
                            ),
                            unit = unit,
                            timestamp =
                                System.currentTimeMillis() +
                                        name.hashCode()
                        )
                    }
            }

            onResult(newIngredients)
            _isProcessing.value = false
        }
    }

    /**
     * Примерная оценка количества по числу обнаруженных объектов.
     */
    private fun estimateQuantity(
        count: Int,
        unit: String
    ): Double {
        return when (unit) {
            "шт" -> count.toDouble()
            "мл" -> 200.0 * count
            else -> 100.0 * count
        }
    }
}