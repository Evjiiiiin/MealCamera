package com.example.mealcamera.data.dao

import androidx.room.*
import com.example.mealcamera.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {

    // ========== ОСНОВНЫЕ МЕТОДЫ ДЛЯ РЕЦЕПТОВ ==========

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecipe(recipe: Recipe): Long

    @Update
    suspend fun updateRecipe(recipe: Recipe)

    @Query("SELECT * FROM recipes ORDER BY popularityScore DESC, name ASC")
    fun getAllRecipes(): Flow<List<Recipe>>

    @Query("SELECT * FROM recipes WHERE recipeId = :recipeId")
    fun getRecipeById(recipeId: Long): Flow<Recipe>

    @Query("SELECT recipeId FROM recipes WHERE firestoreId = :firestoreId LIMIT 1")
    suspend fun getRecipeIdByFirestoreId(firestoreId: String): Long?

    @Query("SELECT * FROM recipes WHERE name = :name AND category = :category LIMIT 1")
    suspend fun getRecipeByNameAndCategory(name: String, category: String): Recipe?

    @Query("SELECT COUNT(*) FROM recipes")
    suspend fun getRecipeCount(): Int

    @Query("UPDATE recipes SET popularityScore = popularityScore + 1 WHERE recipeId = :recipeId")
    suspend fun incrementPopularity(recipeId: Long)

    @Query("DELETE FROM recipes WHERE recipeId = :recipeId")
    suspend fun deleteRecipe(recipeId: Long)

    // ========== МЕТОДЫ ДЛЯ ИНГРЕДИЕНТОВ ==========

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIngredient(ingredient: Ingredient): Long

    @Query("SELECT * FROM ingredients")
    suspend fun getAllIngredients(): List<Ingredient>

    @Query("SELECT * FROM ingredients WHERE name = :name LIMIT 1")
    suspend fun getIngredientByName(name: String): Ingredient?

    @Query("SELECT * FROM ingredients WHERE ingredientId = :ingredientId")
    suspend fun getIngredientById(ingredientId: Long): Ingredient?

    // ========== МЕТОДЫ ДЛЯ СВЯЗИ РЕЦЕПТОВ И ИНГРЕДИЕНТОВ ==========

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecipeIngredientCrossRef(crossRef: RecipeIngredientCrossRef)

    @Query("SELECT * FROM recipe_ingredient_cross_ref WHERE recipeId = :recipeId AND ingredientId = :ingredientId")
    suspend fun getCrossRef(recipeId: Long, ingredientId: Long): RecipeIngredientCrossRef?

    @Query("DELETE FROM recipe_ingredient_cross_ref WHERE recipeId = :recipeId")
    suspend fun deleteCrossRefsByRecipeId(recipeId: Long)

    // ========== МЕТОДЫ ДЛЯ ШАГОВ РЕЦЕПТОВ ==========

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStep(step: RecipeStep): Long

    @Query("SELECT * FROM recipe_steps WHERE recipeId = :recipeId ORDER BY stepNumber ASC")
    fun getStepsByRecipeId(recipeId: Long): Flow<List<RecipeStep>>

    @Query("DELETE FROM recipe_steps WHERE recipeId = :recipeId")
    suspend fun deleteStepsByRecipeId(recipeId: Long)

    // ========== МЕТОДЫ ДЛЯ ИНГРЕДИЕНТОВ ШАГОВ ==========

    @Insert
    suspend fun insertStepIngredient(stepIngredient: StepIngredient)

    @Insert
    suspend fun insertStepIngredients(stepIngredients: List<StepIngredient>)

    @Query("SELECT * FROM step_ingredients WHERE stepId = :stepId")
    suspend fun getStepIngredients(stepId: Long): List<StepIngredient>

    @Query("DELETE FROM step_ingredients WHERE stepId IN (SELECT stepId FROM recipe_steps WHERE recipeId = :recipeId)")
    suspend fun deleteStepIngredientsByRecipeId(recipeId: Long)

    // ========== ТРАНЗАКЦИОННЫЕ ЗАПРОСЫ (СО ВСЕМИ ДАННЫМИ) ==========

    @Transaction
    @Query("SELECT * FROM recipes")
    suspend fun getAllRecipesWithIngredients(): List<RecipeWithIngredients>

    @Transaction
    @Query("SELECT * FROM recipes WHERE recipeId = :recipeId")
    fun getRecipeWithIngredientsById(recipeId: Long): Flow<RecipeWithIngredients>

    @Transaction
    @Query("SELECT * FROM recipe_steps WHERE recipeId = :recipeId ORDER BY stepNumber ASC")
    fun getStepsWithIngredients(recipeId: Long): Flow<List<StepWithIngredients>>

    // ========== ФИЛЬТРАЦИЯ ==========

    @Query("SELECT * FROM recipes WHERE category = :category")
    fun getRecipesByCategory(category: String): Flow<List<Recipe>>

    @Query("SELECT * FROM recipes WHERE cuisine = :cuisine")
    fun getRecipesByCuisine(cuisine: String): Flow<List<Recipe>>

    @Query("SELECT * FROM recipes WHERE category = :category AND cuisine = :cuisine")
    fun getRecipesByCategoryAndCuisine(category: String, cuisine: String): Flow<List<Recipe>>
}