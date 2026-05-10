package com.example.mealcamera.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.mealcamera.data.remote.FirestoreService
import com.example.mealcamera.databinding.ActivityLoginBinding
import com.example.mealcamera.ui.home.MainActivity
import com.example.mealcamera.util.GoogleAuthHelper
import com.example.mealcamera.util.YandexAuthHelper
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
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

    // Секретный ключ для генерации пароля (никогда не меняйте после релиза)
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

        // Если уже авторизован, переходим на главный экран
        if (auth.currentUser != null && !auth.currentUser!!.isAnonymous) {
            startMainActivity()
        } else if (auth.currentUser?.isAnonymous == true) {
            // Если остался анонимный пользователь, выходим из него
            auth.signOut()
        }

        setupClickListeners()
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

    private fun loginWithEmail() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Заполните все поля", Toast.LENGTH_SHORT).show()
            return
        }
        showLoading()
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                hideLoading()
                if (task.isSuccessful) startMainActivity()
                else Toast.makeText(this, "Ошибка: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
            }
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
                    // Пробуем войти с существующим аккаунтом
                    auth.signInWithEmailAndPassword(email, password).await()
                } catch (e: FirebaseAuthInvalidCredentialsException) {
                    // Аккаунт не существует, создаём новый
                    auth.createUserWithEmailAndPassword(email, password).await()
                } catch (e: FirebaseAuthUserCollisionException) {
                    // Теоретически невозможная ситуация, но обработаем
                    auth.signInWithEmailAndPassword(email, password).await()
                }

                val uid = auth.currentUser!!.uid
                // Обновляем профиль в Firestore
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
        binding.btnLogin.isEnabled = false
    }

    private fun hideLoading() {
        binding.progressBar.visibility = View.GONE
        binding.btnLogin.isEnabled = true
    }

    private fun startMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}