package com.example.mealcamera.data.dao

import androidx.room.*
import com.example.mealcamera.data.model.FavoriteRecipe
import com.example.mealcamera.data.model.Recipe
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {

    @Query("SELECT r.* FROM recipes r INNER JOIN favorites f ON r.recipeId = f.recipeId WHERE f.userId = :userId")
    fun getFavoriteRecipes(userId: String): Flow<List<Recipe>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE userId = :userId AND recipeId = :recipeId)")
    suspend fun isFavorite(userId: String, recipeId: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteRecipe)

    @Query("DELETE FROM favorites WHERE userId = :userId AND recipeId = :recipeId")
    suspend fun removeFavorite(userId: String, recipeId: Long)

    @Query("SELECT * FROM favorites WHERE userId = :userId")
    suspend fun getAllFavorites(userId: String): List<FavoriteRecipe>
}