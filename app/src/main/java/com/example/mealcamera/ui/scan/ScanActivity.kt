package com.example.mealcamera.ui.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mealcamera.MealCameraApplication
import com.example.mealcamera.R
import com.example.mealcamera.data.model.ScannedIngredient
import com.example.mealcamera.data.util.UnitHelper
import com.example.mealcamera.databinding.ActivityScanBinding
import com.example.mealcamera.ml.DetectedFood
import com.example.mealcamera.ui.SharedViewModel
import com.example.mealcamera.ui.catalog.IngredientCatalogActivity
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

    private val viewModel: ScanViewModel by viewModels {
        (application as MealCameraApplication).viewModelFactory
    }

    private val sharedViewModel: SharedViewModel by lazy {
        (application as MealCameraApplication).sharedViewModel
    }

    companion object {
        private const val CAMERA_PERMISSION_CODE = 100
        const val EXTRA_DETECTED_INGREDIENTS = "detected_ingredients"
    }

    private val manualSelectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val selectedNames = data?.getStringArrayListExtra("selected_names") ?: return@registerForActivityResult

            // Получаем текущие ингредиенты из временной корзины
            val currentIngredients = sharedViewModel.temporaryIngredients.value
            val currentNames = currentIngredients.map { it.name }.toSet()

            // Добавляем только новые ингредиенты
            val newIngredients = selectedNames
                .filter { name -> !currentNames.contains(name) }
                .map { name ->
                    ScannedIngredient(
                        name = name,
                        imagePath = "",
                        quantity = "1",
                        unit = UnitHelper.getDefaultUnit(name),
                        timestamp = System.currentTimeMillis()
                    )
                }

            if (newIngredients.isNotEmpty()) {
                sharedViewModel.addToTemporary(newIngredients)
                Toast.makeText(this, "Добавлено: ${newIngredients.joinToString { it.name }}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Все ингредиенты уже добавлены", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Проверяем, есть ли активная сессия
        if (sharedViewModel.isSessionActive() && !sharedViewModel.shouldResetSession()) {
            // Если есть активная сессия, показываем диалог
            showActiveSessionDialog()
        }

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

    private fun showActiveSessionDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Активная сессия")
            .setMessage("У вас есть незавершённый подбор рецептов. Хотите продолжить или начать новый?")
            .setPositiveButton("Продолжить") { _, _ ->
                // Переходим в ResultActivity с сохранённой сессией
                startActivity(Intent(this, ResultActivity::class.java))
                finish()
            }
            .setNegativeButton("Новый подбор") { _, _ ->
                // Очищаем сессию и начинаем заново
                sharedViewModel.endSession()
            }
            .setCancelable(false)
            .show()
    }

    private fun setupRecyclerView() {
        scannedAdapter = ScannedIngredientAdapter { ingredient ->
            sharedViewModel.removeFromTemporary(ingredient)
        }
        binding.scannedIngredientsRecyclerView.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.scannedIngredientsRecyclerView.adapter = scannedAdapter
    }

    private fun observeViewModels() {
        binding.bottomPanel.visibility = View.VISIBLE

        lifecycleScope.launch {
            sharedViewModel.temporaryIngredients.collect { ingredients ->
                scannedAdapter.submitList(ingredients)
                binding.btnShowResults.isEnabled = ingredients.isNotEmpty()
                binding.btnShowResults.alpha = if (ingredients.isNotEmpty()) 1f else 0.5f
            }
        }

        lifecycleScope.launch {
            viewModel.isProcessing.collect { isProcessing ->
                binding.progressBar.visibility = if (isProcessing) View.VISIBLE else View.GONE
                binding.btnCapture.isEnabled = !isProcessing
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnShowResults.setOnClickListener {
            val scannedIngredients = sharedViewModel.temporaryIngredients.value
            if (scannedIngredients.isNotEmpty()) {
                // Начинаем новую сессию с выбранными ингредиентами
                sharedViewModel.startSession(scannedIngredients)

                val intent = Intent(this, ResultActivity::class.java)
                startActivity(intent)
            } else {
                Toast.makeText(this, "Добавьте хотя бы один ингредиент", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnAddManually.setOnClickListener {
            val intent = Intent(this, IngredientCatalogActivity::class.java)
            val selectedNames = sharedViewModel.temporaryIngredients.value.map { it.name }
            intent.putStringArrayListExtra("selected_names", ArrayList(selectedNames))
            manualSelectionLauncher.launch(intent)
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
                    val capturedBitmap: Bitmap = image.toBitmapSafe()
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

            viewModel.getIngredientsFromDetection(detectedFoods, capturedBitmap) { newIngredients ->
                sharedViewModel.addToTemporary(newIngredients)
                Toast.makeText(this, "Добавлено: ${newIngredients.joinToString { it.name }}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Не удалось распознать продукты. Попробуйте другой ракурс.", Toast.LENGTH_LONG).show()
        }
        viewModel.setProcessing(false)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private fun checkCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                Toast.makeText(this, "Нужен доступ к камере", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder()
                .setTargetRotation(binding.viewFinder.display.rotation)
                .build()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (exc: Exception) {
                Toast.makeText(this, "Ошибка запуска камеры", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }
}