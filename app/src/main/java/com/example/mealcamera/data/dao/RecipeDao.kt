package com.example.mealcamera.data.dao

import androidx.room.*
import com.example.mealcamera.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {
    // ======== INGREDIENTS ========
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIngredient(ingredient: Ingredient): Long

    @Query("SELECT * FROM ingredients")
    suspend fun getAllIngredients(): List<Ingredient>

    // ======== RECIPES ========
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecipe(recipe: Recipe): Long // <-- ИСПРАВЛЕНО: удалено второе "fun"

    @Query("SELECT * FROM recipes ORDER BY popularityScore DESC, name ASC")
    fun getAllRecipes(): Flow<List<Recipe>>

    @Query("SELECT * FROM recipes WHERE recipeId = :recipeId")
    fun getRecipeById(recipeId: Long): Flow<Recipe>

    @Query("SELECT COUNT(*) FROM recipes")
    suspend fun getRecipeCount(): Int

    @Query("UPDATE recipes SET popularityScore = popularityScore + 1 WHERE recipeId = :recipeId")
    suspend fun incrementPopularity(recipeId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStep(step: RecipeStep)

    @Query("SELECT * FROM recipe_steps WHERE recipeId = :recipeId ORDER BY stepNumber ASC")
    fun getStepsByRecipeId(recipeId: Long): Flow<List<RecipeStep>>

    // ======== CROSS-REFERENCE ========
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecipeIngredientCrossRef(crossRef: RecipeIngredientCrossRef)

    @Query("SELECT * FROM recipe_ingredient_cross_ref WHERE recipeId = :recipeId AND ingredientId = :ingredientId")
    suspend fun getCrossRef(recipeId: Long, ingredientId: Long): RecipeIngredientCrossRef?

    // ======== СЛОЖНЫЕ ЗАПРОСЫ ========
    @Transaction
    @Query("SELECT * FROM recipes")
    suspend fun getAllRecipesWithIngredients(): List<RecipeWithIngredients>

    @Transaction
    @Query("SELECT * FROM recipes WHERE recipeId = :recipeId")
    fun getRecipeWithIngredientsById(recipeId: Long): Flow<RecipeWithIngredients>


    @Query("SELECT * FROM recipes WHERE category = :category")
    fun getRecipesByCategory(category: String): Flow<List<Recipe>>
}
