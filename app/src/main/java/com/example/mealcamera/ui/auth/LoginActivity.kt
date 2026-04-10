package com.example.mealcamera.ui.auth

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Patterns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.widget.doOnTextChanged
import com.example.mealcamera.databinding.ActivityLoginBinding
import com.example.mealcamera.ui.home.MainActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    
    private val prefs by lazy { getSharedPreferences("auth_security", Context.MODE_PRIVATE) }
    private var lockoutTimer: CountDownTimer? = null

    companion object {
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_LOCKOUT_TIME = "lockout_time"
        private const val MAX_ATTEMPTS_BEFORE_DELAY = 3
        private const val MAX_ATTEMPTS_BEFORE_LOCKOUT = 5
        private const val DELAY_MS = 2000L
        private const val LOCKOUT_MS = 60000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        setupListeners()
        checkLockoutStatus()
    }

    private fun setupListeners() {
        binding.etEmail.doOnTextChanged { _, _, _, _ -> binding.tilEmail.error = null }
        binding.etPassword.doOnTextChanged { _, _, _, _ -> binding.tilPassword.error = null }

        binding.btnLogin.setOnClickListener {
            if (validateInput()) {
                performLogin()
            }
        }

        binding.btnRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun checkLockoutStatus() {
        val lockoutUntil = prefs.getLong(KEY_LOCKOUT_TIME, 0)
        val currentTime = System.currentTimeMillis()

        if (currentTime < lockoutUntil) {
            startLockoutTimer(lockoutUntil - currentTime)
        }
    }

    private fun startLockoutTimer(millis: Long) {
        binding.btnLogin.isEnabled = false
        lockoutTimer?.cancel()
        
        lockoutTimer = object : CountDownTimer(millis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                val text = "Блокировка: $seconds сек"
                binding.btnLogin.text = text
            }

            override fun onFinish() {
                binding.btnLogin.isEnabled = true
                binding.btnLogin.text = "Войти"
                resetAttempts()
            }
        }.start()
    }

    private fun validateInput(): Boolean {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
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
        }

        return isValid
    }

    private fun performLogin() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        val attempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0)
        
        if (attempts >= MAX_ATTEMPTS_BEFORE_DELAY && attempts < MAX_ATTEMPTS_BEFORE_LOCKOUT) {
            Toast.makeText(this, "Слишком много попыток. Подождите...", Toast.LENGTH_SHORT).show()
            binding.btnLogin.postDelayed({ executeLogin(email, password) }, DELAY_MS)
        } else {
            executeLogin(email, password)
        }
    }

    private fun executeLogin(email: String, password: String) {
        binding.btnLogin.isEnabled = false

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    resetAttempts()
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                } else {
                    binding.btnLogin.isEnabled = true
                    handleLoginError(task.exception)
                }
            }
    }

    private fun handleLoginError(exception: Exception?) {
        val attempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        prefs.edit { putInt(KEY_FAILED_ATTEMPTS, attempts) }

        if (attempts >= MAX_ATTEMPTS_BEFORE_LOCKOUT) {
            val lockoutTime = System.currentTimeMillis() + LOCKOUT_MS
            prefs.edit { putLong(KEY_LOCKOUT_TIME, lockoutTime) }
            startLockoutTimer(LOCKOUT_MS)
            Toast.makeText(this, "Аккаунт временно заблокирован", Toast.LENGTH_LONG).show()
        } else {
            when (exception) {
                is FirebaseAuthInvalidUserException -> binding.tilEmail.error = "Пользователь не найден"
                is FirebaseAuthInvalidCredentialsException -> binding.tilPassword.error = "Неверный пароль"
                else -> Toast.makeText(this, "Ошибка входа. Проверьте интернет", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun resetAttempts() {
        prefs.edit { 
            remove(KEY_FAILED_ATTEMPTS)
            remove(KEY_LOCKOUT_TIME)
        }
        binding.btnLogin.text = "Войти"
    }

    override fun onDestroy() {
        lockoutTimer?.cancel()
        super.onDestroy()
    }
}