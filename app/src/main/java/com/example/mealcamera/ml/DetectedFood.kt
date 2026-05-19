package com.example.mealcamera.ml

import android.graphics.RectF

data class DetectedFood(
    val name: String,
    val originalLabel: String,
    val confidence: Float,
    val boundingBox: RectF? = null,
    val imagePath: String = "",
    val source: String = SOURCE_LOCAL,
    val isLowConfidence: Boolean = confidence in LOW_CONFIDENCE_MIN..LOW_CONFIDENCE_MAX
)

const val SOURCE_LOCAL = "Локальная модель"
const val SOURCE_ROBOFLOW = "Roboflow"
const val LOW_CONFIDENCE_MIN = 0.4f
const val LOW_CONFIDENCE_MAX = 0.7f