package com.example.mealcamera.data.util

object UnitHelper {

    // ========== ОСНОВНЫЕ МЕТОДЫ ==========

    /**
     * Возвращает единицу измерения по умолчанию для ингредиента
     */
    fun getDefaultUnit(ingredientName: String): String {
        val name = ingredientName.lowercase().trim()

        // Жидкости
        if (isLiquid(name)) return "мл"

        // Сыпучие/весовые
        if (isWeightBased(name)) return "г"

        // Штучные
        if (isPieceBased(name)) return "шт"

        // По умолчанию
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
            String.format("%.1f", quantity).replace(',', '.')
        }
    }

    /**
     * Конвертирует между единицами измерения
     */
    fun convert(quantity: Double, fromUnit: String, toUnit: String): Double {
        if (fromUnit == toUnit) return quantity

        return when (fromUnit to toUnit) {
            "кг" to "г" -> quantity * 1000
            "г" to "кг" -> quantity / 1000
            "л" to "мл" -> quantity * 1000
            "мл" to "л" -> quantity / 1000
            else -> quantity
        }
    }

    // ========== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ==========

    private fun isLiquid(name: String): Boolean {
        val liquids = listOf(
            "молоко", "вода", "сливки", "вино", "сок", "кефир",
            "йогурт", "масло растительное", "уксус", "соус"
        )
        return liquids.any { name.contains(it) }
    }

    private fun isWeightBased(name: String): Boolean {
        val weightBased = listOf(
            "мука", "сахар", "соль", "крупа", "рис", "гречка",
            "макароны", "орехи", "сыр", "творог", "мясо", "курица",
            "рыба", "овощи", "фрукты", "ягоды"
        )
        return weightBased.any { name.contains(it) }
    }

    private fun isPieceBased(name: String): Boolean {
        val pieceBased = listOf(
            "яйцо", "лимон", "яблоко", "банан", "помидор", "огурец",
            "картофель", "лук", "морковь", "перец", "чеснок", "зубчик",
            "авокадо", "апельсин"
        )
        return pieceBased.any { name.contains(it) }
    }
}