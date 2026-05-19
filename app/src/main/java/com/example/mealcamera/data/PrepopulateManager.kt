package com.example.mealcamera.data

import android.content.Context
import android.util.Log
import com.example.mealcamera.data.dao.RecipeDao
import com.example.mealcamera.data.model.Ingredient
import com.example.mealcamera.data.model.Recipe
import com.example.mealcamera.data.model.RecipeIngredientCrossRef
import com.example.mealcamera.data.model.RecipeStep
import com.example.mealcamera.data.remote.CloudIngredient
import com.example.mealcamera.data.remote.CloudIngredientData
import com.example.mealcamera.data.remote.CloudRecipe
import com.example.mealcamera.data.remote.FirestoreService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException

class PrepopulateManager(
    private val context: Context,
    private val firestoreService: FirestoreService
) {

    suspend fun prepopulateIfNeeded(recipeDao: RecipeDao) = withContext(Dispatchers.IO) {
        try {
            // Проверяем наличие данных в Firestore асинхронно, без блокировки
            val hasCloudData = runCatching { firestoreService.hasData() }.getOrDefault(false)
            val localRecipeCount = runCatching { recipeDao.getRecipeCount() }.getOrDefault(0)

            if (localRecipeCount > 0) {
                Log.d("PrepopulateManager", "Локальная БД уже содержит данные ($localRecipeCount рецептов)")
                return@withContext
            }

            if (hasCloudData) {
                Log.d("PrepopulateManager", "В Firestore есть данные — пропускаем локальную prepopulate")
                return@withContext
            }

            Log.d("PrepopulateManager", "Заполняем локальную БД из JSON")
            val (ingredients, recipes) = parseJsonRecipes()

            saveToLocalDatabase(recipeDao, ingredients, recipes)

            // Загружаем в Firestore в фоне, игнорируем ошибки, чтобы не ломать запуск
            try {
                firestoreService.uploadInitialData(
                    ingredients = ingredients.map { (name, isAlwaysAvailable, isCoreIngredient) ->
                        CloudIngredientData(
                            id = name,
                            name = name,
                            isAlwaysAvailable = isAlwaysAvailable,
                            isCoreIngredient = isCoreIngredient
                        )
                    },
                    recipes = recipes
                )
            } catch (e: Exception) {
                Log.e("PrepopulateManager", "Ошибка загрузки в Firestore, но локальные данные сохранены", e)
            }
        } catch (e: Exception) {
            Log.e("PrepopulateManager", "Ошибка prepopulate", e)
        }
    }

    private fun parseJsonRecipes(): Pair<List<Triple<String, Boolean, Boolean>>, List<CloudRecipe>> {
        val json = loadJsonFromAssets()
        val root = JSONObject(json)

        val ingredients = mutableListOf<Triple<String, Boolean, Boolean>>()
        val ingredientsArray = root.getJSONArray("ingredients")
        for (i in 0 until ingredientsArray.length()) {
            val ingredientObj = ingredientsArray.getJSONObject(i)
            ingredients.add(
                Triple(
                    ingredientObj.getString("name"),
                    ingredientObj.optBoolean("isAlwaysAvailable", false),
                    ingredientObj.optBoolean("isCoreIngredient", true)
                )
            )
        }

        val recipes = mutableListOf<CloudRecipe>()
        val recipesArray = root.getJSONArray("recipes")
        for (i in 0 until recipesArray.length()) {
            val recipeObj = recipesArray.getJSONObject(i)
            val recipeName = recipeObj.getString("name")

            val recipeIngredients = mutableListOf<CloudIngredient>()
            val recipeIngredientsArray = recipeObj.getJSONArray("ingredients")
            for (j in 0 until recipeIngredientsArray.length()) {
                val ingObj = recipeIngredientsArray.getJSONObject(j)
                recipeIngredients.add(
                    CloudIngredient(
                        name = ingObj.getString("name"),
                        quantity = jsonQuantityToString(ingObj),
                        unit = ingObj.optString("unit", "")
                    )
                )
            }

            val steps = mutableListOf<com.example.mealcamera.data.remote.StepData>()
            if (recipeObj.has("steps")) {
                val stepsArray = recipeObj.getJSONArray("steps")
                for (j in 0 until stepsArray.length()) {
                    val stepObj = stepsArray.getJSONObject(j)

                    val stepIngredients = mutableListOf<CloudIngredient>()
                    if (stepObj.has("ingredients")) {
                        val stepIngredientsArray = stepObj.getJSONArray("ingredients")
                        for (k in 0 until stepIngredientsArray.length()) {
                            val stepIngObj = stepIngredientsArray.getJSONObject(k)
                            stepIngredients.add(
                                CloudIngredient(
                                    name = stepIngObj.getString("name"),
                                    quantity = jsonQuantityToString(stepIngObj),
                                    unit = stepIngObj.optString("unit", "")
                                )
                            )
                        }
                    }

                    val stepImagePath = stepObj.optString("imagePath", "")
                        .ifBlank { buildDefaultStepImagePath(recipeName, j + 1) }

                    steps.add(
                        com.example.mealcamera.data.remote.StepData(
                            title = stepObj.getString("title"),
                            description = stepObj.getString("description"),
                            timerMinutes = stepObj.optInt("timerMinutes", 0),
                            imagePath = stepImagePath,
                            ingredients = stepIngredients
                        )
                    )
                }
            }

            val (cuisine, cuisineCode) = detectCuisine(recipeName)

            recipes.add(
                CloudRecipe(
                    name = recipeName,
                    description = recipeObj.getString("description"),
                    imagePath = recipeObj.getString("imagePath"),
                    category = recipeObj.getString("category"),
                    prepTime = recipeObj.getString("prepTime"),
                    popularityScore = recipeObj.optInt("popularityScore", 0),
                    cuisine = cuisine,
                    cuisineCode = cuisineCode,
                    ingredients = recipeIngredients,
                    steps = steps,
                    calories = recipeObj.optInt("calories", 0),
                    proteins = recipeObj.optDouble("proteins", 0.0),
                    fats = recipeObj.optDouble("fats", 0.0),
                    carbs = recipeObj.optDouble("carbs", 0.0),
                    totalWeight = recipeObj.optInt("totalWeight", 0),
                    basePortions = recipeObj.optInt("basePortions", 1).coerceAtLeast(1)
                )
            )
        }

        return Pair(ingredients, recipes)
    }

    private fun jsonQuantityToString(obj: JSONObject, key: String = "quantity"): String {
        return when (val value = obj.opt(key)) {
            null, JSONObject.NULL -> ""
            is Number -> {
                val asDouble = value.toDouble()
                if (asDouble % 1.0 == 0.0) asDouble.toInt().toString() else asDouble.toString()
            }
            else -> value.toString()
        }
    }

    private fun buildDefaultStepImagePath(recipeName: String, stepNumber: Int): String {
        val slug = recipeName
            .lowercase()
            .replace("ё", "е")
            .replace(Regex("[^a-zа-я0-9]+"), "_")
            .trim('_')
        return "step_images/${slug}/step_${stepNumber}.jpg"
    }

    private fun detectCuisine(recipeName: String): Pair<String, String> {
        return when {
            recipeName.contains("Борщ") || recipeName.contains("Щи") || recipeName.contains("Солянка") ||
                    recipeName.contains("Гречка") || recipeName.contains("Котлеты") || recipeName.contains("Жаркое") ||
                    recipeName.contains("Блины") || recipeName.contains("Сырники") -> Pair("Русская", "RU")
            recipeName.contains("Паста") || recipeName.contains("Карбонара") || recipeName.contains("Ризотто") ||
                    recipeName.contains("Тирамису") || recipeName.contains("Пицца") -> Pair("Итальянская", "IT")
            recipeName.contains("Паэлья") || recipeName.contains("Гаспачо") || recipeName.contains("Тапас") -> Pair("Испанская", "ES")
            recipeName.contains("Рататуй") || recipeName.contains("Круассан") || recipeName.contains("Крем-брюле") -> Pair("Французская", "FR")
            recipeName.contains("Панкейки") || recipeName.contains("Бургер") || recipeName.contains("Стейк") ||
                    recipeName.contains("Чизкейк") -> Pair("Американская", "US")
            recipeName.contains("Суши") || recipeName.contains("Вок") || recipeName.contains("Лапша") -> Pair("Азиатская", "JP")
            else -> Pair("Русская", "RU")
        }
    }

    private suspend fun saveToLocalDatabase(
        recipeDao: RecipeDao,
        ingredients: List<Triple<String, Boolean, Boolean>>,
        recipes: List<CloudRecipe>
    ) = withContext(Dispatchers.IO) {

        val ingredientNameToId = mutableMapOf<String, Long>()

        ingredients.forEach { (name, isAlwaysAvailable, isCoreIngredient) ->
            val ingredient = Ingredient(
                name = name,
                isAlwaysAvailable = isAlwaysAvailable,
                isCoreIngredient = isCoreIngredient
            )
            val id = recipeDao.insertIngredient(ingredient)
            ingredientNameToId[name] = id
        }

        recipes.forEach { cloudRecipe ->
            val recipe = Recipe(
                firestoreId = null,
                name = cloudRecipe.name,
                description = cloudRecipe.description,
                imagePath = cloudRecipe.imagePath,
                category = cloudRecipe.category,
                prepTime = cloudRecipe.prepTime,
                popularityScore = cloudRecipe.popularityScore,
                cuisine = cloudRecipe.cuisine,
                cuisineCode = cloudRecipe.cuisineCode,
                calories = cloudRecipe.calories,
                proteins = cloudRecipe.proteins,
                fats = cloudRecipe.fats,
                carbs = cloudRecipe.carbs,
                totalWeight = cloudRecipe.totalWeight,
                basePortions = cloudRecipe.basePortions.coerceAtLeast(1)
            )
            val recipeId = recipeDao.insertRecipe(recipe)

            cloudRecipe.ingredients.forEach { recipeIngredient ->
                val ingredientId = ingredientNameToId[recipeIngredient.name]
                if (ingredientId != null) {
                    val crossRef = RecipeIngredientCrossRef(
                        recipeId = recipeId,
                        ingredientId = ingredientId,
                        quantity = recipeIngredient.quantity,
                        unit = recipeIngredient.unit
                    )
                    recipeDao.insertRecipeIngredientCrossRef(crossRef)
                } else {
                    Log.w("PrepopulateManager", "⚠️ Ингредиент '${recipeIngredient.name}' не найден в базе")
                }
            }

            cloudRecipe.steps.forEachIndexed { index, stepData ->
                val step = RecipeStep(
                    recipeId = recipeId,
                    stepNumber = index + 1,
                    title = stepData.title,
                    instruction = stepData.description,
                    timerMinutes = stepData.timerMinutes,
                    imagePath = stepData.imagePath
                )
                val stepId = recipeDao.insertStep(step)

                stepData.ingredients.forEach { stepIngredient ->
                    val ingredientId = ingredientNameToId[stepIngredient.name]
                    if (ingredientId != null) {
                        val stepIng = com.example.mealcamera.data.model.StepIngredient(
                            stepId = stepId,
                            ingredientId = ingredientId,
                            quantity = stepIngredient.quantity,
                            unit = stepIngredient.unit
                        )
                        recipeDao.insertStepIngredient(stepIng)
                    }
                }
            }
        }
    }

    private fun loadJsonFromAssets(): String {
        return try {
            context.assets.open("recipes.json").bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            Log.e("PrepopulateManager", "Ошибка чтения JSON файла", e)
            throw e
        }
    }
}