package com.example.mealcamera.data

import android.util.Log
import com.example.mealcamera.data.dao.RecipeDao
import com.example.mealcamera.data.dao.ShoppingListDao
import com.example.mealcamera.data.model.*
import com.example.mealcamera.data.remote.FirestoreService
import com.example.mealcamera.data.remote.RecipeData
import com.example.mealcamera.data.remote.CloudRecipe
import com.example.mealcamera.data.remote.StepData
import com.example.mealcamera.data.remote.CloudIngredient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.Locale

class RecipeRepository(
    private val recipeDao: RecipeDao,
    private val shoppingListDao: ShoppingListDao,
    private val firestoreService: FirestoreService
) {

    val allRecipes: Flow<List<Recipe>> = recipeDao.getAllRecipes()

    fun allRecipesWithIngredientsFlow(): Flow<List<RecipeWithIngredients>> = 
        recipeDao.getAllRecipesWithIngredientsFlow()

    private fun normalize(name: String): String {
        return name.trim().lowercase(Locale.ROOT).replace("ё", "е")
    }

    suspend fun syncRecipesFromCloud() = withContext(Dispatchers.IO) {
        try {
            Log.d("RecipeRepository", "🔄 Starting sync from cloud")
            syncIngredientsFromCloud()
            val cloudRecipes = firestoreService.getAllRecipes()
            
            // 1. Получаем список всех firestoreId из облака
            val cloudIds = cloudRecipes.map { it.id }.toSet()

            // 2. Получаем все локальные рецепты, у которых есть firestoreId
            val localRecipes = recipeDao.getAllRecipes().first()
            
            // 3. Удаляем локальные рецепты, которых больше нет в облаке
            localRecipes.forEach { local ->
                if (local.firestoreId != null && !cloudIds.contains(local.firestoreId)) {
                    Log.d("RecipeRepository", "🗑 Deleting recipe not found in cloud: ${local.name}")
                    recipeDao.deleteRecipe(local.recipeId)
                }
            }

            // 4. Обновляем или добавляем рецепты из облака
            cloudRecipes.forEach { cloudData ->
                val existingIdByFirestore = recipeDao.getRecipeIdByFirestoreId(cloudData.id)
                val existingByNameAndCategory = recipeDao.getRecipeByNameAndCategory(
                    cloudData.recipe.name,
                    cloudData.recipe.category
                )
                when {
                    existingIdByFirestore != null -> {
                        updateExistingRecipe(cloudData, existingIdByFirestore)
                    }
                    existingByNameAndCategory != null -> {
                        updateExistingRecipe(cloudData, existingByNameAndCategory.recipeId)
                    }
                    else -> {
                        insertNewRecipe(cloudData)
                    }
                }
            }
            Log.d("RecipeRepository", "✅ Sync completed successfully")
        } catch (e: Exception) {
            Log.e("RecipeRepository", "❌ Error during sync", e)
        }
    }

    private suspend fun syncIngredientsFromCloud() {
        try {
            val cloudIngredients = firestoreService.getAllIngredients()
            val localIngredients = recipeDao.getAllIngredients()
            val localNormalizedNames = localIngredients.map { normalize(it.name) }.toSet()
            cloudIngredients.forEach { cloudIngredient ->
                val normalizedName = normalize(cloudIngredient.name)
                if (normalizedName.isNotEmpty() && normalizedName !in localNormalizedNames) {
                    recipeDao.insertIngredient(
                        Ingredient(
                            name = cloudIngredient.name,
                            isAlwaysAvailable = cloudIngredient.isAlwaysAvailable,
                            isCoreIngredient = cloudIngredient.isCoreIngredient
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("RecipeRepository", "❌ Error syncing ingredients", e)
        }
    }

    fun expandAllergens(allergens: List<String>): List<String> {
        val allergenToIngredients = mapOf(
            "Молочные продукты" to listOf("молоко", "сыр", "сливочное масло", "сливки", "йогурт", "маскарпоне", "творог", "сметана", "кефир", "кумыс", "ряженка"),
            "Мясо" to listOf("говядина", "свинина", "баранина", "курица", "бекон", "мясо", "фарш", "индейка", "ветчина", "колбаса"),
            "Крупы" to listOf("мука", "рис", "гречка", "макароны", "овсян", "хлеб", "гранола", "крупы", "пшениц", "ячмень", "рожь"),
            "Орехи" to listOf("орехи", "арахис", "миндаль", "фундук", "кешью", "фисташки", "грецкий орех"),
            "Морепродукты" to listOf("рыба", "краб", "креветк", "морепродукт", "мидии", "омар", "кальмар"),
            "Яйца" to listOf("яйцо", "яичный")
        )
        return allergens.flatMap { allergen ->
            allergenToIngredients[allergen] ?: listOf(allergen)
        }.distinct()
    }

    private suspend fun insertNewRecipe(cloudData: RecipeData) {
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
            createdByUserId = cloudData.recipe.authorId,
            isPublicRecipe = cloudData.recipe.isPublic
        )
        val localId = recipeDao.insertRecipe(newRecipe)
        saveIngredientsForRecipe(cloudData, localId)
        saveStepsForRecipe(cloudData, localId)
    }

    private suspend fun updateExistingRecipe(cloudData: RecipeData, existingId: Long) {
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
            createdByUserId = cloudData.recipe.authorId,
            isPublicRecipe = cloudData.recipe.isPublic
        )
        recipeDao.updateRecipe(updatedRecipe)
        recipeDao.deleteCrossRefsByRecipeId(existingId)
        recipeDao.deleteStepsByRecipeId(existingId)
        recipeDao.deleteStepIngredientsByRecipeId(existingId)
        saveIngredientsForRecipe(cloudData, existingId)
        saveStepsForRecipe(cloudData, existingId)
    }

    private suspend fun saveIngredientsForRecipe(cloudData: RecipeData, localRecipeId: Long) {
        val cachedIngredients = recipeDao.getAllIngredients().toMutableList()
        cloudData.recipe.ingredients.forEach { cloudIngredient ->
            val name = cloudIngredient.name.trim()
            if (name.isEmpty()) return@forEach
            val normalizedName = normalize(name)
            val existingIngredient = cachedIngredients.find { normalize(it.name) == normalizedName }
            val ingredientId = if (existingIngredient != null) {
                existingIngredient.ingredientId
            } else {
                val newId = recipeDao.insertIngredient(Ingredient(name = name))
                cachedIngredients.add(Ingredient(ingredientId = newId, name = name))
                newId
            }
            recipeDao.insertRecipeIngredientCrossRef(
                RecipeIngredientCrossRef(
                    recipeId = localRecipeId,
                    ingredientId = ingredientId,
                    quantity = cloudIngredient.quantity,
                    unit = cloudIngredient.unit
                )
            )
        }
    }

    private suspend fun saveStepsForRecipe(cloudData: RecipeData, localRecipeId: Long) {
        val cachedIngredients = recipeDao.getAllIngredients().toMutableList()
        cloudData.recipe.steps.forEachIndexed { index, stepData ->
            val stepId = recipeDao.insertStep(
                RecipeStep(
                    recipeId = localRecipeId,
                    stepNumber = index + 1,
                    title = stepData.title,
                    instruction = stepData.description,
                    timerMinutes = stepData.timerMinutes,
                    imagePath = stepData.imagePath
                )
            )
            stepData.ingredients.forEach { stepIngredient ->
                val name = stepIngredient.name.trim()
                if (name.isEmpty()) return@forEach
                val normalizedName = normalize(name)
                val existingIngredient = cachedIngredients.find { normalize(it.name) == normalizedName }
                val ingredientId = if (existingIngredient != null) {
                    existingIngredient.ingredientId
                } else {
                    val newId = recipeDao.insertIngredient(Ingredient(name = name))
                    cachedIngredients.add(Ingredient(ingredientId = newId, name = name))
                    newId
                }
                recipeDao.insertStepIngredient(
                    StepIngredient(
                        stepId = stepId,
                        ingredientId = ingredientId,
                        quantity = stepIngredient.quantity,
                        unit = stepIngredient.unit
                    )
                )
            }
        }
    }

    suspend fun addUserRecipe(cloudRecipe: CloudRecipe, userId: String, isPublic: Boolean): Long? {
        return withContext(Dispatchers.IO) {
            try {
                val firestoreId = firestoreService.addRecipe(cloudRecipe) ?: return@withContext null
                val recipe = Recipe(
                    firestoreId = firestoreId,
                    name = cloudRecipe.name,
                    description = cloudRecipe.description,
                    imagePath = cloudRecipe.imagePath,
                    category = cloudRecipe.category,
                    prepTime = cloudRecipe.prepTime,
                    popularityScore = 0,
                    cuisine = cloudRecipe.cuisine,
                    cuisineCode = cloudRecipe.cuisineCode,
                    createdByUserId = userId,
                    isPublicRecipe = isPublic
                )
                val localId = recipeDao.insertRecipe(recipe)
                val recipeData = RecipeData(id = firestoreId, recipe = cloudRecipe)
                saveIngredientsForRecipe(recipeData, localId)
                saveStepsForRecipe(recipeData, localId)
                localId
            } catch (_: Exception) {
                null
            }
        }
    }

    suspend fun updateUserRecipe(recipeId: Long, firestoreId: String?, cloudRecipe: CloudRecipe, userId: String, isPublic: Boolean): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (firestoreId != null) {
                    firestoreService.updateRecipe(firestoreId, cloudRecipe)
                }
                val updatedRecipe = Recipe(
                    recipeId = recipeId,
                    firestoreId = firestoreId,
                    name = cloudRecipe.name,
                    description = cloudRecipe.description,
                    imagePath = cloudRecipe.imagePath,
                    category = cloudRecipe.category,
                    prepTime = cloudRecipe.prepTime,
                    popularityScore = 0,
                    cuisine = cloudRecipe.cuisine,
                    cuisineCode = cloudRecipe.cuisineCode,
                    createdByUserId = userId,
                    isPublicRecipe = isPublic
                )
                recipeDao.updateRecipe(updatedRecipe)
                recipeDao.deleteCrossRefsByRecipeId(recipeId)
                recipeDao.deleteStepsByRecipeId(recipeId)
                recipeDao.deleteStepIngredientsByRecipeId(recipeId)
                
                val recipeData = RecipeData(id = firestoreId ?: "", recipe = cloudRecipe)
                saveIngredientsForRecipe(recipeData, recipeId)
                saveStepsForRecipe(recipeData, recipeId)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    suspend fun deleteUserRecipe(recipeId: Long, firestoreId: String?): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (firestoreId != null) {
                    firestoreService.deleteRecipe(firestoreId)
                }
                recipeDao.deleteRecipe(recipeId)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    fun getRecipeById(recipeId: Long): Flow<Recipe> = recipeDao.getRecipeById(recipeId)

    suspend fun getFullRecipeForEditing(recipeId: Long): CloudRecipe? {
        return withContext(Dispatchers.IO) {
            try {
                val recipe = recipeDao.getRecipeById(recipeId).first()
                val ingredients = recipeDao.getRecipeWithIngredientsById(recipeId).first().ingredients.map { ing ->
                    val crossRef = recipeDao.getCrossRef(recipeId, ing.ingredientId)
                    CloudIngredient(ing.name, crossRef?.quantity ?: "", crossRef?.unit ?: "")
                }
                val steps = recipeDao.getStepsByRecipeId(recipeId).first().map { step ->
                    val stepIngs = recipeDao.getStepIngredients(step.stepId).map { si ->
                        val ing = recipeDao.getIngredientById(si.ingredientId)
                        CloudIngredient(ing?.name ?: "", si.quantity, si.unit)
                    }
                    StepData(step.title, step.instruction, step.timerMinutes, step.imagePath, stepIngs)
                }
                CloudRecipe(recipe.name, recipe.description, recipe.imagePath, recipe.category, recipe.prepTime, recipe.popularityScore, recipe.cuisine, recipe.cuisineCode, ingredients, steps, recipe.createdByUserId ?: "", recipe.isPublicRecipe ?: true)
            } catch (_: Exception) {
                null
            }
        }
    }

    suspend fun incrementRecipePopularity(recipeId: Long) = recipeDao.incrementPopularity(recipeId)
    suspend fun getAllDbIngredients(): List<Ingredient> = recipeDao.getAllIngredients()
    suspend fun getAllRecipesWithIngredients(): List<RecipeWithIngredients> = recipeDao.getAllRecipesWithIngredients()

    fun getScaledIngredientsForRecipe(recipeId: Long, portions: Int): Flow<List<IngredientWithDetails>> {
        val safePortions = portions.coerceIn(1, 10)
        return recipeDao.getRecipeWithIngredientsById(recipeId).map { recipeWithIngredients ->
            recipeWithIngredients.ingredients.map { ingredient ->
                val crossRef = recipeDao.getCrossRef(recipeId, ingredient.ingredientId)
                val baseQuantity = crossRef?.quantity ?: ""
                val unit = crossRef?.unit ?: ""
                val scaledQuantity = baseQuantity.toDoubleOrNull()?.let { numeric ->
                    val total = numeric * safePortions
                    if (total % 1.0 == 0.0) total.toInt().toString()
                    else String.format(Locale.US, "%.1f", total)
                } ?: baseQuantity
                IngredientWithDetails(ingredient, scaledQuantity, unit)
            }
        }
    }

    fun getStepsForRecipe(recipeId: Long): Flow<List<RecipeStep>> = recipeDao.getStepsByRecipeId(recipeId)

    fun getCookingStepsWithIngredients(recipeId: Long, portions: Int): Flow<List<CookingStepWithIngredients>> {
        val safePortions = portions.coerceIn(1, 10)
        return recipeDao.getStepsByRecipeId(recipeId).map { steps ->
            steps.map { step ->
                val stepIngredients = recipeDao.getStepIngredients(step.stepId)
                val ingredientDetails = stepIngredients.mapNotNull { stepIngredient ->
                    val ingredient = recipeDao.getIngredientById(stepIngredient.ingredientId) ?: return@mapNotNull null
                    val scaledQuantity = stepIngredient.quantity.toDoubleOrNull()?.let { numeric ->
                        val total = numeric * safePortions
                        if (total % 1.0 == 0.0) total.toInt().toString()
                        else String.format(Locale.US, "%.1f", total)
                    } ?: stepIngredient.quantity
                    IngredientWithDetails(ingredient, scaledQuantity, stepIngredient.unit)
                }
                CookingStepWithIngredients(step, ingredientDetails)
            }
        }
    }

    suspend fun getRecipeIngredientCrossRef(recipeId: Long, ingredientId: Long): RecipeIngredientCrossRef? = recipeDao.getCrossRef(recipeId, ingredientId)
    fun getShoppingListItems(userId: String): Flow<List<ShoppingListItem>> = shoppingListDao.getAllItems(userId)
    suspend fun updateShoppingListItemChecked(item: ShoppingListItem, checked: Boolean) = shoppingListDao.updateItem(item.copy(isChecked = checked))
    suspend fun clearCheckedShoppingListItems(userId: String) = shoppingListDao.deleteCheckedItems(userId)

    suspend fun addScaledIngredientsToShoppingList(recipeId: Long, portions: Int, userId: String) {
        val recipeWithIngredients = recipeDao.getRecipeWithIngredientsById(recipeId).first()
        val safePortions = portions.coerceIn(1, 10)
        recipeWithIngredients.ingredients.forEach { ingredient ->
            val crossRef = recipeDao.getCrossRef(recipeId, ingredient.ingredientId)
            val baseQuantity = crossRef?.quantity ?: ""
            val unit = crossRef?.unit ?: ""
            val scaledQuantity = baseQuantity.toDoubleOrNull()?.let { numeric ->
                val total = numeric * safePortions
                if (total % 1.0 == 0.0) total.toInt().toString()
                else String.format(Locale.US, "%.1f", total)
            } ?: baseQuantity
            val existing = shoppingListDao.getItemByNameAndUnit(userId, ingredient.name, unit)
            if (existing == null) {
                shoppingListDao.insertItem(ShoppingListItem(userId = userId, name = ingredient.name, quantity = scaledQuantity, unit = unit))
            } else {
                val existingQty = existing.quantity.toDoubleOrNull() ?: 0.0
                val addQty = scaledQuantity.toDoubleOrNull() ?: 0.0
                val newQuantity = existingQty + addQty
                val newQuantityStr = if (newQuantity % 1.0 == 0.0) newQuantity.toInt().toString() else String.format(Locale.US, "%.1f", newQuantity)
                shoppingListDao.updateItem(existing.copy(quantity = newQuantityStr))
            }
        }
    }
}