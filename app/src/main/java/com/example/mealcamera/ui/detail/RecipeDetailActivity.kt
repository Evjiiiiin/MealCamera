package com.example.mealcamera.ui.detail

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.mealcamera.MealCameraApplication
import com.example.mealcamera.R
import com.example.mealcamera.databinding.ActivityRecipeDetailBinding
import com.example.mealcamera.ViewModelFactory
import kotlinx.coroutines.launch

class RecipeDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRecipeDetailBinding
    private val viewModel: RecipeDetailViewModel by viewModels {
        (application as MealCameraApplication).viewModelFactory
    }

    companion object {
        const val EXTRA_RECIPE_ID = "recipe_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecipeDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val recipeId = intent.getLongExtra(EXTRA_RECIPE_ID, -1)
        if (recipeId != -1L) {
            viewModel.loadRecipe(recipeId)
            observeData()
        } else {
            Toast.makeText(this, "Ошибка: неверный ID рецепта", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun observeData() {
        lifecycleScope.launch {
            viewModel.recipe.collect { recipe ->
                recipe?.let {
                    binding.tvRecipeName.text = it.name
                    binding.tvDescription.text = it.description
                    binding.tvPrepTime.text = it.prepTime
                    binding.tvCategory.text = it.category
                    Glide.with(this@RecipeDetailActivity)
                        .load(it.imagePath)
                        .placeholder(R.drawable.ic_recipe_placeholder)
                        .into(binding.ivRecipeImage)
                }
            }
        }

        lifecycleScope.launch {
            viewModel.ingredients.collect { ingredients ->
                val ingredientsText = ingredients.joinToString("\n") { detail ->
                    "• ${detail.ingredient.name}: ${detail.quantity} ${detail.unit}"
                }
                binding.tvIngredients.text = ingredientsText
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
