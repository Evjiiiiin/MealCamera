package com.example.mealcamera.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.mealcamera.MealCameraApplication
import com.example.mealcamera.data.local.AppStatsManager
import com.example.mealcamera.databinding.FragmentProfileInfoBinding
import com.example.mealcamera.ui.auth.AllergenSelectionActivity
import com.example.mealcamera.ui.auth.LoginActivity
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProfileInfoFragment : Fragment() {

    private var _binding: FragmentProfileInfoBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProfileViewModel by viewModels {
        (requireActivity().application as MealCameraApplication).viewModelFactory
    }

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

        observeViewModel()
        setupListeners()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.stats.collect { stats ->
                binding.tvRecipesCount.text = stats.cookedCount.toString()
                binding.tvScansCount.text = stats.uniqueCount.toString()

                val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                val recentText = stats.recentRecipes
                    .joinToString(separator = "\n") { recipe: AppStatsManager.RecentCookedRecipe ->
                        "• ${recipe.name} — ${dateFormat.format(Date(recipe.cookedAtMillis))}"
                    }
                    .ifBlank { "Пока нет приготовленных блюд" }
                binding.tvRecentCookedRecipes.text = recentText
            }
        }
    }

    private fun setupListeners() {
        binding.btnEditProfile.setOnClickListener {
            Toast.makeText(context, "Редактирование профиля скоро появится!", Toast.LENGTH_SHORT).show()
        }

        binding.btnEditAllergens.setOnClickListener {
            startActivity(Intent(requireContext(), AllergenSelectionActivity::class.java))
        }

        binding.btnLogout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(requireContext(), LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }
    }

    override fun onResume() {
        super.onResume()
        // Обновляем статистику при каждом возвращении на экран
        viewModel.refreshStats()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}