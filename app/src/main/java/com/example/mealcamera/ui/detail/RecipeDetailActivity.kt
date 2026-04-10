package com.example.mealcamera.ui.detail

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.mealcamera.MealCameraApplication
import com.example.mealcamera.R
import com.example.mealcamera.databinding.ActivityRecipeDetailBinding
import com.example.mealcamera.ui.cooking.CookingActivity
import com.google.firebase.auth.FirebaseAuth
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

    private val currentUserId: String?
        get() = FirebaseAuth.getInstance().currentUser?.uid

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
        observeViewModel()
        setupButtons()

        viewModel.loadRecipe(recipeId)
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupIngredientsList() {
        ingredientsAdapter = IngredientsAdapter()
        binding.rvIngredients.layoutManager = LinearLayoutManager(this)
        binding.rvIngredients.adapter = ingredientsAdapter
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.recipe.collect { recipe ->
                if (recipe != null) bindRecipe(recipe)
            }
        }

        lifecycleScope.launch {
            viewModel.ingredients.collect { ingredients ->
                ingredientsAdapter.submitList(ingredients)
            }
        }

        lifecycleScope.launch {
            viewModel.portions.collect { portions ->
                binding.tvPortionsValue.text = portions.toString()
            }
        }

        lifecycleScope.launch {
            viewModel.isFavorite.collect { isFavorite ->
                val icon = if (isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_outline
                binding.btnAddToFavorites.setImageResource(icon)
            }
        }
    }

    private fun bindRecipe(recipe: com.example.mealcamera.data.model.Recipe) {
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
            val portions = viewModel.portions.value

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
            val portions = viewModel.portions.value
            val userId = currentUserId ?: "guest"
            lifecycleScope.launch {
                repository.addScaledIngredientsToShoppingList(recipeId, portions, userId)
                Toast.makeText(
                    this@RecipeDetailActivity,
                    "Ингредиенты добавлены в список покупок с учетом $portions порций",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        binding.btnMinusPortion.setOnClickListener {
            val current = viewModel.portions.value
            if (current > 1) viewModel.setPortions(current - 1)
        }

        binding.btnPlusPortion.setOnClickListener {
            val current = viewModel.portions.value
            if (current < 10) viewModel.setPortions(current + 1)
        }
    }
}