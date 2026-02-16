package com.example.mealcamera.data.util

object UnitHelper {
    fun getUnitForIngredient(ingredientName: String): String {
        val name = ingredientName.lowercase()
        return when {
            name.contains("молоко") || name.contains("вода") || name.contains("сливки") -> "мл"
            name.contains("масло") || name.contains("сметана") -> "г"
            name.contains("яйцо") || name.contains("лимон") || name.contains("яблоко") || name.contains("зубчик") -> "шт"
            else -> "г" // Дефолтная единица измерения
        }
    }

    /**
     * Форматирует числовое количество для отображения.
     * Убирает десятичную часть, если число целое (например, 2.0 -> "2").
     * @param quantity Числовое количество.
     * @return Отформатированная строка с количеством.
     */
    fun formatQuantity(quantity: Double): String {
        return if (quantity % 1 == 0.0) {
            quantity.toInt().toString()
        } else {
            // Форматируем до одного знака после запятой, используя точку в качестве разделителя
            "%.1f".format(quantity).replace(',', '.')
        }
    }
}
