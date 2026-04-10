package com.example.mealcamera.data

import android.util.Log
import com.example.mealcamera.data.dao.FavoriteDao
import com.example.mealcamera.data.model.FavoriteRecipe
import com.example.mealcamera.data.model.Recipe
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.tasks.await

class FavoriteRepository(
    private val favoriteDao: FavoriteDao,
    private val firestore: FirebaseFirestore
) {

    private val currentUserId: String?
        get() = FirebaseAuth.getInstance().currentUser?.uid

    fun getFavoriteRecipes(): Flow<List<Recipe>>? {
        val userId = currentUserId ?: return null
        return favoriteDao.getFavoriteRecipes(userId)
    }

    suspend fun isFavorite(recipeId: Long): Boolean {
        val userId = currentUserId ?: return false
        return favoriteDao.isFavorite(userId, recipeId)
    }

    fun getFavoriteRecipeIds(): Flow<List<Long>> {
        val userId = currentUserId ?: return emptyFlow()
        return favoriteDao.getFavoriteIdsFlow(userId)
    }

    /**
     * Toggles favorite state. Uses the Recipe object to ensure cloud sync has metadata.
     */
    suspend fun toggleFavorite(recipe: Recipe) {
        val userId = currentUserId ?: return
        val isFav = favoriteDao.isFavorite(userId, recipe.recipeId)

        if (isFav) {
            removeFavorite(recipe.recipeId)
        } else {
            addFavorite(recipe)
        }
    }

    /**
     * Adds a recipe to favorites with full cloud sync.
     */
    suspend fun addFavorite(recipe: Recipe) {
        val userId = currentUserId ?: return
        Log.d("FavoriteRepository", "❤️ Adding to favorites: ${recipe.name}")

        val favorite = FavoriteRecipe(
            userId = userId,
            recipeId = recipe.recipeId,
            firestoreId = recipe.firestoreId
        )
        favoriteDao.addFavorite(favorite)

        try {
            val docId = recipe.firestoreId ?: recipe.recipeId.toString()
            val userFavoritesRef = firestore
                .collection("users")
                .document(userId)
                .collection("favorites")
                .document(docId)

            val cloudFavorite = mapOf(
                "recipeId" to docId,
                "recipeName" to recipe.name,
                "addedAt" to FieldValue.serverTimestamp()
            )

            userFavoritesRef.set(cloudFavorite).await()
            Log.d("FavoriteRepository", "✅ Favorite saved to cloud")
        } catch (e: Exception) {
            Log.e("FavoriteRepository", "❌ Error saving to cloud", e)
        }
    }

    /**
     * Removes a recipe from favorites by ID.
     */
    suspend fun removeFavorite(recipeId: Long) {
        val userId = currentUserId ?: return
        Log.d("FavoriteRepository", "💔 Removing from favorites: $recipeId")

        favoriteDao.removeFavorite(userId, recipeId)

        try {
            val userFavoritesRef = firestore
                .collection("users")
                .document(userId)
                .collection("favorites")
                .document(recipeId.toString())

            userFavoritesRef.delete().await()
            Log.d("FavoriteRepository", "✅ Favorite removed from cloud")
        } catch (e: Exception) {
            Log.e("FavoriteRepository", "❌ Error removing from cloud", e)
        }
    }
}