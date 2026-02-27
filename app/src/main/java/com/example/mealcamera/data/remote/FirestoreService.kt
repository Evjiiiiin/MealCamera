package com.example.mealcamera.data.remote

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FirestoreService {

    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val recipesCollection = db.collection("recipes")

    suspend fun getAllRecipes(): List<RecipeData> {
        return try {
            val snapshot = recipesCollection.get().await()
            Log.d("FirestoreService", "Loaded ${snapshot.documents.size} documents")

            snapshot.documents.mapNotNull { document ->
                try {
                    // Ручное преобразование для большей надежности
                    val name = document.getString("name") ?: ""
                    val description = document.getString("description") ?: ""
                    val category = document.getString("category") ?: ""
                    val prepTime = document.getString("prepTime") ?: ""

                    // Получаем ингредиенты как список
                    val ingredientsList = mutableListOf<CloudIngredient>()
                    val ingredientsArray = document.get("ingredients") as? List<Map<String, String>>
                    ingredientsArray?.forEach { ingMap ->
                        ingredientsList.add(
                            CloudIngredient(
                                name = ingMap["name"] ?: "",
                                quantity = ingMap["quantity"] ?: "",
                                unit = ingMap["unit"] ?: ""
                            )
                        )
                    }

                    // Получаем шаги как список
                    val stepsList = mutableListOf<String>()
                    val stepsArray = document.get("steps") as? List<String>
                    stepsArray?.forEach { step ->
                        stepsList.add(step)
                    }

                    val cloudRecipe = CloudRecipe(
                        name = name,
                        description = description,
                        imagePath = document.getString("imagePath") ?: "",
                        category = category,
                        prepTime = prepTime,
                        popularityScore = document.getLong("popularityScore")?.toInt() ?: 0,
                        ingredients = ingredientsList,
                        steps = stepsList
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
    val ingredients: List<CloudIngredient> = emptyList(),
    val steps: List<String> = emptyList()
)

data class CloudIngredient(
    val name: String = "",
    val quantity: String = "",
    val unit: String = ""
)