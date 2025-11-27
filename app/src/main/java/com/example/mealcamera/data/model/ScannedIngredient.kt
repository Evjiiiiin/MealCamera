package com.example.mealcamera.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ScannedIngredient(
    val name: String,
    val imagePath: String,
    val quantity: String,
    val unit: String,
    val timestamp: Long = System.currentTimeMillis()
) : Parcelable
