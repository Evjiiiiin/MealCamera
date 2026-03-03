package com.example.mealcamera.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
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
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var recipeAdapter: RecipeAdapter

    private val viewModel: MainViewModel by viewModels {
        (application as MealCameraApplication).viewModelFactory
    }

    private val favoriteRepository by lazy {
        (application as MealCameraApplication).favoriteRepository
    }

    private val sharedViewModel by lazy {
        (application as MealCameraApplication).sharedViewModel
    }

    private var isProfileVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupGreeting()
        setupRecyclerView()
        setupSwipeRefresh()
        setupSearch()
        setupFilters()
        setupBottomNavigation()
        observeViewModel()
        observeFavorites()
        observeFavoriteChanges()

        if (intent.getBooleanExtra("open_profile", false)) {
            showProfile()
        }
    }

    override fun onResume() {
        super.onResume()
        binding.bottomNavigationView.menu.findItem(R.id.navigation_home).isChecked = true
    }

    fun updateNavigationSelection(itemId: Int) {
        binding.bottomNavigationView.menu.findItem(itemId).isChecked = true
    }

    private fun showProfile() {
        binding.bottomNavigationView.selectedItemId = R.id.navigation_profile
        binding.mainContentLayer.visibility = View.GONE
        if (supportFragmentManager.findFragmentById(R.id.fragment_container) == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, ProfileFragment())
                .commit()
        }
        isProfileVisible = true
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
        recipeAdapter = RecipeAdapter(
            onItemClick = { recipe ->
                startActivity(Intent(this, RecipeDetailActivity::class.java).apply {
                    putExtra(RecipeDetailActivity.EXTRA_RECIPE_ID, recipe.recipeId)
                })
            },
            onFavoriteClick = { recipe, isFavorite ->
                lifecycleScope.launch {
                    favoriteRepository.toggleFavorite(recipe)
                    recipeAdapter.updateFavoriteStatus(recipe.recipeId, isFavorite)
                }
            }
        )
        binding.recipesRecyclerView.apply {
            layoutManager = GridLayoutManager(this@MainActivity, 2)
            adapter = recipeAdapter
        }
    }

    private fun observeFavoriteChanges() {
        lifecycleScope.launch {
            sharedViewModel.favoriteChanged.collect { (recipeId, isFavorite) ->
                recipeAdapter.updateFavoriteStatus(recipeId, isFavorite)
            }
        }
    }

    fun refreshFavorites() {
        lifecycleScope.launch {
            val favoriteIds = favoriteRepository.getFavoriteIds()
            recipeAdapter.setFavoriteIds(favoriteIds)
        }
    }

    private fun observeFavorites() {
        lifecycleScope.launch {
            val favoriteIds = favoriteRepository.getFavoriteIds()
            recipeAdapter.setFavoriteIds(favoriteIds)
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.refreshRecipes()
        }

        binding.swipeRefreshLayout.setColorSchemeResources(
            android.R.color.holo_blue_bright,
            android.R.color.holo_green_light,
            android.R.color.holo_orange_light,
            android.R.color.holo_red_light
        )
    }

    private fun setupSearch() {
        binding.searchEditText.doAfterTextChanged { text ->
            viewModel.setSearchQuery(text?.toString().orEmpty())
        }
    }

    private fun setupFilters() {
        // Фильтр по категориям
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

        // Фильтр по кухням
        binding.chipAllCuisines.isChecked = true
        viewModel.setCuisineFilter("Все кухни")

        binding.cuisineChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val cuisine = when (checkedIds.firstOrNull()) {
                R.id.chipRussian -> "Русская"
                R.id.chipItalian -> "Итальянская"
                R.id.chipSpanish -> "Испанская"
                R.id.chipFrench -> "Французская"
                R.id.chipAmerican -> "Американская"
                R.id.chipAsian -> "Азиатская"
                R.id.chipMediterranean -> "Средиземноморская"
                R.id.chipAllCuisines -> "Все кухни"
                else -> "Все кухни"
            }
            viewModel.setCuisineFilter(cuisine)
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    if (isProfileVisible) {
                        binding.mainContentLayer.visibility = View.VISIBLE
                        supportFragmentManager.findFragmentById(R.id.fragment_container)?.let { fragment ->
                            supportFragmentManager.beginTransaction().remove(fragment).commit()
                        }
                        isProfileVisible = false
                    }
                    true
                }

                R.id.navigation_camera -> {
                    startActivity(Intent(this, ScanActivity::class.java))
                    false
                }

                R.id.navigation_profile -> {
                    if (!isProfileVisible) {
                        binding.mainContentLayer.visibility = View.GONE

                        if (supportFragmentManager.findFragmentById(R.id.fragment_container) == null) {
                            supportFragmentManager.beginTransaction()
                                .replace(R.id.fragment_container, ProfileFragment())
                                .commit()
                        }
                        isProfileVisible = true
                    }
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

                if (state.isRefreshing) {
                    binding.swipeRefreshLayout.isRefreshing = true
                } else {
                    binding.swipeRefreshLayout.isRefreshing = false
                }

                state.error?.let {
                    Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}