package com.example.mealcamera.ui.scan

import android.Manifest
import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mealcamera.MealCameraApplication
import com.example.mealcamera.R
import com.example.mealcamera.data.model.ScannedIngredient
import com.example.mealcamera.databinding.ActivityScanBinding
import com.example.mealcamera.ui.SharedViewModel
import com.example.mealcamera.ui.catalog.IngredientCatalogActivity
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScanFragment : Fragment() {

    private var _binding: ActivityScanBinding? = null
    private val binding get() = _binding!!

    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private lateinit var cardsAdapter: ScannedCardsAdapter

    private val viewModel: ScanViewModel by viewModels {
        (requireActivity().application as MealCameraApplication).viewModelFactory
    }

    private val sharedViewModel: SharedViewModel by lazy {
        (requireActivity().application as MealCameraApplication).sharedViewModel
    }

    // Обработка результата из каталога ингредиентов
    private val catalogLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val selectedNames = result.data?.getStringArrayListExtra("selected_names") ?: return@registerForActivityResult
            
            // Получаем текущие ингредиенты, чтобы не затереть их и не создать дубли
            val currentIngs = sharedViewModel.temporaryIngredients.value.toMutableList()
            
            selectedNames.forEach { name ->
                if (currentIngs.none { it.name.equals(name, ignoreCase = true) }) {
                    currentIngs.add(ScannedIngredient(
                        name = name,
                        imagePath = "", 
                        quantity = "1", 
                        unit = "г",
                        timestamp = System.currentTimeMillis() + name.hashCode()
                    ))
                }
            }
            sharedViewModel.addToTemporary(currentIngs)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) startCamera()
        else Toast.makeText(requireContext(), R.string.camera_permission_denied, Toast.LENGTH_SHORT).show()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = ActivityScanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()

        setupCardsRecyclerView()
        setupBackPressed()

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        observeViewModels()
        setupClickListeners()
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
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (sharedViewModel.temporaryIngredients.value.isNotEmpty()) {
                    AlertDialog.Builder(requireContext())
                        .setTitle("Сохранить черновик?")
                        .setMessage("Вы хотите сохранить отсканированные продукты для следующего раза?")
                        .setPositiveButton("Да, сохранить") { _, _ ->
                            findNavController().popBackStack()
                        }
                        .setNegativeButton("Удалить") { _, _ ->
                            sharedViewModel.clearTemporary()
                            findNavController().popBackStack()
                        }
                        .setNeutralButton("Отмена", null)
                        .show()
                } else {
                    findNavController().popBackStack()
                }
            }
        })
    }

    private fun observeViewModels() {
        // Главный "сейф": Камера отображает ВСЁ, что в SharedViewModel
        viewLifecycleOwner.lifecycleScope.launch {
            sharedViewModel.temporaryIngredients.collect { ingredients ->
                cardsAdapter.submitList(ingredients)
                
                val hasItems = ingredients.isNotEmpty()
                binding.btnShowResults.isEnabled = hasItems
                binding.btnShowResults.alpha = if (hasItems) 1.0f else 0.5f
                binding.btnShowResults.text = if (hasItems) "ДАЛЕЕ (${ingredients.size})" else "ДАЛЕЕ"
                
                // Прокрутка к последнему добавленному
                if (hasItems) {
                    binding.rvScannedIngredients.smoothScrollToPosition(ingredients.size - 1)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isProcessing.collect { isProcessing ->
                binding.progressBar.visibility = if (isProcessing) View.VISIBLE else View.GONE
                binding.btnCapture.isEnabled = !isProcessing
                binding.viewFinder.alpha = if (isProcessing) 0.7f else 1.0f
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

        binding.btnAddManually.setOnClickListener {
            val intent = Intent(requireContext(), IngredientCatalogActivity::class.java).apply {
                val currentNames = sharedViewModel.temporaryIngredients.value.map { it.name }
                putStringArrayListExtra("selected_names", ArrayList(currentNames))
            }
            catalogLauncher.launch(intent)
        }

        binding.btnCapture.setOnClickListener { takePhoto() }
        
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun takePhoto() {
        val bitmap = binding.viewFinder.bitmap ?: return
        viewModel.processImageWithBitmap(bitmap) { detected ->
            activity?.runOnUiThread {
                viewModel.getIngredientsFromDetection(detected, bitmap) { newIngs ->
                    if (newIngs.isNotEmpty()) {
                        sharedViewModel.addToTemporary(newIngs)
                        Toast.makeText(requireContext(), getString(R.string.added_format, newIngs.joinToString { it.name }), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "Продукт не распознан", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(binding.viewFinder.surfaceProvider) }
            imageCapture = ImageCapture.Builder().build()
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(viewLifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            } catch (ignored: Exception) { }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        _binding = null
    }
}