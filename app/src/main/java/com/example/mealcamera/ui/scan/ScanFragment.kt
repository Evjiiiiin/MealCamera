package com.example.mealcamera.ui.scan

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
            setCameraFeatureEnabled(isGranted)
            if (isGranted) {
                startCamera()
                showDetectorModeHint()
            } else {
                showCameraDisabledState("Камера выключена: разрешение не выдано")
            }
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
        configureCameraAccess()
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

    private fun configureCameraAccess() {
        if (!isCameraFeatureEnabled()) {
            showCameraDisabledState("Камера выключена в настройках профиля")
            return
        }

        if (hasCameraPermission()) {
            setCameraFeatureEnabled(true)
            startCamera()
            showDetectorModeHint()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val controller = LifecycleCameraController(requireContext())
        controller.bindToLifecycle(viewLifecycleOwner)
        binding.viewFinder.controller = controller
        binding.btnCapture.isEnabled = true
        binding.btnCapture.alpha = 1f
        cameraController = controller
    }

    private fun showCameraDisabledState(message: String) {
        binding.viewFinder.controller = null
        binding.btnCapture.isEnabled = false
        binding.btnCapture.alpha = DISABLED_BUTTON_ALPHA
        cameraController = null
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        Log.d(TAG, message)
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun isCameraFeatureEnabled(): Boolean {
        return requireContext()
            .getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .getBoolean("camera_enabled", true)
    }

    private fun setCameraFeatureEnabled(enabled: Boolean) {
        requireContext()
            .getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("camera_enabled", enabled)
            .apply()
    }

    private fun showDetectorModeHint() {
        val cloudAvailable = roboflowDetector?.isAvailable() == true
        val mode = if (cloudAvailable) {
            "Режим: облачный"
        } else {
            "Режим: локальный"
        }
        Toast.makeText(requireContext(), mode, Toast.LENGTH_SHORT).show()
        Log.d(TAG, mode)
    }

    private fun observeIngredientsOnly() {
        viewLifecycleOwner.lifecycleScope.launch {
            sharedViewModel.temporaryIngredients.collect { ingredients ->
                cardsAdapter.submitList(ingredients)
                updateShowResultsButton(ingredients.isNotEmpty())
            }
        }
    }

    private fun updateShowResultsButton(enabled: Boolean) {
        binding.btnShowResults.isEnabled = enabled
        binding.btnShowResults.isClickable = enabled
        binding.btnShowResults.alpha = if (enabled) 1f else DISABLED_BUTTON_ALPHA
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

            showAnalysisOverlay(bitmap)
            setCaptureControlsEnabled(false)

            viewLifecycleOwner.lifecycleScope.launch {
                val detectionResult = withContext(Dispatchers.IO) { analyzeCapture(bitmap) }

                _binding ?: return@launch
                hideAnalysisOverlay()
                setCaptureControlsEnabled(true)

                val detections = detectionResult.first
                val sourceTag = detectionResult.second

                if (detections.isEmpty()) {
                    Toast.makeText(
                        requireContext(),
                        "Не удалось найти продукты. Поднесите камеру ближе, улучшите свет или добавьте продукт вручную.",
                        Toast.LENGTH_LONG
                    ).show()
                    Log.w(TAG, "Capture analysis result=empty source=$sourceTag")
                    return@launch
                }

                Log.d(TAG, "Capture analysis result=ok source=$sourceTag detections=${detections.size}")

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

    private fun analyzeCapture(bitmap: Bitmap): Pair<List<DetectedFood>, String> {
        val cloud = roboflowDetector
        val local = localDetector

        if (cloud?.isAvailable() == true) {
            val cloudFull = cloud.detectFood(bitmap)
            val cloudCrop = detectOnPass(
                createCenterCropPass(bitmap, CENTER_CROP_RATIO),
                cloud::detectFood
            )
            val mergedCloud = mergeDetections(cloudFull + cloudCrop, MERGE_IOU_THRESHOLD)

            val localAssist = local?.let { detector ->
                detectAcrossLocalPasses(bitmap, detector::detectFood)
            }.orEmpty()

            val mergedAll = mergeDetections(mergedCloud + localAssist, MERGE_IOU_THRESHOLD)
            Log.d(
                TAG,
                "Cloud+local multi-pass: cloudFull=${cloudFull.size}, cloudCrop=${cloudCrop.size}, " +
                        "cloudMerged=${mergedCloud.size}, localAssist=${localAssist.size}, merged=${mergedAll.size}"
            )

            if (mergedAll.isNotEmpty()) {
                Log.d(TAG, "Capture analysis source=CLOUD_LOCAL_MERGE, detections=${mergedAll.size}")
                return mergedAll to "cloud_local_merge"
            }

            return emptyList<DetectedFood>() to "cloud_local_empty"
        }

        Log.w(TAG, "Capture analysis source=LOCAL reason=cloud_unavailable")
        val localDetections = local?.let { detectAcrossLocalPasses(bitmap, it::detectFood) }.orEmpty()
        return mergeDetections(localDetections, MERGE_IOU_THRESHOLD) to "local_only"
    }

    private fun detectAcrossLocalPasses(
        source: Bitmap,
        detect: (Bitmap) -> List<DetectedFood>
    ): List<DetectedFood> {
        val passes = listOf(
            CapturePass(source, offsetX = 0, offsetY = 0, ownsBitmap = false, name = "full"),
            createCenterCropPass(source, CENTER_CROP_RATIO),
            createHorizontalCropPass(source, EDGE_CROP_RATIO, alignEnd = false),
            createHorizontalCropPass(source, EDGE_CROP_RATIO, alignEnd = true),
            createVerticalCropPass(source, EDGE_CROP_RATIO, alignEnd = false),
            createVerticalCropPass(source, EDGE_CROP_RATIO, alignEnd = true),
            createCornerCropPass(source, TILE_CROP_RATIO, alignEndX = false, alignEndY = false),
            createCornerCropPass(source, TILE_CROP_RATIO, alignEndX = true, alignEndY = false),
            createCornerCropPass(source, TILE_CROP_RATIO, alignEndX = false, alignEndY = true),
            createCornerCropPass(source, TILE_CROP_RATIO, alignEndX = true, alignEndY = true)
        )

        return passes.flatMap { pass ->
            detectOnPass(pass, detect).also { detections ->
                Log.d(TAG, "Local pass=${pass.name}, detections=${detections.size}")
            }
        }
    }

    private fun detectOnPass(
        pass: CapturePass,
        detect: (Bitmap) -> List<DetectedFood>
    ): List<DetectedFood> {
        return try {
            detect(pass.bitmap).map { detection -> detection.translateToOriginal(pass.offsetX, pass.offsetY) }
        } finally {
            if (pass.ownsBitmap) pass.bitmap.recycle()
        }
    }

    private fun createCenterCropPass(source: Bitmap, ratio: Float): CapturePass {
        val safeRatio = ratio.coerceIn(0.5f, 1f)
        val cropWidth = (source.width * safeRatio).toInt().coerceAtLeast(1)
        val cropHeight = (source.height * safeRatio).toInt().coerceAtLeast(1)
        val left = ((source.width - cropWidth) / 2).coerceAtLeast(0)
        val top = ((source.height - cropHeight) / 2).coerceAtLeast(0)
        return CapturePass(
            bitmap = Bitmap.createBitmap(source, left, top, cropWidth, cropHeight),
            offsetX = left,
            offsetY = top,
            ownsBitmap = true,
            name = "center"
        )
    }

    private fun createHorizontalCropPass(source: Bitmap, ratio: Float, alignEnd: Boolean): CapturePass {
        val safeRatio = ratio.coerceIn(0.5f, 1f)
        val cropWidth = (source.width * safeRatio).toInt().coerceAtLeast(1)
        val left = if (alignEnd) (source.width - cropWidth).coerceAtLeast(0) else 0
        val name = if (alignEnd) "right" else "left"
        return CapturePass(
            bitmap = Bitmap.createBitmap(source, left, 0, cropWidth, source.height),
            offsetX = left,
            offsetY = 0,
            ownsBitmap = true,
            name = name
        )
    }

    private fun createVerticalCropPass(source: Bitmap, ratio: Float, alignEnd: Boolean): CapturePass {
        val safeRatio = ratio.coerceIn(0.5f, 1f)
        val cropHeight = (source.height * safeRatio).toInt().coerceAtLeast(1)
        val top = if (alignEnd) (source.height - cropHeight).coerceAtLeast(0) else 0
        val name = if (alignEnd) "bottom" else "top"
        return CapturePass(
            bitmap = Bitmap.createBitmap(source, 0, top, source.width, cropHeight),
            offsetX = 0,
            offsetY = top,
            ownsBitmap = true,
            name = name
        )
    }

    private fun createCornerCropPass(
        source: Bitmap,
        ratio: Float,
        alignEndX: Boolean,
        alignEndY: Boolean
    ): CapturePass {
        val safeRatio = ratio.coerceIn(0.5f, 1f)
        val cropWidth = (source.width * safeRatio).toInt().coerceAtLeast(1)
        val cropHeight = (source.height * safeRatio).toInt().coerceAtLeast(1)
        val left = if (alignEndX) (source.width - cropWidth).coerceAtLeast(0) else 0
        val top = if (alignEndY) (source.height - cropHeight).coerceAtLeast(0) else 0
        val horizontalName = if (alignEndX) "right" else "left"
        val verticalName = if (alignEndY) "bottom" else "top"
        return CapturePass(
            bitmap = Bitmap.createBitmap(source, left, top, cropWidth, cropHeight),
            offsetX = left,
            offsetY = top,
            ownsBitmap = true,
            name = "${horizontalName}_${verticalName}"
        )
    }

    private fun DetectedFood.translateToOriginal(offsetX: Int, offsetY: Int): DetectedFood {
        val sourceBox = boundingBox ?: return this
        return copy(
            boundingBox = RectF(
                sourceBox.left + offsetX,
                sourceBox.top + offsetY,
                sourceBox.right + offsetX,
                sourceBox.bottom + offsetY
            )
        )
    }

    private fun mergeDetections(detections: List<DetectedFood>, iouThreshold: Float): List<DetectedFood> {
        if (detections.isEmpty()) return emptyList()

        val sorted = detections.sortedByDescending { it.confidence }
        val kept = mutableListOf<DetectedFood>()

        for (candidate in sorted) {
            val candidateBox = candidate.boundingBox
            if (candidateBox == null) {
                kept.add(candidate)
                continue
            }

            val isDuplicate = kept.any { selected ->
                val selectedBox = selected.boundingBox ?: return@any false
                selected.name.equals(candidate.name, ignoreCase = true) &&
                        calculateIoU(selectedBox, candidateBox) >= iouThreshold
            }

            if (!isDuplicate) kept.add(candidate)
        }

        return kept.take(MAX_CAPTURE_DETECTIONS)
    }

    private fun calculateIoU(first: RectF, second: RectF): Float {
        val left = maxOf(first.left, second.left)
        val top = maxOf(first.top, second.top)
        val right = minOf(first.right, second.right)
        val bottom = minOf(first.bottom, second.bottom)

        val intersection = if (right > left && bottom > top) (right - left) * (bottom - top) else 0f
        val firstArea = (first.right - first.left) * (first.bottom - first.top)
        val secondArea = (second.right - second.left) * (second.bottom - second.top)
        val union = firstArea + secondArea - intersection

        return if (union <= 0f) 0f else intersection / union
    }

    private fun showAnalysisOverlay(bitmap: Bitmap) {
        binding.ivCapturedFrame.setImageBitmap(bitmap)
        binding.analysisOverlay.visibility = View.VISIBLE
    }

    private fun hideAnalysisOverlay() {
        val currentBinding = _binding ?: return
        currentBinding.analysisOverlay.visibility = View.GONE
        currentBinding.ivCapturedFrame.setImageDrawable(null)
    }

    private fun setCaptureControlsEnabled(enabled: Boolean) {
        binding.btnCapture.isEnabled = enabled
        binding.btnCapture.isClickable = enabled
        binding.btnCapture.alpha = if (enabled) 1f else DISABLED_BUTTON_ALPHA
        binding.btnAddManually.isEnabled = enabled
        binding.btnAddManually.isClickable = enabled
        binding.btnAddManually.alpha = if (enabled) 1f else DISABLED_BUTTON_ALPHA
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
        hideAnalysisOverlay()
        cameraExecutor?.shutdown()
        cameraExecutor = null
        localDetector?.close()
        localDetector = null
        roboflowDetector = null
        cameraController = null
        _binding = null
    }

    private data class CapturePass(
        val bitmap: Bitmap,
        val offsetX: Int,
        val offsetY: Int,
        val ownsBitmap: Boolean,
        val name: String
    )

    companion object {
        private const val TAG = "ScanFragment"
        private const val CENTER_CROP_RATIO = 0.8f
        private const val EDGE_CROP_RATIO = 0.68f
        private const val TILE_CROP_RATIO = 0.58f
        private const val MERGE_IOU_THRESHOLD = 0.60f
        private const val MAX_CAPTURE_DETECTIONS = 24
        private const val DISABLED_BUTTON_ALPHA = 0.45f
    }
}