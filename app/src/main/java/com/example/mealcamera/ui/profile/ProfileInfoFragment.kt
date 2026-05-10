package com.example.mealcamera.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.mealcamera.MealCameraApplication
import com.example.mealcamera.R
import com.example.mealcamera.data.local.AppStatsManager
import com.example.mealcamera.databinding.FragmentProfileInfoBinding
import com.example.mealcamera.ui.auth.AllergenSelectionActivity
import com.example.mealcamera.util.ImageStorage
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProfileInfoFragment : Fragment() {

    private var _binding: FragmentProfileInfoBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ProfileViewModel by viewModels {
        (requireActivity().application as MealCameraApplication).viewModelFactory
    }
    private lateinit var imageStorage: ImageStorage

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileInfoBinding.inflate(inflater, container, false)
        imageStorage = ImageStorage(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.profileContent.animate().alpha(1f).setDuration(500).start()
        loadAvatar()
        observeViewModel()
        setupListeners()
    }

    private fun loadAvatar() {
        val user = FirebaseAuth.getInstance().currentUser
        val avatarFile = imageStorage.getAvatarFile(user?.uid ?: "")
        Glide.with(this)
            .load(avatarFile)
            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
            .skipMemoryCache(true)
            .placeholder(R.drawable.ic_profile_placeholder)
            .error(R.drawable.ic_profile_placeholder)
            .into(binding.ivAvatar)
    }

    private fun observeViewModel() {
        val user = FirebaseAuth.getInstance().currentUser
        binding.tvEmail.text = user?.email ?: "Гость"
        binding.tvDisplayName.text = user?.displayName ?: "Пользователь"

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.stats.collect { stats ->
                if (stats.isLoading) {
                    binding.statsLoading.visibility = View.VISIBLE
                    binding.statsLayout.alpha = 0.3f
                } else {
                    binding.statsLoading.visibility = View.GONE
                    binding.statsLayout.animate().alpha(1f).setDuration(300).start()
                    binding.tvRecipesCount.text = stats.cookedCount.toString()
                    binding.tvScansCount.text = stats.uniqueCount.toString()
                }
                val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                val recentText = stats.recentRecipes.take(5).joinToString(separator = "\n") { recipe ->
                    "• ${recipe.name} — ${dateFormat.format(Date(recipe.cookedAtMillis))}"
                }.ifBlank { "Пока нет приготовленных блюд" }
                binding.tvRecentCookedRecipes.text = recentText
            }
        }
    }

    private fun setupListeners() {
        binding.btnEditProfile.setOnClickListener {
            startActivity(Intent(requireContext(), ProfileEditActivity::class.java))
        }
        binding.btnEditAllergens.setOnClickListener {
            startActivity(Intent(requireContext(), AllergenSelectionActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshStats()
        val user = FirebaseAuth.getInstance().currentUser
        binding.tvDisplayName.text = user?.displayName ?: "Пользователь"
        loadAvatar()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}