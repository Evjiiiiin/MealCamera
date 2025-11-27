package com.example.mealcamera.ui.result

import android.content.Intent
import android.os.Bundle
import android.view.View // <-- ИСПРАВЛЕНО: Правильный импорт для View.VISIBLE/GONE
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.mealcamera.MealCameraApplication
import com.example.mealcamera.R
import com.example.mealcamera.data.model.EditableIngredient
import com.example.mealcamera.data.model.RecipeResult
import com.example.mealcamera.databinding.ActivityResultBinding // <-- ИСПРАВЛЕНО: Используем конкретный Binding
import com.example.mealcamera.ui.SharedViewModel
import com.example.mealcamera.ui.detail.RecipeDetailActivity
import com.example.mealcamera.ui.home.MainActivity
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResultBinding // <-- ИСПРАВЛЕНО: Правильный тип

    private val viewModel: ResultViewModel by viewModels {
        (application as MealCameraApplication).viewModelFactory
    }

    private val sharedViewModel: SharedViewModel by lazy {
        ViewModelProvider(
            (application as MealCameraApplication),
            (application as MealCameraApplication).viewModelFactory
        )[SharedViewModel::class.java]
    }

    private lateinit var editableAdapter: EditableIngredientAdapter
    private lateinit var perfectAdapter: ResultAdapter
    private lateinit var oneMissingAdapter: ResultAdapter
    private lateinit var twoMissingAdapter: ResultAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // --- ИСПРАВЛЕНО: Правильная инициализация биндинга ---
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupBottomNavigation()
        setupRecyclerViews()
        observeViewModels()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
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
                    finish() // Возвращаемся на ScanActivity
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
    }

    private fun observeViewModels() {
        // Подписываемся на SharedViewModel для получения списка ингредиентов
        lifecycleScope.launch {
            sharedViewModel.scannedIngredients.collectLatest { ingredients ->
                if (ingredients.isNotEmpty()) {
                    val editableIngredients = ingredients.map {
                        EditableIngredient(id = it.timestamp, name = it.name, quantity = it.quantity, unit = it.unit)
                    }
                    viewModel.setInitialIngredients(editableIngredients)
                } else {
                    // Если в SharedViewModel не осталось ингредиентов (все удалили),
                    // то закрываем этот экран и возвращаемся на камеру.
                    finish()
                }
            }
        }

        // Остальная логика остается привязанной к локальному ResultViewModel
        lifecycleScope.launch {
            viewModel.editableIngredients.collectLatest { ingredients ->
                if (ingredients.isNotEmpty()) {
                    editableAdapter = EditableIngredientAdapter(ingredients)
                    binding.editableIngredientsRecyclerView.adapter = editableAdapter

                    binding.btnApplyFilters.setOnClickListener {
                        val updatedIngredients = editableAdapter.getEditedIngredients()
                        viewModel.findRecipes(updatedIngredients)
                    }
                    binding.btnApplyFilters.performClick()
                }
            }
        }

        observeResults(viewModel.perfectRecipes, binding.tvPerfectMatchHeader, perfectAdapter)
        observeResults(viewModel.oneMissingRecipes, binding.tvOneMissingHeader, oneMissingAdapter)
        observeResults(viewModel.twoMissingRecipes, binding.tvTwoMissingHeader, twoMissingAdapter)

        lifecycleScope.launch {
            combine(
                viewModel.perfectRecipes,
                viewModel.oneMissingRecipes,
                viewModel.twoMissingRecipes
            ) { p, o, t ->
                p.isEmpty() && o.isEmpty() && t.isEmpty()
            }.collect { isEmpty ->
                binding.resultsContainer.visibility = if (!isEmpty) View.VISIBLE else View.GONE
                binding.tvNoResults.visibility = if (isEmpty) View.VISIBLE else View.GONE
            }
        }
    }

    private fun observeResults(flow: StateFlow<List<RecipeResult>>, header: TextView, adapter: ResultAdapter) {
        lifecycleScope.launch {
            flow.collect { recipes ->
                header.visibility = if (recipes.isNotEmpty()) View.VISIBLE else View.GONE
                adapter.submitList(recipes)
            }
        }
    }

    private fun navigateToDetail(recipeId: Long) {
        startActivity(Intent(this, RecipeDetailActivity::class.java).apply {
            putExtra(RecipeDetailActivity.EXTRA_RECIPE_ID, recipeId)
        })
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
