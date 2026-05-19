package com.example.mealcamera.ui.detail

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.mealcamera.MealCameraApplication
import com.example.mealcamera.R
import com.example.mealcamera.data.model.Recipe
import com.example.mealcamera.databinding.ActivityRecipeDetailBinding
import com.example.mealcamera.ui.cooking.CookingActivity
import com.google.android.material.appbar.AppBarLayout
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlin.math.abs

class RecipeDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_RECIPE_ID = "recipe_id"
    }

    private lateinit var binding: ActivityRecipeDetailBinding
    private lateinit var ingredientsAdapter: IngredientsAdapter
    private var currentRecipe: Recipe? = null
    private var isDisplayingPer100g = false

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

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finishWithAnimation()
            }
        })
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = ""
        binding.toolbar.setNavigationOnClickListener { finishWithAnimation() }

        binding.appBarLayout.addOnOffsetChangedListener(AppBarLayout.OnOffsetChangedListener { appBarLayout, verticalOffset ->
            val totalScrollRange = appBarLayout.totalScrollRange
            if (totalScrollRange == 0) return@OnOffsetChangedListener
            val percentage = abs(verticalOffset).toFloat() / totalScrollRange.toFloat()

            if (percentage >= 0.8f) {
                binding.tvToolbarTitle.alpha = (percentage - 0.8f) * 5f
            } else {
                binding.tvToolbarTitle.alpha = 0f
            }
        })
    }

    private fun setupIngredientsList() {
        ingredientsAdapter = IngredientsAdapter()
        binding.rvIngredients.layoutManager = LinearLayoutManager(this)
        binding.rvIngredients.adapter = ingredientsAdapter
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.recipe.collect { recipe ->
                if (recipe != null) {
                    currentRecipe = recipe
                    updateKbjuDisplay()
                    bindRecipeInfo(recipe)
                }
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
                updateKbjuDisplay()
            }
        }

        lifecycleScope.launch {
            viewModel.isFavorite.collect { isFavorite ->
                val icon = if (isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_outlined
                binding.btnAddToFavorites.setImageResource(icon)
            }
        }
    }

    private fun bindRecipeInfo(recipe: Recipe) {
        binding.tvRecipeName.text = recipe.name
        binding.tvToolbarTitle.text = recipe.name
        binding.tvDescription.text = recipe.description

        // Отображение времени приготовления
        if (recipe.prepTime.isNotBlank()) {
            binding.tvPrepTime.text = recipe.prepTime
            binding.tvPrepTime.visibility = View.VISIBLE
        } else {
            binding.tvPrepTime.visibility = View.GONE
        }

        Glide.with(this)
            .load(recipe.imagePath)
            .placeholder(R.drawable.ic_recipe_placeholder)
            .error(R.drawable.ic_recipe_placeholder)
            .into(binding.ivRecipe)
    }

    private fun updateKbjuDisplay() {
        val recipe = currentRecipe ?: return
        val portions = viewModel.portions.value
        val basePortions = recipe.basePortions.coerceAtLeast(1)
        val scale = portions.toDouble() / basePortions.toDouble()
        val currentTotalWeight = (recipe.totalWeight * scale).toInt()

        if (isDisplayingPer100g) {
            updateToggleVisuals(is100gActive = true)
            if (recipe.totalWeight > 0) {
                val per100gScale = 100.0 / recipe.totalWeight.toDouble()
                binding.tvCalories.text = (recipe.calories * per100gScale).toInt().toString()
                binding.tvProteins.text = String.format(java.util.Locale.getDefault(), "%.1f", recipe.proteins * per100gScale)
                binding.tvFats.text = String.format(java.util.Locale.getDefault(), "%.1f", recipe.fats * per100gScale)
                binding.tvCarbs.text = String.format(java.util.Locale.getDefault(), "%.1f", recipe.carbs * per100gScale)
                binding.tvKbjuSubtitle.text = "Расчет на 100 г"
            } else {
                binding.tvCalories.text = recipe.calories.toString()
                binding.tvProteins.text = String.format(java.util.Locale.getDefault(), "%.1f", recipe.proteins)
                binding.tvFats.text = String.format(java.util.Locale.getDefault(), "%.1f", recipe.fats)
                binding.tvCarbs.text = String.format(java.util.Locale.getDefault(), "%.1f", recipe.carbs)
                binding.tvKbjuSubtitle.text = "Вес не указан"
            }
        } else {
            updateToggleVisuals(is100gActive = false)
            binding.tvCalories.text = (recipe.calories * scale).toInt().toString()
            binding.tvProteins.text = String.format(java.util.Locale.getDefault(), "%.1f", recipe.proteins * scale)
            binding.tvFats.text = String.format(java.util.Locale.getDefault(), "%.1f", recipe.fats * scale)
            binding.tvCarbs.text = String.format(java.util.Locale.getDefault(), "%.1f", recipe.carbs * scale)
            binding.tvKbjuSubtitle.text = if (recipe.totalWeight > 0) "Вес: $currentTotalWeight г" else "Расчет на $portions порц."
        }
    }

    private fun updateToggleVisuals(is100gActive: Boolean) {
        val activeColor = ContextCompat.getColorStateList(this, R.color.surface_card)
        if (is100gActive) {
            binding.btn100g.setBackgroundResource(R.drawable.rounded_search_bg)
            binding.btn100g.backgroundTintList = activeColor
            binding.btn100g.setTextColor(ContextCompat.getColor(this, R.color.text_main))
            binding.btn100g.paint.isFakeBoldText = true

            binding.btnWholeProduct.setBackgroundResource(0)
            binding.btnWholeProduct.backgroundTintList = null
            binding.btnWholeProduct.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            binding.btnWholeProduct.paint.isFakeBoldText = false
        } else {
            binding.btnWholeProduct.setBackgroundResource(R.drawable.rounded_search_bg)
            binding.btnWholeProduct.backgroundTintList = activeColor
            binding.btnWholeProduct.setTextColor(ContextCompat.getColor(this, R.color.text_main))
            binding.btnWholeProduct.paint.isFakeBoldText = true

            binding.btn100g.setBackgroundResource(0)
            binding.btn100g.backgroundTintList = null
            binding.btn100g.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            binding.btn100g.paint.isFakeBoldText = false
        }
    }

    private fun setupButtons() {
        binding.btnWholeProduct.setOnClickListener {
            isDisplayingPer100g = false
            updateKbjuDisplay()
        }

        binding.btn100g.setOnClickListener {
            isDisplayingPer100g = true
            updateKbjuDisplay()
        }

        binding.btnStartCooking.setOnClickListener {
            val recipeId = currentRecipe?.recipeId ?: -1L
            val recipeName = currentRecipe?.name ?: ""
            val portions = viewModel.portions.value

            val intent = Intent(this, CookingActivity::class.java).apply {
                putExtra(CookingActivity.EXTRA_RECIPE_ID, recipeId)
                putExtra(CookingActivity.EXTRA_RECIPE_NAME, recipeName)
                putExtra(CookingActivity.EXTRA_PORTIONS, portions)
            }
            startActivity(intent)
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        binding.btnAddToFavorites.setOnClickListener {
            currentRecipe?.let { viewModel.toggleFavorite(it.recipeId) }
        }

        binding.btnAddToShoppingList.setOnClickListener {
            val recipeId = currentRecipe?.recipeId ?: return@setOnClickListener
            val portions = viewModel.portions.value
            val userId = currentUserId ?: "guest"
            lifecycleScope.launch {
                repository.addScaledIngredientsToShoppingList(recipeId, portions, userId)
                Toast.makeText(this@RecipeDetailActivity, "✅ Ингредиенты добавлены в список покупок", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnMinusPortion.setOnClickListener {
            val current = viewModel.portions.value
            if (current > 1) viewModel.setPortions(current - 1)
        }

        binding.btnPlusPortion.setOnClickListener {
            val current = viewModel.portions.value
            if (current < 99) viewModel.setPortions(current + 1)
        }
    }

    private fun finishWithAnimation() {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}