package com.example.mealcamera.ui.catalog

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mealcamera.MealCameraApplication
import com.example.mealcamera.databinding.ActivityIngredientCatalogBinding
import kotlinx.coroutines.launch

class IngredientCatalogActivity : AppCompatActivity() {
    private lateinit var binding: ActivityIngredientCatalogBinding
    private lateinit var adapter: IngredientAdapter
    private val selectedNames = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIngredientCatalogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Получаем уже выбранные ингредиенты из Intent
        val preSelectedNames = intent.getStringArrayListExtra("selected_names") ?: arrayListOf()
        selectedNames.addAll(preSelectedNames)

        setupToolbar()
        setupRecyclerView()
        loadIngredients()
        setupSearch()
        setupConfirmButton()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Выбор ингредиентов"
        binding.toolbar.setNavigationOnClickListener {
            // Возвращаем результат с текущим выделением
            returnResult()
        }
    }

    private fun setupRecyclerView() {
        adapter = IngredientAdapter(
            selectedNames = selectedNames,
            onSelectionChanged = { name, isSelected ->
                if (isSelected) {
                    selectedNames.add(name)
                } else {
                    selectedNames.remove(name)
                }
            }
        )

        binding.rvIngredients.layoutManager = LinearLayoutManager(this)
        binding.rvIngredients.adapter = adapter
    }

    private fun loadIngredients() {
        val recipeRepository = (application as MealCameraApplication).recipeRepository
        lifecycleScope.launch {
            val allIngredients = recipeRepository.getAllDbIngredients()
            adapter.setData(allIngredients)
        }
    }

    private fun setupSearch() {
        binding.etSearch.doOnTextChanged { text, _, _, _ ->
            adapter.filter(text.toString())
        }
    }

    private fun setupConfirmButton() {
        binding.btnConfirmSelection.setOnClickListener {
            returnResult()
        }
    }

    private fun returnResult() {
        val intent = Intent().apply {
            putStringArrayListExtra("selected_names", ArrayList(selectedNames))
        }
        setResult(RESULT_OK, intent)
        finish()
    }

    override fun onBackPressed() {
        returnResult()
        super.onBackPressed()
    }
}