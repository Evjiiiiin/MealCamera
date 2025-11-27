package com.example.mealcamera.ui.home

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.example.mealcamera.MealCameraApplication
import com.example.mealcamera.R
import com.example.mealcamera.databinding.ActivityMainBinding
import com.example.mealcamera.ui.detail.RecipeDetailActivity
import com.example.mealcamera.ui.scan.ScanActivity
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var recipeAdapter: RecipeAdapter
    private val viewModel: MainViewModel by viewModels {
        (application as MealCameraApplication).viewModelFactory
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupGreeting()
        setupRecyclerView()
        setupFilters()
        setupBottomNavigation()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        binding.bottomNavigationView.menu.findItem(R.id.navigation_home).isChecked = true
    }

    private fun setupGreeting() {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val greeting = when (hour) {
            in 6..11 -> "Доброе утро"
            in 12..17 -> "Добрый день"
            in 18..22 -> "Добрый вечер"
            else -> "Доброй ночи"
        }
        binding.greetingTextView.text = greeting
    }

    private fun setupRecyclerView() {
        recipeAdapter = RecipeAdapter { recipe ->
            startActivity(Intent(this, RecipeDetailActivity::class.java).apply {
                putExtra(RecipeDetailActivity.EXTRA_RECIPE_ID, recipe.recipeId)
            })
        }
        binding.recipesRecyclerView.apply {
            layoutManager = GridLayoutManager(this@MainActivity, 2)
            adapter = recipeAdapter
        }
    }

    private fun setupFilters() {
        binding.chipAll.isChecked = true
        viewModel.setCategoryFilter("Все")

        // --- ИСПРАВЛЕНО: Возвращаем старый, но 100% рабочий метод ---
        binding.filterChipGroup.setOnCheckedChangeListener { group, checkedId ->
            val chip = group.findViewById<Chip>(checkedId)
            // Если чип не найден (например, при сбросе), ставим "Все" по умолчанию
            viewModel.setCategoryFilter(chip?.text.toString() ?: "Все")
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    // Мы уже здесь, ничего не делаем
                    true
                }
                R.id.navigation_camera -> {
                    // Запускаем ScanActivity
                    startActivity(Intent(this, ScanActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    })
                    true
                }
                else -> false
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                recipeAdapter.submitList(state.filteredRecipes)
            }
        }
    }
}
