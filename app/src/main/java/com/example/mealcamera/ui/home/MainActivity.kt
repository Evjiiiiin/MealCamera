package com.example.mealcamera.ui.home

import android.content.Intent
import android.os.Bundle
import android.util.Log
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
        loadRecipesWithAllergens()

        // Обработка флагов из Intent
        handleIntentFlags(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntentFlags(intent)
    }

    private fun handleIntentFlags(intent: Intent) {
        when {
            intent.getBooleanExtra("open_profile", false) -> {
                // Если пришли с флагом открытия профиля
                showProfile()
            }
            intent.getBooleanExtra("open_from_result", false) -> {
                // Если пришли с экрана результатов, просто показываем главный экран
                if (isProfileVisible) {
                    hideProfile()
                }
            }
        }
    }

    private fun loadRecipesWithAllergens() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            viewModel.refreshRecipes()
            return
        }

        lifecycleScope.launch {
            try {
                val firestoreService = (application as MealCameraApplication).firestoreService
                val userAllergens = firestoreService.getUserAllergens(userId)

                Log.d("MainActivity", "✅ Loaded allergens for user: ${userAllergens.size}")

                val recipeRepository = (application as MealCameraApplication).recipeRepository
                recipeRepository.syncRecipesFromCloud(userAllergens)

                viewModel.refreshRecipes()
            } catch (e: Exception) {
                Log.e("MainActivity", "❌ Error loading allergens: ${e.message}")
                viewModel.refreshRecipes()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // При возвращении на главный экран обновляем навигацию
        if (!isProfileVisible) {
            binding.bottomNavigationView.menu.findItem(R.id.navigation_home).isChecked = true
        }
    }

    fun updateNavigationSelection(itemId: Int) {
        binding.bottomNavigationView.menu.findItem(itemId).isChecked = true
    }

    private fun showProfile() {
        // Показываем профиль
        binding.mainContentLayer.visibility = View.GONE
        binding.fragmentContainer.visibility = View.VISIBLE

        if (supportFragmentManager.findFragmentById(R.id.fragment_container) == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, ProfileFragment())
                .commit()
        }

        isProfileVisible = true
        updateNavigationSelection(R.id.navigation_profile)
    }

    private fun hideProfile() {
        // Скрываем профиль, показываем основной контент
        binding.mainContentLayer.visibility = View.VISIBLE
        binding.fragmentContainer.visibility = View.GONE

        supportFragmentManager.findFragmentById(R.id.fragment_container)?.let {
            supportFragmentManager.beginTransaction().remove(it).commit()
        }

        isProfileVisible = false
        updateNavigationSelection(R.id.navigation_home)
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
        binding.searchEditText.doAfterTextChanged { query ->
            viewModel.setSearchQuery(query?.toString() ?: "")
        }
    }

    private fun setupFilters() {
        // Фильтр по категориям
        binding.filterChipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            val selectedChip = group.findViewById<com.google.android.material.chip.Chip>(
                if (checkedIds.isNotEmpty()) checkedIds[0] else R.id.chipAll
            )
            val category = selectedChip?.text.toString()
            viewModel.setCategoryFilter(category)
        }

        // Фильтр по кухням
        binding.cuisineChipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            val selectedChip = group.findViewById<com.google.android.material.chip.Chip>(
                if (checkedIds.isNotEmpty()) checkedIds[0] else R.id.chipAllCuisines
            )
            val cuisine = selectedChip?.text.toString()
            viewModel.setCuisineFilter(cuisine)
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigationView.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.navigation_home -> {
                    if (isProfileVisible) {
                        hideProfile()
                    } else {
                        // Если уже на главной, ничего не делаем
                    }
                    true
                }
                R.id.navigation_camera -> {
                    startActivity(Intent(this, ScanActivity::class.java))
                    true
                }
                R.id.navigation_profile -> {
                    if (!isProfileVisible) {
                        showProfile()
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
                binding.swipeRefreshLayout.isRefreshing = state.isRefreshing
                state.error?.let { errorMessage ->
                    Toast.makeText(this@MainActivity, errorMessage, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}