package com.example.mealcamera.data

import android.content.Context
import android.util.Log
import com.example.mealcamera.data.dao.RecipeDao
import com.example.mealcamera.data.model.Ingredient
import com.example.mealcamera.data.model.Recipe
import com.example.mealcamera.data.model.RecipeIngredientCrossRef
import com.example.mealcamera.data.model.RecipeStep
import com.example.mealcamera.data.remote.CloudIngredient
import com.example.mealcamera.data.remote.CloudRecipe
import com.example.mealcamera.data.remote.FirestoreService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class PrepopulateManager(
    private val context: Context,
    private val firestoreService: FirestoreService
) {

    suspend fun prepopulateIfNeeded(recipeDao: RecipeDao) {
        val hasRecipes = recipeDao.getAllRecipesWithIngredients().isNotEmpty()
        if (!hasRecipes) {
            Log.i("PrepopulateManager", "🔄 База данных пуста, начинаю заполнение...")
            try {
                // Загружаем данные из JSON
                val (ingredients, recipes) = loadDataFromJson()

                // Проверяем, есть ли данные в Firestore
                val firestoreHasData = firestoreService.hasData()

                if (!firestoreHasData) {
                    Log.i("PrepopulateManager", "📤 Firestore пуст, загружаю начальные данные...")

                    // Конвертируем ингредиенты для Firestore
                    val firestoreIngredients = ingredients.map { (name, isAlwaysAvailable, isCoreIngredient) ->
                        com.example.mealcamera.data.remote.CloudIngredientData(
                            name = name,
                            isAlwaysAvailable = isAlwaysAvailable,
                            isCoreIngredient = isCoreIngredient
                        )
                    }

                    firestoreService.uploadInitialData(firestoreIngredients, recipes)

                    // Если облако пусто (или оффлайн при первом запуске), берём JSON как источник локальных данных
                    saveToLocalDatabase(recipeDao, ingredients, recipes)
                } else {
                    Log.i("PrepopulateManager", "☁️ Данные уже есть в Firestore, локально ждём синхронизацию из облака")
                }

                Log.i("PrepopulateManager", "✅ База данных успешно заполнена")
            } catch (e: Exception) {
                Log.e("PrepopulateManager", "❌ Ошибка при заполнении базы данных", e)
            }
        }
    }

    private suspend fun loadDataFromJson(): Pair<List<Triple<String, Boolean, Boolean>>, List<CloudRecipe>> {
        val jsonString = loadJsonFromAssets()
        val jsonObject = JSONObject(jsonString)

        // Загрузка ингредиентов
        val ingredientsArray = jsonObject.getJSONArray("ingredients")
        val ingredients = mutableListOf<Triple<String, Boolean, Boolean>>()

        for (i in 0 until ingredientsArray.length()) {
            val ingredientObj = ingredientsArray.getJSONObject(i)
            ingredients.add(Triple(
                ingredientObj.getString("name"),
                ingredientObj.optBoolean("isAlwaysAvailable", false),
                ingredientObj.optBoolean("isCoreIngredient", true)
            ))
        }

        // Загрузка рецептов
        val recipesArray = jsonObject.getJSONArray("recipes")
        val recipes = mutableListOf<CloudRecipe>()

        for (i in 0 until recipesArray.length()) {
            val recipeObj = recipesArray.getJSONObject(i)
            val recipeName = recipeObj.getString("name")

            val recipeIngredients = mutableListOf<CloudIngredient>()
            val recipeIngredientsArray = recipeObj.getJSONArray("ingredients")
            for (j in 0 until recipeIngredientsArray.length()) {
                val ingRef = recipeIngredientsArray.getJSONObject(j)
                recipeIngredients.add(CloudIngredient(
                    name = ingRef.getString("name"),
                    quantity = ingRef.getString("quantity"),
                    unit = ingRef.optString("unit", "")
                ))
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
                            stepIngredients.add(CloudIngredient(
                                name = stepIngObj.getString("name"),
                                quantity = stepIngObj.getString("quantity"),
                                unit = stepIngObj.optString("unit", "")
                            ))
                        }
                    }

                    val stepImagePath = stepObj.optString("imagePath", "")
                        .ifBlank { buildDefaultStepImagePath(recipeName, j + 1) }

                    steps.add(com.example.mealcamera.data.remote.StepData(
                        title = stepObj.getString("title"),
                        description = stepObj.getString("description"),
                        timerMinutes = stepObj.optInt("timerMinutes", 0),
                        imagePath = stepImagePath,
                        ingredients = stepIngredients
                    ))
                }
            }

            // Определяем кухню
            val (cuisine, cuisineCode) = detectCuisine(recipeName)

            recipes.add(CloudRecipe(
                name = recipeName,
                description = recipeObj.getString("description"),
                imagePath = recipeObj.optString("imagePath", ""),
                category = recipeObj.getString("category"),
                prepTime = recipeObj.getString("prepTime"),
                popularityScore = recipeObj.optInt("popularityScore", 0),
                cuisine = cuisine,
                cuisineCode = cuisineCode,
                ingredients = recipeIngredients,
                steps = steps
            ))
        }

        return Pair(ingredients, recipes)
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
            recipeName.contains("Борщ") ||
                    recipeName.contains("Щи") ||
                    recipeName.contains("Солянка") ||
                    recipeName.contains("Гречка") ||
                    recipeName.contains("Котлеты") ||
                    recipeName.contains("Жаркое") ||
                    recipeName.contains("Блины") ||
                    recipeName.contains("Сырники") -> Pair("Русская", "RU")

            recipeName.contains("Паста") ||
                    recipeName.contains("Карбонара") ||
                    recipeName.contains("Ризотто") ||
                    recipeName.contains("Тирамису") ||
                    recipeName.contains("Пицца") -> Pair("Итальянская", "IT")

            recipeName.contains("Паэлья") ||
                    recipeName.contains("Гаспачо") ||
                    recipeName.contains("Тапас") -> Pair("Испанская", "ES")

            recipeName.contains("Рататуй") ||
                    recipeName.contains("Круассан") ||
                    recipeName.contains("Крем-брюле") -> Pair("Французская", "FR")

            recipeName.contains("Панкейки") ||
                    recipeName.contains("Бургер") ||
                    recipeName.contains("Стейк") ||
                    recipeName.contains("Чизкейк") -> Pair("Американская", "US")

            recipeName.contains("Суши") ||
                    recipeName.contains("Вок") ||
                    recipeName.contains("Лапша") -> Pair("Азиатская", "JP")

            else -> Pair("Русская", "RU")
        }
    }

    private suspend fun saveToLocalDatabase(
        recipeDao: RecipeDao,
        ingredients: List<Triple<String, Boolean, Boolean>>,
        recipes: List<CloudRecipe>
    ) = withContext(Dispatchers.IO) {

        val ingredientNameToId = mutableMapOf<String, Long>()

        // Сохраняем ингредиенты
        ingredients.forEach { (name, isAlwaysAvailable, isCoreIngredient) ->
            val ingredient = Ingredient(
                name = name,
                isAlwaysAvailable = isAlwaysAvailable,
                isCoreIngredient = isCoreIngredient
            )
            val id = recipeDao.insertIngredient(ingredient)
            ingredientNameToId[name] = id
        }

        // Сохраняем рецепты
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
                cuisineCode = cloudRecipe.cuisineCode
            )
            val recipeId = recipeDao.insertRecipe(recipe)

            // Сохраняем связи с ингредиентами (общие)
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

            // Сохраняем шаги
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

                // Сохраняем ингредиенты шага
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