package com.example.mealcamera.data

import android.util.Log
import com.example.mealcamera.data.dao.RecipeDao
import com.example.mealcamera.data.dao.ShoppingListDao
import com.example.mealcamera.data.model.*
import com.example.mealcamera.data.remote.FirestoreService
import com.example.mealcamera.data.remote.RecipeData
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

    suspend fun syncRecipesFromCloud() = withContext(Dispatchers.IO) {
        try {
            Log.d("RecipeRepository", "🔄 Starting sync from cloud")

            syncIngredientsFromCloud()

            val localRecipes = recipeDao.getAllRecipesWithIngredients()
            val localRecipeMap = localRecipes.associate {
                "${it.recipe.name.lowercase()}|${it.recipe.category.lowercase()}" to it.recipe
            }

            val cloudRecipes = firestoreService.getAllRecipes()
            Log.d("RecipeRepository", "📦 Loaded ${cloudRecipes.size} recipes from cloud")

            var addedCount = 0
            var updatedCount = 0

            cloudRecipes.forEach { cloudData ->
                val cloudRecipe = cloudData.recipe
                val recipeKey = "${cloudRecipe.name.lowercase()}|${cloudRecipe.category.lowercase()}"

                val existingByFirestoreId = recipeDao.getRecipeIdByFirestoreId(cloudData.id)
                val existingByName = localRecipeMap[recipeKey]

                when {
                    existingByFirestoreId != null -> {
                        Log.d("RecipeRepository", "   🔄 Updating existing recipe by firestoreId: ${cloudRecipe.name}")
                        updateExistingRecipe(cloudData, existingByFirestoreId)
                        updatedCount++
                    }

                    existingByName != null && existingByName.firestoreId == null -> {
                        Log.d("RecipeRepository", "   🔗 Linking local JSON recipe to firestoreId: ${cloudRecipe.name}")
                        val updatedRecipe = existingByName.copy(
                            firestoreId = cloudData.id,
                            name = cloudRecipe.name,
                            description = cloudRecipe.description,
                            imagePath = cloudRecipe.imagePath,
                            prepTime = cloudRecipe.prepTime,
                            popularityScore = cloudRecipe.popularityScore,
                            cuisine = cloudRecipe.cuisine,
                            cuisineCode = cloudRecipe.cuisineCode
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

            Log.d("RecipeRepository", "✅ Sync completed: +$addedCount new, 🔄 $updatedCount updated")

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
                    existing.isCoreIngredient != cloudIngredient.isCoreIngredient) {
                    val updatedIngredient = existing.copy(
                        isAlwaysAvailable = cloudIngredient.isAlwaysAvailable,
                        isCoreIngredient = cloudIngredient.isCoreIngredient
                    )
                    recipeDao.updateIngredient(updatedIngredient)
                    updatedCount++
                    Log.d("RecipeRepository", "   🔄 Updated ingredient: ${cloudIngredient.name}")
                }
            }

            Log.d("RecipeRepository", "   📊 Ingredients: +$addedCount new, 🔄 $updatedCount updated")

        } catch (e: Exception) {
            Log.e("RecipeRepository", "❌ Error syncing ingredients", e)
        }
    }

    private suspend fun insertNewRecipe(cloudData: RecipeData) {
        Log.d("RecipeRepository", "   📝 Inserting new recipe: ${cloudData.recipe.name}")

        val newRecipe = Recipe(
            firestoreId = cloudData.id,
            name = cloudData.recipe.name,
            description = cloudData.recipe.description,
            imagePath = cloudData.recipe.imagePath,
            category = cloudData.recipe.category,
            prepTime = cloudData.recipe.prepTime,
            popularityScore = cloudData.recipe.popularityScore,
            cuisine = cloudData.recipe.cuisine,
            cuisineCode = cloudData.recipe.cuisineCode
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
            cuisineCode = cloudData.recipe.cuisineCode
        )
        recipeDao.updateRecipe(updatedRecipe)

        updateRecipeIngredientsAndSteps(cloudData, existingId)

        Log.d("RecipeRepository", "   ✅ Successfully updated recipe: ${cloudData.recipe.name}")
    }

    private suspend fun updateRecipeIngredientsAndSteps(cloudData: RecipeData, recipeId: Long) {
        recipeDao.deleteCrossRefsByRecipeId(recipeId)
        recipeDao.deleteStepsByRecipeId(recipeId)
        recipeDao.deleteStepIngredientsByRecipeId(recipeId)

        saveIngredientsForRecipe(cloudData, recipeId)
        saveStepsForRecipe(cloudData, recipeId)
    }

    private suspend fun saveIngredientsForRecipe(
        cloudData: RecipeData,
        localId: Long
    ) {
        val allIngredients = recipeDao.getAllIngredients()
        val ingredientNameToId = allIngredients.associateBy { it.name.lowercase() }

        cloudData.recipe.ingredients.forEach { cloudIngredient ->
            val name = cloudIngredient.name
            if (name.isNotEmpty()) {
                val ingredientId = ingredientNameToId[name.lowercase()]?.ingredientId
                    ?: run {
                        recipeDao.insertIngredient(Ingredient(name = name))
                    }

                // Безопасное преобразование quantity в строку
                val quantityStr = cloudIngredient.quantity.toString()

                val crossRef = RecipeIngredientCrossRef(
                    recipeId = localId,
                    ingredientId = ingredientId,
                    quantity = quantityStr,
                    unit = cloudIngredient.unit
                )
                recipeDao.insertRecipeIngredientCrossRef(crossRef)

                Log.d("RecipeRepository", "      Saved ingredient: $name = $quantityStr ${cloudIngredient.unit}")
            }
        }
    }

    private suspend fun saveStepsForRecipe(
        cloudData: RecipeData,
        localId: Long
    ) {
        val allIngredients = recipeDao.getAllIngredients()
        val ingredientNameToId = allIngredients.associateBy { it.name.lowercase() }

        cloudData.recipe.steps.forEachIndexed { index, stepData ->
            val step = RecipeStep(
                recipeId = localId,
                stepNumber = index + 1,
                title = stepData.title,
                instruction = stepData.description,
                timerMinutes = stepData.timerMinutes,
                imagePath = stepData.imagePath
            )
            val stepId = recipeDao.insertStep(step)

            stepData.ingredients.forEach { cloudIngredient ->
                val ingredientName = cloudIngredient.name
                val ingredientId = ingredientNameToId[ingredientName.lowercase()]?.ingredientId
                    ?: run {
                        recipeDao.insertIngredient(Ingredient(name = ingredientName))
                    }

                // Безопасное преобразование quantity в строку
                val quantityStr = cloudIngredient.quantity.toString()

                val stepIngredient = StepIngredient(
                    stepId = stepId,
                    ingredientId = ingredientId,
                    quantity = quantityStr,
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
}