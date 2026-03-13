package com.example.mealcamera.ui.addrecipe

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.mealcamera.MealCameraApplication
import com.example.mealcamera.data.remote.CloudIngredient
import com.example.mealcamera.data.remote.CloudRecipe
import com.example.mealcamera.data.remote.StepData
import com.example.mealcamera.databinding.ActivityAddRecipeBinding
import com.example.mealcamera.util.FirebaseStorageHelper
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.io.InputStream

class AddRecipeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddRecipeBinding
    private val recipeRepository by lazy {
        (application as MealCameraApplication).recipeRepository
    }

    private val storageHelper = FirebaseStorageHelper()
    private var selectedImageUri: Uri? = null
    private var selectedImageBitmap: Bitmap? = null

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            selectedImageUri = it
            // Показываем превью
            binding.ivRecipePreview.setImageURI(it)
            binding.ivRecipePreview.visibility = android.view.View.VISIBLE
            // Преобразуем Uri в Bitmap для загрузки (можно сделать позже)
            selectedImageBitmap = uriToBitmap(it)
        }
    }

    private fun uriToBitmap(uri: Uri): Bitmap? {
        return try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddRecipeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupClickListeners()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Добавить рецепт"
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupClickListeners() {
        // Выбор фото
        binding.btnSelectImage.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        // Добавление ингредиента
        binding.btnAddIngredient.setOnClickListener {
            addIngredientField()
        }

        // Добавление шага
        binding.btnAddStep.setOnClickListener {
            addStepField()
        }

        // Сохранение рецепта
        binding.btnSaveRecipe.setOnClickListener {
            saveRecipe()
        }
    }

    private fun addIngredientField() {
        val ingredientView = layoutInflater.inflate(
            R.layout.item_add_ingredient,
            binding.ingredientsContainer,
            false
        )
        ingredientView.findViewById<android.widget.ImageButton>(R.id.btnRemoveIngredient).setOnClickListener {
            binding.ingredientsContainer.removeView(ingredientView)
        }
        binding.ingredientsContainer.addView(ingredientView)
    }

    private fun addStepField() {
        val stepView = layoutInflater.inflate(
            R.layout.item_add_step,
            binding.stepsContainer,
            false
        )
        stepView.findViewById<android.widget.ImageButton>(R.id.btnRemoveStep).setOnClickListener {
            binding.stepsContainer.removeView(stepView)
        }
        binding.stepsContainer.addView(stepView)
    }

    private fun saveRecipe() {
        val name = binding.etRecipeName.text.toString()
        val description = binding.etDescription.text.toString()

        if (name.isEmpty() || description.isEmpty()) {
            Toast.makeText(this, "Заполните название и описание", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                // Собираем ингредиенты
                val ingredients = mutableListOf<CloudIngredient>()
                for (i in 0 until binding.ingredientsContainer.childCount) {
                    val view = binding.ingredientsContainer.getChildAt(i)
                    val name = view.findViewById<android.widget.EditText>(R.id.etIngredientName).text.toString()
                    val quantity = view.findViewById<android.widget.EditText>(R.id.etIngredientQuantity).text.toString()
                    val unit = view.findViewById<android.widget.EditText>(R.id.etIngredientUnit).text.toString()

                    if (name.isNotEmpty()) {
                        ingredients.add(CloudIngredient(name, quantity, unit))
                    }
                }

                // Собираем шаги
                val steps = mutableListOf<StepData>()
                for (i in 0 until binding.stepsContainer.childCount) {
                    val view = binding.stepsContainer.getChildAt(i)
                    val title = view.findViewById<android.widget.EditText>(R.id.etStepTitle).text.toString()
                    val description = view.findViewById<android.widget.EditText>(R.id.etStepDescription).text.toString()
                    val timerText = view.findViewById<android.widget.EditText>(R.id.etStepTimer).text.toString()
                    val timer = timerText.toIntOrNull() ?: 0

                    if (title.isNotEmpty() && description.isNotEmpty()) {
                        steps.add(StepData(
                            title = title,
                            description = description,
                            timerMinutes = timer,
                            ingredients = emptyList()
                        ))
                    }
                }

                // Время подготовки и приготовления (собираем)
                val prepHours = binding.etPrepHours.text.toString().toIntOrNull() ?: 0
                val prepMinutes = binding.etPrepMinutes.text.toString().toIntOrNull() ?: 0
                val cookHours = binding.etCookHours.text.toString().toIntOrNull() ?: 0
                val cookMinutes = binding.etCookMinutes.text.toString().toIntOrNull() ?: 0
                val totalMinutes = prepHours*60 + prepMinutes + cookHours*60 + cookMinutes
                val finalPrepTime = if (totalMinutes > 0) {
                    if (totalMinutes >= 60) "${totalMinutes/60} ч ${totalMinutes%60} мин" else "$totalMinutes мин"
                } else {
                    "0 мин"
                }

                // Категория (можно оставить пустой или взять из какого-то поля, но в макете нет поля для категории, поэтому ставим "Другое")
                val category = "Другое"

                // Загружаем изображение, если выбрано
                var imagePath = ""
                if (selectedImageBitmap != null) {
                    imagePath = storageHelper.uploadRecipeImage(selectedImageBitmap!!) ?: ""
                }

                val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"
                val isPublic = binding.switchPublic.isChecked

                val recipe = CloudRecipe(
                    name = name,
                    description = description,
                    imagePath = imagePath,
                    category = category,
                    prepTime = finalPrepTime,
                    cuisine = "Разное",
                    cuisineCode = "XX",
                    ingredients = ingredients,
                    steps = steps,
                    authorId = userId,
                    isPublic = isPublic
                )

                val recipeId = recipeRepository.addUserRecipe(recipe, userId, isPublic)

                if (recipeId != null) {
                    Toast.makeText(this@AddRecipeActivity, "Рецепт успешно добавлен!", Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    Toast.makeText(this@AddRecipeActivity, "Ошибка при сохранении", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@AddRecipeActivity, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}