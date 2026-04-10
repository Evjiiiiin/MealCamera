package com.example.mealcamera.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.example.mealcamera.MealCameraApplication
import com.example.mealcamera.databinding.FragmentHomeBinding
import com.example.mealcamera.ui.detail.RecipeDetailActivity
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.launch
import java.util.Calendar

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var recipeAdapter: RecipeAdapter
    private val viewModel: MainViewModel by viewModels {
        (requireActivity().application as MealCameraApplication).viewModelFactory
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupGreeting()
        setupRecyclerView()
        setupSearch()
        setupFilters()
        setupSwipeRefresh()
        observeViewModel()
    }

    private fun setupGreeting() {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        binding.greetingTextView.text = when (hour) {
            in 6..11 -> "Доброе утро"
            in 12..17 -> "Добрый день"
            in 18..22 -> "Добрый вечер"
            else -> "Доброй ночи"
        }
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
    }

    private fun setupFilters() {
        binding.filterChipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            val checkedId = checkedIds.firstOrNull()
            val chip = if (checkedId != null) group.findViewById<Chip>(checkedId) else null
            viewModel.setCategoryFilter(chip?.text?.toString() ?: "Все")
        }
        binding.cuisineChipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            val checkedId = checkedIds.firstOrNull()
            val chip = if (checkedId != null) group.findViewById<Chip>(checkedId) else null
            viewModel.setCuisineFilter(chip?.text?.toString() ?: "Все кухни")
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refreshRecipes()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                recipeAdapter.setFavoriteIds(state.favoriteIds)
                recipeAdapter.submitList(state.recipes)
                binding.swipeRefresh.isRefreshing = state.isRefreshing
                
                // Восстанавливаем состояние поиска и фильтров только если они изменились
                if (binding.searchEditText.text.toString() != state.searchQuery) {
                    binding.searchEditText.setText(state.searchQuery)
                }
                restoreChipState(binding.filterChipGroup, state.categoryFilter)
                restoreChipState(binding.cuisineChipGroup, state.cuisineFilter)
            }
        }
    }

    private fun restoreChipState(group: ChipGroup, value: String) {
        for (i in 0 until group.childCount) {
            val chip = group.getChildAt(i) as? Chip
            if (chip?.text?.toString() == value) {
                if (!chip.isChecked) {
                    group.check(chip.id)
                }
                break
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}