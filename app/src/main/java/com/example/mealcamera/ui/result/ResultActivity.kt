package com.example.mealcamera.ui.result

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.mealcamera.MealCameraApplication
import com.example.mealcamera.R
import com.example.mealcamera.data.model.EditableIngredient
import com.example.mealcamera.data.util.UnitHelper // <-- Импорт остается
import com.example.mealcamera.databinding.ActivityResultBinding
import com.example.mealcamera.ui.detail.RecipeDetailActivity
import com.example.mealcamera.ui.home.MainActivity
import com.example.mealcamera.ui.scan.ScanActivity
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResultBinding
    private val viewModel: ResultViewModel by viewModels {
        (application as MealCameraApplication).viewModelFactory
    }

    private lateinit var perfectAdapter: ResultAdapter
    private lateinit var oneMissingAdapter: ResultAdapter
    private lateinit var twoMissingAdapter: ResultAdapter

    private var editableIngredientMutableList: MutableList<EditableIngredient> = mutableListOf()
    private lateinit var editableAdapter: EditableIngredientAdapter


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val detectedNames = intent.getStringArrayListExtra(ScanActivity.EXTRA_DETECTED_INGREDIENTS) ?: arrayListOf()

        val initialList = detectedNames.mapIndexed { index, name ->
            EditableIngredient(
                id = index.toLong(),
                name = name,
                quantity = "",
                // Автоматически предлагаем единицу измерения при создании, используя ваш UnitHelper
                unit = UnitHelper.getUnitForIngredient(name)
            )
        }.toMutableList()

        viewModel.setInitialIngredients(initialList)

        setupToolbar()
        setupBottomNavigation()
        setupRecyclerViews()
        setupPortionControls()
        observeViewModels()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigationView.selectedItemId = R.id.navigation_camera
        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    startActivity(Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    })
                    true
                }
                R.id.navigation_camera -> {
                    finish()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupRecyclerViews() {
        perfectAdapter = ResultAdapter { navigateToDetail(it.recipe.recipeId) }
        oneMissingAdapter = ResultAdapter { navigateToDetail(it.recipe.recipeId) }
        twoMissingAdapter = ResultAdapter { navigateToDetail(it.recipe.recipeId) }

        binding.perfectMatchRecyclerView.adapter = perfectAdapter
        binding.oneMissingRecyclerView.adapter = oneMissingAdapter
        binding.twoMissingRecyclerView.adapter = twoMissingAdapter

        editableAdapter = EditableIngredientAdapter(editableIngredientMutableList) { ingredient ->
            editableIngredientMutableList.removeAll { it.id == ingredient.id }
            editableAdapter.updateIngredients(editableIngredientMutableList)
            viewModel.findRecipes(editableAdapter.getEditedIngredients().map { it.copy() })
        }
        binding.editableIngredientsRecyclerView.adapter = editableAdapter
    }

    private fun setupPortionControls() {
        binding.btnPlusPortion.setOnClickListener {
            viewModel.setPortions(viewModel.portions.value + 1)
        }

        binding.btnMinusPortion.setOnClickListener {
            viewModel.setPortions(viewModel.portions.value - 1)
        }
    }

    private fun observeViewModels() {
        lifecycleScope.launch {
            viewModel.editableIngredients.collect { list ->
                editableIngredientMutableList.clear()
                editableIngredientMutableList.addAll(list)
                editableAdapter.updateIngredients(editableIngredientMutableList)

                if (list.isNotEmpty()) {
                    binding.btnApplyFilters.setOnClickListener {
                        val updated = editableAdapter.getEditedIngredients().map { it.copy() }
                        viewModel.findRecipes(updated)
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.portions.collect { portionCount ->
                binding.tvPortions.text = "Порции: $portionCount"
                binding.btnMinusPortion.isEnabled = portionCount > 1
                binding.btnPlusPortion.isEnabled = portionCount < 10
            }
        }

        lifecycleScope.launch {
            combine(
                viewModel.perfectRecipes,
                viewModel.oneMissingRecipes,
                viewModel.twoMissingRecipes
            ) { p, o, t -> Triple(p, o, t) }.collect { (p, o, t) ->
                perfectAdapter.submitList(p)
                oneMissingAdapter.submitList(o)
                twoMissingAdapter.submitList(t)

                binding.tvPerfectMatchHeader.visibility = if (p.isNotEmpty()) View.VISIBLE else View.GONE
                binding.tvOneMissingHeader.visibility = if (o.isNotEmpty()) View.VISIBLE else View.GONE
                binding.tvTwoMissingHeader.visibility = if (t.isNotEmpty()) View.VISIBLE else View.GONE

                val isEmpty = p.isEmpty() && o.isEmpty() && t.isEmpty()
                binding.resultsContainer.visibility = if (!isEmpty) View.VISIBLE else View.GONE
                binding.tvNoResults.visibility = if (isEmpty) View.VISIBLE else View.GONE
            }
        }
    }

    private fun navigateToDetail(recipeId: Long) {
        startActivity(Intent(this, RecipeDetailActivity::class.java).apply {
            putExtra(RecipeDetailActivity.EXTRA_RECIPE_ID, recipeId)
        })
    }
}
