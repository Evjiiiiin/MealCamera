package com.example.mealcamera.data.util

import java.util.Locale

object UnitHelper {

    /**
     * Возвращает единицу по умолчанию для ингредиента.
     */
    fun getDefaultUnit(ingredientName: String): String {
        val name = ingredientName.lowercase().trim()
        return when {
            isLiquid(name) -> "мл"
            isPieceBased(name) -> "шт"
            else -> "г"
        }
    }

    /**
     * Форматирование количества для отображения (без лишних нулей).
     */
    fun formatQuantity(quantity: Double): String {
        return if (quantity <= 0) "0"
        else if (quantity % 1.0 == 0.0) {
            quantity.toInt().toString()
        } else {
            String.format(Locale.US, "%.1f", quantity)
        }
    }

    /**
     * Конвертирует количество в базовую единицу (г для массы, мл для объема).
     * Использует средние значения веса для бытовых мер.
     */
    fun toBaseUnit(quantity: Double, unit: String): Double {
        val u = unit.lowercase().trim().replace(".", "").replace(" ", "")
        return when (u) {
            "кг" -> quantity * 1000.0
            "л" -> quantity * 1000.0
            "г", "гр", "гм", "мл" -> quantity
            "стл", "столоваяложка" -> quantity * 15.0
            "чл", "чайнаяложка" -> quantity * 5.0
            "стакан" -> quantity * 200.0
            "мг" -> quantity / 1000.0
            "десл", "десертнаяложка" -> quantity * 10.0
            // Штучные и специфичные меры возвращают NaN для предотвращения неверных расчетов веса
            "шт", "зубчик", "ломтик", "кусочек", "щепотка", "пучок", "пачка", "банка", "упаковка" -> Double.NaN
            else -> Double.NaN
        }
    }

    /**
     * Универсальная конвертация между единицами.
     */
    fun convert(quantity: Double, fromUnit: String, toUnit: String): Double {
        val from = fromUnit.lowercase().trim().replace(".", "").replace(" ", "")
        val to = toUnit.lowercase().trim().replace(".", "").replace(" ", "")
        
        if (from == to) return quantity
        
        val inBase = toBaseUnit(quantity, from)
        if (inBase.isNaN()) return Double.NaN

        return when (to) {
            "кг", "л" -> inBase / 1000.0
            "г", "гр", "гм", "мл" -> inBase
            "стл", "столоваяложка" -> inBase / 15.0
            "чл", "чайнаяложка" -> inBase / 5.0
            "стакан" -> inBase / 200.0
            "мг" -> inBase * 1000.0
            "десл", "десертнаяложка" -> inBase / 10.0
            else -> Double.NaN
        }
    }

    fun isDiscreteUnit(unit: String): Boolean {
        val u = unit.lowercase().trim().replace(".", "").replace(" ", "")
        return u in listOf("шт", "зубчик", "ломтик", "кусочек", "щепотка", "пучок", "пачка", "банка", "упаковка", "повкусу")
    }

    fun areDiscreteUnitsCompatible(unit1: String, unit2: String): Boolean {
        val u1 = unit1.lowercase().trim().replace(".", "").replace(" ", "")
        val u2 = unit2.lowercase().trim().replace(".", "").replace(" ", "")
        if (u1 == u2) return true
        val pieces = listOf("шт", "зубчик", "ломтик", "кусочек")
        return u1 in pieces && u2 in pieces
    }

    fun isDiscreteAmountSufficient(haveQty: Double, haveUnit: String,
                                   needQty: Double, needUnit: String): Boolean {
        if (!areDiscreteUnitsCompatible(haveUnit, needUnit)) return false
        return haveQty >= needQty
    }

    private fun isLiquid(name: String): Boolean {
        val liquids = listOf("молоко", "вода", "сливки", "вино", "сок", "кефир", "йогурт", "масло", "уксус", "соус", "бульон", "сироп")
        return liquids.any { name.contains(it) }
    }

    private fun isPieceBased(name: String): Boolean {
        val pieces = listOf("яйцо", "яблоко", "банан", "лук", "морковь", "картофель", "помидор", "огурец", "перец", "чеснок", "лимон")
        return pieces.any { name.contains(it) }
    }
}
