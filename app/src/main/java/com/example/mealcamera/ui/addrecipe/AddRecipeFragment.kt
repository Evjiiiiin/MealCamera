package com.example.mealcamera.ui.addrecipe

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.mealcamera.MealCameraApplication
import com.example.mealcamera.R
import com.example.mealcamera.data.remote.CloudIngredient
import com.example.mealcamera.data.remote.CloudRecipe
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
    private val stepImageBitmaps = mutableMapOf<Int, Bitmap>()
    private var currentStepImagePickingIndex = -1

    private val categories = listOf("Завтрак", "Обед", "Ужин", "Десерт", "Напитки", "Перекус")
    private val cuisines = listOf("Русская", "Итальянская", "Испанская", "Французская", "Азиатская", "Американская", "Другая")
    private val units = listOf("г", "кг", "мл", "л", "шт", "ст.л.", "ч.л.", "стакан")

    private val mainImagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            Glide.with(this).load(it).into(binding.ivRecipePreview)
            binding.btnSelectImage.visibility = View.GONE
            selectedMainImageBitmap = uriToBitmap(it)
        }
    }

    private val stepImagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val bitmap = uriToBitmap(it)
            if (bitmap != null && currentStepImagePickingIndex != -1) {
                stepImageBitmaps[currentStepImagePickingIndex] = bitmap
                val stepView = binding.stepsContainer.getChildAt(currentStepImagePickingIndex)
                stepView?.findViewById<ImageView>(R.id.ivStepImage)?.let { iv ->
                    Glide.with(this).load(it).into(iv)
                    iv.tag = ""
                }
                stepView?.findViewById<View>(R.id.btnSelectStepImage)?.visibility = View.GONE
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = ActivityAddRecipeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recipeId = arguments?.getLong("edit_recipe_id") ?: -1L
        val isEditing = recipeId > 0

        setupNavigation(isEditing)
        
        if (isEditing) {
            binding.tvHeader.text = "Редактирование"
            binding.btnDeleteRecipe.visibility = View.VISIBLE
            // Уменьшаем длину экрана для режима редактирования
            val density = resources.displayMetrics.density
            binding.bottomButtonsContainer.setPadding(
                binding.bottomButtonsContainer.paddingLeft,
                binding.bottomButtonsContainer.paddingTop,
                binding.bottomButtonsContainer.paddingRight,
                (48 * density).toInt()
            )
        } else {
            binding.tvHeader.text = "Новый рецепт"
            binding.btnDeleteRecipe.visibility = View.GONE
        }

        setupDropdowns()
        setupClickListeners()
        observeViewModel()

        if (isEditing) {
            viewModel.loadRecipe(recipeId)
        } else {
            viewModel.resetEditing()
        }
    }

    private fun setupNavigation(isEditing: Boolean) {
        if (isEditing) {
            binding.btnBack.visibility = View.VISIBLE
            binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        } else {
            binding.btnBack.visibility = View.GONE
        }
        (requireActivity() as? com.example.mealcamera.ui.home.MainActivity)?.setBottomNavVisibility(!isEditing)
    }

    private fun setupDropdowns() {
        binding.spinnerCategory.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, categories))
        binding.spinnerCuisine.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, cuisines))
    }

    private fun setupClickListeners() {
        binding.imageCard.setOnClickListener { mainImagePicker.launch("image/*") }
        binding.btnSelectImage.setOnClickListener { mainImagePicker.launch("image/*") }
        binding.btnAddIngredient.setOnClickListener { addIngredientRow(binding.ingredientsContainer) }
        binding.btnAddStep.setOnClickListener { addStepRow() }
        binding.btnSaveRecipe.setOnClickListener { saveRecipe() }
        binding.btnClearFields.setOnClickListener { showClearConfirmation() }
        binding.btnDeleteRecipe.setOnClickListener { showDeleteConfirmation() }

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    findNavController().popBackStack()
                }
            }
        )
    }

    private fun showDeleteConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Удалить рецепт?")
            .setMessage("Это действие нельзя отменить.")
            .setPositiveButton("Удалить") { _, _ -> viewModel.deleteRecipe() }
            .setNegativeButton("Отмена", null).show()
    }

    private fun showClearConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Очистить всё?")
            .setMessage("Все введенные данные будут удалены.")
            .setPositiveButton("Да") { _, _ -> clearAllFields() }
            .setNegativeButton("Нет", null).show()
    }

    private fun clearAllFields() {
        binding.etRecipeName.text?.clear()
        binding.etDescription.text?.clear()
        binding.etCalories.text?.clear()
        binding.etProteins.text?.clear()
        binding.etFats.text?.clear()
        binding.etCarbs.text?.clear()
        binding.etCookHours.text?.clear()
        binding.etCookMinutes.text?.clear()
        binding.etTotalWeight.text?.clear()
        binding.ingredientsContainer.removeAllViews()
        binding.stepsContainer.removeAllViews()
        binding.ivRecipePreview.setImageResource(R.drawable.ic_recipe_placeholder)
        binding.btnSelectImage.visibility = View.VISIBLE
        selectedMainImageBitmap = null
        currentMainImagePath = ""
        stepImageBitmaps.clear()
        viewModel.resetEditing()
    }

    private fun addIngredientRow(container: LinearLayout, data: CloudIngredient? = null) {
        val row = layoutInflater.inflate(R.layout.item_add_ingredient, container, false)
        val actvUnit = row.findViewById<AutoCompleteTextView>(R.id.tvIngredientUnit)
        actvUnit.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, units))
        data?.let {
            row.findViewById<EditText>(R.id.etIngredientName).setText(it.name)
            row.findViewById<EditText>(R.id.etIngredientQuantity).setText(it.quantity)
            actvUnit.setText(it.unit, false)
        } ?: run { actvUnit.setText(units[0], false) }
        row.findViewById<View>(R.id.btnRemoveIngredient).setOnClickListener { container.removeView(row) }
        container.addView(row)
    }

    private fun addStepRow(data: StepData? = null) {
        val row = layoutInflater.inflate(R.layout.item_add_step, binding.stepsContainer, false)
        row.findViewById<View>(R.id.stepImageCard).setOnClickListener {
            currentStepImagePickingIndex = binding.stepsContainer.indexOfChild(row)
            stepImagePicker.launch("image/*")
        }
        data?.let {
            row.findViewById<EditText>(R.id.etStepTitle).setText(it.title)
            row.findViewById<EditText>(R.id.etStepDescription).setText(it.description)
            if (it.timerMinutes > 0) {
                row.findViewById<View>(R.id.tilStepTimer).visibility = View.VISIBLE
                row.findViewById<EditText>(R.id.etStepTimer).setText(it.timerMinutes.toString())
                row.findViewById<Button>(R.id.btnToggleTimer).text = "- Таймер"
            }
            if (it.imagePath.isNotBlank()) {
                val iv = row.findViewById<ImageView>(R.id.ivStepImage)
                Glide.with(this).load(it.imagePath).into(iv)
                iv.tag = it.imagePath
                row.findViewById<View>(R.id.btnSelectStepImage).visibility = View.GONE
            }
            it.ingredients.forEach { ing -> addIngredientRow(row.findViewById(R.id.stepIngredientsContainer), ing) }
        }

        row.findViewById<Button>(R.id.btnToggleTimer).setOnClickListener { btn ->
            val til = row.findViewById<View>(R.id.tilStepTimer)
            if (til.visibility == View.VISIBLE) {
                til.visibility = View.GONE
                (btn as Button).text = "+ Таймер"
            } else {
                til.visibility = View.VISIBLE
                (btn as Button).text = "- Таймер"
            }
        }

        row.findViewById<Button>(R.id.btnAddStepIngredient).setOnClickListener {
            addIngredientRow(row.findViewById(R.id.stepIngredientsContainer))
        }

        row.findViewById<View>(R.id.btnRemoveStep).setOnClickListener {
            val idx = binding.stepsContainer.indexOfChild(row)
            stepImageBitmaps.remove(idx)
            binding.stepsContainer.removeView(row)
        }
        binding.stepsContainer.addView(row)
    }

    private fun saveRecipe() {
        val h = binding.etCookHours.text.toString().ifBlank { "0" }
        val m = binding.etCookMinutes.text.toString().ifBlank { "0" }
        viewModel.saveRecipe(
            name = binding.etRecipeName.text.toString(),
            description = binding.etDescription.text.toString(),
            category = binding.spinnerCategory.text.toString(),
            cuisine = binding.spinnerCuisine.text.toString(),
            cuisineCode = "RU",
            prepTime = if (h == "0") "$m мин" else "$h ч $m мин",
            ingredients = collectIngredients(binding.ingredientsContainer),
            steps = collectSteps(),
            mainImage = selectedMainImageBitmap,
            stepImages = stepImageBitmaps,
            userId = FirebaseAuth.getInstance().currentUser?.uid ?: "",
            isPublic = binding.switchPublic.isChecked,
            calories = binding.etCalories.text.toString().toIntOrNull() ?: 0,
            proteins = binding.etProteins.text.toString().toDoubleOrNull() ?: 0.0,
            fats = binding.etFats.text.toString().toDoubleOrNull() ?: 0.0,
            carbs = binding.etCarbs.text.toString().toDoubleOrNull() ?: 0.0,
            currentImagePath = currentMainImagePath,
            totalWeight = binding.etTotalWeight.text.toString().toIntOrNull() ?: 0
        )
    }

    private fun collectIngredients(container: LinearLayout): List<CloudIngredient> {
        return (0 until container.childCount).map { i ->
            val v = container.getChildAt(i)
            CloudIngredient(
                v.findViewById<EditText>(R.id.etIngredientName).text.toString(),
                v.findViewById<EditText>(R.id.etIngredientQuantity).text.toString(),
                v.findViewById<AutoCompleteTextView>(R.id.tvIngredientUnit).text.toString()
            )
        }
    }

    private fun collectSteps(): List<StepData> {
        return (0 until binding.stepsContainer.childCount).map { i ->
            val v = binding.stepsContainer.getChildAt(i)
            val iv = v.findViewById<ImageView>(R.id.ivStepImage)
            StepData(
                v.findViewById<EditText>(R.id.etStepTitle).text.toString(),
                v.findViewById<EditText>(R.id.etStepDescription).text.toString(),
                v.findViewById<EditText>(R.id.etStepTimer).text.toString().toIntOrNull() ?: 0,
                iv.tag as? String ?: "",
                collectIngredients(v.findViewById(R.id.stepIngredientsContainer))
            )
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                if (_binding == null) return@collect
                when (state) {
                    is AddRecipeState.Loading -> binding.progressBar.visibility = View.VISIBLE
                    is AddRecipeState.Loaded -> {
                        binding.progressBar.visibility = View.GONE
                        fillFields(state.recipe)
                    }
                    is AddRecipeState.Success -> {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(requireContext(), "Рецепт сохранен", Toast.LENGTH_SHORT).show()
                        findNavController().popBackStack()
                    }
                    is AddRecipeState.Deleted -> {
                        binding.progressBar.visibility = View.GONE
                        findNavController().popBackStack()
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

    private fun fillFields(recipe: CloudRecipe) {
        selectedMainImageBitmap = null
        stepImageBitmaps.clear()

        binding.etRecipeName.setText(recipe.name)
        binding.etDescription.setText(recipe.description)
        currentMainImagePath = recipe.imagePath
        if (recipe.imagePath.isNotBlank()) {
            Glide.with(this).load(recipe.imagePath).into(binding.ivRecipePreview)
            binding.btnSelectImage.visibility = View.GONE
        } else {
            binding.btnSelectImage.visibility = View.VISIBLE
        }
        binding.spinnerCategory.setText(recipe.category, false)
        binding.spinnerCuisine.setText(recipe.cuisine, false)
        binding.etCalories.setText(recipe.calories.toString())
        binding.etProteins.setText(recipe.proteins.toString())
        binding.etFats.setText(recipe.fats.toString())
        binding.etCarbs.setText(recipe.carbs.toString())
        binding.etTotalWeight.setText(recipe.totalWeight.toString())
        binding.switchPublic.isChecked = recipe.isPublic

        val timeRegex = "(\\d+)\\s*ч\\s*(\\d+)\\s*мин".toRegex()
        val minutesOnlyRegex = "(\\d+)\\s*мин".toRegex()
        timeRegex.find(recipe.prepTime)?.let {
            binding.etCookHours.setText(it.groupValues[1])
            binding.etCookMinutes.setText(it.groupValues[2])
        } ?: minutesOnlyRegex.find(recipe.prepTime)?.let {
            binding.etCookHours.setText("0")
            binding.etCookMinutes.setText(it.groupValues[1])
        }

        binding.ingredientsContainer.removeAllViews()
        recipe.ingredients.forEach { addIngredientRow(binding.ingredientsContainer, it) }
        binding.stepsContainer.removeAllViews()
        recipe.steps.forEach { addStepRow(it) }
    }

    private fun uriToBitmap(uri: Uri): Bitmap? {
        return try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val options = BitmapFactory.Options().apply { inSampleSize = 2 }
            BitmapFactory.decodeStream(inputStream, null, options)
        } catch (e: Exception) {
            Log.e("AddRecipeFragment", "Bitmap failed", e)
            null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
