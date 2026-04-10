package com.example.mealcamera.ui.addrecipe

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.mealcamera.MealCameraApplication
import com.example.mealcamera.R
import com.example.mealcamera.data.remote.CloudIngredient
import com.example.mealcamera.data.remote.StepData
import com.example.mealcamera.databinding.ActivityAddRecipeBinding
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class AddRecipeFragment : Fragment() {

    private var _binding: ActivityAddRecipeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AddRecipeViewModel by viewModels {
        (requireActivity().application as MealCameraApplication).viewModelFactory
    }

    private var selectedMainImageBitmap: Bitmap? = null
    private var currentMainImagePath: String = ""

    private val categories = listOf("Завтрак", "Обед", "Ужин", "Десерт", "Напитки")
    private val cuisineMap = mapOf(
        "Русская" to "RU", "Итальянская" to "IT", "Испанская" to "ES",
        "Французская" to "FR", "Американская" to "US", "Азиатская" to "JP",
        "Средиземноморская" to "GR", "Другая" to "XX"
    )
    private val allPossibleUnits = listOf("г", "кг", "мл", "л", "шт", "ст.л.", "ч.л.", "стакан", "щепотка", "по вкусу")

    private val mainImagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            binding.ivRecipePreview.setImageURI(it)
            binding.ivRecipePreview.visibility = View.VISIBLE
            selectedMainImageBitmap = uriToBitmap(it)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = ActivityAddRecipeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupSpinners()
        setupClickListeners()
        observeViewModel()
    }

    private fun setupToolbar() {
        binding.toolbar.title = "Новый рецепт"
    }

    private fun setupSpinners() {
        val catAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, categories)
        binding.spinnerCategory.adapter = catAdapter

        val cuisines = cuisineMap.keys.toList()
        val cuisAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, cuisines)
        binding.spinnerCuisine.adapter = cuisAdapter
    }

    private fun setupClickListeners() {
        binding.btnSelectImage.setOnClickListener { mainImagePicker.launch("image/*") }
        binding.btnAddIngredient.setOnClickListener { addIngredientRow(binding.ingredientsContainer) }
        binding.btnAddStep.setOnClickListener { addStepRow() }
        binding.btnSaveRecipe.setOnClickListener { saveRecipe() }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is AddRecipeState.Loading -> binding.progressBar.visibility = View.VISIBLE
                    is AddRecipeState.Success -> {
                        Toast.makeText(requireContext(), "Рецепт сохранен!", Toast.LENGTH_SHORT).show()
                        findNavController().navigate(R.id.navigation_home)
                    }
                    is AddRecipeState.Error -> {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                    }
                    else -> binding.progressBar.visibility = View.GONE
                }
            }
        }
    }

    private fun addIngredientRow(container: LinearLayout, data: CloudIngredient? = null) {
        val row = layoutInflater.inflate(R.layout.item_add_ingredient, container, false)
        val etName = row.findViewById<EditText>(R.id.etIngredientName)
        val tvUnit = row.findViewById<TextView>(R.id.tvIngredientUnit)
        val etQty = row.findViewById<EditText>(R.id.etIngredientQuantity)
        val unitContainer = row.findViewById<View>(R.id.unit_container)
        
        data?.let {
            etName.setText(it.name)
            etQty.setText(it.quantity)
            tvUnit.text = it.unit
        }

        unitContainer.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Выберите меру")
                .setItems(allPossibleUnits.toTypedArray()) { _, which ->
                    tvUnit.text = allPossibleUnits[which]
                }
                .show()
        }

        row.findViewById<ImageButton>(R.id.btnRemoveIngredient).setOnClickListener { container.removeView(row) }
        container.addView(row)
    }

    private fun addStepRow(data: StepData? = null) {
        val stepView = layoutInflater.inflate(R.layout.item_add_step, binding.stepsContainer, false)
        val stepIngredientsContainer = stepView.findViewById<LinearLayout>(R.id.stepIngredientsContainer)
        val btnAddIngToStep = stepView.findViewById<Button>(R.id.btnAddStepIngredient)
        
        btnAddIngToStep.setOnClickListener {
            addIngredientRow(stepIngredientsContainer)
        }

        data?.let {
            stepView.findViewById<EditText>(R.id.etStepTitle).setText(it.title)
            stepView.findViewById<EditText>(R.id.etStepDescription).setText(it.description)
            stepView.findViewById<EditText>(R.id.etStepTimer).setText(it.timerMinutes.toString())
            it.ingredients.forEach { ing -> addIngredientRow(stepIngredientsContainer, ing) }
        }

        stepView.findViewById<ImageButton>(R.id.btnRemoveStep).setOnClickListener { binding.stepsContainer.removeView(stepView) }
        binding.stepsContainer.addView(stepView)
    }

    private fun saveRecipe() {
        val name = binding.etRecipeName.text.toString()
        if (name.isBlank()) {
            Toast.makeText(requireContext(), "Введите название блюда", Toast.LENGTH_SHORT).show()
            return
        }
        
        val hours = binding.etCookHours.text.toString().ifBlank { "0" }
        val minutes = binding.etCookMinutes.text.toString().ifBlank { "0" }
        val totalTime = if (hours == "0") "$minutes мин" else "$hours ч $minutes мин"

        viewModel.saveRecipe(
            name = name,
            description = binding.etDescription.text.toString(),
            category = binding.spinnerCategory.selectedItem.toString(),
            cuisine = binding.spinnerCuisine.selectedItem.toString(),
            cuisineCode = cuisineMap[binding.spinnerCuisine.selectedItem.toString()] ?: "XX",
            prepTime = totalTime,
            ingredients = collectIngredients(binding.ingredientsContainer),
            steps = collectSteps(),
            mainImage = selectedMainImageBitmap,
            stepImages = emptyMap(),
            userId = FirebaseAuth.getInstance().currentUser?.uid ?: "",
            isPublic = binding.switchPublic.isChecked,
            currentImagePath = currentMainImagePath
        )
    }

    private fun collectIngredients(container: LinearLayout): List<CloudIngredient> {
        return (0 until container.childCount).mapNotNull { i ->
            val v = container.getChildAt(i)
            val etName = v.findViewById<EditText>(R.id.etIngredientName)
            val etQty = v.findViewById<EditText>(R.id.etIngredientQuantity)
            val tvUnit = v.findViewById<TextView>(R.id.tvIngredientUnit)
            
            val n = etName.text.toString()
            if (n.isNotBlank()) {
                CloudIngredient(
                    name = n, 
                    quantity = etQty.text.toString(), 
                    unit = tvUnit.text.toString()
                )
            } else null
        }
    }

    private fun collectSteps(): List<StepData> {
        return (0 until binding.stepsContainer.childCount).map { i ->
            val v = binding.stepsContainer.getChildAt(i)
            val etTitle = v.findViewById<EditText>(R.id.etStepTitle)
            val etDesc = v.findViewById<EditText>(R.id.etStepDescription)
            val etTimer = v.findViewById<EditText>(R.id.etStepTimer)
            val stepIngContainer = v.findViewById<LinearLayout>(R.id.stepIngredientsContainer)

            StepData(
                title = etTitle.text.toString(),
                description = etDesc.text.toString(),
                timerMinutes = etTimer.text.toString().toIntOrNull() ?: 0,
                imagePath = "",
                ingredients = collectIngredients(stepIngContainer)
            )
        }
    }

    private fun uriToBitmap(uri: Uri): Bitmap? =
        requireContext().contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
