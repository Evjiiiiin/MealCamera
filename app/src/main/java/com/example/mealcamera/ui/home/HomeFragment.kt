package com.example.mealcamera.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.example.mealcamera.MealCameraApplication
import com.example.mealcamera.R
import com.example.mealcamera.data.model.DEFAULT_MAX_CALORIES_PER_PORTION
import com.example.mealcamera.databinding.FragmentHomeBinding
import com.example.mealcamera.ui.detail.RecipeDetailActivity
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.util.Calendar

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var recipeAdapter: RecipeAdapter
    private val viewModel: MainViewModel by activityViewModels {
        (requireActivity().application as MealCameraApplication).viewModelFactory
    }

    private val auth = FirebaseAuth.getInstance()
    private var latestAdaptiveCaloriesMax: Float = DEFAULT_MAX_CALORIES_PER_PORTION

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupGreeting()
        setupRecyclerView()
        setupSearch()
        setupSwipeRefresh()
        observeViewModel()
    }

    private fun setupGreeting() {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val greetingText = when (hour) {
            in 6..11 -> "Доброе утро"
            in 12..17 -> "Добрый день"
            in 18..22 -> "Добрый вечер"
            else -> "Доброй ночи"
        }
        binding.tvGreeting.text = greetingText

        val displayName = auth.currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "Что будем готовить?"
        binding.tvUserName.text = displayName
    }

    private fun setupRecyclerView() {
        recipeAdapter = RecipeAdapter(
            onItemClick = { recipe ->
                startActivity(Intent(requireContext(), RecipeDetailActivity::class.java).apply {
                    putExtra(RecipeDetailActivity.EXTRA_RECIPE_ID, recipe.recipeId)
                })
            },
            onFavoriteClick = { recipe, isFavorite ->
                viewModel.toggleFavorite(recipe, isFavorite)
            }
        )
        binding.recipesRecyclerView.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = recipeAdapter
        }
    }

    private fun setupSearch() {
        binding.searchEditText.doOnTextChanged { text, _, _, _ ->
            val query = text?.toString().orEmpty()
            viewModel.setSearchQuery(query)
            binding.btnClearSearch.isVisible = query.isNotEmpty()
        }

        binding.btnClearSearch.setOnClickListener {
            binding.searchEditText.text?.clear()
            viewModel.setSearchQuery("")
        }

        binding.btnFilter.setOnClickListener {
            showFilterBottomSheet()
        }
    }

    private fun showFilterBottomSheet() {
        // Используем newInstance для безопасной передачи данных при пересоздании фрагмента
        val bottomSheet = FilterBottomSheetFragment.newInstance(
            filters = viewModel.currentFilterState,
            maxCaloriesLimit = latestAdaptiveCaloriesMax
        ).apply {
            onApply = { newFilters ->
                viewModel.setFilters(newFilters)
            }
        }
        bottomSheet.show(parentFragmentManager, "FilterBottomSheet")
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refreshRecipes()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                latestAdaptiveCaloriesMax = state.adaptiveMaxCalories
                recipeAdapter.setFavoriteIds(state.favoriteIds)
                recipeAdapter.submitList(state.recipes)
                binding.swipeRefresh.isRefreshing = state.isRefreshing

                if (binding.searchEditText.text.toString() != state.searchQuery) {
                    binding.searchEditText.setText(state.searchQuery)
                }
                updateFilterIcon(state)
            }
        }
    }

    private fun updateFilterIcon(state: MainUiState) {
        val filter = viewModel.currentFilterState
        val hasActiveFilters = state.categoryFilter != "Все" || state.cuisineFilter != "Все кухни"
                || filter.minPrepTime > 0f || filter.maxPrepTime < 240f
                || filter.minCalories > 0f || filter.maxCalories < DEFAULT_MAX_CALORIES_PER_PORTION

        if (hasActiveFilters) {
            binding.btnFilter.setColorFilter(resources.getColor(R.color.color_primary, null))
        } else {
            binding.btnFilter.clearColorFilter()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}