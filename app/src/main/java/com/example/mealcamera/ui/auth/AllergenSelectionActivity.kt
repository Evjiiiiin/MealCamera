package com.example.mealcamera.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.mealcamera.databinding.ActivityAllergenSelectionBinding
import com.example.mealcamera.ui.home.MainActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class AllergenSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAllergenSelectionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAllergenSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnContinue.setOnClickListener {
            saveAllergenPreferences()
        }

        binding.btnSkip.setOnClickListener {
            openMain()
        }
    }

    private fun saveAllergenPreferences() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        val email = FirebaseAuth.getInstance().currentUser?.email.orEmpty()

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

        val payload = mapOf(
            "email" to email,
            "allergens" to selectedAllergens,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId)
            .set(payload)
            .addOnSuccessListener {
                Toast.makeText(this, "Предпочтения сохранены", Toast.LENGTH_SHORT).show()
                openMain()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Не удалось сохранить: ${e.message}", Toast.LENGTH_LONG).show()
                openMain()
            }
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finishAffinity()
    }
}
