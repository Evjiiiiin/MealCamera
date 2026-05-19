package com.example.mealcamera.ui.scan

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.camera.view.LifecycleCameraController
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mealcamera.MealCameraApplication
import com.example.mealcamera.R
import com.example.mealcamera.data.model.ScannedIngredient
import com.example.mealcamera.data.util.UnitHelper
import com.example.mealcamera.databinding.ActivityScanBinding
import com.example.mealcamera.ml.DetectedFood
import com.example.mealcamera.ml.RoboflowFoodDetector
import com.example.mealcamera.ml.TFLiteFoodDetector
import com.example.mealcamera.ui.SharedViewModel
import com.example.mealcamera.ui.catalog.IngredientCatalogActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScanFragment : Fragment() {

    private var _binding: ActivityScanBinding? = null
    private val binding get() = _binding!!

    private var cameraController: LifecycleCameraController? = null
    private var cameraExecutor: ExecutorService? = null
    private var localDetector: TFLiteFoodDetector? = null
    private var roboflowDetector: RoboflowFoodDetector? = null
    private var loadingDialog: AlertDialog? = null

    private lateinit var cardsAdapter: ScannedCardsAdapter

    private val viewModel: ScanViewModel by viewModels {
        (requireActivity().application as MealCameraApplication).viewModelFactory
    }

    private val sharedViewModel: SharedViewModel by lazy {
        (requireActivity().application as MealCameraApplication).sharedViewModel
    }

    private fun isAllergen(name: String): Boolean {
        val normalized = name.trim().lowercase().replace("ё", "е")
        return viewModel.userAllergens.value.any { normalized.contains(it) }
    }

    private val catalogLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val selectedNames = result.data?.getStringArrayListExtra("selected_names")
                    ?: return@registerForActivityResult

                val allergens = selectedNames.filter { isAllergen(it) }
                if (allergens.isNotEmpty()) {
                    Toast.makeText(
                        requireContext(),
                        "Некоторые продукты не добавлены из-за аллергии: ${allergens.joinToString()}",
                        Toast.LENGTH_LONG
                    ).show()
                }

                val filteredNames = selectedNames.filter { !isAllergen(it) }
                val currentIngs = sharedViewModel.temporaryIngredients.value.toMutableList()

                val normalizedSelected = filteredNames
                    .map { it.trim().lowercase().replace("ё", "е") }
                    .toSet()

                currentIngs.removeAll { ing ->
                    !normalizedSelected.contains(ing.name.trim().lowercase().replace("ё", "е"))
                }

                filteredNames.forEach { name ->
                    val exists = currentIngs.any { it.name.equals(name, ignoreCase = true) }
                    if (!exists) {
                        currentIngs.add(
                            ScannedIngredient(
                                name = name,
                                imagePath = "",
                                quantity = "1",
                                unit = UnitHelper.getDefaultUnit(name),
                                timestamp = System.currentTimeMillis() + name.hashCode()
                            )
                        )
                    }
                }

                sharedViewModel.setTemporaryIngredients(currentIngs)
            }
        }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) startCamera()
            else Toast.makeText(requireContext(), "Нужно разрешение на камеру", Toast.LENGTH_LONG).show()
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ActivityScanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cameraExecutor = Executors.newSingleThreadExecutor()
        localDetector = TFLiteFoodDetector(requireContext())
        roboflowDetector = RoboflowFoodDetector(requireContext())

        setupCardsRecyclerView()
        setupBackPressed()
        setupClickListeners()
        observeIngredientsOnly()
        showDetectorModeHint()

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun setupCardsRecyclerView() {
        cardsAdapter = ScannedCardsAdapter { ingredient ->
            sharedViewModel.removeFromTemporary(ingredient)
        }
        binding.rvScannedIngredients.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = cardsAdapter
        }
    }

    private fun setupBackPressed() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (sharedViewModel.temporaryIngredients.value.isNotEmpty()) {
                        AlertDialog.Builder(requireContext())
                            .setTitle("Сохранить черновик?")
                            .setMessage("Сохранить найденные продукты для следующего раза?")
                            .setPositiveButton("Сохранить") { _, _ -> findNavController().popBackStack() }
                            .setNegativeButton("Очистить") { _, _ ->
                                sharedViewModel.clearTemporary()
                                findNavController().popBackStack()
                            }
                            .setNeutralButton("Отмена", null)
                            .show()
                    } else {
                        findNavController().popBackStack()
                    }
                }
            }
        )
    }

    private fun startCamera() {
        val controller = LifecycleCameraController(requireContext())
        controller.bindToLifecycle(viewLifecycleOwner)
        binding.viewFinder.controller = controller
        cameraController = controller
    }

    private fun showDetectorModeHint() {
        val cloudAvailable = roboflowDetector?.isAvailable() == true
        val mode = if (cloudAvailable) {
            "Режим: только Roboflow (по снимку)"
        } else {
            "Режим: только локальная модель (по снимку)"
        }
        Toast.makeText(requireContext(), mode, Toast.LENGTH_SHORT).show()
        Log.d(TAG, mode)
    }

    private fun observeIngredientsOnly() {
        viewLifecycleOwner.lifecycleScope.launch {
            sharedViewModel.temporaryIngredients.collect { ingredients ->
                cardsAdapter.submitList(ingredients)
                binding.btnShowResults.isEnabled = ingredients.isNotEmpty()
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnShowResults.setOnClickListener {
            val ingredients = sharedViewModel.temporaryIngredients.value
            if (ingredients.isNotEmpty()) {
                sharedViewModel.startSession(ingredients)
                findNavController().navigate(R.id.navigation_result)
            }
        }

        binding.btnCapture.setOnClickListener {
            val bitmap = binding.viewFinder.bitmap
            if (bitmap == null) {
                Toast.makeText(requireContext(), "Камера не готова", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            showLoadingDialog()
            binding.btnCapture.isEnabled = false

            viewLifecycleOwner.lifecycleScope.launch {
                val detections = withContext(Dispatchers.IO) {
                    val cloud = roboflowDetector
                    if (cloud?.isAvailable() == true) {
                        Log.d(TAG, "Capture mode: CLOUD_ONLY")
                        cloud.detectFood(bitmap)
                    } else {
                        Log.d(TAG, "Capture mode: LOCAL_ONLY")
                        localDetector?.detectFood(bitmap).orEmpty()
                    }
                }

                hideLoadingDialog()
                binding.btnCapture.isEnabled = true

                if (detections.isEmpty()) {
                    Toast.makeText(
                        requireContext(),
                        "Продукты не обнаружены. Попробуйте другой ракурс/свет.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                val allergenDetections = detections.filter { isAllergen(it.name) }
                if (allergenDetections.isNotEmpty()) {
                    AlertDialog.Builder(requireContext())
                        .setTitle("Обнаружена аллергия")
                        .setMessage(
                            "Обнаружен продукт (${allergenDetections.first().name}), который не рекомендуется пользователю из-за аллергии. Добавить остальные продукты?"
                        )
                        .setPositiveButton("Добавить безопасные") { _, _ ->
                            processDetections(bitmap, detections.filter { !isAllergen(it.name) })
                        }
                        .setNegativeButton("Отмена", null)
                        .show()
                } else {
                    processDetections(bitmap, detections)
                }
            }
        }

        binding.btnAddManually.setOnClickListener {
            val intent = Intent(requireContext(), IngredientCatalogActivity::class.java).apply {
                putStringArrayListExtra(
                    "selected_names",
                    ArrayList(sharedViewModel.temporaryIngredients.value.map { it.name })
                )
            }
            catalogLauncher.launch(intent)
        }

        binding.btnBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun showLoadingDialog() {
        if (loadingDialog?.isShowing == true) return

        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(56, 40, 56, 40)
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val progressBar = ProgressBar(requireContext()).apply {
            isIndeterminate = true
        }

        val textView = TextView(requireContext()).apply {
            text = "Анализируем фото..."
            textSize = 16f
            setPadding(32, 0, 0, 0)
        }

        container.addView(progressBar)
        container.addView(textView)

        loadingDialog = AlertDialog.Builder(requireContext())
            .setView(container)
            .setCancelable(false)
            .create()
            .also { it.show() }
    }

    private fun hideLoadingDialog() {
        loadingDialog?.dismiss()
        loadingDialog = null
    }

    private fun processDetections(bitmap: android.graphics.Bitmap, detections: List<DetectedFood>) {
        viewModel.processCapturedDetections(bitmap, detections) { newIngredients ->
            if (newIngredients.isNotEmpty()) {
                sharedViewModel.addToTemporary(newIngredients)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        hideLoadingDialog()
        cameraExecutor?.shutdown()
        cameraExecutor = null
        localDetector?.close()
        localDetector = null
        roboflowDetector = null
        cameraController = null
        _binding = null
    }

    companion object {
        private const val TAG = "ScanFragment"
    }
}