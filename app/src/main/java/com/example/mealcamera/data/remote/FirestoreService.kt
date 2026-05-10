package com.example.mealcamera.data.remote

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreService {

    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val recipesCollection = db.collection("recipes")
    private val ingredientsCollection = db.collection("ingredients")
    private val usersCollection = db.collection("users")

    private fun anyToString(value: Any?): String {
        return when (value) {
            null -> ""
            is String -> value
            is Number -> {
                val asDouble = value.toDouble()
                if (asDouble % 1.0 == 0.0) asDouble.toInt().toString() else asDouble.toString()
            }
            is Boolean -> value.toString()
            else -> value.toString()
        }
    }

    // ========== ПОЛЬЗОВАТЕЛИ ==========

    suspend fun saveUserProfile(userId: String, name: String, photoUrl: String, yandexId: String? = null): Boolean {
        return try {
            val data = mutableMapOf<String, Any>(
                "displayName" to name,
                "photoUrl" to photoUrl,
                "updatedAt" to System.currentTimeMillis()
            )
            yandexId?.let { data["yandexId"] = it }
            
            usersCollection.document(userId).set(data, com.google.firebase.firestore.SetOptions.merge()).await()
            Log.d("FirestoreService", "Профиль сохранён: name=$name, yandexId=$yandexId")
            true
        } catch (e: Exception) {
            Log.e("FirestoreService", "Ошибка сохранения профиля", e)
            false
        }
    }

    suspend fun getUserProfile(userId: String): Map<String, Any>? {
        return try {
            val doc = usersCollection.document(userId).get().await()
            if (doc.exists()) doc.data else null
        } catch (e: Exception) { null }
    }

    suspend fun saveCookingHistory(userId: String, recipeId: Long, recipeName: String): Boolean {
        return try {
            val historyData = mapOf(
                "recipeId" to recipeId,
                "recipeName" to recipeName,
                "cookedAt" to System.currentTimeMillis()
            )
            usersCollection.document(userId)
                .collection("cooking_history")
                .add(historyData)
                .await()
            true
        } catch (e: Exception) { false }
    }

    suspend fun getCookingHistory(userId: String): List<Map<String, Any>> {
        return try {
            val snapshot = usersCollection.document(userId)
                .collection("cooking_history")
                .orderBy("cookedAt", Query.Direction.DESCENDING)
                .get()
                .await()
            snapshot.documents.mapNotNull { it.data }
        } catch (e: Exception) { emptyList() }
    }

    // ========== ИНГРЕДИЕНТЫ ==========

    suspend fun getAllIngredients(): List<CloudIngredientData> {
        return try {
            val snapshot = ingredientsCollection.get().await()
            snapshot.documents.mapNotNull { document ->
                try {
                    val name = document.getString("name") ?: return@mapNotNull null
                    val isAlwaysAvailable = document.getBoolean("isAlwaysAvailable") ?: false
                    val isCoreIngredient = document.getBoolean("isCoreIngredient") ?: true

                    CloudIngredientData(
                        id = document.id,
                        name = name,
                        isAlwaysAvailable = isAlwaysAvailable,
                        isCoreIngredient = isCoreIngredient
                    )
                } catch (e: Exception) { null }
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun addIngredient(ingredient: CloudIngredientData): Boolean {
        return try {
            val data = mapOf(
                "name" to ingredient.name,
                "isAlwaysAvailable" to ingredient.isAlwaysAvailable,
                "isCoreIngredient" to ingredient.isCoreIngredient
            )
            ingredientsCollection.document(ingredient.name).set(data).await()
            true
        } catch (e: Exception) { false }
    }

    // ========== РЕЦЕПТЫ ==========

    suspend fun getAllRecipes(): List<RecipeData> {
        return try {
            val snapshot = recipesCollection.get().await()
            snapshot.documents.mapNotNull { document ->
                try {
                    val name = document.getString("name") ?: ""
                    val description = document.getString("description") ?: ""
                    val category = document.getString("category") ?: ""
                    val prepTime = document.getString("prepTime") ?: ""
                    val cuisine = document.getString("cuisine") ?: "Русская"
                    val cuisineCode = document.getString("cuisineCode") ?: "RU"
                    val authorId = document.getString("authorId") ?: "admin"
                    val isPublic = document.getBoolean("isPublic") ?: true

                    val totalWeight = document.getLong("totalWeight")?.toInt() ?: 0

                    val ingredientsList = mutableListOf<CloudIngredient>()
                    val ingredientsField = document.get("ingredients")
                    if (ingredientsField is List<*>) {
                        ingredientsField.forEach { item ->
                            if (item is Map<*, *>) {
                                val ingName = item["name"] as? String ?: ""
                                val quantity = anyToString(item["quantity"])
                                val unit = anyToString(item["unit"])
                                if (ingName.isNotEmpty()) {
                                    ingredientsList.add(CloudIngredient(ingName, quantity, unit))
                                }
                            }
                        }
                    }

                    val stepsList = mutableListOf<StepData>()
                    val stepsField = document.get("steps")
                    if (stepsField is List<*>) {
                        stepsField.forEach { step ->
                            if (step is Map<*, *>) {
                                val ingredients = mutableListOf<CloudIngredient>()
                                val rawIngredients = step["ingredients"]
                                if (rawIngredients is List<*>) {
                                    rawIngredients.forEach { ingredient ->
                                        if (ingredient is Map<*, *>) {
                                            val ingredientName = ingredient["name"] as? String ?: ""
                                            if (ingredientName.isNotEmpty()) {
                                                ingredients.add(
                                                    CloudIngredient(
                                                        name = ingredientName,
                                                        quantity = anyToString(ingredient["quantity"]),
                                                        unit = anyToString(ingredient["unit"])
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                                stepsList.add(
                                    StepData(
                                        title = step["title"] as? String ?: "",
                                        description = step["description"] as? String ?: "",
                                        timerMinutes = (step["timerMinutes"] as? Number)?.toInt() ?: 0,
                                        imagePath = step["imagePath"] as? String ?: "",
                                        ingredients = ingredients
                                    )
                                )
                            }
                        }
                    }

                    val cloudRecipe = CloudRecipe(
                        name = name,
                        description = description,
                        imagePath = document.getString("imagePath") ?: "",
                        category = category,
                        prepTime = prepTime,
                        popularityScore = document.getLong("popularityScore")?.toInt() ?: 0,
                        cuisine = cuisine,
                        cuisineCode = cuisineCode,
                        ingredients = ingredientsList,
                        steps = stepsList,
                        authorId = authorId,
                        isPublic = isPublic,
                        calories = document.getLong("calories")?.toInt() ?: 0,
                        proteins = document.getDouble("proteins") ?: 0.0,
                        fats = document.getDouble("fats") ?: 0.0,
                        carbs = document.getDouble("carbs") ?: 0.0,
                        totalWeight = totalWeight
                    )

                    RecipeData(id = document.id, recipe = cloudRecipe)
                } catch (e: Exception) { null }
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun addRecipe(recipe: CloudRecipe): String? {
        return try {
            val data = mapOf(
                "name" to recipe.name,
                "description" to recipe.description,
                "imagePath" to recipe.imagePath,
                "category" to recipe.category,
                "prepTime" to recipe.prepTime,
                "popularityScore" to recipe.popularityScore,
                "cuisine" to recipe.cuisine,
                "cuisineCode" to recipe.cuisineCode,
                "authorId" to recipe.authorId,
                "isPublic" to recipe.isPublic,
                "calories" to recipe.calories,
                "proteins" to recipe.proteins,
                "fats" to recipe.fats,
                "carbs" to recipe.carbs,
                "totalWeight" to recipe.totalWeight,
                "ingredients" to recipe.ingredients.map {
                    mapOf("name" to it.name, "quantity" to it.quantity, "unit" to it.unit)
                },
                "steps" to recipe.steps.map { step ->
                    mapOf(
                        "title" to step.title,
                        "description" to step.description,
                        "timerMinutes" to step.timerMinutes,
                        "imagePath" to step.imagePath,
                        "ingredients" to step.ingredients.map {
                            mapOf("name" to it.name, "quantity" to it.quantity, "unit" to it.unit)
                        }
                    )
                }
            )
            val docRef = recipesCollection.add(data).await()
            docRef.id
        } catch (e: Exception) { null }
    }

    suspend fun updateRecipe(recipeId: String, recipe: CloudRecipe): Boolean {
        return try {
            val data = mapOf(
                "name" to recipe.name,
                "description" to recipe.description,
                "imagePath" to recipe.imagePath,
                "category" to recipe.category,
                "prepTime" to recipe.prepTime,
                "popularityScore" to recipe.popularityScore,
                "cuisine" to recipe.cuisine,
                "cuisineCode" to recipe.cuisineCode,
                "authorId" to recipe.authorId,
                "isPublic" to recipe.isPublic,
                "calories" to recipe.calories,
                "proteins" to recipe.proteins,
                "fats" to recipe.fats,
                "carbs" to recipe.carbs,
                "totalWeight" to recipe.totalWeight,
                "ingredients" to recipe.ingredients.map {
                    mapOf("name" to it.name, "quantity" to it.quantity, "unit" to it.unit)
                },
                "steps" to recipe.steps.map { step ->
                    mapOf(
                        "title" to step.title,
                        "description" to step.description,
                        "timerMinutes" to step.timerMinutes,
                        "imagePath" to step.imagePath,
                        "ingredients" to step.ingredients.map {
                            mapOf("name" to it.name, "quantity" to it.quantity, "unit" to it.unit)
                        }
                    )
                }
            )
            recipesCollection.document(recipeId).set(data).await()
            true
        } catch (e: Exception) { false }
    }

    suspend fun deleteRecipe(recipeId: String): Boolean {
        return try {
            recipesCollection.document(recipeId).delete().await()
            true
        } catch (e: Exception) { false }
    }

    suspend fun uploadInitialData(
        ingredients: List<CloudIngredientData>,
        recipes: List<CloudRecipe>
    ) {
        try {
            for (ingredient in ingredients) { addIngredient(ingredient) }
            for (recipe in recipes) { addRecipe(recipe) }
        } catch (e: Exception) {
            Log.e("FirestoreService", "❌ Error uploading initial data", e)
        }
    }

    suspend fun hasData(): Boolean {
        return try {
            val ingredientsSnapshot = ingredientsCollection.limit(1).get().await()
            val recipesSnapshot = recipesCollection.limit(1).get().await()
            (ingredientsSnapshot.documents.isNotEmpty() || recipesSnapshot.documents.isNotEmpty())
        } catch (e: Exception) {
            Log.e("FirestoreService", "hasData error", e)
            false
        }
    }

    suspend fun getUserAllergens(userId: String): List<String> {
        return try {
            val doc = usersCollection.document(userId).get().await()
            val allergensObj = doc.get("allergens")
            when (allergensObj) {
                is List<*> -> allergensObj.filterIsInstance<String>()
                else -> emptyList()
            }
        } catch (e: Exception) { emptyList() }
    }

    fun getUserAllergensFlow(userId: String): Flow<List<String>> = callbackFlow {
        val listener = usersCollection.document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null && snapshot.exists()) {
                    val allergensObj = snapshot.get("allergens")
                    val allergens = when (allergensObj) {
                        is List<*> -> allergensObj.filterIsInstance<String>()
                        else -> emptyList()
                    }
                    trySend(allergens)
                } else {
                    trySend(emptyList())
                }
            }
        awaitClose { listener.remove() }
    }
}

data class RecipeData(
    val id: String,
    val recipe: CloudRecipe
)

data class CloudRecipe(
    val name: String = "",
    val description: String = "",
    val imagePath: String = "",
    val category: String = "",
    val prepTime: String = "",
    val popularityScore: Int = 0,
    val cuisine: String = "Русская",
    val cuisineCode: String = "RU",
    val ingredients: List<CloudIngredient> = emptyList(),
    val steps: List<StepData> = emptyList(),
    val authorId: String = "admin",
    val isPublic: Boolean = true,
    val calories: Int = 0,
    val proteins: Double = 0.0,
    val fats: Double = 0.0,
    val carbs: Double = 0.0,
    val totalWeight: Int = 0
)

data class StepData(
    val title: String = "",
    val description: String = "",
    val timerMinutes: Int = 0,
    val imagePath: String = "",
    val ingredients: List<CloudIngredient> = emptyList()
)

data class CloudIngredient(
    val name: String = "",
    val quantity: String = "",
    val unit: String = ""
)

data class CloudIngredientData(
    val id: String = "",
    val name: String = "",
    val isAlwaysAvailable: Boolean = false,
    val isCoreIngredient: Boolean = true
)
