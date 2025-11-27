// Файл: com/example/mealcamera/util/IngredientTranslator.kt
package com.example.mealcamera.util

object IngredientTranslator {
    // Ключ - английская метка из labels.txt (от модели)
    // Значение - название в твоем recipes.json (строго буква в букву!)
    private val translationMap = mapOf(
        "apple" to "Яблоко",
        "banana" to "Банан",
        "bread" to "Хлеб",
        "broccoli" to "Брокколи",
        "carrot" to "Морковь",
        "cheese" to "Сыр",
        "chicken" to "Куриное филе", // или "Курица целая", выбери основное
        "cucumber" to "Огурец",
        "egg" to "Яйцо",
        "fish" to "Рыба",
        "lemon" to "Лимон",
        "milk" to "Молоко",
        "mushroom" to "Грибы",
        "onion" to "Лук",
        "orange" to "Апельсин", // Добавь в JSON если нет, или игнорируй
        "potato" to "Картофель",
        "rice" to "Рис",
        "tomato" to "Помидор",
        "beetroot" to "Свекла",
        "bell pepper" to "Перец болгарский",
        "capsicum" to "Перец болгарский",
        "cabbage" to "Капуста",
        "meat" to "Говядина", // У модели могут быть общие классы
        "beef" to "Говядина",
        "pork" to "Свинина",
        "pasta" to "Макароны",
        "corn" to "Кукуруза", // В JSON нет, но пусть будет
        "spinach" to "Шпинат",
        "strawberry" to "Клубника",
        "butter" to "Сливочное масло",
        "cream" to "Сливки",
        "flour" to "Мука",
        "oil" to "Растительное масло",
        "salt" to "Соль",
        "sugar" to "Сахар",
        "pepper" to "Перец",
        "garlic" to "Чеснок"
        // Добавь остальные маппинги по мере необходимости
    )

    fun translate(englishLabel: String): String {
        // Приводим к нижнему регистру для поиска
        return translationMap[englishLabel.lowercase()] ?: englishLabel
    }
}