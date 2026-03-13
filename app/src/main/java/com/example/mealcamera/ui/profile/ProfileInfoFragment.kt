package com.example.mealcamera.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.mealcamera.data.local.AppStatsManager
import com.example.mealcamera.databinding.FragmentProfileInfoBinding
import com.example.mealcamera.ui.auth.LoginActivity
import com.example.mealcamera.ui.addrecipe.AddRecipeActivity
import com.example.mealcamera.ui.shopping.ShoppingListActivity
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProfileInfoFragment : Fragment() {

    private var _binding: FragmentProfileInfoBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val user = FirebaseAuth.getInstance().currentUser

        binding.tvEmail.text = user?.email ?: "Гость"
        binding.tvDisplayName.text = user?.displayName ?: "Пользователь"

        val statsManager = AppStatsManager(requireContext())
        binding.tvRecipesCount.text = statsManager.getCookedRecipesCount(user?.uid).toString()
        binding.tvScansCount.text = statsManager.getUniqueCookedRecipesCount(user?.uid).toString()

        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        val recentText = statsManager.getRecentCookedRecipes(user?.uid)
            .joinToString(separator = "\n") { recipe: AppStatsManager.RecentCookedRecipe ->
                "• ${recipe.name} — ${dateFormat.format(Date(recipe.cookedAtMillis))}"
            }
            .ifBlank { "Пока нет приготовленных блюд" }
        binding.tvRecentCookedRecipes.text = recentText

        binding.btnEditProfile.setOnClickListener {
            Toast.makeText(context, "Редактирование профиля скоро появится!", Toast.LENGTH_SHORT).show()
        }

        binding.btnShoppingList.setOnClickListener {
            startActivity(Intent(requireContext(), ShoppingListActivity::class.java))
        }

        binding.btnAddRecipe.setOnClickListener {
            startActivity(Intent(requireContext(), AddRecipeActivity::class.java))
        }

        binding.btnLogout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(requireContext(), LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}