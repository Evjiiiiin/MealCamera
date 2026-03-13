package com.example.mealcamera.data.remote

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FirestoreService {

    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val recipesCollection = db.collection("recipes")
    private val ingredientsCollection = db.collection("ingredients")

    // ========== ИНГРЕДИЕНТЫ ==========

    /**
     * Получить все ингредиенты из Firestore
     */
    suspend fun getAllIngredients(): List<CloudIngredientData> {
        return try {
            val snapshot = ingredientsCollection.get().await()
            Log.d("FirestoreService", "🔥 Loaded ${snapshot.documents.size} ingredients from Firebase")

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
                } catch (e: Exception) {
                    Log.e("FirestoreService", "Error parsing ingredient ${document.id}", e)
                    null
                }
            }

        } catch (e: Exception) {
            Log.e("FirestoreService", "Error loading ingredients", e)
            emptyList()
        }
    }

    /**
     * Добавить новый ингредиент в Firestore
     */
    suspend fun addIngredient(ingredient: CloudIngredientData): Boolean {
        return try {
            val data = mapOf(
                "name" to ingredient.name,
                "isAlwaysAvailable" to ingredient.isAlwaysAvailable,
                "isCoreIngredient" to ingredient.isCoreIngredient
            )
            // Используем имя как ID для уникальности
            ingredientsCollection.document(ingredient.name).set(data).await()
            Log.d("FirestoreService", "✅ Added ingredient: ${ingredient.name}")
            true
        } catch (e: Exception) {
            Log.e("FirestoreService", "Error adding ingredient", e)
            false
        }
    }

    /**
     * Обновить ингредиент
     */
    suspend fun updateIngredient(ingredient: CloudIngredientData): Boolean {
        return try {
            val data = mapOf(
                "name" to ingredient.name,
                "isAlwaysAvailable" to ingredient.isAlwaysAvailable,
                "isCoreIngredient" to ingredient.isCoreIngredient
            )
            ingredientsCollection.document(ingredient.id).set(data).await()
            Log.d("FirestoreService", "✅ Updated ingredient: ${ingredient.name}")
            true
        } catch (e: Exception) {
            Log.e("FirestoreService", "Error updating ingredient", e)
            false
        }
    }

    // ========== РЕЦЕПТЫ ==========

    /**
     * Получить все рецепты из Firestore
     */
    suspend fun getAllRecipes(): List<RecipeData> {
        return try {
            val snapshot = recipesCollection.get().await()
            Log.d("FirestoreService", "🔥 Loaded ${snapshot.documents.size} recipes from Firebase")

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

                    // Получаем ингредиенты как список
                    val ingredientsList = mutableListOf<CloudIngredient>()
                    val ingredientsField = document.get("ingredients")
                    if (ingredientsField is List<*>) {
                        ingredientsField.forEach { item ->
                            if (item is Map<*, *>) {
                                val ingName = item["name"] as? String ?: ""
                                val quantity = item["quantity"] as? String ?: ""
                                val unit = item["unit"] as? String ?: ""
                                if (ingName.isNotEmpty()) {
                                    ingredientsList.add(CloudIngredient(ingName, quantity, unit))
                                }
                            }
                        }
                    }

                    // Получаем шаги как список структурированных данных
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
                                                        quantity = ingredient["quantity"] as? String ?: "",
                                                        unit = ingredient["unit"] as? String ?: ""
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
                                        timerMinutes = (step["timerMinutes"] as? Long)?.toInt() ?: 0,
                                        imagePath = step["imagePath"] as? String ?: "",
                                        ingredients = ingredients
                                    )
                                )
                            } else if (step is String) {
                                // Поддержка старого формата, где шаги были просто строками
                                stepsList.add(
                                    StepData(
                                        title = "Шаг",
                                        description = step,
                                        timerMinutes = 0,
                                        imagePath = "",
                                        ingredients = emptyList()
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
                        isPublic = isPublic
                    )

                    RecipeData(
                        id = document.id,
                        recipe = cloudRecipe
                    )
                } catch (e: Exception) {
                    Log.e("FirestoreService", "Error parsing document ${document.id}", e)
                    null
                }
            }

        } catch (e: Exception) {
            Log.e("FirestoreService", "Error loading recipes", e)
            emptyList()
        }
    }

    /**
     * Добавить новый рецепт
     */
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
            Log.d("FirestoreService", "✅ Added recipe: ${recipe.name} with ID: ${docRef.id}")
            docRef.id
        } catch (e: Exception) {
            Log.e("FirestoreService", "Error adding recipe", e)
            null
        }
    }

    /**
     * Обновить рецепт
     */
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
            recipesCollection.document(recipeId).set(data).await()
            Log.d("FirestoreService", "✅ Updated recipe: ${recipe.name}")
            true
        } catch (e: Exception) {
            Log.e("FirestoreService", "Error updating recipe", e)
            false
        }
    }

    /**
     * Удалить рецепт
     */
    suspend fun deleteRecipe(recipeId: String): Boolean {
        return try {
            recipesCollection.document(recipeId).delete().await()
            Log.d("FirestoreService", "✅ Deleted recipe with ID: $recipeId")
            true
        } catch (e: Exception) {
            Log.e("FirestoreService", "Error deleting recipe", e)
            false
        }
    }

    // ========== ЗАГРУЗКА НАЧАЛЬНЫХ ДАННЫХ ==========

    /**
     * Загрузить начальные данные из JSON в Firestore
     * Вызывается только при первом запуске
     */
    suspend fun uploadInitialData(
        ingredients: List<CloudIngredientData>,
        recipes: List<CloudRecipe>
    ) {
        try {
            // 1. Загружаем ингредиенты
            Log.d("FirestoreService", "📤 Uploading ${ingredients.size} ingredients...")
            var ingredientsUploaded = 0
            for (ingredient in ingredients) {
                val success = addIngredient(ingredient)
                if (success) ingredientsUploaded++
            }
            Log.d("FirestoreService", "✅ Uploaded $ingredientsUploaded/${ingredients.size} ingredients")

            // 2. Загружаем рецепты
            Log.d("FirestoreService", "📤 Uploading ${recipes.size} recipes...")
            var recipesUploaded = 0
            for (recipe in recipes) {
                val id = addRecipe(recipe)
                if (id != null) recipesUploaded++
            }
            Log.d("FirestoreService", "✅ Uploaded $recipesUploaded/${recipes.size} recipes")

        } catch (e: Exception) {
            Log.e("FirestoreService", "❌ Error uploading initial data", e)
        }
    }

    /**
     * Проверить, есть ли уже данные в Firestore
     */
    suspend fun hasData(): Boolean {
        return try {
            val ingredientsSnapshot = ingredientsCollection.limit(1).get().await()
            val recipesSnapshot = recipesCollection.limit(1).get().await()
            (ingredientsSnapshot.documents.isNotEmpty() || recipesSnapshot.documents.isNotEmpty())
        } catch (e: Exception) {
            Log.e("FirestoreService", "Error checking data", e)
            false
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