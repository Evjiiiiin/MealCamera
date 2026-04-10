package com.example.mealcamera.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import com.example.mealcamera.databinding.ActivityRegisterBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        setupListeners()
    }

    private fun setupListeners() {
        // Очистка ошибок при вводе текста
        binding.etEmail.doOnTextChanged { _, _, _, _ ->
            binding.tilEmail.error = null
        }
        binding.etPassword.doOnTextChanged { _, _, _, _ ->
            binding.tilPassword.error = null
        }
        binding.etConfirmPassword.doOnTextChanged { _, _, _, _ ->
            binding.tilConfirmPassword.error = null
        }

        binding.btnDoRegister.setOnClickListener {
            if (validateInput()) {
                performRegistration()
            }
        }

        binding.btnBackToLogin.setOnClickListener {
            finish()
        }
    }

    private fun validateInput(): Boolean {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val confirmPassword = binding.etConfirmPassword.text.toString().trim()
        var isValid = true

        if (email.isEmpty()) {
            binding.tilEmail.error = "Введите email"
            isValid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = "Некорректный формат email"
            isValid = false
        }

        if (password.isEmpty()) {
            binding.tilPassword.error = "Введите пароль"
            isValid = false
        } else if (password.length < 6) {
            binding.tilPassword.error = "Пароль должен быть не менее 6 символов"
            isValid = false
        }

        if (confirmPassword != password) {
            binding.tilConfirmPassword.error = "Пароли не совпадают"
            isValid = false
        }

        return isValid
    }

    private fun performRegistration() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        binding.btnDoRegister.isEnabled = false

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                binding.btnDoRegister.isEnabled = true
                if (task.isSuccessful) {
                    Toast.makeText(this, "Аккаунт успешно создан!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, AllergenSelectionActivity::class.java))
                    finishAffinity()
                } else {
                    when (task.exception) {
                        is FirebaseAuthUserCollisionException -> {
                            binding.tilEmail.error = "Пользователь с такой почтой уже существует"
                        }
                        is FirebaseAuthInvalidCredentialsException -> {
                            binding.tilEmail.error = "Некорректный формат почты"
                        }
                        else -> {
                            Toast.makeText(this, "Ошибка регистрации. Проверьте интернет", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
    }
}
