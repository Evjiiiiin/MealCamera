package com.example.mealcamera.data.util

import java.util.Locale

object UnitHelper {

    private const val WATER_DENSITY = 1.0
    private const val OIL_DENSITY = 0.92
    private const val MILK_DENSITY = 1.03
    private const val THICK_SAUCE_DENSITY = 1.3

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
     * Конвертирует количество в базовую единицу без знания ингредиента:
     * г для массы, мл для объема. Штучные единицы возвращают NaN.
     */
    fun toBaseUnit(quantity: Double, unit: String): Double {
        val u = normalizeUnit(unit)
        return when (u) {
            "кг" -> quantity * 1000.0
            "л" -> quantity * 1000.0
            "г", "мл" -> quantity
            "стл" -> quantity * 15.0
            "чл" -> quantity * 5.0
            "стакан" -> quantity * 200.0
            "мг" -> quantity / 1000.0
            "десл" -> quantity * 10.0
            "шт", "зубчик", "ломтик", "кусочек", "щепотка", "пучок", "пачка", "банка", "упаковка" -> Double.NaN
            else -> Double.NaN
        }
    }

    /**
     * Конвертирует количество в граммы с учетом ингредиента.
     * Это нужно для сравнения, например: 1 кг картофеля >= 2 шт картофеля.
     */
    fun toBaseUnit(quantity: Double, unit: String, ingredientName: String): Double {
        val u = normalizeUnit(unit)
        val name = normalizeName(ingredientName)
        return when {
            isWeightUnit(u) -> toGrams(quantity, u)
            isVolumeUnit(u) -> toMilliliters(quantity, u) * getDensity(name)
            isPieceUnit(u) -> quantity * getPieceWeightGrams(name, u)
            else -> Double.NaN
        }
    }

    /**
     * Универсальная конвертация между единицами без знания ингредиента.
     */
    fun convert(quantity: Double, fromUnit: String, toUnit: String): Double {
        val from = normalizeUnit(fromUnit)
        val to = normalizeUnit(toUnit)

        if (from == to) return quantity

        val inBase = toBaseUnit(quantity, from)
        if (inBase.isNaN()) return Double.NaN

        return when (to) {
            "кг", "л" -> inBase / 1000.0
            "г", "мл" -> inBase
            "стл" -> inBase / 15.0
            "чл" -> inBase / 5.0
            "стакан" -> inBase / 200.0
            "мг" -> inBase * 1000.0
            "десл" -> inBase / 10.0
            else -> Double.NaN
        }
    }

    /**
     * Универсальная конвертация между единицами с учетом ингредиента и плотности/среднего веса.
     */
    fun convert(quantity: Double, fromUnit: String, toUnit: String, ingredientName: String): Double {
        val from = normalizeUnit(fromUnit)
        val to = normalizeUnit(toUnit)
        val name = normalizeName(ingredientName)

        if (from == to) return quantity

        val inGrams = toBaseUnit(quantity, from, name)
        if (inGrams.isNaN()) return Double.NaN

        return when {
            isWeightUnit(to) -> fromGrams(inGrams, to)
            isVolumeUnit(to) -> fromMilliliters(inGrams / getDensity(name), to)
            isPieceUnit(to) -> inGrams / getPieceWeightGrams(name, to)
            else -> Double.NaN
        }
    }

    fun isAmountSufficient(
        haveQty: Double,
        haveUnit: String,
        needQty: Double,
        needUnit: String,
        ingredientName: String
    ): Boolean {
        val haveBase = toBaseUnit(haveQty, haveUnit, ingredientName)
        val needBase = toBaseUnit(needQty, needUnit, ingredientName)

        return if (haveBase.isNaN() || needBase.isNaN()) {
            areDiscreteUnitsCompatible(haveUnit, needUnit) && isDiscreteAmountSufficient(haveQty, haveUnit, needQty, needUnit)
        } else {
            haveBase >= (needBase - 0.01)
        }
    }

    fun calculateMissingQuantity(
        haveQty: Double,
        haveUnit: String,
        needQty: Double,
        needUnit: String,
        ingredientName: String
    ): Double {
        val haveBase = toBaseUnit(haveQty, haveUnit, ingredientName)
        val needBase = toBaseUnit(needQty, needUnit, ingredientName)

        if (haveBase.isNaN() || needBase.isNaN()) {
            return (needQty - haveQty).coerceAtLeast(0.0)
        }

        val missingBase = (needBase - haveBase).coerceAtLeast(0.0)
        return convert(missingBase, "г", needUnit, ingredientName).takeIf { !it.isNaN() } ?: (needQty - haveQty).coerceAtLeast(0.0)
    }

    fun isDiscreteUnit(unit: String): Boolean {
        return normalizeUnit(unit) in listOf(
            "шт", "зубчик", "ломтик", "кусочек", "щепотка", "пучок", "пачка", "банка", "упаковка", "повкусу"
        )
    }

    fun areDiscreteUnitsCompatible(unit1: String, unit2: String): Boolean {
        val u1 = normalizeUnit(unit1)
        val u2 = normalizeUnit(unit2)
        if (u1 == u2) return true
        val pieces = listOf("шт", "зубчик", "ломтик", "кусочек")
        return u1 in pieces && u2 in pieces
    }

    fun isDiscreteAmountSufficient(haveQty: Double, haveUnit: String, needQty: Double, needUnit: String): Boolean {
        if (!areDiscreteUnitsCompatible(haveUnit, needUnit)) return false
        return haveQty >= needQty
    }

