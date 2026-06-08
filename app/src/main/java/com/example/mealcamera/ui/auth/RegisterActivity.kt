package com.example.mealcamera.ui.auth

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import com.example.mealcamera.data.remote.FirestoreService
import com.example.mealcamera.databinding.ActivityRegisterBinding
import com.example.mealcamera.util.GoogleAuthHelper
import com.example.mealcamera.util.YandexAuthHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var googleAuthHelper: GoogleAuthHelper
    private lateinit var yandexAuthHelper: YandexAuthHelper
    private val firestoreService = FirestoreService()

    private var selectedImageUri: Uri? = null

    private val YANDEX_SALT = "MealCamera_Yandex_Salt_2024"

    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedImageUri = it
            binding.ivRegisterProfile.setImageURI(it)
        }
    }

    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val credential = googleAuthHelper.getCredential(result.data)
        if (credential != null) {
            signInWithCredential(credential)
        }
    }

    private val yandexSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val token = yandexAuthHelper.handleResult(result.resultCode, result.data)
        if (token != null) {
            handleYandexToken(token)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        googleAuthHelper = GoogleAuthHelper(this)
        yandexAuthHelper = YandexAuthHelper(this)

        setupListeners()
    }

    private fun setupListeners() {
        binding.etName.doOnTextChanged { _, _, _, _ -> binding.tilName.error = null }
        binding.etEmail.doOnTextChanged { _, _, _, _ -> binding.tilEmail.error = null }
        binding.etPassword.doOnTextChanged { _, _, _, _ -> binding.tilPassword.error = null }

        binding.cardProfileImage.setOnClickListener { imagePicker.launch("image/*") }

        binding.btnDoRegister.setOnClickListener {
            if (validateInput()) {
                performRegistration()
            }
        }

        binding.tvGoToLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        binding.btnGoogleAuth.setOnClickListener {
            googleAuthHelper.signIn(googleSignInLauncher)
        }

        binding.btnYandexAuth.setOnClickListener {
            yandexAuthHelper.signIn(yandexSignInLauncher)
        }
    }

    private fun signInWithCredential(credential: com.google.firebase.auth.AuthCredential) {
        lifecycleScope.launch {
            try {
                val result = auth.signInWithCredential(credential).await()
                val user = result.user
                if (user != null) {
                    firestoreService.saveUserProfile(user.uid, user.displayName ?: "User", user.photoUrl?.toString() ?: "")
                    startActivity(Intent(this@RegisterActivity, AllergenSelectionActivity::class.java))
                    finishAffinity()
                }
            } catch (e: Exception) {
                Toast.makeText(this@RegisterActivity, "Ошибка Google: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleYandexToken(token: String) {
        lifecycleScope.launch {
            try {
                val userInfo = yandexAuthHelper.fetchUserInfo(token)
                if (userInfo == null) {
                    Toast.makeText(this@RegisterActivity, "Данные Яндекса не получены", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val yandexId = userInfo.optString("id")
                val name = userInfo.optString("display_name", "Yandex User")
                val avatarUrl = "https://avatars.yandex.net/get-yapic/${userInfo.optString("default_avatar_id")}/islands-200"

                val email = "yandex_${yandexId}@mealcamera.app"
                val password = generatePassword(yandexId)

                try {
                    auth.signInWithEmailAndPassword(email, password).await()
                } catch (e: FirebaseAuthInvalidCredentialsException) {
                    auth.createUserWithEmailAndPassword(email, password).await()
                }

                val uid = auth.currentUser!!.uid
                firestoreService.saveUserProfile(uid, name, avatarUrl, yandexId)

                startActivity(Intent(this@RegisterActivity, AllergenSelectionActivity::class.java))
                finishAffinity()
            } catch (e: Exception) {
                Toast.makeText(this@RegisterActivity, "Ошибка Яндекс: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun generatePassword(yandexId: String): String {
        val input = YANDEX_SALT + yandexId
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }.take(24)
    }

    private fun validateInput(): Boolean {
        val name = binding.etName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        var isValid = true

        if (name.isEmpty()) {
            binding.tilName.error = "Введите ваше имя"
            isValid = false
        }

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
        } else if (!password.any { it.isDigit() } || !password.any { it.isLetter() }) {
            binding.tilPassword.error = "Пароль должен содержать буквы и цифры"
            isValid = false
        }

        return isValid
    }

    private fun performRegistration() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val name = binding.etName.text.toString().trim()

        binding.btnDoRegister.isEnabled = false

        lifecycleScope.launch {
            try {
                // проверка существования пользователя через попытку создания
                val result = auth.createUserWithEmailAndPassword(email, password).await()
                val user = result.user

                if (user != null) {
                    var photoUrl = ""
                    selectedImageUri?.let { uri ->
                        photoUrl = uploadProfileImage(user.uid, uri)
                    }

                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(name)
                        .setPhotoUri(Uri.parse(photoUrl))
                        .build()
                    user.updateProfile(profileUpdates).await()

                    firestoreService.saveUserProfile(user.uid, name, photoUrl)

                    Toast.makeText(this@RegisterActivity, "Добро пожаловать, $name!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@RegisterActivity, AllergenSelectionActivity::class.java))
                    finishAffinity()
                }
            } catch (e: Exception) {
                binding.btnDoRegister.isEnabled = true
                handleRegistrationError(e)
            }
        }
    }

    private suspend fun uploadProfileImage(userId: String, uri: Uri): String {
        return try {
            val storageRef = FirebaseStorage.getInstance().reference.child("avatars/$userId.jpg")
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos)
            storageRef.putBytes(baos.toByteArray()).await()
            storageRef.downloadUrl.await().toString()
        } catch (e: Exception) {
            ""
        }
    }

    private fun handleRegistrationError(e: Exception) {
        when (e) {
            is FirebaseAuthUserCollisionException -> {
                binding.tilEmail.error = "Аккаунт с таким email уже зарегистрирован"
            }
            is FirebaseAuthInvalidCredentialsException -> {
                binding.tilEmail.error = "Некорректная почта"
            }
            else -> {
                Toast.makeText(this, "Ошибка регистрации: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }
}