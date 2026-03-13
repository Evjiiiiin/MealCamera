package com.example.mealcamera.data.remote

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FirestoreService {

    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val recipesCollection = db.collection("recipes")
    private val ingredientsCollection = db.collection("ingredients")
    private val usersCollection = db.collection("users")

    // ========== ИНГРЕДИЕНТЫ ==========

    suspend fun getAllIngredients(): List<CloudIngredientData> {
        return try {
            val snapshot = ingredientsCollection.get().await()
            Log.d("FirestoreService", "🔥 Loaded ${snapshot.documents.size} ingredients")

            snapshot.documents.mapNotNull { document ->
                try {
                    CloudIngredientData(
                        id = document.id,
                        name = document.getString("name") ?: return@mapNotNull null,
                        isAlwaysAvailable = document.getBoolean("isAlwaysAvailable") ?: false,
                        isCoreIngredient = document.getBoolean("isCoreIngredient") ?: true
                    )
                } catch (e: Exception) {
                    Log.e("FirestoreService", "Error parsing ingredient", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("FirestoreService", "Error loading ingredients", e)
            emptyList()
        }
    }

    suspend fun addIngredient(ingredient: CloudIngredientData): Boolean {
        return try {
            ingredientsCollection.document(ingredient.name).set(
                mapOf(
                    "name" to ingredient.name,
                    "isAlwaysAvailable" to ingredient.isAlwaysAvailable,
                    "isCoreIngredient" to ingredient.isCoreIngredient
                )
            ).await()
            true
        } catch (e: Exception) {
            Log.e("FirestoreService", "Error adding ingredient", e)
            false
        }
    }

    // ========== РЕЦЕПТЫ ==========

    suspend fun getAllRecipes(): List<RecipeData> {
        return try {
            val snapshot = recipesCollection.get().await()
            snapshot.documents.mapNotNull { doc -> parseRecipeDocument(doc) }
        } catch (e: Exception) {
            Log.e("FirestoreService", "Error loading recipes", e)
            emptyList()
        }
    }

    suspend fun getRecipesExcludingAllergens(allergenNames: List<String>): List<RecipeData> {
        val allRecipes = getAllRecipes()
        if (allergenNames.isEmpty()) return allRecipes

        return allRecipes.filter { recipeData ->
            val hasAllergen = recipeData.recipe.ingredients.any { ingredient ->
                allergenNames.any { allergen ->
                    ingredient.name.contains(allergen, ignoreCase = true)
                }
            }
            !hasAllergen
        }
    }

    suspend fun addUserRecipe(recipe: CloudRecipe, authorId: String, isPublic: Boolean): String? {
        return try {
            val data = mapOf(
                "name" to recipe.name,
                "description" to recipe.description,
                "imagePath" to recipe.imagePath,
                "category" to recipe.category,
                "prepTime" to recipe.prepTime,
                "popularityScore" to 0,
                "cuisine" to recipe.cuisine,
                "cuisineCode" to recipe.cuisineCode,
                "authorId" to authorId,
                "isPublic" to isPublic,
                "createdAt" to System.currentTimeMillis(),
                "ingredients" to recipe.ingredients.map {
                    mapOf(
                        "name" to it.name,
                        "quantity" to it.quantity,
                        "unit" to it.unit
                    )
                },
                "steps" to recipe.steps.map { step ->
                    mapOf(
                        "title" to step.title,
                        "description" to step.description,
                        "timerMinutes" to step.timerMinutes,
                        "imagePath" to step.imagePath,
                        "ingredients" to step.ingredients.map {
                            mapOf(
                                "name" to it.name,
                                "quantity" to it.quantity,
                                "unit" to it.unit
                            )
                        }
                    )
                }
            )
            val docRef = recipesCollection.add(data).await()
            Log.d("FirestoreService", "✅ User added recipe: ${recipe.name}")
            docRef.id
        } catch (e: Exception) {
            Log.e("FirestoreService", "Error adding user recipe", e)
            null
        }
    }

    suspend fun getUserRecipes(userId: String): List<RecipeData> {
        return try {
            val snapshot = recipesCollection
                .whereEqualTo("authorId", userId)
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .await()
            snapshot.documents.mapNotNull { doc -> parseRecipeDocument(doc) }
        } catch (e: Exception) {
            Log.e("FirestoreService", "Error loading user recipes", e)
            emptyList()
        }
    }

    suspend fun getPublicRecipes(): List<RecipeData> {
        return try {
            val snapshot = recipesCollection
                .whereEqualTo("isPublic", true)
                .get()
                .await()
            snapshot.documents.mapNotNull { doc -> parseRecipeDocument(doc) }
        } catch (e: Exception) {
            Log.e("FirestoreService", "Error loading public recipes", e)
            emptyList()
        }
    }

    private fun parseRecipeDocument(document: com.google.firebase.firestore.DocumentSnapshot): RecipeData? {
        return try {
            val name = document.getString("name") ?: return null

            val ingredientsList = mutableListOf<CloudIngredient>()
            (document.get("ingredients") as? List<*>)?.forEach { item ->
                if (item is Map<*, *>) {
                    ingredientsList.add(
                        CloudIngredient(
                            name = item["name"] as? String ?: "",
                            quantity = (item["quantity"] as? Any)?.toString() ?: "1",
                            unit = item["unit"] as? String ?: ""
                        )
                    )
                }
            }

            val stepsList = mutableListOf<StepData>()
            (document.get("steps") as? List<*>)?.forEach { step ->
                if (step is Map<*, *>) {
                    val stepIngredients = mutableListOf<CloudIngredient>()
                    (step["ingredients"] as? List<*>)?.forEach { ing ->
                        if (ing is Map<*, *>) {
                            stepIngredients.add(
                                CloudIngredient(
                                    name = ing["name"] as? String ?: "",
                                    quantity = (ing["quantity"] as? Any)?.toString() ?: "1",
                                    unit = ing["unit"] as? String ?: ""
                                )
                            )
                        }
                    }

                    stepsList.add(
                        StepData(
                            title = step["title"] as? String ?: "Шаг",
                            description = step["description"] as? String ?: "",
                            timerMinutes = (step["timerMinutes"] as? Number)?.toInt() ?: 0,
                            imagePath = step["imagePath"] as? String ?: "",
                            ingredients = stepIngredients
                        )
                    )
                }
            }

            val cloudRecipe = CloudRecipe(
                name = name,
                description = document.getString("description") ?: "",
                imagePath = document.getString("imagePath") ?: "",
                category = document.getString("category") ?: "",
                prepTime = document.getString("prepTime") ?: "",
                popularityScore = (document.get("popularityScore") as? Number)?.toInt() ?: 0,
                cuisine = document.getString("cuisine") ?: "Русская",
                cuisineCode = document.getString("cuisineCode") ?: "RU",
                ingredients = ingredientsList,
                steps = stepsList,
                authorId = document.getString("authorId") ?: "admin",
                isPublic = document.getBoolean("isPublic") ?: true
            )

            RecipeData(document.id, cloudRecipe)
        } catch (e: Exception) {
            Log.e("FirestoreService", "Error parsing document", e)
            null
        }
    }

    suspend fun hasData(): Boolean {
        return try {
            val ingredients = ingredientsCollection.limit(1).get().await()
            val recipes = recipesCollection.limit(1).get().await()
            ingredients.documents.isNotEmpty() || recipes.documents.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    suspend fun uploadInitialData(
        ingredients: List<CloudIngredientData>,
        recipes: List<CloudRecipe>
    ) {
        try {
            ingredients.forEach { addIngredient(it) }
            recipes.forEach { addUserRecipe(it, "admin", true) }
        } catch (e: Exception) {
            Log.e("FirestoreService", "Error uploading initial data", e)
        }
    }

    // ========== ПОЛЬЗОВАТЕЛИ ==========

    suspend fun getUserAllergens(userId: String): List<String> {
        return try {
            val doc = usersCollection.document(userId).get().await()
            @Suppress("UNCHECKED_CAST")
            (doc.get("allergens") as? List<String>) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}

// ========== DATA CLASSES ==========

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
    val isPublic: Boolean = true
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