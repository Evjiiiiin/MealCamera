package com.example.mealcamera.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.mealcamera.MealCameraApplication
import com.example.mealcamera.databinding.ActivityAllergenSelectionBinding
import com.example.mealcamera.ui.home.MainActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AllergenSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAllergenSelectionBinding
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAllergenSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadUserAllergens()

        binding.btnContinue.setOnClickListener {
            saveAllergenPreferences()
        }

        binding.btnSkip.setOnClickListener {
            openMain()
        }
    }

    private fun loadUserAllergens() {
        val userId = auth.currentUser?.uid ?: return
        lifecycleScope.launch {
            try {
                val document = firestore.collection("users").document(userId).get().await()
                val rawAllergens = document.get("allergens")
                val allergens = if (rawAllergens is List<*>) {
                    rawAllergens.filterIsInstance<String>()
                } else {
                    emptyList()
                }

                binding.cbDairy.isChecked = allergens.contains("Молочные продукты")
                binding.cbMeat.isChecked = allergens.contains("Мясо")
                binding.cbGrains.isChecked = allergens.contains("Крупы")
                binding.cbNuts.isChecked = allergens.contains("Орехи")
                binding.cbSeafood.isChecked = allergens.contains("Морепродукты")
                binding.cbEggs.isChecked = allergens.contains("Яйца")

            } catch (e: Exception) {
                Log.e("AllergenSelection", "Error loading allergens", e)
            }
        }
    }

    private fun saveAllergenPreferences() {
        val userId = auth.currentUser?.uid
        val email = auth.currentUser?.email.orEmpty()

        if (userId.isNullOrBlank()) {
            Toast.makeText(this, "Пользователь не найден", Toast.LENGTH_SHORT).show()
            openMain()
            return
        }

        val selectedAllergens = mutableListOf<String>()
        if (binding.cbDairy.isChecked) selectedAllergens.add("Молочные продукты")
        if (binding.cbMeat.isChecked) selectedAllergens.add("Мясо")
        if (binding.cbGrains.isChecked) selectedAllergens.add("Крупы")
        if (binding.cbNuts.isChecked) selectedAllergens.add("Орехи")
        if (binding.cbSeafood.isChecked) selectedAllergens.add("Морепродукты")
        if (binding.cbEggs.isChecked) selectedAllergens.add("Яйца")

        val userPayload = mapOf(
            "email" to email,
            "allergens" to selectedAllergens,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        lifecycleScope.launch {
            try {
                firestore.collection("users")
                    .document(userId)
                    .set(userPayload, com.google.firebase.firestore.SetOptions.merge())
                    .await()

                Toast.makeText(
                    this@AllergenSelectionActivity,
                    "Аллергены сохранены",
                    Toast.LENGTH_SHORT
                ).show()

                (application as MealCameraApplication).sharedViewModel.notifyAllergensChanged()
                openMain()

            } catch (e: Exception) {
                val errorMsg = e.message ?: "Unknown error"
                Toast.makeText(
                    this@AllergenSelectionActivity,
                    "Ошибка сохранения: $errorMsg",
                    Toast.LENGTH_SHORT
                ).show()
                Log.e("AllergenSelection", "Error saving allergens", e)
            }
        }
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}