    private fun normalizeUnit(unit: String): String {
        val compact = unit.lowercase(Locale.ROOT)
            .trim()
            .replace("ё", "е")
            .replace(".", "")
            .replace(",", "")
            .replace("-", "")
            .replace("\\s+".toRegex(), "")

        return when (compact) {
            "г", "гр", "грамм", "грамма", "граммов" -> "г"
            "кг", "килограмм", "килограмма", "килограммов" -> "кг"
            "мг", "миллиграмм", "миллиграмма", "миллиграммов" -> "мг"
            "мл", "миллилитр", "миллилитра", "миллилитров" -> "мл"
            "л", "литр", "литра", "литров" -> "л"
            "стл", "стложка", "стложки", "стложек", "столоваяложка", "столовуюложку",
            "стовыеложки", "столовыеложки", "столовыхложек" -> "стл"
            "чл", "чложка", "чложки", "чложек", "чайнаяложка", "чайнуюложку",
            "чайныеложки", "чайныхложек" -> "чл"
            "десл", "дложка", "дложки", "дложек", "десертнаяложка", "десертнуюложку",
            "десертныеложки", "десертныхложек" -> "десл"
            "шт", "штука", "штуки", "штук", "ед", "единица", "единицы" -> "шт"
            "зуб", "зубчик", "зубчика", "зубчиков", "зубч" -> "зубчик"
            "ломтик", "ломтика", "ломтиков" -> "ломтик"
            "кусочек", "кусочка", "кусочков" -> "кусочек"
            "щепотка", "щепотки", "щепоток" -> "щепотка"
            "пучок", "пучка", "пучков" -> "пучок"
            "пачка", "пачки", "пачек" -> "пачка"
            "банка", "банки", "банок" -> "банка"
            "упаковка", "упаковки", "упаковок" -> "упаковка"
            "стакан", "стакана", "стаканов" -> "стакан"
            "повкусу" -> "повкусу"
            else -> compact
        }
    }

    private fun normalizeName(name: String): String {
        return name.lowercase(Locale.ROOT).trim().replace("ё", "е")
    }

    private fun isWeightUnit(unit: String): Boolean = unit in listOf("мг", "г", "кг")

    private fun isVolumeUnit(unit: String): Boolean = unit in listOf(
        "мл", "л", "стл", "чл", "стакан", "десл"
    )

    private fun isPieceUnit(unit: String): Boolean = unit in listOf(
        "шт", "зубчик", "ломтик", "кусочек", "пучок", "пачка", "банка", "упаковка"
    )

    private fun toGrams(quantity: Double, unit: String): Double {
        return when (unit) {
            "мг" -> quantity / 1000.0
            "кг" -> quantity * 1000.0
            else -> quantity
        }
    }

    private fun fromGrams(grams: Double, unit: String): Double {
        return when (unit) {
            "мг" -> grams * 1000.0
            "кг" -> grams / 1000.0
            else -> grams
        }
    }

    private fun toMilliliters(quantity: Double, unit: String): Double {
        return when (unit) {
            "л" -> quantity * 1000.0
            "стл" -> quantity * 15.0
            "чл" -> quantity * 5.0
            "стакан" -> quantity * 200.0
            "десл" -> quantity * 10.0
            else -> quantity
        }
    }

    private fun fromMilliliters(milliliters: Double, unit: String): Double {
        return when (unit) {
            "л" -> milliliters / 1000.0
            "стл" -> milliliters / 15.0
            "чл" -> milliliters / 5.0
            "стакан" -> milliliters / 200.0
            "десл" -> milliliters / 10.0
            else -> milliliters
        }
    }

    private fun getDensity(name: String): Double {
        return when {
            listOf("масло", "растительное масло", "оливковое масло", "подсолнечное масло").any { name.contains(it) } -> OIL_DENSITY
            listOf("молоко", "сливки", "кефир", "йогурт", "ряженка").any { name.contains(it) } -> MILK_DENSITY
            listOf("мед", "сметана", "соус", "сироп", "паста", "майонез", "кетчуп").any { name.contains(it) } -> THICK_SAUCE_DENSITY
            else -> WATER_DENSITY
        }
    }

    private fun getPieceWeightGrams(name: String, unit: String): Double {
        if (unit == "зубчик" || name.contains("чеснок")) return 5.0

        return when {
            name.contains("яйц") -> 55.0
            name.contains("картоф") -> 120.0
            name.contains("томат") || name.contains("помидор") -> 120.0
            name.contains("лук") -> 110.0
            name.contains("морков") -> 100.0
            name.contains("банан") -> 120.0
            name.contains("яблок") -> 180.0
            name.contains("огур") -> 100.0
            name.contains("лимон") -> 100.0
            name.contains("перец") -> 120.0
            else -> 100.0
        }
    }

    private fun isLiquid(name: String): Boolean {
        val liquids = listOf(
            "молоко", "вода", "сливки", "вино", "сок", "чай", "кофе", "кефир", "йогурт",
            "масло", "уксус", "соус", "бульон", "сироп", "сметана", "мед"
        )
        return liquids.any { name.contains(it) }
    }

    private fun isPieceBased(name: String): Boolean {
        val pieces = listOf(
            "яйцо", "яблоко", "банан", "лук", "морковь", "картофель", "картошка", "помидор",
            "томат", "огурец", "перец", "чеснок", "лимон"
        )
        return pieces.any { name.contains(it) }
    }
}