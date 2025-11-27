package com.example.mealcamera.ui.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mealcamera.MealCameraApplication
import com.example.mealcamera.data.model.ScannedIngredient
import com.example.mealcamera.databinding.ActivityScanBinding
import com.example.mealcamera.ml.DetectedFood
import com.example.mealcamera.ui.SharedViewModel
import com.example.mealcamera.ui.result.ResultActivity
import com.example.mealcamera.util.toBitmapSafe
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScanBinding
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private lateinit var scannedAdapter: ScannedIngredientAdapter

    // Локальный ViewModel для логики камеры
    private val viewModel: ScanViewModel by viewModels {
        (application as MealCameraApplication).viewModelFactory
    }

    // --- ИСПРАВЛЕНО: Получаем SharedViewModel от Application ---
    private val sharedViewModel: SharedViewModel by lazy {
        ViewModelProvider(
            (application as MealCameraApplication), // Контекст приложения, который является ViewModelStoreOwner
            (application as MealCameraApplication).viewModelFactory
        )[SharedViewModel::class.java]
    }

    companion object {
        private const val CAMERA_PERMISSION_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = ""

        cameraExecutor = Executors.newSingleThreadExecutor()
        if (checkCameraPermission()) {
            startCamera()
        } else {
            requestCameraPermission()
        }

        setupRecyclerView()
        observeViewModels()
        setupClickListeners()
    }

    private fun setupRecyclerView() {
        scannedAdapter = ScannedIngredientAdapter { ingredient ->
            // Удаляем ингредиент через SharedViewModel
            sharedViewModel.removeIngredient(ingredient)
        }
        binding.scannedIngredientsRecyclerView.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.scannedIngredientsRecyclerView.adapter = scannedAdapter
    }

    private fun observeViewModels() {
        // Подписываемся на SharedViewModel для отображения списка
        lifecycleScope.launch {
            sharedViewModel.scannedIngredients.collect { ingredients ->
                binding.bottomPanel.visibility = if (ingredients.isNotEmpty()) View.VISIBLE else View.GONE
                scannedAdapter.submitList(ingredients)
            }
        }

        // Локальный ViewModel управляет только состоянием прогресс-бара
        lifecycleScope.launch {
            viewModel.isProcessing.collect { isProcessing ->
                binding.progressBar.visibility = if (isProcessing) View.VISIBLE else View.GONE
                binding.btnCapture.isEnabled = !isProcessing
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnShowResults.setOnClickListener {
            // Просто запускаем ResultActivity, данные он возьмет сам
            if (sharedViewModel.scannedIngredients.value.isNotEmpty()) {
                startActivity(Intent(this, ResultActivity::class.java))
            } else {
                Toast.makeText(this, "Отсканируйте хотя бы один продукт", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnAddManually.setOnClickListener {
            showAddIngredientDialog()
        }

        binding.btnCapture.setOnClickListener {
            takePhoto()
        }
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        viewModel.setProcessing(true)

        val bitmap = binding.viewFinder.bitmap
        if (bitmap != null) {
            viewModel.processImageWithBitmap(bitmap) { detectedFoods ->
                runOnUiThread { handleDetectionResult(detectedFoods, bitmap) }
            }
        } else {
            imageCapture.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val capturedBitmap = image.toBitmapSafe()
                    image.close()
                    viewModel.processImageWithBitmap(capturedBitmap) { detectedFoods ->
                        runOnUiThread { handleDetectionResult(detectedFoods, capturedBitmap) }
                    }
                }
                override fun onError(exc: ImageCaptureException) {
                    runOnUiThread {
                        Toast.makeText(this@ScanActivity, "Ошибка съемки: ${exc.message}", Toast.LENGTH_SHORT).show()
                        viewModel.setProcessing(false)
                    }
                }
            })
        }
    }

    private fun handleDetectionResult(detectedFoods: List<DetectedFood>, capturedBitmap: Bitmap) {
        if (detectedFoods.isNotEmpty()) {
            val message = detectedFoods.joinToString("\n") { "• ${it.name}" }
            Toast.makeText(this, "Обнаружено:\n$message", Toast.LENGTH_LONG).show()

            // Добавляем ингредиенты через SharedViewModel
            viewModel.getIngredientsFromDetection(detectedFoods, capturedBitmap) { newIngredients ->
                sharedViewModel.addIngredients(newIngredients)
            }
        } else {
            Toast.makeText(this, "Не удалось распознать продукты. Попробуйте другой ракурс.", Toast.LENGTH_LONG).show()
        }
        viewModel.setProcessing(false)
    }

    private fun showAddIngredientDialog() {
        val input = EditText(this)
        input.hint = "Например: яблоко, банан, хлеб"
        AlertDialog.Builder(this)
            .setTitle("Добавить ингредиент вручную")
            .setView(input)
            .setPositiveButton("Добавить") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotBlank()) {
                    // Добавляем вручную через SharedViewModel
                    sharedViewModel.addIngredientManually(name)
                    Toast.makeText(this, "Добавлено: $name", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private fun checkCameraPermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    private fun requestCameraPermission() { ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE) }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) { super.onRequestPermissionsResult(requestCode, permissions, grantResults); if (requestCode == CAMERA_PERMISSION_CODE) { if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) { startCamera() } else {
        Toast.makeText(this, "Нужен доступ к камере", Toast.LENGTH_LONG).show(); finish() } } }
    private fun startCamera() { val cameraProviderFuture = ProcessCameraProvider.getInstance(this); cameraProviderFuture.addListener({ val cameraProvider = cameraProviderFuture.get(); val preview = Preview.Builder().build().also { it.setSurfaceProvider(binding.viewFinder.surfaceProvider) }; imageCapture = ImageCapture.Builder().setTargetRotation(binding.viewFinder.display.rotation).build(); val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA; try { cameraProvider.unbindAll(); cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture) } catch (exc: Exception) { Toast.makeText(this, "Ошибка запуска камеры", Toast.LENGTH_SHORT).show() } }, ContextCompat.getMainExecutor(this)) }
}
