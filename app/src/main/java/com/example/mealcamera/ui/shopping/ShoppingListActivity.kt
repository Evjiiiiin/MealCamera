package com.example.mealcamera.ui.shopping

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mealcamera.MealCameraApplication
import com.example.mealcamera.databinding.ActivityShoppingListBinding
import kotlinx.coroutines.launch

class ShoppingListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityShoppingListBinding
    private lateinit var adapter: ShoppingListAdapter

    private val repository by lazy {
        (application as MealCameraApplication).recipeRepository
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityShoppingListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecycler()
        observeItems()
        setupActions()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Список покупок"
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecycler() {
        adapter = ShoppingListAdapter { item, checked ->
            lifecycleScope.launch {
                repository.updateShoppingListItemChecked(item, checked)
            }
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun observeItems() {
        lifecycleScope.launch {
            repository.getShoppingListItems().collect { items ->
                adapter.submitList(items)
                binding.tvEmpty.visibility = if (items.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            }
        }
    }

    private fun setupActions() {
        binding.btnAddFromFavorites.setOnClickListener {
            Toast.makeText(this, "Добавление из избранного будет в следующем шаге", Toast.LENGTH_SHORT).show()
        }

        binding.btnClearChecked.setOnClickListener {
            lifecycleScope.launch {
                repository.clearCheckedShoppingListItems()
                Toast.makeText(this@ShoppingListActivity, "Отмеченные позиции удалены", Toast.LENGTH_SHORT).show()
            }
        }
    }
}