package com.example.mealcamera.ml

import android.graphics.RectF

data class DetectedFood(
    val name: String,
    val originalLabel: String,
    val confidence: Float,
    val boundingBox: RectF? = null,
    val imagePath: String = ""
)
