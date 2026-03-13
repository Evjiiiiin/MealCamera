package com.example.mealcamera.ui.result

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.mealcamera.MealCameraApplication
import com.example.mealcamera.R
import com.example.mealcamera.data.model.EditableIngredient
import com.example.mealcamera.data.util.UnitHelper
import com.example.mealcamera.databinding.ActivityResultBinding
import com.example.mealcamera.ui.SharedViewModel
import com.example.mealcamera.ui.catalog.IngredientCatalogActivity
import com.example.mealcamera.ui.detail.RecipeDetailActivity
import com.example.mealcamera.ui.home.MainActivity
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResultBinding
    private val viewModel: ResultViewModel by viewModels {
        (application as MealCameraApplication).viewModelFactory
    }

    private val sharedViewModel: SharedViewModel by lazy {
        (application as MealCameraApplication).sharedViewModel
    }

    private lateinit var perfectAdapter: ResultAdapter
    private lateinit var oneMissingAdapter: ResultAdapter
    private lateinit var twoMissingAdapter: ResultAdapter

    private var editableIngredientMutableList: MutableList<EditableIngredient> = mutableListOf()
    private lateinit var editableAdapter: EditableIngredientAdapter

    private val addIngredientLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val selectedNames = data?.getStringArrayListExtra("selected_names") ?: return@registerForActivityResult

            val currentIngredientNames = editableIngredientMutableList.map { it.name }.toSet()

            val newIngredients = selectedNames
                .filter { name -> !currentIngredientNames.contains(name) }
                .map { name ->
                    EditableIngredient(
                        id = System.currentTimeMillis(),
                        name = name,
                        quantity = "1",
                        unit = UnitHelper.getDefaultUnit(name)
                    )
                }

            if (newIngredients.isNotEmpty()) {
                val updatedList = editableIngredientMutableList.toMutableList()
                updatedList.addAll(newIngredients)
                viewModel.addIngredients(updatedList)
                Toast.makeText(this, "Добавлено: ${newIngredients.joinToString { it.name }}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Все выбранные ингредиенты уже есть в списке", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!checkUserAndSession()) {
            return
        }

        setupBackPressed()
        setupToolbar()
        setupBottomNavigation()
        setupRecyclerViews()
        setupPortionControls()
        setupAddButton()
        setupResetButton()
        observeViewModels()
    }

    private fun checkUserAndSession(): Boolean {
        val currentUser = FirebaseAuth.getInstance().currentUser
        val session = sharedViewModel.activeSession.value

        if (session == null) {
            Toast.makeText(this, "Нет активной сессии", Toast.LENGTH_SHORT).show()
            finish()
            return false
        }

        val sessionUserId = session.userId
        val currentUserId = currentUser?.uid ?: "guest"

        if (sessionUserId != currentUserId) {
            Toast.makeText(
                this,
                "Сессия принадлежит другому пользователю. Начните новый подбор.",
                Toast.LENGTH_LONG
            ).show()
            sharedViewModel.endSession()
            finish()
            return false
        }

        if (sharedViewModel.shouldResetSession()) {
            Toast.makeText(this, "Сессия устарела. Начните новый подбор.", Toast.LENGTH_LONG).show()
            sharedViewModel.endSession()
            finish()
            return false
        }

        viewModel.restoreSession(session.ingredients, session.portions)
        return true
    }

    private fun setupBackPressed() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // При нажатии назад возвращаемся на экран камеры
                finish()
            }
        })
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            finish() // Возврат на экран камеры
        }
    }

    private fun setupBottomNavigation() {
        // Устанавливаем выбранный пункт - камера (так как мы на экране результатов)
        binding.bottomNavigationView.selectedItemId = R.id.navigation_camera

        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    // Переход на главный экран (сохраняем сессию)
                    val intent = Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        putExtra("open_from_result", true) // Флаг, что пришли с результатов
                    }
                    startActivity(intent)
                    finish()
                    true
                }
                R.id.navigation_camera -> {
                    // Возврат на экран камеры (закрываем ResultActivity)
                    finish()
                    true
                }
                R.id.navigation_profile -> {
                    // Переход в профиль (через главный экран с флагом open_profile)
                    val intent = Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        putExtra("open_profile", true)
                        putExtra("open_from_result", true)
                    }
                    startActivity(intent)
                    finish()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupRecyclerViews() {
        perfectAdapter = ResultAdapter { navigateToDetail(it.recipe.recipeId) }
        oneMissingAdapter = ResultAdapter { navigateToDetail(it.recipe.recipeId) }
        twoMissingAdapter = ResultAdapter { navigateToDetail(it.recipe.recipeId) }

        binding.perfectMatchRecyclerView.adapter = perfectAdapter
        binding.oneMissingRecyclerView.adapter = oneMissingAdapter
        binding.twoMissingRecyclerView.adapter = twoMissingAdapter

        editableAdapter = EditableIngredientAdapter(
            ingredients = editableIngredientMutableList,
            onDeleteClick = { ingredient ->
                viewModel.removeIngredient(ingredient)
            },
            onUpdateClick = { ingredient ->
                viewModel.updateIngredient(ingredient)
            },
            fragmentActivity = this
        )
        binding.editableIngredientsRecyclerView.adapter = editableAdapter
    }

    private fun setupPortionControls() {
        binding.btnPlusPortion.setOnClickListener {
            viewModel.setPortions(viewModel.portions.value + 1)
        }

        binding.btnMinusPortion.setOnClickListener {
            viewModel.setPortions(viewModel.portions.value - 1)
        }
    }

    private fun setupAddButton() {
        binding.btnAddIngredient.setOnClickListener {
            val intent = Intent(this, IngredientCatalogActivity::class.java)
            val selectedNames = editableIngredientMutableList.map { it.name }
            intent.putStringArrayListExtra("selected_names", ArrayList(selectedNames))
            addIngredientLauncher.launch(intent)
        }
    }

    private fun setupResetButton() {
        binding.btnResetSession.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Начать заново")
                .setMessage("Вы уверены? Все текущие настройки будут сброшены.")
                .setPositiveButton("Да") { _, _ ->
                    sharedViewModel.endSession()
                    finish()
                }
                .setNegativeButton("Нет", null)
                .show()
        }
    }

    private fun observeViewModels() {
        lifecycleScope.launch {
            viewModel.editableIngredients.collect { list ->
                editableIngredientMutableList.clear()
                editableIngredientMutableList.addAll(list)
                editableAdapter.updateIngredients(editableIngredientMutableList)

                if (list.isNotEmpty()) {
                    binding.btnApplyFilters.setOnClickListener {
                        val updated = editableAdapter.getEditedIngredients().map { it.copy() }
                        viewModel.findRecipes(updated)
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.portions.collect { portionCount ->
                binding.tvPortions.text = "Порции: $portionCount"
                binding.btnMinusPortion.isEnabled = portionCount > 1
                binding.btnPlusPortion.isEnabled = portionCount < 10
            }
        }

        lifecycleScope.launch {
            combine(
                viewModel.perfectRecipes,
                viewModel.oneMissingRecipes,
                viewModel.twoMissingRecipes
            ) { p, o, t -> Triple(p, o, t) }.collect { (p, o, t) ->
                perfectAdapter.submitList(p)
                oneMissingAdapter.submitList(o)
                twoMissingAdapter.submitList(t)

                binding.tvPerfectMatchHeader.visibility = if (p.isNotEmpty()) View.VISIBLE else View.GONE
                binding.tvOneMissingHeader.visibility = if (o.isNotEmpty()) View.VISIBLE else View.GONE
                binding.tvTwoMissingHeader.visibility = if (t.isNotEmpty()) View.VISIBLE else View.GONE

                val isEmpty = p.isEmpty() && o.isEmpty() && t.isEmpty()
                binding.resultsContainer.visibility = if (!isEmpty) View.VISIBLE else View.GONE
                binding.tvNoResults.visibility = if (isEmpty) View.VISIBLE else View.GONE
            }
        }
    }

    private fun navigateToDetail(recipeId: Long) {
        startActivity(Intent(this, RecipeDetailActivity::class.java).apply {
            putExtra(RecipeDetailActivity.EXTRA_RECIPE_ID, recipeId)
        })
    }

    override fun onResume() {
        super.onResume()
        // При возвращении на экран обновляем выделение в навигации
        binding.bottomNavigationView.menu.findItem(R.id.navigation_camera).isChecked = true
    }
}