package com.example.mealcamera.data.util

import java.util.Locale

object UnitHelper {

    /**
     * Возвращает единицу измерения по умолчанию для ингредиента
     */
    fun getDefaultUnit(ingredientName: String): String {
        val name = ingredientName.lowercase().trim()
        if (isLiquid(name)) return "мл"
        if (isWeightBased(name)) return "г"
        if (isPieceBased(name)) return "шт"
        return "г"
    }

    /**
     * Возвращает список доступных единиц для ингредиента
     */
    fun getAvailableUnits(ingredientName: String): List<String> {
        val name = ingredientName.lowercase().trim()
        return when {
            isLiquid(name) -> listOf("мл", "л", "стакан", "ст.л.", "ч.л.")
            isWeightBased(name) -> listOf("г", "кг", "ст.л.", "ч.л.")
            isPieceBased(name) -> listOf("шт", "г", "кг")
            else -> listOf("г", "кг", "шт", "мл", "л", "ст.л.", "ч.л.")
        }
    }

    /**
     * Форматирует количество для отображения
     */
    fun formatQuantity(quantity: Double): String {
        return if (quantity % 1 == 0.0) {
            quantity.toInt().toString()
        } else {
            String.format(Locale.US, "%.1f", quantity)
        }
    }

    /**
     * Конвертирует количество в базовую единицу (г или мл) для сравнения
     */
    fun toBaseUnit(quantity: Double, unit: String): Double {
        return when (unit.lowercase()) {
            "кг" -> quantity * 1000.0
            "л" -> quantity * 1000.0
            "г", "мл", "шт" -> quantity
            "ст.л." -> quantity * 15.0
            "ч.л." -> quantity * 5.0
            "стакан" -> quantity * 200.0
            else -> quantity
        }
    }

    /**
     * Конвертирует между любыми единицами измерения
     */
    fun convert(quantity: Double, fromUnit: String, toUnit: String): Double {
        if (fromUnit == toUnit) return quantity
        val inBase = toBaseUnit(quantity, fromUnit)
        
        return when (toUnit.lowercase()) {
            "кг", "л" -> inBase / 1000.0
            "г", "мл", "шт" -> inBase
            "ст.л." -> inBase / 15.0
            "ч.л." -> inBase / 5.0
            "стакан" -> inBase / 200.0
            else -> inBase
        }
    }

    private fun isLiquid(name: String): Boolean {
        val liquids = listOf("молоко", "вода", "сливки", "вино", "сок", "кефир", "йогурт", "масло растительное", "уксус", "соус")
        return liquids.any { name.contains(it) }
    }

    private fun isWeightBased(name: String): Boolean {
        val weightBased = listOf("мука", "сахар", "соль", "крупа", "рис", "гречка", "макароны", "орехи", "сыр", "творог", "мясо", "курица", "рыба", "овощи", "фрукты", "ягоды")
        return weightBased.any { name.contains(it) }
    }

    private fun isPieceBased(name: String): Boolean {
        val pieceBased = listOf("яйцо", "яблоко", "банан", "лук", "морковь", "картофель", "помидор", "огурец", "перец", "чеснок", "лимон")
        return pieceBased.any { name.contains(it) }
    }
}