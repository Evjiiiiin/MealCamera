package com.example.mealcamera.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.mealcamera.databinding.FragmentProfileInfoBinding
import com.google.firebase.auth.FirebaseAuth

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

        // Заглушка для статистики (потом можно сделать реальные данные)
        binding.tvRecipesCount.text = "0"
        binding.tvScansCount.text = "0"

        binding.btnEditProfile.setOnClickListener {
            Toast.makeText(context, "Редактирование профиля скоро появится!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}