package com.example.mealcamera.data

import android.content.Context
import android.util.Log
import com.example.mealcamera.data.dao.RecipeDao
import com.example.mealcamera.data.model.Ingredient
import com.example.mealcamera.data.model.Recipe
import com.example.mealcamera.data.model.RecipeIngredientCrossRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class PrepopulateManager(private val context: Context) {

    suspend fun prepopulateIfNeeded(recipeDao: RecipeDao) {
        val hasRecipes = recipeDao.getAllRecipesWithIngredients().isNotEmpty()
        if (!hasRecipes) {
            Log.i("PrepopulateManager", "🔄 База данных пуста, начинаю заполнение...")
            try {
                loadDataFromJson(recipeDao)
                Log.i("PrepopulateManager", "✅ База данных успешно заполнена")
            } catch (e: Exception) {
                Log.e("PrepopulateManager", "❌ Ошибка при заполнении базы данных", e)
            }
        }
    }

    private suspend fun loadDataFromJson(recipeDao: RecipeDao) = withContext(Dispatchers.IO) {
        val jsonString = loadJsonFromAssets()
        val jsonObject = JSONObject(jsonString)

        // Загрузка ингредиентов
        val ingredientsArray = jsonObject.getJSONArray("ingredients")
        val ingredientNameToId = mutableMapOf<String, Long>()

        for (i in 0 until ingredientsArray.length()) {
            val ingredientObj = ingredientsArray.getJSONObject(i)
            val ingredient = Ingredient(
                name = ingredientObj.getString("name"),
                isAlwaysAvailable = ingredientObj.optBoolean("isAlwaysAvailable", false),
                isCoreIngredient = ingredientObj.optBoolean("isCoreIngredient", true)
            )
            val id = recipeDao.insertIngredient(ingredient)
            ingredientNameToId[ingredient.name] = id
        }

        // Загрузка рецептов
        val recipesArray = jsonObject.getJSONArray("recipes")
        for (i in 0 until recipesArray.length()) {
            val recipeObj = recipesArray.getJSONObject(i)

            val recipe = Recipe(
                name = recipeObj.getString("name"),
                description = recipeObj.getString("description"),
                imagePath = recipeObj.getString("imagePath"),
                category = recipeObj.getString("category"),
                prepTime = recipeObj.getString("prepTime"),
                popularityScore = recipeObj.optInt("popularityScore", 0)
            )
            val recipeId = recipeDao.insertRecipe(recipe)

            // Добавление ингредиентов к рецепту
            val recipeIngredientsArray = recipeObj.getJSONArray("ingredients")
            for (j in 0 until recipeIngredientsArray.length()) {
                val ingredientRef = recipeIngredientsArray.getJSONObject(j)
                val ingredientName = ingredientRef.getString("name")
                val ingredientId = ingredientNameToId[ingredientName]

                if (ingredientId != null) {
                    val crossRef = RecipeIngredientCrossRef(
                        recipeId = recipeId,
                        ingredientId = ingredientId,
                        quantity = ingredientRef.getString("quantity"),
                        unit = ingredientRef.optString("unit", "")
                    )
                    recipeDao.insertRecipeIngredientCrossRef(crossRef)
                } else {
                    Log.w("PrepopulateManager", "Ингредиент '$ingredientName' не найден в базе")
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