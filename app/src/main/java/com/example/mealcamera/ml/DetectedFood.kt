package com.example.mealcamera.ml

data class DetectedFood(
    val name: String,
    val originalLabel: String,
    val confidence: Float,
    val imagePath: String
)