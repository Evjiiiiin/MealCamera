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
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mealcamera.MealCameraApplication
import com.example.mealcamera.R
import com.example.mealcamera.data.model.EditableIngredient
import com.example.mealcamera.databinding.ActivityResultBinding
import com.example.mealcamera.ui.SharedViewModel
import com.example.mealcamera.ui.catalog.IngredientCatalogActivity
import com.example.mealcamera.ui.home.RecipeAdapter
import kotlinx.coroutines.flow.combine
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
            
            selectedNames.forEach { name ->
                if (currentList.none { it.name.equals(name, ignoreCase = true) }) {
                    currentList.add(EditableIngredient(
                        id = System.currentTimeMillis() + name.hashCode(),
                        name = name,
                        quantity = "1",
                        unit = "г"
                    ))
                }
            }
            viewModel.addIngredients(currentList)
            Toast.makeText(requireContext(), "Ингредиенты добавлены", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = ActivityResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupToolbar()
        setupRecyclerViews()
        setupPortionControls()
        observeViewModels()

        val session = sharedViewModel.activeSession.value
        if (session != null) {
            viewModel.restoreSession(session.ingredients, session.portions)
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun setupRecyclerViews() {
        perfectAdapter = RecipeAdapter(
            onItemClick = { recipe -> navigateToDetail(recipe.recipeId) },
            onFavoriteClick = { _, _ -> }
        )
        oneMissingAdapter = RecipeAdapter(
            onItemClick = { recipe -> navigateToDetail(recipe.recipeId) },
            onFavoriteClick = { _, _ -> }
        )
        twoMissingAdapter = RecipeAdapter(
            onItemClick = { recipe -> navigateToDetail(recipe.recipeId) },
            onFavoriteClick = { _, _ -> }
        )

        binding.perfectMatchRecyclerView.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = perfectAdapter
            isNestedScrollingEnabled = false
        }
        binding.oneMissingRecyclerView.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = oneMissingAdapter
            isNestedScrollingEnabled = false
        }
        binding.twoMissingRecyclerView.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = twoMissingAdapter
            isNestedScrollingEnabled = false
        }

        editableAdapter = EditableIngredientAdapter(
            ingredients = mutableListOf(),
            onDeleteClick = { viewModel.removeIngredient(it) },
            onUpdateClick = { viewModel.updateIngredient(it) },
            fragmentActivity = requireActivity()
        )
        binding.editableIngredientsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.editableIngredientsRecyclerView.adapter = editableAdapter
    }

    private fun setupPortionControls() {
        binding.btnPlusPortion.setOnClickListener { viewModel.setPortions(viewModel.portions.value + 1) }
        binding.btnMinusPortion.setOnClickListener { viewModel.setPortions(viewModel.portions.value - 1) }
        
        binding.btnApplyFilters.setOnClickListener {
            viewModel.findRecipes(viewModel.editableIngredients.value)
        }

        binding.btnAddIngredient.setOnClickListener {
            val intent = Intent(requireContext(), IngredientCatalogActivity::class.java).apply {
                val currentNames = viewModel.editableIngredients.value.map { it.name }
                putStringArrayListExtra("selected_names", ArrayList(currentNames))
            }
            catalogLauncher.launch(intent)
        }

        binding.btnResetSession.setOnClickListener {
            sharedViewModel.endSession()
            findNavController().navigate(R.id.navigation_camera)
        }
    }

    private fun observeViewModels() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.editableIngredients.collect { list ->
                editableAdapter.updateIngredients(list)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.portions.collect { binding.tvPortions.text = "Порции: $it" }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            combine(viewModel.perfectRecipes, viewModel.oneMissingRecipes, viewModel.twoMissingRecipes) { p, o, t ->
                Triple(p, o, t)
            }.collect { (p, o, t) ->
                perfectAdapter.submitList(p)
                oneMissingAdapter.submitList(o)
                twoMissingAdapter.submitList(t)

                binding.tvPerfectMatchHeader.visibility = if (p.isNotEmpty()) View.VISIBLE else View.GONE
                binding.perfectMatchRecyclerView.visibility = if (p.isNotEmpty()) View.VISIBLE else View.GONE
                
                binding.tvOneMissingHeader.visibility = if (o.isNotEmpty()) View.VISIBLE else View.GONE
                binding.oneMissingRecyclerView.visibility = if (o.isNotEmpty()) View.VISIBLE else View.GONE
                
                binding.tvTwoMissingHeader.visibility = if (t.isNotEmpty()) View.VISIBLE else View.GONE
                binding.twoMissingRecyclerView.visibility = if (t.isNotEmpty()) View.VISIBLE else View.GONE
                
                val isEmpty = p.isEmpty() && o.isEmpty() && t.isEmpty()
                binding.tvNoResults.visibility = if (isEmpty) View.VISIBLE else View.GONE
            }
        }
    }

    private fun navigateToDetail(recipeId: Long) {
        val intent = Intent(requireContext(), com.example.mealcamera.ui.detail.RecipeDetailActivity::class.java).apply {
            putExtra(com.example.mealcamera.ui.detail.RecipeDetailActivity.EXTRA_RECIPE_ID, recipeId)
        }
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}