package com.example.mealcamera.ui.profile

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.mealcamera.MealCameraApplication
import com.example.mealcamera.R
import com.example.mealcamera.data.model.Recipe
import com.example.mealcamera.databinding.FragmentMyRecipesBinding
import com.example.mealcamera.ui.detail.RecipeDetailActivity
import kotlinx.coroutines.launch

class MyRecipesFragment : Fragment() {

    private var _binding: FragmentMyRecipesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProfileViewModel by viewModels {
        (requireActivity().application as MealCameraApplication).viewModelFactory
    }

    private lateinit var adapter: MyRecipesAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMyRecipesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        adapter = MyRecipesAdapter(
            onDeleteClick = { recipe -> showDeleteConfirmation(recipe) },
            onEditClick = { recipe ->
                // Навигация через NavController во фрагмент добавления/редактирования
                findNavController().navigate(
                    R.id.navigation_add_recipe,
                    bundleOf("edit_recipe_id" to recipe.recipeId)
                )
            },
            onItemClick = { recipe ->
                startActivity(Intent(requireContext(), RecipeDetailActivity::class.java).apply {
                    putExtra(RecipeDetailActivity.EXTRA_RECIPE_ID, recipe.recipeId)
                })
            }
        )
        binding.rvMyRecipes.adapter = adapter
    }

    private fun showDeleteConfirmation(recipe: Recipe) {
        AlertDialog.Builder(requireContext())
            .setTitle("Удалить рецепт?")
            .setMessage("Вы уверены, что хотите удалить '${recipe.name}'?")
            .setPositiveButton("Удалить") { _, _ -> viewModel.deleteRecipe(recipe) }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.myRecipes.collect { recipes ->
                adapter.submitList(recipes)
                binding.tvEmptyState.visibility = if (recipes.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.deleteStatus.collect { success ->
                if (success) Toast.makeText(requireContext(), "Рецепт удален", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}