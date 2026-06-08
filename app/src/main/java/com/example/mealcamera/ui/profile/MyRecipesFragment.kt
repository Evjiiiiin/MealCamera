package com.example.mealcamera.ui.profile

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.Navigation
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
        setupEmptyState()
    }

    private fun setupRecyclerView() {
        adapter = MyRecipesAdapter(
            onDeleteClick = { recipe -> showDeleteConfirmation(recipe) },
            onEditClick = { recipe ->
                Log.d("MyRecipes", "Редактирование рецепта ID=${recipe.recipeId}, name=${recipe.name}")
                val bundle = Bundle().apply {
                    putLong("edit_recipe_id", recipe.recipeId)
                }
                val navOptions = NavOptions.Builder()
                    .setEnterAnim(R.anim.slide_in_right)
                    .setExitAnim(R.anim.slide_out_left)
                    .setPopEnterAnim(R.anim.slide_in_left)
                    .setPopExitAnim(R.anim.slide_out_right)
                    .build()
                Navigation.findNavController(requireActivity(), R.id.nav_host_fragment)
                    .navigate(R.id.navigation_add_recipe, bundle, navOptions)
            },
            onItemClick = { recipe ->
                val intent = Intent(requireContext(), RecipeDetailActivity::class.java).apply {
                    putExtra(RecipeDetailActivity.EXTRA_RECIPE_ID, recipe.recipeId)
                }
                startActivity(intent)
                @Suppress("DEPRECATION")
                requireActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
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
                if (recipes.isEmpty()) {
                    binding.emptyState.visibility = View.VISIBLE
                } else {
                    binding.emptyState.visibility = View.GONE
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.deleteStatus.collect { success ->
                if (success) Toast.makeText(requireContext(), "Рецепт удалён", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupEmptyState() {
        binding.btnCreateRecipe.setOnClickListener {
            Navigation.findNavController(requireActivity(), R.id.nav_host_fragment)
                .navigate(R.id.navigation_add_recipe)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}