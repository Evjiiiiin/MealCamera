package com.example.mealcamera.data

import android.util.Log
import com.example.mealcamera.data.dao.RecipeDao
import com.example.mealcamera.data.dao.ShoppingListDao
import com.example.mealcamera.data.model.*
import com.example.mealcamera.data.remote.FirestoreService
import com.example.mealcamera.data.remote.RecipeData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class RecipeRepository(
    private val recipeDao: RecipeDao,
    private val shoppingListDao: ShoppingListDao,
    private val firestoreService: FirestoreService
) {

    val allRecipes: Flow<List<Recipe>> = recipeDao.getAllRecipes()

    suspend fun syncRecipesFromCloud() = withContext(Dispatchers.IO) {
        try {
            Log.d("RecipeRepository", "🔄 Starting sync from cloud")

            // Синхронизация ингредиентов
            syncIngredientsFromCloud()

            // Синхронизация рецептов
            val cloudRecipes = firestoreService.getAllRecipes()
            Log.d("RecipeRepository", "📦 Loaded ${cloudRecipes.size} recipes from cloud")

            cloudRecipes.forEach { cloudData ->
                val existingId = recipeDao.getRecipeIdByFirestoreId(cloudData.id)

                if (existingId == null) {
                    Log.d("RecipeRepository", "   ➕ Inserting new recipe")
                    insertNewRecipe(cloudData)
                } else {
                    Log.d("RecipeRepository", "   🔄 Updating existing recipe")
                    updateExistingRecipe(cloudData, existingId)
                }
            }

            Log.d("RecipeRepository", "✅ Sync completed successfully")
        } catch (e: Exception) {
            Log.e("RecipeRepository", "❌ Error during sync", e)
        }
    }

    private suspend fun syncIngredientsFromCloud() {
        try {
            val cloudIngredients = firestoreService.getAllIngredients()
            Log.d("RecipeRepository", "📦 Loaded ${cloudIngredients.size} ingredients from cloud")

            val localIngredients = recipeDao.getAllIngredients()

            cloudIngredients.forEach { cloudIngredient ->
                val existing = localIngredients.find { it.name == cloudIngredient.name }
                if (existing == null) {
                    recipeDao.insertIngredient(Ingredient(
                        name = cloudIngredient.name,
                        isAlwaysAvailable = cloudIngredient.isAlwaysAvailable,
                        isCoreIngredient = cloudIngredient.isCoreIngredient
                    ))
                    Log.d("RecipeRepository", "   ➕ Added new ingredient: ${cloudIngredient.name}")
                }
            }
        } catch (e: Exception) {
            Log.e("RecipeRepository", "❌ Error syncing ingredients", e)
        }
    }

    private suspend fun insertNewRecipe(cloudData: RecipeData) {
        Log.d("RecipeRepository", "   📝 Inserting new recipe: ${cloudData.recipe.name}")

        val newRecipe = Recipe(
            firestoreId = cloudData.id,
            name = cloudData.recipe.name,
            description = cloudData.recipe.description,
            imagePath = cloudData.recipe.imagePath,
            category = cloudData.recipe.category,
            prepTime = cloudData.recipe.prepTime,
            popularityScore = cloudData.recipe.popularityScore,
            cuisine = cloudData.recipe.cuisine,
            cuisineCode = cloudData.recipe.cuisineCode
        )
        val localId = recipeDao.insertRecipe(newRecipe)

        // Сохраняем общие ингредиенты
        saveIngredientsForRecipe(cloudData, localId)

        // Сохраняем шаги с их ингредиентами
        saveStepsForRecipe(cloudData, localId)

        Log.d("RecipeRepository", "   ✅ Successfully inserted recipe with ID: $localId")
    }

    private suspend fun updateExistingRecipe(cloudData: RecipeData, existingId: Long) {
        Log.d("RecipeRepository", "   📝 Updating existing recipe: ${cloudData.recipe.name}")

        val updatedRecipe = Recipe(
            recipeId = existingId,
            firestoreId = cloudData.id,
            name = cloudData.recipe.name,
            description = cloudData.recipe.description,
            imagePath = cloudData.recipe.imagePath,
            category = cloudData.recipe.category,
            prepTime = cloudData.recipe.prepTime,
            popularityScore = cloudData.recipe.popularityScore,
            cuisine = cloudData.recipe.cuisine,
            cuisineCode = cloudData.recipe.cuisineCode
        )
        recipeDao.updateRecipe(updatedRecipe)

        // Удаляем старые связи
        recipeDao.deleteCrossRefsByRecipeId(existingId)
        recipeDao.deleteStepsByRecipeId(existingId)
        recipeDao.deleteStepIngredientsByRecipeId(existingId)

        // Сохраняем новые
        saveIngredientsForRecipe(cloudData, existingId)
        saveStepsForRecipe(cloudData, existingId)

        Log.d("RecipeRepository", "   ✅ Successfully updated recipe: ${cloudData.recipe.name}")
    }

    private suspend fun saveIngredientsForRecipe(
        cloudData: RecipeData,
        localId: Long
    ) {
        cloudData.recipe.ingredients.forEach { cloudIngredient ->
            val name = cloudIngredient.name
            if (name.isNotEmpty()) {
                val existingIngredients = recipeDao.getAllIngredients()
                val existingIngredient = existingIngredients.find { it.name == name }

                val ingredientId = if (existingIngredient != null) {
                    existingIngredient.ingredientId
                } else {
                    recipeDao.insertIngredient(Ingredient(name = name))
                }

                recipeDao.insertRecipeIngredientCrossRef(
                    RecipeIngredientCrossRef(
                        recipeId = localId,
                        ingredientId = ingredientId,
                        quantity = cloudIngredient.quantity,
                        unit = cloudIngredient.unit
                    )
                )
            }
        }
    }

    private suspend fun saveStepsForRecipe(
        cloudData: RecipeData,
        localId: Long
    ) {
        cloudData.recipe.steps.forEachIndexed { index, stepData ->
            // Создаем шаг
            val step = RecipeStep(
                recipeId = localId,
                stepNumber = index + 1,
                title = stepData.title,
                instruction = stepData.description,
                timerMinutes = stepData.timerMinutes ?: 0,
                imagePath = stepData.imagePath ?: ""
            )
            val stepId = recipeDao.insertStep(step)

            // Сохраняем ингредиенты для этого шага
            stepData.ingredients?.forEach { stepIngredient ->
                val name = stepIngredient.name
                if (name.isNotEmpty()) {
                    // Находим или создаем ингредиент
                    val existingIngredients = recipeDao.getAllIngredients()
                    val existingIngredient = existingIngredients.find { it.name == name }

                    val ingredientId = if (existingIngredient != null) {
                        existingIngredient.ingredientId
                    } else {
                        recipeDao.insertIngredient(Ingredient(name = name))
                    }

                    // Создаем связь шага с ингредиентом
                    recipeDao.insertStepIngredient(
                        StepIngredient(
                            stepId = stepId,
                            ingredientId = ingredientId,
                            quantity = stepIngredient.quantity,
                            unit = stepIngredient.unit
                        )
                    )
                }
            }
        }
    }

    suspend fun insertRecipe(recipe: Recipe): Long = recipeDao.insertRecipe(recipe)

    fun getRecipeById(recipeId: Long): Flow<Recipe> = recipeDao.getRecipeById(recipeId)

    suspend fun incrementRecipePopularity(recipeId: Long) = recipeDao.incrementPopularity(recipeId)

    suspend fun getAllDbIngredients(): List<Ingredient> = recipeDao.getAllIngredients()

    suspend fun getAllRecipesWithIngredients(): List<RecipeWithIngredients> = recipeDao.getAllRecipesWithIngredients()

    fun getIngredientsForRecipe(recipeId: Long): Flow<List<IngredientWithDetails>> {
        return recipeDao.getRecipeWithIngredientsById(recipeId).map { recipeWithIngredients ->
            val ingredientsWithDetailsList = mutableListOf<IngredientWithDetails>()
            for (ingredient in recipeWithIngredients.ingredients) {
                val crossRef = recipeDao.getCrossRef(recipeId, ingredient.ingredientId)
                ingredientsWithDetailsList.add(
                    IngredientWithDetails(
                        ingredient = ingredient,
                        quantity = crossRef?.quantity ?: "",
                        unit = crossRef?.unit ?: ""
                    )
                )
            }
            ingredientsWithDetailsList
        }
    }

    fun getStepsForRecipe(recipeId: Long): Flow<List<RecipeStep>> = recipeDao.getStepsByRecipeId(recipeId)

    fun getStepsWithIngredients(recipeId: Long): Flow<List<StepWithIngredients>> =
        recipeDao.getStepsWithIngredients(recipeId)

    suspend fun getRecipeIngredientCrossRef(recipeId: Long, ingredientId: Long): RecipeIngredientCrossRef? =
        recipeDao.getCrossRef(recipeId, ingredientId)
}