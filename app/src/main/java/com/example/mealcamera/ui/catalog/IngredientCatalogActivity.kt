package com.example.mealcamera.ui.catalog

import android.content.Intent
import android.os.Bundle
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
    private val selectedList = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIngredientCatalogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = IngredientAdapter { name, isSelected ->
            if (isSelected) selectedList.add(name) else selectedList.remove(name)
        }

        binding.rvIngredients.layoutManager = LinearLayoutManager(this)
        binding.rvIngredients.adapter = adapter

        // ИСПРАВЛЕНО ЗДЕСЬ: используем recipeRepository вместо repository
        val recipeRepository = (application as MealCameraApplication).recipeRepository
        lifecycleScope.launch {
            val allIngredients = recipeRepository.getAllDbIngredients() // Убедись, что этот метод есть в репозитории
            // Предполагается, что adapter.setData() принимает List<Ingredient>,
            // а getAllDbIngredients() возвращает List<Ingredient>.
            // Если adapter.setData() ожидает List<String>, то нужно будет map-ить.
            // Сейчас оставляем как есть, полагая, что IngredientAdapter готов к List<Ingredient>.
            adapter.setData(allIngredients)
        }

        binding.etSearch.doOnTextChanged { text, _, _, _ ->
            adapter.filter(text.toString())
        }

        binding.btnConfirmSelection.setOnClickListener {
            val intent = Intent()
            intent.putStringArrayListExtra("selected_list", ArrayList(selectedList))
            setResult(RESULT_OK, intent)
            finish()
        }
    }
}
