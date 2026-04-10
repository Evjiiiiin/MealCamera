package com.example.mealcamera.ui.home

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.mealcamera.R
import com.example.mealcamera.MealCameraApplication
import com.example.mealcamera.databinding.ActivityMainBinding
import com.example.mealcamera.ui.BaseActivity

class MainActivity : BaseActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    
    private val viewModel: MainViewModel by viewModels {
        (application as MealCameraApplication).viewModelFactory
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        binding.bottomNavigationView.setupWithNavController(navController)

        // Глобальное управление видимостью меню (п. 1 и п. 2 вашего плана)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.navigation_home,
                R.id.navigation_shopping_list,
                R.id.navigation_add_recipe,
                R.id.navigation_profile -> {
                    // МЕНЮ ВИДИМО на этих вкладках
                    binding.bottomNavCard.visibility = View.VISIBLE
                }
                R.id.navigation_camera, 
                R.id.navigation_result,
                R.id.navigation_recipe_detail -> {
                    // МЕНЮ СКРЫТО на Камере, Результатах и в Рецепте
                    binding.bottomNavCard.visibility = View.GONE
                }
                else -> binding.bottomNavCard.visibility = View.GONE
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}