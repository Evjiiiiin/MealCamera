package com.example.mealcamera.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class EditableIngredient(
    val id: Long,               // ID из БД
    val name: String,           // Имя (например, "Яблоко")
    var quantity: String = "",  // Количество, введенное пользователем (должно быть 'var')
    var unit: String = ""       // Мера (должно быть 'var')
) : Parcelable