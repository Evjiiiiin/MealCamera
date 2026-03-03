package com.example.mealcamera.data

import android.util.Log
import com.example.mealcamera.data.dao.FavoriteDao
import com.example.mealcamera.data.model.FavoriteRecipe
import com.example.mealcamera.data.model.Recipe
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

class FavoriteRepository(
    val favoriteDao: FavoriteDao,
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

    suspend fun getFavoriteIds(): Set<Long> {
        val userId = currentUserId ?: return emptySet()
        return favoriteDao.getAllFavorites(userId).map { it.recipeId }.toSet()
    }

    suspend fun toggleFavorite(recipe: Recipe) {
        val userId = currentUserId ?: return
        val isFav = favoriteDao.isFavorite(userId, recipe.recipeId)

        if (isFav) {
            removeFavorite(userId, recipe)
        } else {
            addFavorite(userId, recipe)
        }
    }

    private suspend fun addFavorite(userId: String, recipe: Recipe) {
        Log.d("FavoriteRepository", "❤️ Adding to favorites: ${recipe.name}")

        val favorite = FavoriteRecipe(
            userId = userId,
            recipeId = recipe.recipeId,
            firestoreId = recipe.firestoreId
        )
        favoriteDao.addFavorite(favorite)

        try {
            val userFavoritesRef = firestore
                .collection("users")
                .document(userId)
                .collection("favorites")
                .document(recipe.firestoreId ?: recipe.recipeId.toString())

            val cloudFavorite = mapOf(
                "recipeId" to (recipe.firestoreId ?: recipe.recipeId.toString()),
                "recipeName" to recipe.name,
                "addedAt" to FieldValue.serverTimestamp()
            )

            userFavoritesRef.set(cloudFavorite).await()
            Log.d("FavoriteRepository", "✅ Favorite saved to cloud")
        } catch (e: Exception) {
            Log.e("FavoriteRepository", "❌ Error saving to cloud", e)
        }
    }

    private suspend fun removeFavorite(userId: String, recipe: Recipe) {
        Log.d("FavoriteRepository", "💔 Removing from favorites: ${recipe.name}")

        favoriteDao.removeFavorite(userId, recipe.recipeId)

        try {
            val userFavoritesRef = firestore
                .collection("users")
                .document(userId)
                .collection("favorites")
                .document(recipe.firestoreId ?: recipe.recipeId.toString())

            userFavoritesRef.delete().await()
            Log.d("FavoriteRepository", "✅ Favorite removed from cloud")
        } catch (e: Exception) {
            Log.e("FavoriteRepository", "❌ Error removing from cloud", e)
        }
    }

    suspend fun syncFavoritesFromCloud() {
        val userId = currentUserId ?: return
        try {
            val snapshot = firestore
                .collection("users")
                .document(userId)
                .collection("favorites")
                .get()
                .await()

            Log.d("FavoriteRepository", "🔄 Synced ${snapshot.documents.size} favorites from cloud")
        } catch (e: Exception) {
            Log.e("FavoriteRepository", "❌ Error syncing favorites", e)
        }
    }
}