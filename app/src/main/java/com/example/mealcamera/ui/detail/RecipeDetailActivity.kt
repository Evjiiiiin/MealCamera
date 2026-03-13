package com.example.mealcamera.ui.detail

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.mealcamera.MealCameraApplication
import com.example.mealcamera.R
import com.example.mealcamera.data.model.Recipe
import com.example.mealcamera.databinding.ActivityRecipeDetailBinding
import com.example.mealcamera.ui.cooking.CookingActivity
import kotlinx.coroutines.launch

class RecipeDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_RECIPE_ID = "recipe_id"
    }

    private lateinit var binding: ActivityRecipeDetailBinding
    private lateinit var ingredientsAdapter: IngredientsAdapter

    private val viewModel: RecipeDetailViewModel by lazy {
        ViewModelProvider(this, (application as MealCameraApplication).viewModelFactory)
            .get(RecipeDetailViewModel::class.java)
    }

    private val repository by lazy {
        (application as MealCameraApplication).recipeRepository
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecipeDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val recipeId = intent.getLongExtra(EXTRA_RECIPE_ID, -1L)
        if (recipeId == -1L) {
            finish()
            return
        }

        setupToolbar()
        setupIngredientsList()
        observeRecipe(recipeId)
        observeIngredients(recipeId)
        setupButtons()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbarDetail)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbarDetail.setNavigationOnClickListener { finish() }
    }

    private fun setupIngredientsList() {
        ingredientsAdapter = IngredientsAdapter()
        binding.rvIngredients.adapter = ingredientsAdapter
    }

    private fun observeRecipe(recipeId: Long) {
        lifecycleScope.launch {
            viewModel.getRecipeById(recipeId).collect { recipe ->
                bindRecipe(recipe)
            }
        }
    }

    private fun observeIngredients(recipeId: Long) {
        lifecycleScope.launch {
            viewModel.getIngredientsForRecipe(recipeId).collect { ingredients ->
                ingredientsAdapter.submitList(ingredients)
            }
        }
    }

    private fun bindRecipe(recipe: Recipe) {
        supportActionBar?.title = recipe.name
        binding.tvRecipeName.text = recipe.name
        binding.tvDescription.text = recipe.description
        binding.tvPrepTime.text = "⏱ ${recipe.prepTime}"

        Glide.with(this)
            .load(recipe.imagePath)
            .placeholder(R.drawable.ic_recipe_placeholder)
            .error(R.drawable.ic_recipe_placeholder)
            .into(binding.ivRecipe)
    }

    private fun setupButtons() {
        binding.btnStartCooking.setOnClickListener {
            val recipeId = intent.getLongExtra(EXTRA_RECIPE_ID, -1L)
            val recipeName = binding.tvRecipeName.text.toString()
            val portions = binding.sliderPortions.value.toInt()

            val intent = Intent(this, CookingActivity::class.java).apply {
                putExtra(CookingActivity.EXTRA_RECIPE_ID, recipeId)
                putExtra(CookingActivity.EXTRA_RECIPE_NAME, recipeName)
                putExtra(CookingActivity.EXTRA_PORTIONS, portions)
            }
            startActivity(intent)
        }

        binding.btnAddToFavorites.setOnClickListener {
            val recipeId = intent.getLongExtra(EXTRA_RECIPE_ID, -1L)
            viewModel.toggleFavorite(recipeId)
        }

        binding.btnAddToShoppingList.setOnClickListener {
            val recipeId = intent.getLongExtra(EXTRA_RECIPE_ID, -1L)
            lifecycleScope.launch {
                val recipe = viewModel.getRecipeByIdOnce(recipeId) ?: return@launch
                repository.addIngredientsToShoppingList(recipe.recipeId)
                Toast.makeText(
                    this@RecipeDetailActivity,
                    "Ингредиенты добавлены в список покупок",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        binding.sliderPortions.addOnChangeListener { _, value, _ ->
            binding.tvPortionsValue.text = "${value.toInt()} порц."
        }
    }
}