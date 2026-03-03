package com.example.mealcamera.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mealcamera.MealCameraApplication
import com.example.mealcamera.R
import com.example.mealcamera.databinding.FragmentFavoritesBinding
import com.example.mealcamera.ui.detail.RecipeDetailActivity
import com.example.mealcamera.ui.home.MainActivity
import kotlinx.coroutines.launch

class FavoritesFragment : Fragment() {

    private var _binding: FragmentFavoritesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FavoritesViewModel by viewModels {
        (requireActivity().application as MealCameraApplication).viewModelFactory
    }

    private lateinit var adapter: FavoriteAdapter

    private val detailActivityResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.loadFavorites()
            val activity = requireActivity()
            if (activity is MainActivity) {
                activity.refreshFavorites()
                // 👇 ВАЖНО: Убеждаемся, что навигация показывает правильную вкладку
                activity.updateNavigationSelection(R.id.navigation_profile)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFavoritesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        observeData()
    }

    private fun setupRecyclerView() {
        adapter = FavoriteAdapter(
            onItemClick = { recipe ->
                val intent = Intent(requireContext(), RecipeDetailActivity::class.java).apply {
                    putExtra(RecipeDetailActivity.EXTRA_RECIPE_ID, recipe.recipeId)
                }
                detailActivityResult.launch(intent)
            },
            onFavoriteClick = { recipe ->
                lifecycleScope.launch {
                    val favoriteRepository = (requireActivity().application as MealCameraApplication).favoriteRepository
                    favoriteRepository.toggleFavorite(recipe)
                    Toast.makeText(requireContext(), "Удалено из избранного", Toast.LENGTH_SHORT).show()
                    viewModel.loadFavorites()

                    val activity = requireActivity()
                    if (activity is MainActivity) {
                        activity.refreshFavorites()
                    }
                }
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    private fun observeData() {
        lifecycleScope.launch {
            viewModel.favoriteRecipes.collect { recipes ->
                if (recipes.isEmpty()) {
                    binding.recyclerView.visibility = View.GONE
                    binding.tvEmpty.visibility = View.VISIBLE
                } else {
                    binding.recyclerView.visibility = View.VISIBLE
                    binding.tvEmpty.visibility = View.GONE
                    adapter.submitList(recipes)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 👇 При возврате к фрагменту обновляем навигацию
        val activity = requireActivity()
        if (activity is MainActivity) {
            activity.updateNavigationSelection(R.id.navigation_profile)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}