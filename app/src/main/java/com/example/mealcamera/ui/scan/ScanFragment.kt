package com.example.mealcamera.ui.scan

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.LifecycleCameraController
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mealcamera.MealCameraApplication
import com.example.mealcamera.R
import com.example.mealcamera.data.model.ScannedIngredient
import com.example.mealcamera.data.util.UnitHelper
import com.example.mealcamera.databinding.ActivityScanBinding
import com.example.mealcamera.ml.TFLiteFoodDetector
import com.example.mealcamera.ui.SharedViewModel
import com.example.mealcamera.ui.catalog.IngredientCatalogActivity
import com.example.mealcamera.util.toBitmapSafe
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScanFragment : Fragment() {

    private var _binding: ActivityScanBinding? = null
    private val binding get() = _binding!!

    private var cameraController: LifecycleCameraController? = null
    private var cameraExecutor: ExecutorService? = null
    private var detector: TFLiteFoodDetector? = null
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

    private val catalogLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val selectedNames = result.data?.getStringArrayListExtra("selected_names") ?: return@registerForActivityResult
            
            // Фильтрация по аллергенам
            val allergens = selectedNames.filter { isAllergen(it) }
            if (allergens.isNotEmpty()) {
                Toast.makeText(requireContext(), "Некоторые продукты не добавлены из-за аллергии: ${allergens.joinToString()}", Toast.LENGTH_LONG).show()
            }
            val filteredNames = selectedNames.filter { !isAllergen(it) }

            val currentIngs = sharedViewModel.temporaryIngredients.value.toMutableList()

            // 1. Удаляем ингредиенты, которые больше не выбраны в каталоге
            val normalizedSelected = filteredNames.map { it.trim().lowercase().replace("ё", "е") }.toSet()
            currentIngs.removeAll { ing ->
                !normalizedSelected.contains(ing.name.trim().lowercase().replace("ё", "е"))
            }

            // 2. Добавляем новые, которых не было
            filteredNames.forEach { name ->
                val isAlreadyPresent = currentIngs.any { it.name.equals(name, ignoreCase = true) }
                if (!isAlreadyPresent) {
                    currentIngs.add(ScannedIngredient(
                        name = name,
                        imagePath = "",
                        quantity = "1",
                        unit = UnitHelper.getDefaultUnit(name),
                        timestamp = System.currentTimeMillis() + name.hashCode()
                    ))
                }
            }
            sharedViewModel.setTemporaryIngredients(currentIngs)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) startCamera() else Toast.makeText(requireContext(), "Нужно разрешение на камеру", Toast.LENGTH_LONG).show()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = ActivityScanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()
        detector = TFLiteFoodDetector(requireContext())

        setupCardsRecyclerView()
        setupBackPressed()
        observeViewModels()
        setupClickListeners()

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun setupCardsRecyclerView() {
        cardsAdapter = ScannedCardsAdapter { ingredient -> sharedViewModel.removeFromTemporary(ingredient) }
        binding.rvScannedIngredients.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = cardsAdapter
        }
    }

    private fun setupBackPressed() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
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
                        .setNeutralButton("Отмена", null).show()
                } else {
                    findNavController().popBackStack()
                }
            }
        })
    }

    private fun startCamera() {
        val controller = LifecycleCameraController(requireContext())
        controller.bindToLifecycle(viewLifecycleOwner)
        cameraExecutor?.let { executor ->
            controller.setImageAnalysisAnalyzer(executor) { imageProxy -> analyzeFrame(imageProxy) }
        }
        controller.imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
        binding.viewFinder.controller = controller
        cameraController = controller
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun analyzeFrame(imageProxy: ImageProxy) {
        val currentDetector = detector
        if (currentDetector == null) {
            imageProxy.close()
            return
        }
        try {
            val bitmap = imageProxy.toBitmapSafe()
            val detections = currentDetector.detectFood(bitmap)
            bitmap.recycle()
            viewLifecycleOwner.lifecycleScope.launch { viewModel.updateDetections(detections) }
        } catch (e: Exception) {
            Log.e("ScanFragment", "Analysis error", e)
        } finally {
            imageProxy.close()
        }
    }

    private fun observeViewModels() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedViewModel.temporaryIngredients.collect { ingredients ->
                    cardsAdapter.submitList(ingredients)
                    binding.btnShowResults.isEnabled = ingredients.isNotEmpty()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.latestDetections.collect { detections ->
                    if (detections.isNotEmpty()) {
                        val allergenDetected = detections.any { isAllergen(it.name) }
                        binding.cardDetectionHint.visibility = View.VISIBLE
                        
                        if (allergenDetected) {
                            binding.cardDetectionHint.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red_error))
                            binding.tvDetectionName.text = "⚠️ Обнаружен аллерген!"
                        } else {
                            binding.cardDetectionHint.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.color_primary))
                            val names = detections.take(3).joinToString { it.name }
                            binding.tvDetectionName.text = if (detections.size > 3) "$names..." else names
                        }
                    } else {
                        binding.cardDetectionHint.visibility = View.GONE
                    }
                }
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
            val detections = viewModel.latestDetections.value

            if (bitmap == null) {
                Toast.makeText(requireContext(), "Камера не готова", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (detections.isEmpty()) {
                Toast.makeText(requireContext(), "Продукты не распознаны. Попробуйте другой ракурс.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            val detectedAllergens = detections.filter { isAllergen(it.name) }
            if (detectedAllergens.isNotEmpty()) {
                AlertDialog.Builder(requireContext())
                    .setTitle("Обнаружена аллергия")
                    .setMessage("Обнаружен продукт (${detectedAllergens.first().name}), который не рекомендуется пользователю из-за аллергии. Добавить остальные продукты?")
                    .setPositiveButton("Добавить безопасные") { _, _ ->
                        processDetections(bitmap, detections.filter { !isAllergen(it.name) })
                    }
                    .setNegativeButton("Отмена", null)
                    .show()
            } else {
                processDetections(bitmap, detections)
            }
        }

        binding.btnAddManually.setOnClickListener {
            val intent = Intent(requireContext(), IngredientCatalogActivity::class.java).apply {
                putStringArrayListExtra("selected_names", ArrayList(sharedViewModel.temporaryIngredients.value.map { it.name }))
            }
            catalogLauncher.launch(intent)
        }

        binding.btnBack.setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
    }

    private fun processDetections(bitmap: android.graphics.Bitmap, detections: List<com.example.mealcamera.ml.DetectedFood>) {
        viewModel.processCapturedDetections(bitmap, detections) { newIngredients ->
            if (newIngredients.isNotEmpty()) {
                sharedViewModel.addToTemporary(newIngredients)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor?.shutdown()
        detector?.close()
        _binding = null
    }
}