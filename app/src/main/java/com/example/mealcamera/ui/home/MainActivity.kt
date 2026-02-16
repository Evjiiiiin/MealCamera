package com.example.mealcamera.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.example.mealcamera.MealCameraApplication
import com.example.mealcamera.R
import com.example.mealcamera.databinding.ActivityMainBinding
import com.example.mealcamera.ui.detail.RecipeDetailActivity
import com.example.mealcamera.ui.profile.ProfileFragment
import com.example.mealcamera.ui.scan.ScanActivity
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
        setupSearch()
        setupFilters()
        setupBottomNavigation()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        binding.bottomNavigationView.menu.findItem(R.id.navigation_home).isChecked = true
        binding.mainContentLayer.visibility = View.VISIBLE
    }

    private fun setupGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
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

    private fun setupSearch() {
        binding.searchEditText.doAfterTextChanged { text ->
            viewModel.setSearchQuery(text?.toString().orEmpty())
        }
    }

    private fun setupFilters() {
        binding.chipAll.isChecked = true
        viewModel.setCategoryFilter("Все")

        binding.filterChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val category = when (checkedIds.firstOrNull()) {
                R.id.chipBreakfast -> "Завтрак"
                R.id.chipLunch -> "Обед"
                R.id.chipDinner -> "Ужин"
                R.id.chipAll -> "Все"
                else -> "Все"
            }
            viewModel.setCategoryFilter(category)
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    binding.mainContentLayer.visibility = View.VISIBLE
                    supportFragmentManager.findFragmentById(R.id.fragment_container)?.let { fragment ->
                        supportFragmentManager.beginTransaction().remove(fragment).commit()
                    }
                    true
                }

                R.id.navigation_camera -> {
                    startActivity(Intent(this, ScanActivity::class.java))
                    false
                }

                R.id.navigation_profile -> {
                    binding.mainContentLayer.visibility = View.GONE
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, ProfileFragment())
                        .commit()
                    true
                }

                else -> false
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                recipeAdapter.submitList(state.recipes)
            }
        }
    }
}
