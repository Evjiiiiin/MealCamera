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
import com.example.mealcamera.data.util.UnitHelper
import com.example.mealcamera.util.IngredientTranslator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
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

    private fun capitalize(text: String): String {
        return text.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    suspend fun getRecipeByIdSync(recipeId: Long): Recipe? = withContext(Dispatchers.IO) {
        recipeDao.getRecipeByIdSync(recipeId)
    }

    suspend fun capitalizeExistingIngredients() = withContext(Dispatchers.IO) {
        try {
            val ingredients = recipeDao.getAllIngredients()
            ingredients.forEach { ingredient ->
                val capitalized = capitalize(ingredient.name)
                if (capitalized != ingredient.name) {
                    recipeDao.updateIngredientName(ingredient.ingredientId, capitalized)
                }
            }
            
            val recipes = recipeDao.getAllRecipes().firstOrNull() ?: emptyList()
            recipes.forEach { recipe ->
                val capitalized = capitalize(recipe.name)
                if (capitalized != recipe.name) {
                    recipeDao.updateRecipe(recipe.copy(name = capitalized))
                }
            }
        } catch (e: Exception) {
            Log.e("RecipeRepository", "Error capitalizing ingredients", e)
        }
    }

    suspend fun isEdible(name: String): Boolean = withContext(Dispatchers.IO) {
        if (name.isBlank()) return@withContext false
        val normalized = normalize(name)
        val defaults = setOf("соль", "сахар", "вода", "перец", "перец молотый", "черный перец", "молотый перец")
        if (defaults.contains(normalized)) return@withContext true
        
        if (IngredientTranslator.isKnownIngredient(normalized)) return@withContext true
        
        val existsInDb = recipeDao.getIngredientByName(name) != null || 
                         recipeDao.getIngredientByName(normalized) != null
        
        return@withContext existsInDb
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

    suspend fun syncRecipesFromCloud() = withContext(Dispatchers.IO) {
        try {
            syncIngredientsFromCloud()
            val cloudRecipes = firestoreService.getAllRecipes()
            val cloudIds = cloudRecipes.map { it.id }.toSet()

            val localRecipes = runCatching { recipeDao.getAllRecipes().firstOrNull() }.getOrDefault(emptyList())
            localRecipes?.forEach { local ->
                if (local.firestoreId != null && !cloudIds.contains(local.firestoreId)) {
                    recipeDao.deleteRecipe(local.recipeId)
                }
            }

            cloudRecipes.forEach { cloudData ->
                val existingIdByFirestore = recipeDao.getRecipeIdByFirestoreId(cloudData.id)
                val existingByNameAndCategory = recipeDao.getRecipeByNameAndCategory(
                    cloudData.recipe.name,
                    cloudData.recipe.category
                )
                when {
                    existingIdByFirestore != null -> updateExistingRecipe(cloudData, existingIdByFirestore)
                    existingByNameAndCategory != null -> updateExistingRecipe(cloudData, existingByNameAndCategory.recipeId)
                    else -> insertNewRecipe(cloudData)
                }
            }
            
            capitalizeExistingIngredients()
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
                    recipeDao.insertIngredient(Ingredient(name = capitalize(cloudIngredient.name)))
                }
            }
        } catch (e: Exception) { 
            Log.e("RecipeRepository", "Sync ingredients error", e)
        }
    }

    private suspend fun insertNewRecipe(cloudData: RecipeData) {
        val newRecipe = Recipe(
            firestoreId = cloudData.id,
            name = capitalize(cloudData.recipe.name),
            description = cloudData.recipe.description,
            imagePath = cloudData.recipe.imagePath,
            category = cloudData.recipe.category,
            prepTime = cloudData.recipe.prepTime,
            cuisine = cloudData.recipe.cuisine,
            cuisineCode = cloudData.recipe.cuisineCode,
            calories = cloudData.recipe.calories,
            proteins = cloudData.recipe.proteins,
            fats = cloudData.recipe.fats,
            carbs = cloudData.recipe.carbs,
            totalWeight = cloudData.recipe.totalWeight,
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
            name = capitalize(cloudData.recipe.name),
            description = cloudData.recipe.description,
            imagePath = cloudData.recipe.imagePath,
            category = cloudData.recipe.category,
            prepTime = cloudData.recipe.prepTime,
            cuisine = cloudData.recipe.cuisine,
            cuisineCode = cloudData.recipe.cuisineCode,
            calories = cloudData.recipe.calories,
            proteins = cloudData.recipe.proteins,
            fats = cloudData.recipe.fats,
            carbs = cloudData.recipe.carbs,
            totalWeight = cloudData.recipe.totalWeight,
            createdByUserId = cloudData.recipe.authorId,
            isPublicRecipe = cloudData.recipe.isPublic
        )
        recipeDao.updateRecipe(updatedRecipe)
        recipeDao.deleteCrossRefsByRecipeId(recipeId = existingId)
        recipeDao.deleteStepsByRecipeId(recipeId = existingId)
        recipeDao.deleteStepIngredientsByRecipeId(recipeId = existingId)
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
                val capitalized = capitalize(name)
                val newId = recipeDao.insertIngredient(Ingredient(name = capitalized))
                cachedIngredients.add(Ingredient(ingredientId = newId, name = capitalized))
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
        cloudData.recipe.steps.forEachIndexed { index, stepData ->
            val stepId = recipeDao.insertStep(
                RecipeStep(
                    recipeId = localRecipeId,
                    stepNumber = index + 1,
                    title = capitalize(stepData.title),
                    instruction = stepData.description,
                    timerMinutes = stepData.timerMinutes,
                    imagePath = stepData.imagePath
                )
            )
            stepData.ingredients.forEach { stepIngredient ->
                val name = stepIngredient.name.trim()
                if (name.isEmpty()) return@forEach
                val existingIngredient = recipeDao.getAllIngredients().find { normalize(it.name) == normalize(name) }
                val ingredientId = existingIngredient?.ingredientId ?: recipeDao.insertIngredient(Ingredient(name = capitalize(name)))
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
                    name = capitalize(cloudRecipe.name),
                    description = cloudRecipe.description,
                    imagePath = cloudRecipe.imagePath,
                    category = cloudRecipe.category,
                    prepTime = cloudRecipe.prepTime,
                    cuisine = cloudRecipe.cuisine,
                    cuisineCode = cloudRecipe.cuisineCode,
                    calories = cloudRecipe.calories,
                    proteins = cloudRecipe.proteins,
                    fats = cloudRecipe.fats,
                    carbs = cloudRecipe.carbs,
                    totalWeight = cloudRecipe.totalWeight,
                    createdByUserId = userId,
                    isPublicRecipe = isPublic
                )
                val localId = recipeDao.insertRecipe(recipe)
                val recipeData = RecipeData(id = firestoreId, recipe = cloudRecipe)
                saveIngredientsForRecipe(recipeData, localId)
                saveStepsForRecipe(recipeData, localId)
                localId
            } catch (e: Exception) {
                Log.e("RecipeRepository", "Error adding user recipe", e)
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
                    name = capitalize(cloudRecipe.name),
                    description = cloudRecipe.description,
                    imagePath = cloudRecipe.imagePath,
                    category = cloudRecipe.category,
                    prepTime = cloudRecipe.prepTime,
                    cuisine = cloudRecipe.cuisine,
                    cuisineCode = cloudRecipe.cuisineCode,
                    calories = cloudRecipe.calories,
                    proteins = cloudRecipe.proteins,
                    fats = cloudRecipe.fats,
                    carbs = cloudRecipe.carbs,
                    totalWeight = cloudRecipe.totalWeight,
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
            } catch (e: Exception) {
                Log.e("RecipeRepository", "Error updating user recipe", e)
                false
            }
        }
    }

    suspend fun getAllCrossRefs(): List<RecipeIngredientCrossRef> = withContext(Dispatchers.IO) {
        recipeDao.getAllCrossRefs()
    }

    fun getRecipeById(recipeId: Long): Flow<Recipe> = recipeDao.getRecipeById(recipeId)

    suspend fun getFullRecipeForEditing(recipeId: Long): CloudRecipe? = withContext(Dispatchers.IO) {
        try {
            val recipeWithIngredients = recipeDao.getRecipeWithIngredientsByIdSync(recipeId) ?: return@withContext null
            val recipe = recipeWithIngredients.recipe
            val ingredients = recipeWithIngredients.ingredients.map { ing ->
                val crossRef = recipeDao.getCrossRef(recipeId, ing.ingredientId)
                CloudIngredient(ing.name, crossRef?.quantity ?: "", crossRef?.unit ?: "")
            }
            val steps = recipeDao.getStepsByRecipeIdSync(recipeId).map { step ->
                val stepIngs = recipeDao.getStepIngredients(step.stepId)
                val cloudStepIngs = stepIngs.map { si ->
                    val ing = recipeDao.getIngredientById(si.ingredientId)
                    CloudIngredient(ing?.name ?: "", si.quantity, si.unit)
                }
                StepData(step.title, step.instruction, step.timerMinutes, step.imagePath, cloudStepIngs)
            }
            CloudRecipe(recipe.name, recipe.description, recipe.imagePath, recipe.category, recipe.prepTime, 0, recipe.cuisine, recipe.cuisineCode, ingredients, steps, recipe.createdByUserId ?: "", recipe.isPublicRecipe ?: true, recipe.calories, recipe.proteins, recipe.fats, recipe.carbs, recipe.totalWeight)
        } catch (e: Exception) {
            Log.e("RecipeRepository", "Error getting recipe for editing", e)
            null
        }
    }

    suspend fun deleteUserRecipe(recipeId: Long, firestoreId: String?): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (firestoreId != null) { firestoreService.deleteRecipe(firestoreId) }
                recipeDao.deleteRecipe(recipeId)
                true
            } catch (e: Exception) { 
                Log.e("RecipeRepository", "Delete recipe error", e)
                false 
            }
        }
    }

    private fun formatIngredientQuantity(quantity: String, unit: String, portions: Int): Pair<String, String> {
        if ((quantity == "1" && unit.isBlank()) ||
            quantity.lowercase(Locale.ROOT).contains("по вкусу") ||
            unit.lowercase(Locale.ROOT).contains("по вкусу")) {
            return "по вкусу" to ""
        }
        val baseQtyNum = quantity.toDoubleOrNull()
        return if (baseQtyNum != null) {
            val scaledQty = baseQtyNum * portions
            val qtyStr = if (scaledQty % 1.0 == 0.0) scaledQty.toInt().toString()
            else String.format(Locale.US, "%.1f", scaledQty)
            qtyStr to unit
        } else {
            quantity to unit
        }
    }

    fun getScaledIngredientsForRecipe(recipeId: Long, portions: Int): Flow<List<IngredientWithDetails>> {
        val safePortions = portions.coerceIn(1, 10)
        return recipeDao.getRecipeWithIngredientsById(recipeId).map { recipeWithIngredients ->
            recipeWithIngredients.ingredients.map { ingredient ->
                val crossRef = recipeDao.getCrossRef(recipeId, ingredient.ingredientId)
                val (qty, unit) = formatIngredientQuantity(crossRef?.quantity ?: "", crossRef?.unit ?: "", safePortions)
                IngredientWithDetails(ingredient, qty, unit)
            }
        }
    }

    fun getStepsByRecipeId(recipeId: Long): Flow<List<RecipeStep>> = recipeDao.getStepsByRecipeId(recipeId)

    fun getCookingStepsWithIngredients(recipeId: Long, portions: Int): Flow<List<CookingStepWithIngredients>> = flow {
        val safePortions = portions.coerceIn(1, 10)
        recipeDao.getStepsByRecipeId(recipeId).collect { steps ->
            val result = steps.map { step ->
                val stepIngs = recipeDao.getStepIngredients(step.stepId)
                val details = stepIngs.mapNotNull { si ->
                    val ing = recipeDao.getIngredientById(si.ingredientId) ?: return@mapNotNull null
                    val (qty, unit) = formatIngredientQuantity(si.quantity, si.unit, safePortions)
                    IngredientWithDetails(ing, qty, unit)
                }
                CookingStepWithIngredients(step, details)
            }
            emit(result)
        }
    }

    suspend fun addScaledIngredientsToShoppingListExplicit(userId: String, name: String, quantity: Double, unit: String) {
        withContext(Dispatchers.IO) {
            val formattedQty = UnitHelper.formatQuantity(quantity)
            if (formattedQty == "0") return@withContext

            val existing = shoppingListDao.getItemByNameAndUnit(userId, name, unit)
            if (existing == null) {
                shoppingListDao.insertItem(ShoppingListItem(userId = userId, name = name, quantity = formattedQty, unit = unit))
            } else {
                if (formattedQty == "по вкусу" || existing.quantity == "по вкусу") {
                    shoppingListDao.updateItem(existing.copy(quantity = "по вкусу", unit = ""))
                } else {
                    val existingQty = existing.quantity.toDoubleOrNull() ?: 0.0
                    val addQty = quantity
                    val newQuantity = existingQty + addQty
                    val newQuantityStr = UnitHelper.formatQuantity(newQuantity)
                    shoppingListDao.updateItem(existing.copy(quantity = newQuantityStr))
                }
            }
        }
    }

    suspend fun addScaledIngredientsToShoppingList(recipeId: Long, portions: Int, userId: String) {
        val recipeWithIngredients = recipeDao.getRecipeWithIngredientsById(recipeId).firstOrNull() ?: return
        val safePortions = portions.coerceIn(1, 10)
        recipeWithIngredients.ingredients.forEach { ingredient ->
            val crossRef = recipeDao.getCrossRef(recipeId, ingredient.ingredientId)
            val baseQuantity = crossRef?.quantity ?: ""
            val baseUnit = crossRef?.unit ?: ""
            val (scaledQuantity, scaledUnit) = formatIngredientQuantity(baseQuantity, baseUnit, safePortions)
            val existing = shoppingListDao.getItemByNameAndUnit(userId, ingredient.name, scaledUnit)
            if (existing == null) {
                shoppingListDao.insertItem(ShoppingListItem(userId = userId, name = ingredient.name, quantity = scaledQuantity, unit = scaledUnit))
            } else {
                if (scaledQuantity == "по вкусу" || existing.quantity == "по вкусу") {
                    shoppingListDao.updateItem(existing.copy(quantity = "по вкусу", unit = ""))
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

    suspend fun incrementRecipePopularity(recipeId: Long) = recipeDao.incrementPopularity(recipeId)
    suspend fun getAllDbIngredients(): List<Ingredient> = recipeDao.getAllIngredients()
    suspend fun getAllRecipesWithIngredients(): List<RecipeWithIngredients> = recipeDao.getAllRecipesWithIngredients()
    fun getShoppingListItems(userId: String): Flow<List<ShoppingListItem>> = shoppingListDao.getAllItems(userId)
    suspend fun updateShoppingListItemChecked(item: ShoppingListItem, checked: Boolean) = shoppingListDao.updateItem(item.copy(isChecked = checked))
    suspend fun deleteShoppingListItem(item: ShoppingListItem) = shoppingListDao.deleteItem(item)
    suspend fun clearCheckedShoppingListItems(userId: String) = shoppingListDao.deleteCheckedItems(userId)
}