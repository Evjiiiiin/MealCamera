package com.example.mealcamera.ui.detail

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.mealcamera.MealCameraApplication
import com.example.mealcamera.R
import com.example.mealcamera.data.util.UnitHelper
import com.example.mealcamera.databinding.ActivityRecipeDetailBinding
import com.example.mealcamera.ui.cooking.CookingActivity
import kotlinx.coroutines.flow.combine
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

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            setResult(RESULT_OK)
            finish()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                setResult(RESULT_OK)
                finish()
            }
        })

        val recipeId = intent.getLongExtra(EXTRA_RECIPE_ID, -1L)
        if (recipeId != -1L) {
            viewModel.loadRecipe(recipeId)
        }

        setupListeners()
        observeData()
    }

    private fun setupListeners() {
        binding.btnPlusPortion.setOnClickListener {
            viewModel.setPortions(viewModel.portions.value + 1)
        }
        binding.btnMinusPortion.setOnClickListener {
            viewModel.setPortions(viewModel.portions.value - 1)
        }

        binding.btnFavorite.setOnClickListener {
            viewModel.toggleFavorite()
        }

        binding.btnStartCooking.setOnClickListener {
            val recipe = viewModel.recipe.value ?: return@setOnClickListener
            val intent = Intent(this, CookingActivity::class.java).apply {
                putExtra(CookingActivity.EXTRA_RECIPE_ID, recipe.recipeId)
                putExtra(CookingActivity.EXTRA_RECIPE_NAME, recipe.name)
            }
            startActivity(intent)
        }
    }

    private fun observeData() {
        lifecycleScope.launch {
            viewModel.recipe.collect { recipe ->
                recipe?.let {
                    binding.tvRecipeName.text = it.name
                    binding.tvDescription.text = it.description
                    Glide.with(this@RecipeDetailActivity)
                        .load(it.imagePath)
                        .into(binding.ivRecipeImage)
                }
            }
        }

        lifecycleScope.launch {
            viewModel.isFavorite.collect { isFavorite ->
                val icon = if (isFavorite) {
                    ContextCompat.getDrawable(this@RecipeDetailActivity, R.drawable.ic_favorite_filled)
                } else {
                    ContextCompat.getDrawable(this@RecipeDetailActivity, R.drawable.ic_favorite_outline)
                }
                binding.btnFavorite.setImageDrawable(icon)
            }
        }

        lifecycleScope.launch {
            combine(viewModel.ingredients, viewModel.portions) { ingredients, portionCount ->
                ingredients to portionCount
            }.collect { (list, count) ->
                binding.tvPortionCount.text = count.toString()

                val sb = StringBuilder()
                list.forEach { item ->
                    val baseQty = item.quantity.toDoubleOrNull() ?: 1.0
                    val total = baseQty * count
                    val formattedTotal = UnitHelper.formatQuantity(total)

                    val unit = item.unit.ifBlank {
                        UnitHelper.getDefaultUnit(item.ingredient.name)
                    }

                    sb.append("• ${item.ingredient.name}: $formattedTotal $unit\n")
                }
                binding.tvIngredients.text = sb.toString()
            }
        }

        lifecycleScope.launch {
            viewModel.steps.collect { steps ->
                binding.tvStepsContent.text =
                    if (steps.isEmpty()) "Шаги приготовления отсутствуют"
                    else steps.joinToString("\n\n") { "${it.stepNumber}. ${it.title}\n${it.instruction}" }
            }
        }
    }
}