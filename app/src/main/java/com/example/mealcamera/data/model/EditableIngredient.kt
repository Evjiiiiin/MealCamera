package com.example.mealcamera.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class EditableIngredient(
    val id: Long,
    val name: String,
    var quantity: String = "",
    var unit: String = ""
) : Parcelable
