package com.example.mealcamera.data

import android.util.Log
import com.example.mealcamera.data.dao.RecipeDao
import com.example.mealcamera.data.dao.ShoppingListDao
import com.example.mealcamera.data.model.*
import com.example.mealcamera.data.remote.FirestoreService
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
            Log.d("RecipeRepository", "Starting sync from cloud")
            val cloudRecipes = firestoreService.getAllRecipes()
            Log.d("RecipeRepository", "Loaded ${cloudRecipes.size} recipes from cloud")

            cloudRecipes.forEach { cloudData ->
                val existingId = recipeDao.getRecipeIdByFirestoreId(cloudData.id)

                if (existingId == null) {
                    Log.d("RecipeRepository", "Inserting new recipe: ${cloudData.recipe.name}")

                    // Сохраняем основной рецепт
                    val newRecipe = Recipe(
                        firestoreId = cloudData.id,
                        name = cloudData.recipe.name,
                        description = cloudData.recipe.description,
                        imagePath = cloudData.recipe.imagePath,
                        category = cloudData.recipe.category,
                        prepTime = cloudData.recipe.prepTime,
                        popularityScore = cloudData.recipe.popularityScore
                    )
                    val localId = recipeDao.insertRecipe(newRecipe)

                    // Сохраняем ингредиенты
                    cloudData.recipe.ingredients.forEach { cloudIngredient ->
                        val name = cloudIngredient.name
                        if (name.isNotEmpty()) {
                            // Проверяем, существует ли уже такой ингредиент
                            val existingIngredients = recipeDao.getAllIngredients()
                            val existingIngredient = existingIngredients.find { it.name == name }

                            val ingredientId = if (existingIngredient != null) {
                                existingIngredient.ingredientId
                            } else {
                                recipeDao.insertIngredient(Ingredient(name = name))
                            }

                            // Сохраняем связь рецепта с ингредиентом
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

                    // Сохраняем шаги
                    cloudData.recipe.steps.forEachIndexed { index, instruction ->
                        recipeDao.insertStep(
                            RecipeStep(
                                recipeId = localId,
                                stepNumber = index + 1,
                                instruction = instruction
                            )
                        )
                    }

                    Log.d("RecipeRepository", "Successfully inserted recipe: ${cloudData.recipe.name} with ID: $localId")
                } else {
                    Log.d("RecipeRepository", "Recipe already exists: ${cloudData.recipe.name}")
                }
            }

            Log.d("RecipeRepository", "Sync completed successfully")
        } catch (e: Exception) {
            Log.e("RecipeRepository", "Error during sync", e)
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

    suspend fun getRecipeIngredientCrossRef(recipeId: Long, ingredientId: Long): RecipeIngredientCrossRef? =
        recipeDao.getCrossRef(recipeId, ingredientId)
}