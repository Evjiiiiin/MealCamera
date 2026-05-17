package com.example.mealcamera.ui.catalog

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mealcamera.MealCameraApplication
import com.example.mealcamera.data.remote.FirestoreService
import com.example.mealcamera.databinding.ActivityIngredientCatalogBinding
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class IngredientCatalogActivity : AppCompatActivity() {
    private lateinit var binding: ActivityIngredientCatalogBinding
    private lateinit var adapter: IngredientAdapter
    private val selectedNames = mutableSetOf<String>()
    
    private val firestoreService = FirestoreService()
    private val auth = FirebaseAuth.getInstance()
    private var userAllergens = emptyList<String>()

    private fun normalize(name: String): String {
        return name.trim().lowercase().replace("ё", "е")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            binding = ActivityIngredientCatalogBinding.inflate(layoutInflater)
            setContentView(binding.root)

            val preSelectedNames = intent.getStringArrayListExtra("selected_names") ?: arrayListOf()
            selectedNames.addAll(preSelectedNames.map { normalize(it) })

            setupNavigation()
            
            lifecycleScope.launch {
                loadAllergens()
                setupRecyclerView()
                loadIngredients()
                setupSearch()
                setupConfirmButton()
            }

        } catch (e: Exception) {
            Log.e("IngredientCatalog", "Error creating activity", e)
            finish()
        }
    }

    private suspend fun loadAllergens() {
        val userId = auth.currentUser?.uid ?: return
        val application = application as? MealCameraApplication
        val repository = application?.recipeRepository
        
        val rawAllergens = firestoreService.getUserAllergensFlow(userId).first()
        userAllergens = repository?.expandAllergens(rawAllergens)?.map { it.lowercase() } ?: emptyList()
    }

    private fun setupNavigation() {
        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        adapter = IngredientAdapter(
            selectedNames = selectedNames,
            userAllergens = userAllergens,
            onSelectionChanged = { name, isSelected ->
                val normalized = normalize(name)
                if (isSelected) {
                    selectedNames.add(normalized)
                } else {
                    selectedNames.remove(normalized)
                }
            }
        )
        binding.rvIngredients.layoutManager = LinearLayoutManager(this)
        binding.rvIngredients.adapter = adapter
    }

    private fun loadIngredients() {
        val application = application as? MealCameraApplication
        val recipeRepository = application?.recipeRepository ?: return

        lifecycleScope.launch {
            try {
                val allIngredients = recipeRepository.getAllDbIngredients()
                adapter.setData(allIngredients)
            } catch (e: Exception) {
                Log.e("IngredientCatalog", "Error loading ingredients", e)
            }
        }
    }

    private fun setupSearch() {
        binding.etSearch.doOnTextChanged { text, _, _, _ ->
            val query = text?.toString().orEmpty()
            adapter.filter(query)
        }
    }

    private fun setupConfirmButton() {
        binding.btnConfirmSelection.setOnClickListener {
            returnResult()
        }
    }

    private fun returnResult() {
        val normalizedNames = selectedNames.toList()
        val intent = Intent().apply {
            putStringArrayListExtra("selected_names", ArrayList(normalizedNames))
        }
        setResult(RESULT_OK, intent)
        finish()
    }
}
