package com.example.mealcamera.ui.result

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mealcamera.MealCameraApplication
import com.example.mealcamera.R
import com.example.mealcamera.data.util.UnitHelper
import com.example.mealcamera.data.model.EditableIngredient
import com.example.mealcamera.databinding.ActivityResultBinding
import com.example.mealcamera.ui.SharedViewModel
import com.example.mealcamera.ui.catalog.IngredientCatalogActivity
import com.example.mealcamera.ui.home.RecipeAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ResultFragment : Fragment() {

    private var _binding: ActivityResultBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ResultViewModel by viewModels {
        (requireActivity().application as MealCameraApplication).viewModelFactory
    }

    private val sharedViewModel: SharedViewModel by lazy {
        (requireActivity().application as MealCameraApplication).sharedViewModel
    }

    private lateinit var perfectAdapter: RecipeAdapter
    private lateinit var oneMissingAdapter: RecipeAdapter
    private lateinit var twoMissingAdapter: RecipeAdapter
    private lateinit var editableAdapter: EditableIngredientAdapter

    private val catalogLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val selectedNames = result.data?.getStringArrayListExtra("selected_names") ?: return@registerForActivityResult
            val currentList = viewModel.editableIngredients.value.toMutableList()

            // 1. Удаляем ингредиенты, которые больше не выбраны в каталоге
            val normalizedSelected = selectedNames.map { it.trim().lowercase().replace("ё", "е") }.toSet()
            currentList.removeAll { ing ->
                !normalizedSelected.contains(ing.name.trim().lowercase().replace("ё", "е"))
            }

            // 2. Добавляем новые, которых не было
            selectedNames.forEach { name ->
                val isAlreadyPresent = currentList.any { it.name.equals(name, ignoreCase = true) }
                if (!isAlreadyPresent) {
                    currentList.add(EditableIngredient(
                        id = System.currentTimeMillis() + name.hashCode(),
                        name = name,
                        quantity = "1",
                        unit = UnitHelper.getDefaultUnit(name)
                    ))
                }
            }

            viewModel.addIngredients(currentList)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = ActivityResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupNavigation()
        setupRecyclerViews()
        setupPortionControls()
        observeViewModels()

        sharedViewModel.activeSession.value?.let { session ->
            if (viewModel.editableIngredients.value.isEmpty()) {
                viewModel.restoreSession(session.ingredients, session.portions)
            }
        }
    }

    private fun setupNavigation() {
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun setupRecyclerViews() {
        perfectAdapter = RecipeAdapter(
            onItemClick = { recipe -> navigateToDetail(recipe.recipeId) },
            onFavoriteClick = { recipe, _ -> viewModel.toggleFavorite(recipe) },
            onAddToShoppingList = { missing -> viewModel.addToShoppingList(missing) }
        )
        oneMissingAdapter = RecipeAdapter(
            onItemClick = { recipe -> navigateToDetail(recipe.recipeId) },
            onFavoriteClick = { recipe, _ -> viewModel.toggleFavorite(recipe) },
            onAddToShoppingList = { missing -> viewModel.addToShoppingList(missing) }
        )
        twoMissingAdapter = RecipeAdapter(
            onItemClick = { recipe -> navigateToDetail(recipe.recipeId) },
            onFavoriteClick = { recipe, _ -> viewModel.toggleFavorite(recipe) },
            onAddToShoppingList = { missing -> viewModel.addToShoppingList(missing) }
        )

        binding.perfectMatchRecyclerView.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = perfectAdapter
        }
        binding.oneMissingRecyclerView.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = oneMissingAdapter
        }
        binding.twoMissingRecyclerView.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = twoMissingAdapter
        }

        editableAdapter = EditableIngredientAdapter(
            onDeleteClick = { viewModel.removeIngredient(it) },
            onUpdateClick = { viewModel.updateIngredient(it) }
        )
        binding.editableIngredientsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = editableAdapter
        }
    }

    private fun setupPortionControls() {
        binding.btnPlusPortion.setOnClickListener { viewModel.setPortions(viewModel.portions.value + 1) }
        binding.btnMinusPortion.setOnClickListener { viewModel.setPortions(viewModel.portions.value - 1) }

        binding.btnAddIngredient.setOnClickListener {
            val intent = Intent(requireContext(), IngredientCatalogActivity::class.java).apply {
                val currentNames = viewModel.editableIngredients.value.map { it.name }
                putStringArrayListExtra("selected_names", ArrayList(currentNames))
            }
            catalogLauncher.launch(intent)
        }

        binding.btnResetSession.setOnClickListener {
            viewModel.resetIngredientsOnly()
        }
    }

    private fun observeViewModels() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.editableIngredients.collectLatest { list ->
                        editableAdapter.submitList(list)
                        if (list.isEmpty()) {
                            binding.tvNoResults.visibility = View.GONE
                        }
                    }
                }

                launch {
                    viewModel.portions.collect { binding.tvPortions.text = it.toString() }
                }

                launch {
                    viewModel.isSearching.collect { isSearching ->
                        binding.searchProgressBar.visibility = if (isSearching) View.VISIBLE else View.GONE
                    }
                }

                launch {
                    viewModel.errorEvents.collect { message ->
                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                    }
                }

                launch {
                    viewModel.perfectRecipes.collectLatest { list ->
                        perfectAdapter.submitList(list)
                        binding.tvPerfectMatchHeader.visibility = if (list.isNotEmpty()) View.VISIBLE else View.GONE
                        binding.perfectMatchRecyclerView.visibility = if (list.isNotEmpty()) View.VISIBLE else View.GONE
                        updateNoResultsVisibility()
                    }
                }

                launch {
                    viewModel.oneMissingRecipes.collectLatest { list ->
                        oneMissingAdapter.submitList(list)
                        binding.tvOneMissingHeader.visibility = if (list.isNotEmpty()) View.VISIBLE else View.GONE
                        binding.oneMissingRecyclerView.visibility = if (list.isNotEmpty()) View.VISIBLE else View.GONE
                        updateNoResultsVisibility()
                    }
                }

                launch {
                    viewModel.twoMissingRecipes.collectLatest { list ->
                        twoMissingAdapter.submitList(list)
                        binding.tvTwoMissingHeader.visibility = if (list.isNotEmpty()) View.VISIBLE else View.GONE
                        binding.twoMissingRecyclerView.visibility = if (list.isNotEmpty()) View.VISIBLE else View.GONE
                        updateNoResultsVisibility()
                    }
                }
            }
        }
    }

    private fun updateNoResultsVisibility() {
        val hasIngredients = viewModel.editableIngredients.value.isNotEmpty()
        val isEmpty = viewModel.perfectRecipes.value.isEmpty() &&
                viewModel.oneMissingRecipes.value.isEmpty() &&
                viewModel.twoMissingRecipes.value.isEmpty()

        binding.tvNoResults.visibility = if (hasIngredients && isEmpty && !viewModel.isSearching.value) View.VISIBLE else View.GONE
    }

    private fun navigateToDetail(recipeId: Long) {
        val intent = Intent(requireContext(), com.example.mealcamera.ui.detail.RecipeDetailActivity::class.java).apply {
            putExtra(com.example.mealcamera.ui.detail.RecipeDetailActivity.EXTRA_RECIPE_ID, recipeId)
            putExtra("from_results", true)
        }
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}