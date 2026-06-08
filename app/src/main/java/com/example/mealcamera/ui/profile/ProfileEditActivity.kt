package com.example.mealcamera.ui.profile

import android.app.Activity
import android.content.Context
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.signature.ObjectKey
import com.example.mealcamera.R
import com.example.mealcamera.data.remote.FirestoreService
import com.example.mealcamera.databinding.ActivityProfileEditBinding
import com.example.mealcamera.ui.auth.LoginActivity
import com.example.mealcamera.util.ImageStorage
import com.example.mealcamera.util.ThemeManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ProfileEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileEditBinding
    private val auth = FirebaseAuth.getInstance()
    private val firestoreService = FirestoreService()
    private lateinit var imageStorage: ImageStorage

    private var selectedImageBitmap: Bitmap? = null
    private var isProfileImageRemoved = false
    private var isSyncingPermissionSwitches = false

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                val bitmap = uriToBitmap(uri)
                if (bitmap == null) {
                    Toast.makeText(this, "Не удалось открыть фото", Toast.LENGTH_SHORT).show()
                    return@registerForActivityResult
                }
                selectedImageBitmap = bitmap
                isProfileImageRemoved = false
                binding.ivProfile.setImageBitmap(bitmap)
                binding.btnRemoveProfileImage.visibility = View.VISIBLE
                Log.d("ProfileEdit", "Фото выбрано")
            }
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        setSwitchCheckedSilently(binding.switchCamera, isGranted)
        saveCameraSetting(isGranted, disabledByPermission = !isGranted)
        if (!isGranted) {
            Toast.makeText(this, "Камера выключена: разрешение не выдано", Toast.LENGTH_LONG).show()
        }
    }

    private val voicePermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        setSwitchCheckedSilently(binding.switchVoice, isGranted)
        saveVoiceSetting(isGranted, disabledByPermission = !isGranted)
        if (!isGranted) {
            Toast.makeText(this, "Голосовое управление выключено: разрешение микрофона не выдано", Toast.LENGTH_LONG).show()
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

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) {
            syncPermissionBackedSettings()
        }
    }

    private fun uriToBitmap(uri: Uri): Bitmap? {
        return try {
            val bitmap = contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            } ?: return null

            rotateBitmapIfRequired(uri, bitmap)
        } catch (e: Exception) {
            Log.e("ProfileEdit", "uriToBitmap error", e)
            null
        }
    }

    private fun rotateBitmapIfRequired(uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = contentResolver.openInputStream(uri)?.use { inputStream ->
            ExifInterface(inputStream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } ?: ExifInterface.ORIENTATION_NORMAL

        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }

        if (degrees == 0f) return bitmap

        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun loadCurrentUserData() {
        val user = auth.currentUser
        binding.etName.setText(user?.displayName)

        val avatarFile = imageStorage.getAvatarFile(user?.uid ?: "")
        if (avatarFile.exists()) {
            Glide.with(this)
                .load(avatarFile)
                .signature(ObjectKey(avatarFile.lastModified()))
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .placeholder(R.drawable.ic_profile_placeholder)
                .error(R.drawable.ic_profile_placeholder)
                .into(binding.ivProfile)
            binding.btnRemoveProfileImage.visibility = View.VISIBLE
        } else {
            binding.ivProfile.setImageResource(R.drawable.ic_profile_placeholder)
            binding.btnRemoveProfileImage.visibility = View.GONE
        }

        syncPermissionBackedSettings()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.cardProfileImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            pickImageLauncher.launch(intent)
        }
        binding.btnRemoveProfileImage.setOnClickListener { clearProfileImage() }
        binding.btnRemoveProfileImageIcon.setOnClickListener { clearProfileImage() }

        binding.btnSave.setOnClickListener { saveChanges() }

        binding.switchCamera.setOnCheckedChangeListener { _, isChecked ->
            if (isSyncingPermissionSwitches) return@setOnCheckedChangeListener
            if (isChecked && !hasCameraPermission()) {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            } else {
                saveCameraSetting(isChecked, disabledByPermission = false)
            }
        }

        binding.switchVoice.setOnCheckedChangeListener { _, isChecked ->
            if (isSyncingPermissionSwitches) return@setOnCheckedChangeListener
            if (isChecked && !hasVoicePermission()) {
                voicePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                saveVoiceSetting(isChecked, disabledByPermission = false)
            }
        }

        binding.btnLogout.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }
    }

    private fun clearProfileImage() {
        selectedImageBitmap = null
        isProfileImageRemoved = true
        binding.ivProfile.setImageResource(R.drawable.ic_profile_placeholder)
        binding.btnRemoveProfileImage.visibility = View.GONE
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun hasVoicePermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun syncPermissionBackedSettings() {
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)

        val cameraPermissionGranted = hasCameraPermission()
        val cameraPermissionFlagKnown = prefs.contains(KEY_CAMERA_DISABLED_BY_PERMISSION)
        val cameraWasDisabledByPermission = prefs.getBoolean(KEY_CAMERA_DISABLED_BY_PERMISSION, false)
        val cameraEnabled = when {
            cameraPermissionGranted && cameraWasDisabledByPermission -> true
            cameraPermissionGranted -> prefs.getBoolean("camera_enabled", true)
            else -> false
        }
        setSwitchCheckedSilently(binding.switchCamera, cameraEnabled)
        if (!cameraPermissionGranted && (!cameraPermissionFlagKnown || prefs.getBoolean("camera_enabled", true))) {
            saveCameraSetting(false, disabledByPermission = true)
        } else if (cameraPermissionGranted && cameraWasDisabledByPermission) {
            saveCameraSetting(true, disabledByPermission = false)
        }

        val voicePermissionGranted = hasVoicePermission()
        val voicePermissionFlagKnown = prefs.contains(KEY_VOICE_DISABLED_BY_PERMISSION)
        val voiceWasDisabledByPermission = prefs.getBoolean(KEY_VOICE_DISABLED_BY_PERMISSION, false)
        val voiceEnabled = when {
            voicePermissionGranted && voiceWasDisabledByPermission -> true
            voicePermissionGranted -> prefs.getBoolean("voice_enabled", true)
            else -> false
        }
        setSwitchCheckedSilently(binding.switchVoice, voiceEnabled)
        if (!voicePermissionGranted && (!voicePermissionFlagKnown || prefs.getBoolean("voice_enabled", true))) {
            saveVoiceSetting(false, disabledByPermission = true)
        } else if (voicePermissionGranted && voiceWasDisabledByPermission) {
            saveVoiceSetting(true, disabledByPermission = false)
        }
    }

    private fun setSwitchCheckedSilently(
        switchView: com.google.android.material.materialswitch.MaterialSwitch,
        isChecked: Boolean
    ) {
        isSyncingPermissionSwitches = true
        switchView.isChecked = isChecked
        isSyncingPermissionSwitches = false
    }

    private fun saveCameraSetting(enabled: Boolean, disabledByPermission: Boolean) {
        getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("camera_enabled", enabled)
            .putBoolean(KEY_CAMERA_DISABLED_BY_PERMISSION, disabledByPermission)
            .apply()
    }

    private fun saveVoiceSetting(enabled: Boolean, disabledByPermission: Boolean) {
        getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("voice_enabled", enabled)
            .putBoolean(KEY_VOICE_DISABLED_BY_PERMISSION, disabledByPermission)
            .apply()
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
                val avatarFile = imageStorage.getAvatarFile(user.uid)
                var finalPhotoPath = if (avatarFile.exists() && !isProfileImageRemoved) {
                    avatarFile.absolutePath
                } else {
                    ""
                }

                if (selectedImageBitmap != null) {
                    finalPhotoPath = imageStorage.saveAvatar(user.uid, selectedImageBitmap!!)
                    Log.d("ProfileEdit", "Аватар сохранён: $finalPhotoPath")
                } else if (isProfileImageRemoved && avatarFile.exists()) {
                    imageStorage.deleteImage(avatarFile.absolutePath)
                    Log.d("ProfileEdit", "Аватар удалён")
                }

                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(newName)
                    .build()
                user.updateProfile(profileUpdates).await()
                user.reload().await()

                firestoreService.saveUserProfile(user.uid, newName, finalPhotoPath)

                val cameraDisabledByPermission = binding.switchCamera.isChecked && !hasCameraPermission()
                val voiceDisabledByPermission = binding.switchVoice.isChecked && !hasVoicePermission()
                val cameraEnabled = binding.switchCamera.isChecked && hasCameraPermission()
                val voiceEnabled = binding.switchVoice.isChecked && hasVoicePermission()
                setSwitchCheckedSilently(binding.switchCamera, cameraEnabled)
                setSwitchCheckedSilently(binding.switchVoice, voiceEnabled)
                saveCameraSetting(cameraEnabled, disabledByPermission = cameraDisabledByPermission)
                saveVoiceSetting(voiceEnabled, disabledByPermission = voiceDisabledByPermission)

                Toast.makeText(this@ProfileEditActivity, "Профиль обновлён", Toast.LENGTH_SHORT).show()
                setResult(Activity.RESULT_OK)
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


    companion object {
        private const val KEY_CAMERA_DISABLED_BY_PERMISSION = "camera_disabled_by_permission"
        private const val KEY_VOICE_DISABLED_BY_PERMISSION = "voice_disabled_by_permission"
    }
}