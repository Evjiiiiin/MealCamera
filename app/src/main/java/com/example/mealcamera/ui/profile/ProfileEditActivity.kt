package com.example.mealcamera.ui.profile

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.mealcamera.R
import com.example.mealcamera.data.remote.FirestoreService
import com.example.mealcamera.databinding.ActivityProfileEditBinding
import com.example.mealcamera.ui.auth.LoginActivity
import com.example.mealcamera.ui.home.MainActivity
import com.example.mealcamera.util.ImageStorage
import com.example.mealcamera.util.ThemeManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.InputStream

class ProfileEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileEditBinding
    private val auth = FirebaseAuth.getInstance()
    private val firestoreService = FirestoreService()
    private lateinit var imageStorage: ImageStorage

    private var selectedImageBitmap: Bitmap? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                selectedImageBitmap = uriToBitmap(uri)
                binding.ivProfile.setImageBitmap(selectedImageBitmap)
                Log.d("ProfileEdit", "Фото выбрано")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        imageStorage = ImageStorage(this)

        loadCurrentUserData()
        setupListeners()
        setupThemeToggle()
    }

    private fun uriToBitmap(uri: Uri): Bitmap? {
        return try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            Log.e("ProfileEdit", "uriToBitmap error", e)
            null
        }
    }

    private fun loadCurrentUserData() {
        val user = auth.currentUser
        binding.etName.setText(user?.displayName)

        val avatarFile = imageStorage.getAvatarFile(user?.uid ?: "")
        if (avatarFile.exists()) {
            Glide.with(this)
                .load(avatarFile)
                .placeholder(R.drawable.ic_profile_placeholder)
                .error(R.drawable.ic_profile_placeholder)
                .into(binding.ivProfile)
        } else {
            binding.ivProfile.setImageResource(R.drawable.ic_profile_placeholder)
        }

        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        binding.switchVoice.isChecked = prefs.getBoolean("voice_enabled", true)
        binding.switchCamera.isChecked = prefs.getBoolean("camera_enabled", true)
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.cardProfileImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            pickImageLauncher.launch(intent)
        }

        binding.btnSave.setOnClickListener { saveChanges() }

        binding.btnLogout.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }
    }

    private fun setupThemeToggle() {
        val savedTheme = ThemeManager.getSavedTheme(this)
        val checkId = when (savedTheme) {
            ThemeManager.THEME_LIGHT -> R.id.btnThemeLight
            ThemeManager.THEME_DARK -> R.id.btnThemeDark
            else -> R.id.btnThemeSystem
        }
        binding.toggleTheme.check(checkId)

        binding.toggleTheme.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val themeMode = when (checkedId) {
                    R.id.btnThemeLight -> ThemeManager.THEME_LIGHT
                    R.id.btnThemeDark -> ThemeManager.THEME_DARK
                    else -> ThemeManager.THEME_SYSTEM
                }
                ThemeManager.saveTheme(this, themeMode)
                ThemeManager.applyThemeMode(themeMode)
            }
        }
    }

    private fun saveChanges() {
        val newName = binding.etName.text.toString().trim()
        if (newName.isEmpty()) {
            binding.tilName.error = "Имя не может быть пустым"
            return
        }

        val user = auth.currentUser ?: return
        binding.progressBar.visibility = View.VISIBLE
        binding.btnSave.isEnabled = false

        lifecycleScope.launch {
            try {
                var finalPhotoPath = ""

                if (selectedImageBitmap != null) {
                    finalPhotoPath = imageStorage.saveAvatar(user.uid, selectedImageBitmap!!)
                    Log.d("ProfileEdit", "Аватар сохранён: $finalPhotoPath")
                }

                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(newName)
                    .build()
                user.updateProfile(profileUpdates).await()
                user.reload().await()

                firestoreService.saveUserProfile(user.uid, newName, finalPhotoPath)

                Glide.get(this@ProfileEditActivity).clearMemory()
                Thread { Glide.get(this@ProfileEditActivity).clearDiskCache() }.start()

                val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                prefs.edit().apply {
                    putBoolean("voice_enabled", binding.switchVoice.isChecked)
                    putBoolean("camera_enabled", binding.switchCamera.isChecked)
                    apply()
                }

                Toast.makeText(this@ProfileEditActivity, "Профиль обновлён", Toast.LENGTH_SHORT).show()
                
                // Пересоздаем MainActivity для применения изменений на всех экранах (включая имя)
                val intent = Intent(this@ProfileEditActivity, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                Log.e("ProfileEdit", "Ошибка", e)
                Toast.makeText(this@ProfileEditActivity, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.btnSave.isEnabled = true
            }
        }
    }
}
