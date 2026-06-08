package com.example.mealcamera.ui.auth

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import com.example.mealcamera.data.remote.FirestoreService
import com.example.mealcamera.databinding.ActivityLoginBinding
import com.example.mealcamera.ui.home.MainActivity
import com.example.mealcamera.util.GoogleAuthHelper
import com.example.mealcamera.util.YandexAuthHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.MessageDigest

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var googleAuthHelper: GoogleAuthHelper
    private lateinit var yandexAuthHelper: YandexAuthHelper
    private val firestoreService = FirestoreService()

    private val authPrefs by lazy { getSharedPreferences("auth_security", Context.MODE_PRIVATE) }
    private var loginBlockTimer: CountDownTimer? = null
    private var isEmailLoginLoading = false
    private var isEmailLoginBlocked = false

    private val YANDEX_SALT = "MealCamera_Yandex_Salt_2024"

    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val credential = googleAuthHelper.getCredential(result.data)
        if (credential != null) {
            signInWithCredential(credential)
        } else {
            hideLoading()
            Toast.makeText(this, "Ошибка входа Google", Toast.LENGTH_SHORT).show()
        }
    }

    private val yandexSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val token = yandexAuthHelper.handleResult(result.resultCode, result.data)
        if (token != null) {
            handleYandexToken(token)
        } else {
            hideLoading()
            Toast.makeText(this, "Ошибка входа Яндекс", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        googleAuthHelper = GoogleAuthHelper(this)
        yandexAuthHelper = YandexAuthHelper(this)

        // если уже авторизован, переход на главный экран
        if (auth.currentUser != null && !auth.currentUser!!.isAnonymous) {
            startMainActivity()
        } else if (auth.currentUser?.isAnonymous == true) {
            // если остался анонимный пользователь, выходим из него
            auth.signOut()
        }

        setupClickListeners()
        setupInputErrorReset()
        checkLockoutStatus()
    }

    private fun setupClickListeners() {
        binding.btnLogin.setOnClickListener { loginWithEmail() }
        binding.btnGoogleAuth.setOnClickListener {
            showLoading()
            googleAuthHelper.signIn(googleSignInLauncher)
        }
        binding.btnYandexAuth.setOnClickListener {
            showLoading()
            yandexAuthHelper.signIn(yandexSignInLauncher)
        }
        binding.tvGoToRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun setupInputErrorReset() {
        binding.etEmail.doOnTextChanged { _, _, _, _ -> binding.tilEmail.error = null }
        binding.etPassword.doOnTextChanged { _, _, _, _ -> binding.tilPassword.error = null }
    }

    private fun checkLockoutStatus() {
        val lockoutUntil = authPrefs.getLong(KEY_LOCKOUT_UNTIL, 0L)
        val remainingMillis = lockoutUntil - System.currentTimeMillis()

        if (remainingMillis > 0L) {
            startEmailLoginBlock(remainingMillis, resetAttemptsOnFinish = true)
        } else if (lockoutUntil > 0L) {
            resetAttempts()
        }
    }

    private fun loginWithEmail() {
        if (isEmailLoginBlocked) return

        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        if (!validateEmailLoginInput(email, password)) return

        showLoading()
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                hideLoading()
                if (task.isSuccessful) {
                    resetAttempts()
                    startMainActivity()
                } else {
                    handleEmailLoginError(task.exception)
                }
            }
    }

    private fun validateEmailLoginInput(email: String, password: String): Boolean {
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

    private fun handleEmailLoginError(exception: Exception?) {
        val attempts = authPrefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        authPrefs.edit()
            .putInt(KEY_FAILED_ATTEMPTS, attempts)
            .apply()

        showEmailLoginError(exception)

        when {
            attempts >= MAX_ATTEMPTS_BEFORE_LOCKOUT -> {
                val lockoutUntil = System.currentTimeMillis() + LOCKOUT_MS
                authPrefs.edit()
                    .putLong(KEY_LOCKOUT_UNTIL, lockoutUntil)
                    .apply()
                Toast.makeText(this, "Слишком много попыток. Вход заблокирован на 1 минуту", Toast.LENGTH_LONG).show()
                startEmailLoginBlock(LOCKOUT_MS, resetAttemptsOnFinish = true)
            }
            attempts >= MAX_ATTEMPTS_BEFORE_DELAY -> {
                Toast.makeText(this, "Слишком много попыток. Подождите 5 секунд", Toast.LENGTH_SHORT).show()
                startEmailLoginBlock(DELAY_MS, resetAttemptsOnFinish = false)
            }
        }
    }

    private fun showEmailLoginError(exception: Exception?) {
        when (exception) {
            is FirebaseAuthInvalidUserException -> binding.tilEmail.error = "Пользователь не найден"
            is FirebaseAuthInvalidCredentialsException -> binding.tilPassword.error = "Неверный email или пароль"
            else -> Toast.makeText(this, "Ошибка входа. Проверьте интернет", Toast.LENGTH_LONG).show()
        }
    }

    private fun startEmailLoginBlock(millis: Long, resetAttemptsOnFinish: Boolean) {
        loginBlockTimer?.cancel()
        isEmailLoginBlocked = true
        updateEmailLoginControls()

        loginBlockTimer = object : CountDownTimer(millis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = ((millisUntilFinished + 999) / 1000).coerceAtLeast(1)
                binding.btnLogin.text = "Войти через $seconds сек"
            }

            override fun onFinish() {
                isEmailLoginBlocked = false
                binding.btnLogin.text = "Войти"
                if (resetAttemptsOnFinish) resetAttempts()
                updateEmailLoginControls()
                loginBlockTimer = null
            }
        }.start()
    }

    private fun resetAttempts() {
        authPrefs.edit()
            .remove(KEY_FAILED_ATTEMPTS)
            .remove(KEY_LOCKOUT_UNTIL)
            .apply()
        binding.btnLogin.text = "Войти"
    }

    private fun signInWithCredential(credential: com.google.firebase.auth.AuthCredential) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    task.result?.user?.let { firebaseUser ->
                        lifecycleScope.launch {
                            firestoreService.saveUserProfile(
                                firebaseUser.uid,
                                firebaseUser.displayName ?: "Google User",
                                firebaseUser.photoUrl?.toString() ?: ""
                            )
                            hideLoading()
                            startMainActivity()
                        }
                    }
                } else {
                    hideLoading()
                    Toast.makeText(this, "Ошибка: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun handleYandexToken(token: String) {
        lifecycleScope.launch {
            try {
                val userInfo = withContext(Dispatchers.IO) {
                    yandexAuthHelper.fetchUserInfo(token)
                }
                if (userInfo == null) {
                    hideLoading()
                    Toast.makeText(this@LoginActivity, "Данные Яндекса не получены", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val yandexId = userInfo.optString("id")
                val name = userInfo.optString("display_name", "Yandex User")
                val avatarUrl = "https://avatars.yandex.net/get-yapic/${userInfo.optString("default_avatar_id")}/islands-200"

                val email = "yandex_${yandexId}@mealcamera.app"
                val password = generatePassword(yandexId)

                try {
                    // существующий аккаунт
                    auth.signInWithEmailAndPassword(email, password).await()
                } catch (e: FirebaseAuthInvalidCredentialsException) {
                    // новый аккаунт
                    auth.createUserWithEmailAndPassword(email, password).await()
                } catch (e: FirebaseAuthUserCollisionException) {
                    auth.signInWithEmailAndPassword(email, password).await()
                }

                val uid = auth.currentUser!!.uid
                // обновление профиля в Firestore
                firestoreService.saveUserProfile(uid, name, avatarUrl, yandexId)

                hideLoading()
                startMainActivity()
            } catch (e: Exception) {
                hideLoading()
                Toast.makeText(this@LoginActivity, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun generatePassword(yandexId: String): String {
        val input = YANDEX_SALT + yandexId
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }.take(24) // берём 24 символа
    }

    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        isEmailLoginLoading = true
        updateEmailLoginControls()
    }

    private fun hideLoading() {
        binding.progressBar.visibility = View.GONE
        isEmailLoginLoading = false
        updateEmailLoginControls()
    }

    private fun updateEmailLoginControls() {
        val emailLoginEnabled = !isEmailLoginLoading && !isEmailLoginBlocked
        binding.btnLogin.isEnabled = emailLoginEnabled
        binding.etEmail.isEnabled = emailLoginEnabled
        binding.etPassword.isEnabled = emailLoginEnabled
    }

    private fun startMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        loginBlockTimer?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_LOCKOUT_UNTIL = "lockout_until"
        private const val MAX_ATTEMPTS_BEFORE_DELAY = 3
        private const val MAX_ATTEMPTS_BEFORE_LOCKOUT = 5
        private const val DELAY_MS = 5_000L
        private const val LOCKOUT_MS = 60_000L
    }
}