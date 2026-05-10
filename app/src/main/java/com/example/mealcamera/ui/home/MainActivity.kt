package com.example.mealcamera.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.navigation.NavController
import androidx.navigation.NavOptions
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
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.let {
            if (it.getBooleanExtra("navigate_home", false)) {
                navController.navigate(R.id.navigation_home)
            }
        }
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        binding.bottomNavigationView.setupWithNavController(navController)
        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            val currentDest = navController.currentDestination?.id
            if (currentDest != item.itemId) {
                val navOptions = NavOptions.Builder()
                    .setPopUpTo(item.itemId, inclusive = false)
                    .setLaunchSingleTop(true)
                    .setEnterAnim(android.R.anim.fade_in)
                    .setExitAnim(android.R.anim.fade_out)
                    .setPopEnterAnim(android.R.anim.fade_in)
                    .setPopExitAnim(android.R.anim.fade_out)
                    .build()
                navController.navigate(item.itemId, null, navOptions)
            }
            true
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.navigation_home,
                R.id.navigation_shopping_list,
                R.id.navigation_profile -> {
                    binding.bottomNavCard.visibility = View.VISIBLE
                }
                R.id.navigation_add_recipe,
                R.id.navigation_camera,
                R.id.navigation_result,
                R.id.navigation_recipe_detail -> {
                    binding.bottomNavCard.visibility = View.GONE
                }
                else -> binding.bottomNavCard.visibility = View.GONE
            }
        }
    }

    fun setBottomNavVisibility(visible: Boolean) {
        binding.bottomNavCard.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun selectHomeTab() {
        binding.bottomNavigationView.selectedItemId = R.id.navigation_home
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}