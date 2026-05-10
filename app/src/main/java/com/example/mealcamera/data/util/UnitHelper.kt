package com.example.mealcamera.data.util

import java.util.Locale

object UnitHelper {

    // Возвращает единицу по умолчанию для ингредиента
    fun getDefaultUnit(ingredientName: String): String {
        val name = ingredientName.lowercase().trim()
        return when {
            isLiquid(name) -> "мл"
            isPieceBased(name) -> "шт"
            isWeightBased(name) -> "г"
            else -> "г"
        }
    }

    // Список доступных единиц для ингредиента (для выпадающего списка)
    fun getAvailableUnits(ingredientName: String): List<String> {
        val name = ingredientName.lowercase().trim()
        return when {
            isLiquid(name) -> listOf("мл", "л", "стакан", "ст.л.", "ч.л.")
            isWeightBased(name) -> listOf("г", "кг", "ст.л.", "ч.л.")
            isPieceBased(name) -> listOf("шт", "г", "кг")
            else -> listOf("г", "кг", "шт", "мл", "л", "ст.л.", "ч.л.", "стакан")
        }
    }

    // Форматирование количества для отображения
    fun formatQuantity(quantity: Double): String {
        return if (quantity % 1 == 0.0) {
            quantity.toInt().toString()
        } else {
            String.format(Locale.US, "%.1f", quantity)
        }
    }

    /**
     * Конвертирует количество в базовую единицу (г или мл).
     * Для неподдерживаемых (дискретных) единиц возвращает Double.NaN.
     */
    fun toBaseUnit(quantity: Double, unit: String): Double {
        return when (unit.lowercase()) {
            "кг" -> quantity * 1000.0
            "л" -> quantity * 1000.0
            "г", "мл" -> quantity
            "ст.л." -> quantity * 15.0
            "ч.л." -> quantity * 5.0
            "стакан" -> quantity * 200.0
            "мг" -> quantity / 1000.0
            // Штучные / дискретные меры – не конвертируем в вес
            "шт", "зубчик", "ломтик", "кусочек", "щепотка", "пучок", "пачка",
            "по вкусу" -> Double.NaN
            else -> Double.NaN
        }
    }

    /**
     * Конвертирует между любыми единицами измерения.
     * Если единицы несовместимы, возвращает Double.NaN.
     */
    fun convert(quantity: Double, fromUnit: String, toUnit: String): Double {
        if (fromUnit == toUnit) return quantity
        val inBase = toBaseUnit(quantity, fromUnit)
        if (inBase.isNaN()) return Double.NaN

        return when (toUnit.lowercase()) {
            "кг", "л" -> inBase / 1000.0
            "г", "мл" -> inBase
            "ст.л." -> inBase / 15.0
            "ч.л." -> inBase / 5.0
            "стакан" -> inBase / 200.0
            "мг" -> inBase * 1000.0
            else -> Double.NaN
        }
    }

    // Является ли единица дискретной (штучной, не конвертируемой в граммы)
    fun isDiscreteUnit(unit: String): Boolean {
        val u = unit.lowercase().trim()
        return u in listOf("шт", "зубчик", "ломтик", "кусочек", "щепотка", "пучок", "пачка", "по вкусу")
    }

    // Проверка, что две дискретные единицы считаются эквивалентными
    fun areDiscreteUnitsCompatible(unit1: String, unit2: String): Boolean {
        return isDiscreteUnit(unit1) && isDiscreteUnit(unit2) &&
                unit1.lowercase().trim() == unit2.lowercase().trim()
    }

    // Проверка, что количество достаточно для дискретных мер
    fun isDiscreteAmountSufficient(haveQty: Double, haveUnit: String,
                                   needQty: Double, needUnit: String): Boolean {
        if (!areDiscreteUnitsCompatible(haveUnit, needUnit)) return false
        return haveQty >= needQty
    }

    private fun isLiquid(name: String): Boolean {
        val liquids = listOf("молоко", "вода", "сливки", "вино", "сок", "кефир", "йогурт", "масло растительное", "уксус", "соус",
            "растительное масло", "мёд", "мед", "жидкость", "бульон", "компот", "квас", "пиво", "шампанское", "коньяк")
        return liquids.any { name.contains(it) }
    }

    private fun isWeightBased(name: String): Boolean {
        val weightBased = listOf("мука", "сахар", "соль", "крупа", "рис", "гречка", "макароны", "орехи", "сыр", "творог", "мясо",
            "курица", "рыба", "овощи", "фрукты", "ягоды", "говядина", "свинина", "баранина", "индейка", "бекон", "колбаса",
            "ветчина", "хлеб", "батон", "булка", "масло сливочное", "сливочное масло", "маргарин", "шоколад", "какао")
        return weightBased.any { name.contains(it) }
    }

    private fun isPieceBased(name: String): Boolean {
        val pieceBased = listOf("яйцо", "яблоко", "банан", "лук", "морковь", "картофель", "помидор", "огурец", "перец",
            "чеснок", "лимон", "апельсин", "киви", "авокадо", "баклажан", "кабачок", "свекла", "репа", "редис",
            "ягода", "клубника", "вишня", "черешня", "слива", "абрикос", "персик", "груша")
        return pieceBased.any { name.contains(it) }
    }
}