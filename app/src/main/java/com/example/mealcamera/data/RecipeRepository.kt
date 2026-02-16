package com.example.mealcamera.data

import com.example.mealcamera.data.dao.RecipeDao
import com.example.mealcamera.data.dao.ShoppingListDao
import com.example.mealcamera.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map // Важно: импортируем Flow.map

// Предполагается, что ваш RecipeRepository принимает RecipeDao и ShoppingListDao
class RecipeRepository(
    private val recipeDao: RecipeDao,
    private val shoppingListDao: ShoppingListDao // Предполагается, что ShoppingListDao нужен
) {

    val allRecipes: Flow<List<Recipe>> = recipeDao.getAllRecipes()

    suspend fun insertRecipe(recipe: Recipe): Long {
        return recipeDao.insertRecipe(recipe)
    }

    fun getRecipeById(recipeId: Long): Flow<Recipe> {
        return recipeDao.getRecipeById(recipeId)
    }

    suspend fun incrementRecipePopularity(recipeId: Long) {
        recipeDao.incrementPopularity(recipeId)
    }

    suspend fun getAllDbIngredients(): List<Ingredient> {
        return recipeDao.getAllIngredients()
    }

    suspend fun getAllRecipesWithIngredients(): List<RecipeWithIngredients> {
        return recipeDao.getAllRecipesWithIngredients()
    }

    // ИСПРАВЛЕННЫЙ МЕТОД: getIngredientsForRecipe
    fun getIngredientsForRecipe(recipeId: Long): Flow<List<IngredientWithDetails>> {
        return recipeDao.getRecipeWithIngredientsById(recipeId).map { recipeWithIngredients ->
            // Теперь мы находимся в suspend контексте благодаря Flow.map.
            // Можем безопасно вызывать suspend функции внутри этого блока.
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
            ingredientsWithDetailsList // Возвращаем преобразованный список
        }
    }


    fun getStepsForRecipe(recipeId: Long): Flow<List<RecipeStep>> {
        return recipeDao.getStepsByRecipeId(recipeId)
    }

    // НОВЫЙ МЕТОД: для получения RecipeIngredientCrossRef для конкретного ингредиента в рецепте
    suspend fun getRecipeIngredientCrossRef(recipeId: Long, ingredientId: Long): RecipeIngredientCrossRef? {
        return recipeDao.getCrossRef(recipeId, ingredientId)
    }
}
