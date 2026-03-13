package com.example.mealcamera.data

import android.util.Log
import com.example.mealcamera.data.dao.RecipeDao
import com.example.mealcamera.data.dao.ShoppingListDao
import com.example.mealcamera.data.model.*
import com.example.mealcamera.data.remote.FirestoreService
import com.example.mealcamera.data.remote.RecipeData
import com.example.mealcamera.data.remote.CloudRecipe
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

    suspend fun syncRecipesFromCloud(userAllergens: List<String> = emptyList()) = withContext(Dispatchers.IO) {
        try {
            Log.d("RecipeRepository", "🔄 Starting sync from cloud")

            syncIngredientsFromCloud()

            val localRecipes = recipeDao.getAllRecipesWithIngredients()
            val localRecipeMap = localRecipes.associate {
                "${it.recipe.name.lowercase()}|${it.recipe.category.lowercase()}" to it.recipe
            }

            val cloudRecipes = if (userAllergens.isNotEmpty()) {
                firestoreService.getRecipesExcludingAllergens(userAllergens)
            } else {
                firestoreService.getAllRecipes()
            }

            Log.d("RecipeRepository", "📦 Loaded ${cloudRecipes.size} recipes from cloud")

            var addedCount = 0
            var updatedCount = 0

            cloudRecipes.forEach { cloudData ->
                val cloudRecipe = cloudData.recipe
                val recipeKey = "${cloudRecipe.name.lowercase()}|${cloudRecipe.category.lowercase()}"

                val existingByFirestoreId = localRecipes.find { it.recipe.firestoreId == cloudData.id }?.recipe
                val existingByName = localRecipeMap[recipeKey]

                when {
                    existingByFirestoreId != null -> {
                        Log.d("RecipeRepository", "   🔄 Updating by firestoreId: ${cloudRecipe.name}")
                        updateExistingRecipe(cloudData, existingByFirestoreId.recipeId)
                        updatedCount++
                    }

                    existingByName != null && existingByName.firestoreId == null -> {
                        Log.d("RecipeRepository", "   🔗 Linking local recipe to firestoreId: ${cloudRecipe.name}")
                        val updatedRecipe = existingByName.copy(
                            firestoreId = cloudData.id,
                            name = cloudRecipe.name,
                            description = cloudRecipe.description,
                            imagePath = cloudRecipe.imagePath,
                            prepTime = cloudRecipe.prepTime,
                            popularityScore = cloudRecipe.popularityScore,
                            cuisine = cloudRecipe.cuisine,
                            cuisineCode = cloudRecipe.cuisineCode,
                            authorId = cloudRecipe.authorId,
                            isPublic = cloudRecipe.isPublic
                        )
                        recipeDao.updateRecipe(updatedRecipe)

                        updateRecipeIngredientsAndSteps(cloudData, existingByName.recipeId)
                        updatedCount++
                    }

                    else -> {
                        Log.d("RecipeRepository", "   ➕ Inserting new recipe: ${cloudRecipe.name}")
                        insertNewRecipe(cloudData)
                        addedCount++
                    }
                }
            }

            Log.d("RecipeRepository", "✅ Sync complete: Added=$addedCount, Updated=$updatedCount")

        } catch (e: Exception) {
            Log.e("RecipeRepository", "❌ Error during sync", e)
        }
    }

    private suspend fun syncIngredientsFromCloud() {
        try {
            val cloudIngredients = firestoreService.getAllIngredients()
            Log.d("RecipeRepository", "📦 Loaded ${cloudIngredients.size} ingredients from cloud")

            val localIngredients = recipeDao.getAllIngredients()
            val localIngredientMap = localIngredients.associateBy { it.name.lowercase() }

            var addedCount = 0
            var updatedCount = 0

            cloudIngredients.forEach { cloudIngredient ->
                val existing = localIngredientMap[cloudIngredient.name.lowercase()]

                if (existing == null) {
                    recipeDao.insertIngredient(Ingredient(
                        name = cloudIngredient.name,
                        isAlwaysAvailable = cloudIngredient.isAlwaysAvailable,
                        isCoreIngredient = cloudIngredient.isCoreIngredient
                    ))
                    addedCount++
                    Log.d("RecipeRepository", "   ➕ Added new ingredient: ${cloudIngredient.name}")
                } else if (existing.isAlwaysAvailable != cloudIngredient.isAlwaysAvailable ||
                    existing.isCoreIngredient != cloudIngredient.isCoreIngredient
                ) {
                    val updated = existing.copy(
                        isAlwaysAvailable = cloudIngredient.isAlwaysAvailable,
                        isCoreIngredient = cloudIngredient.isCoreIngredient
                    )
                    recipeDao.updateIngredient(updated)
                    updatedCount++
                    Log.d("RecipeRepository", "   📝 Updated ingredient: ${cloudIngredient.name}")
                }
            }

            Log.d("RecipeRepository", "✅ Ingredients sync: Added=$addedCount, Updated=$updatedCount")

        } catch (e: Exception) {
            Log.e("RecipeRepository", "❌ Error syncing ingredients", e)
        }
    }

    private suspend fun insertNewRecipe(cloudData: RecipeData) {
        Log.d("RecipeRepository", "   ➕ Inserting new recipe: ${cloudData.recipe.name}")

        val newRecipe = Recipe(
            firestoreId = cloudData.id,
            name = cloudData.recipe.name,
            description = cloudData.recipe.description,
            imagePath = cloudData.recipe.imagePath,
            category = cloudData.recipe.category,
            prepTime = cloudData.recipe.prepTime,
            popularityScore = cloudData.recipe.popularityScore,
            cuisine = cloudData.recipe.cuisine,
            cuisineCode = cloudData.recipe.cuisineCode,
            authorId = cloudData.recipe.authorId,
            isPublic = cloudData.recipe.isPublic
        )
        val localId = recipeDao.insertRecipe(newRecipe)

        saveIngredientsForRecipe(cloudData, localId)
        saveStepsForRecipe(cloudData, localId)

        Log.d("RecipeRepository", "   ✅ Successfully inserted recipe with ID: $localId")
    }

    private suspend fun updateExistingRecipe(cloudData: RecipeData, existingId: Long) {
        Log.d("RecipeRepository", "   📝 Updating existing recipe: ${cloudData.recipe.name}")

        val updatedRecipe = Recipe(
            recipeId = existingId,
            firestoreId = cloudData.id,
            name = cloudData.recipe.name,
            description = cloudData.recipe.description,
            imagePath = cloudData.recipe.imagePath,
            category = cloudData.recipe.category,
            prepTime = cloudData.recipe.prepTime,
            popularityScore = cloudData.recipe.popularityScore,
            cuisine = cloudData.recipe.cuisine,
            cuisineCode = cloudData.recipe.cuisineCode,
            authorId = cloudData.recipe.authorId,
            isPublic = cloudData.recipe.isPublic
        )
        recipeDao.updateRecipe(updatedRecipe)

        updateRecipeIngredientsAndSteps(cloudData, existingId)

        Log.d("RecipeRepository", "   ✅ Successfully updated recipe with ID: $existingId")
    }

    private suspend fun updateRecipeIngredientsAndSteps(cloudData: RecipeData, recipeId: Long) {
        recipeDao.deleteCrossRefsByRecipeId(recipeId)
        recipeDao.deleteStepsByRecipeId(recipeId)
        recipeDao.deleteStepIngredientsByRecipeId(recipeId)

        saveIngredientsForRecipe(cloudData, recipeId)
        saveStepsForRecipe(cloudData, recipeId)
    }

    private suspend fun saveIngredientsForRecipe(cloudData: RecipeData, recipeId: Long) {
        cloudData.recipe.ingredients.forEach { cloudIngredient ->
            val ingredient = recipeDao.getIngredientByName(cloudIngredient.name)
                ?: run {
                    val newIngredient = Ingredient(name = cloudIngredient.name)
                    val ingredientId = recipeDao.insertIngredient(newIngredient)
                    newIngredient.copy(ingredientId = ingredientId)
                }

            val crossRef = RecipeIngredientCrossRef(
                recipeId = recipeId,
                ingredientId = ingredient.ingredientId,
                quantity = cloudIngredient.quantity,
                unit = cloudIngredient.unit
            )
            recipeDao.insertRecipeIngredientCrossRef(crossRef)

            Log.d("RecipeRepository", "      Saved ingredient: ${ingredient.name} = ${cloudIngredient.quantity} ${cloudIngredient.unit}")
        }
    }

    private suspend fun saveStepsForRecipe(cloudData: RecipeData, recipeId: Long) {
        cloudData.recipe.steps.forEachIndexed { index, cloudStep ->
            val step = RecipeStep(
                recipeId = recipeId,
                stepNumber = index + 1,
                title = cloudStep.title,
                instruction = cloudStep.description,
                timerMinutes = cloudStep.timerMinutes,
                imagePath = cloudStep.imagePath
            )
            val stepId = recipeDao.insertStep(step)

            cloudStep.ingredients.forEach { cloudIngredient ->
                val ingredient = recipeDao.getIngredientByName(cloudIngredient.name)
                    ?: run {
                        val newIngredient = Ingredient(name = cloudIngredient.name)
                        val ingredientId = recipeDao.insertIngredient(newIngredient)
                        newIngredient.copy(ingredientId = ingredientId)
                    }

                val stepIngredient = StepIngredient(
                    stepId = stepId,
                    ingredientId = ingredient.ingredientId,
                    quantity = cloudIngredient.quantity,
                    unit = cloudIngredient.unit
                )
                recipeDao.insertStepIngredient(stepIngredient)
            }
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

    fun getStepsWithIngredients(recipeId: Long): Flow<List<StepWithIngredients>> =
        recipeDao.getStepsWithIngredients(recipeId)

    fun getCookingStepsWithIngredients(recipeId: Long, portions: Int): Flow<List<CookingStepWithIngredients>> {
        val safePortions = portions.coerceIn(1, 10)

        return recipeDao.getStepsByRecipeId(recipeId).map { steps ->
            steps.map { step ->
                val stepIngredients = recipeDao.getStepIngredients(step.stepId)
                val ingredientDetails = stepIngredients.mapNotNull { stepIngredient ->
                    val ingredient = recipeDao.getIngredientById(stepIngredient.ingredientId) ?: return@mapNotNull null

                    val scaledQuantity = stepIngredient.quantity.toDoubleOrNull()?.let { numeric ->
                        val total = numeric * safePortions
                        if (total % 1.0 == 0.0) total.toInt().toString() else String.format("%.1f", total)
                    } ?: stepIngredient.quantity

                    IngredientWithDetails(
                        ingredient = ingredient,
                        quantity = scaledQuantity,
                        unit = stepIngredient.unit
                    )
                }

                CookingStepWithIngredients(
                    step = step,
                    ingredients = ingredientDetails
                )
            }
        }
    }

    suspend fun getRecipeIngredientCrossRef(recipeId: Long, ingredientId: Long): RecipeIngredientCrossRef? =
        recipeDao.getCrossRef(recipeId, ingredientId)

    // ========== SHOPPING LIST ==========
    suspend fun getShoppingList(): Flow<List<ShoppingListItem>> = shoppingListDao.getAllItems()

    suspend fun addToShoppingList(item: ShoppingListItem) {
        shoppingListDao.insert(item)
    }

    suspend fun removeFromShoppingList(itemId: Long) {
        shoppingListDao.delete(itemId)
    }

    suspend fun clearShoppingList() {
        shoppingListDao.deleteAll()
    }

    suspend fun deleteCheckedItems() {
        shoppingListDao.deleteCheckedItems()
    }

    // ========== ПОЛЬЗОВАТЕЛЬСКИЕ РЕЦЕПТЫ ==========
    suspend fun addUserRecipe(recipe: CloudRecipe, authorId: String, isPublic: Boolean): String? {
        return firestoreService.addUserRecipe(recipe, authorId, isPublic)
    }

    suspend fun getUserRecipes(userId: String): List<RecipeData> {
        return firestoreService.getUserRecipes(userId)
    }

    suspend fun getPublicRecipes(): List<RecipeData> {
        return firestoreService.getPublicRecipes()
    }

    suspend fun syncUserRecipes(userId: String) = withContext(Dispatchers.IO) {
        try {
            val userRecipes = firestoreService.getUserRecipes(userId)
            userRecipes.forEach { recipeData ->
                val existing = recipeDao.getRecipeIdByFirestoreId(recipeData.id)
                if (existing == null) {
                    insertNewRecipe(recipeData)
                }
            }
        } catch (e: Exception) {
            Log.e("RecipeRepository", "Error syncing user recipes", e)
        }
    }
}