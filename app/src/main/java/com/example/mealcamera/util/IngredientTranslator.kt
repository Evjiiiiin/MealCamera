package com.example.mealcamera.util

import java.util.Locale

object IngredientTranslator {
    private val translationMap = mapOf(
        "apple" to "Яблоко",
        "avocado" to "Авокадо",
        "backing powder" to "Разрыхлитель",
        "bacon" to "Бекон",
        "banana" to "Банан",
        "barberry" to "Барбарис",
        "bay leaf" to "Лавровый лист",
        "beef" to "Говядина",
        "beetroot" to "Свекла",
        "bell pepper" to "Перец болгарский",
        "bread" to "Хлеб",
        "breadcrumbs" to "Панировочные сухари",
        "brinjal" to "Баклажан",
        "broccoli" to "Брокколи",
        "buckwheat" to "Гречка",
        "butter" to "Сливочное масло",
        "cabbage" to "Капуста",
        "carrot" to "Морковь",
        "cheese" to "Сыр",
        "cherry" to "Вишня",
        "chicken" to "Куриное филе",
        "chocolate" to "Шоколад",
        "cinnamon" to "Корица",
        "cocoa" to "Какао",
        "coffee" to "Кофе",
        "cookies" to "Печенье",
        "cranberry" to "Клюква",
        "cream" to "Сливки",
        "cream cheese" to "Сливочный сыр",
        "cucumber" to "Огурец",
        "cumin" to "Зира",
        "curd" to "Творог",
        "egg" to "Яйцо",
        "espresso" to "Эспрессо",
        "fish" to "Рыба",
        "flour" to "Мука",
        "garlic" to "Чеснок",
        "ginger" to "Имбирь",
        "granola" to "Гранола",
        "greenery" to "Зелень",
        "honey" to "Мед",
        "iceberg lettuce" to "Салат Айсберг",
        "ketchup" to "Кетчуп",
        "kiwi" to "Киви",
        "lemon" to "Лимон",
        "lime" to "Лайм",
        "mascarpone" to "Маскарпоне",
        "mayonnaise" to "Майонез",
        "milk" to "Молоко",
        "mint" to "Мята",
        "mushroom" to "Грибы",
        "mutton" to "Баранина",
        "nuts" to "Орехи",
        "oatmeal" to "Овсянка",
        "onion" to "Лук",
        "orange" to "Апельсин",
        "pasta" to "Макароны",
        "pea" to "Горох",
        "pear" to "Груша",
        "pineapple" to "Ананас",
        "pork" to "Свинина",
        "potato" to "Картофель",
        "puff pastry" to "Слоеное тесто",
        "raspberry" to "Малина",
        "rice" to "Рис",
        "sausage" to "Сосиски",
        "ham" to "Ветчина",
        "salami" to "Салями",
        "meat" to "Мясо",
        "turkey" to "Индейка",
        "savoiardi" to "Савоярди",
        "sour cream" to "Сметана",
        "spaghetti" to "Спагетти",
        "spring onion" to "Лук зеленый",
        "strawberry" to "Клубника",
        "tea" to "Чай",
        "tomato" to "Помидор",
        "vanile" to "Ваниль",
        "vegetable oil" to "Растительное масло",
        "yogurt" to "Йогурт",
        "zucchini" to "Кабачок",
        "salt" to "Соль",
        "sugar" to "Сахар",
        "water" to "Вода",
        "pepper" to "Перец",
        "oil" to "Масло",
        "yeast" to "Дрожжи"
    )

    private val allKnownNames: Set<String> by lazy {
        (translationMap.keys + translationMap.values).map { it.lowercase(Locale.ROOT) }.toSet()
    }

    fun translate(englishLabel: String): String {
        return translationMap[englishLabel.lowercase()] ?: englishLabel
    }

    fun isKnownIngredient(name: String): Boolean {
        val norm = name.trim().lowercase(Locale.ROOT).replace("ё", "е")
        return allKnownNames.any { it == norm || norm.startsWith(it) || it.startsWith(norm) }
    }
}
