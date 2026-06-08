package com.example.mealcamera.ui.addrecipe

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
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
import androidx.exifinterface.media.ExifInterface
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
import com.google.android.material.textfield.TextInputLayout
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
    private val units = listOf("г", "кг", "мл", "л", "шт", "ст.л.", "ч.л.", "стакан", TASTE_UNIT)

    private val mainImagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val bitmap = uriToBitmap(it)
            if (bitmap == null) {
                Toast.makeText(requireContext(), "Не удалось открыть фото", Toast.LENGTH_SHORT).show()
                return@registerForActivityResult
            }
            selectedMainImageBitmap = bitmap
            currentMainImagePath = ""
            binding.ivRecipePreview.setImageBitmap(bitmap)
            binding.btnSelectImage.visibility = View.GONE
            binding.btnRemoveImage.visibility = View.VISIBLE
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
        binding.btnRemoveImage.setOnClickListener { clearMainRecipeImage() }
        binding.btnAddIngredient.setOnClickListener { addIngredientRow(binding.ingredientsContainer) }
        binding.btnAddStep.setOnClickListener { addStepRow() }
        binding.btnSaveRecipe.setOnClickListener { saveRecipe() }
        binding.btnClearFields.setOnClickListener { showClearConfirmation() }
        binding.btnDeleteRecipe.setOnClickListener { showDeleteConfirmation() }
    }

    private fun showDeleteConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Удалить рецепт?")
            .setMessage("Это действие нельзя отменить.")
            .setPositiveButton("Удалить") { _, _ ->
                viewModel.deleteRecipe()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showClearConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Очистить поля?")
            .setMessage("Все введенные данные будут потеряны.")
            .setPositiveButton("Очистить") { _, _ ->
                clearRecipeForm()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun clearRecipeForm() {
        clearRecipeFieldErrors()

        selectedMainImageBitmap = null
        currentMainImagePath = ""
        stepImageBitmaps.clear()
        currentStepImagePickingIndex = -1

        binding.etRecipeName.text?.clear()
        binding.etDescription.text?.clear()
        clearMainRecipeImage()

        binding.ingredientsContainer.removeAllViews()
        binding.stepsContainer.removeAllViews()

        binding.etCookHours.text?.clear()
        binding.etCookMinutes.text?.clear()
        binding.spinnerCategory.setText("", false)
        binding.spinnerCuisine.setText("", false)
        binding.etCalories.text?.clear()
        binding.etProteins.text?.clear()
        binding.etFats.text?.clear()
        binding.etCarbs.text?.clear()
        binding.etTotalWeight.text?.clear()
        binding.switchPublic.isChecked = true
    }

    private fun clearMainRecipeImage() {
        selectedMainImageBitmap = null
        currentMainImagePath = ""
        binding.ivRecipePreview.setImageResource(R.drawable.ic_recipe_placeholder)
        binding.btnSelectImage.visibility = View.VISIBLE
        binding.btnRemoveImage.visibility = View.GONE
    }

    private fun addIngredientRow(container: LinearLayout, data: CloudIngredient? = null) {
        val row = layoutInflater.inflate(R.layout.item_add_ingredient, container, false)
        val quantityEditText = row.findViewById<EditText>(R.id.etIngredientQuantity)
        val actvUnit = row.findViewById<AutoCompleteTextView>(R.id.tvIngredientUnit)
        actvUnit.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, units))

        data?.let {
            val unit = if (isTasteUnit(it.unit) || isTasteUnit(it.quantity)) TASTE_UNIT else it.unit
            row.findViewById<EditText>(R.id.etIngredientName).setText(it.name)
            quantityEditText.setText(if (isTasteUnit(unit)) "" else it.quantity)
            actvUnit.setText(unit, false)
        } ?: run {
            actvUnit.setText(units[0], false)
        }

        updateTasteQuantityState(row, actvUnit.text.toString())
        actvUnit.setOnItemClickListener { _, _, _, _ ->
            updateTasteQuantityState(row, actvUnit.text.toString())
        }
        actvUnit.setOnDismissListener {
            updateTasteQuantityState(row, actvUnit.text.toString())
        }
        row.findViewById<View>(R.id.btnRemoveIngredient).setOnClickListener { container.removeView(row) }
        container.addView(row)
    }

    private fun updateTasteQuantityState(row: View, unit: String) {
        val quantityLayout = row.findViewById<TextInputLayout>(R.id.layoutIngredientQuantity)
        val quantityEditText = row.findViewById<EditText>(R.id.etIngredientQuantity)
        val isTaste = isTasteUnit(unit)

        if (isTaste) {
            quantityEditText.text?.clear()
        }

        quantityLayout.isEnabled = !isTaste
        quantityEditText.isEnabled = !isTaste
        quantityLayout.alpha = if (isTaste) DISABLED_FIELD_ALPHA else 1f
    }

    private fun isTasteUnit(value: String): Boolean {
        return value.trim().lowercase().contains(TASTE_UNIT)
    }

    private fun addStepRow(data: StepData? = null) {
        val row = layoutInflater.inflate(R.layout.item_add_step, binding.stepsContainer, false)
        val timerLayout = row.findViewById<TextInputLayout>(R.id.tilStepTimer)
        val timerInput = row.findViewById<EditText>(R.id.etStepTimer)
        val timerToggle = row.findViewById<Button>(R.id.btnToggleTimer)

        row.findViewById<View>(R.id.stepImageCard).setOnClickListener {
            currentStepImagePickingIndex = binding.stepsContainer.indexOfChild(row)
            stepImagePicker.launch("image/*")
        }
        row.findViewById<View>(R.id.btnRemoveStep).setOnClickListener { binding.stepsContainer.removeView(row) }
        row.findViewById<Button>(R.id.btnAddStepIngredient).setOnClickListener {
            addIngredientRow(row.findViewById(R.id.stepIngredientsContainer))
        }
        timerToggle.setOnClickListener {
            val showTimer = timerLayout.visibility != View.VISIBLE
            timerLayout.visibility = if (showTimer) View.VISIBLE else View.GONE
            timerToggle.text = if (showTimer) "Убрать таймер" else "+ Таймер"
            if (!showTimer) timerInput.text?.clear()
        }

        data?.let {
            row.findViewById<EditText>(R.id.etStepTitle).setText(it.title)
            row.findViewById<EditText>(R.id.etStepDescription).setText(it.description)
            if (it.timerMinutes > 0) {
                timerInput.setText(it.timerMinutes.toString())
                timerLayout.visibility = View.VISIBLE
                timerToggle.text = "Убрать таймер"
            } else {
                timerInput.text?.clear()
                timerLayout.visibility = View.GONE
                timerToggle.text = "+ Таймер"
            }
            val iv = row.findViewById<ImageView>(R.id.ivStepImage)
            if (it.imagePath.isNotBlank()) {
                iv.tag = it.imagePath
                Glide.with(this).load(it.imagePath).into(iv)
                row.findViewById<View>(R.id.btnSelectStepImage).visibility = View.GONE
            }
            it.ingredients.forEach { ing -> addIngredientRow(row.findViewById(R.id.stepIngredientsContainer), ing) }
        }

        binding.stepsContainer.addView(row)
    }

    private fun saveRecipe() {
        clearRecipeFieldErrors()

        val recipeName = binding.etRecipeName.text.toString().trim()
        if (recipeName.isBlank()) {
            binding.tilRecipeName.error = "Введите название рецепта"
            showValidationMessage("Введите название рецепта")
            return
        }

        val description = binding.etDescription.text.toString().trim()
        if (description.isBlank()) {
            binding.tilDescription.error = "Введите описание рецепта"
            showValidationMessage("Введите описание рецепта")
            return
        }

        if (!validateIngredientRows(binding.ingredientsContainer, "Основные ингредиенты")) return
        val ingredients = collectIngredients(binding.ingredientsContainer)
        val mainIngredientNames = ingredients.map { normalizeIngredientName(it.name) }.toSet()

        if (!validateSteps(mainIngredientNames)) return
        val steps = collectSteps()

        val hoursValue = binding.etCookHours.text.toString().trim().toIntOrNull() ?: 0
        val minutesValue = binding.etCookMinutes.text.toString().trim().toIntOrNull() ?: 0
        if (hoursValue <= 0 && minutesValue <= 0) {
            showValidationMessage("Укажите время приготовления")
            return
        }

        val category = binding.spinnerCategory.text.toString().trim()
        if (category.isBlank()) {
            showValidationMessage("Выберите категорию")
            return
        }

        val cuisine = binding.spinnerCuisine.text.toString().trim()
        if (cuisine.isBlank()) {
            showValidationMessage("Выберите кухню")
            return
        }

        val totalWeight = binding.etTotalWeight.text.toString().trim().toIntOrNull() ?: 0
        if (totalWeight <= 0) {
            showValidationMessage("Укажите вес порции")
            return
        }

        val prepTime = if (hoursValue == 0) "$minutesValue мин" else "$hoursValue ч $minutesValue мин"

        viewModel.saveRecipe(
            name = recipeName,
            description = description,
            category = category,
            cuisine = cuisine,
            cuisineCode = "RU",
            prepTime = prepTime,
            ingredients = ingredients,
            steps = steps,
            mainImage = selectedMainImageBitmap,
            stepImages = stepImageBitmaps,
            userId = FirebaseAuth.getInstance().currentUser?.uid ?: "",
            isPublic = binding.switchPublic.isChecked,
            calories = binding.etCalories.text.toString().toIntOrNull() ?: 0,
            proteins = binding.etProteins.text.toString().toDoubleOrNull() ?: 0.0,
            fats = binding.etFats.text.toString().toDoubleOrNull() ?: 0.0,
            carbs = binding.etCarbs.text.toString().toDoubleOrNull() ?: 0.0,
            currentImagePath = currentMainImagePath,
            totalWeight = totalWeight
        )
    }

    private fun clearRecipeFieldErrors() {
        binding.tilRecipeName.error = null
        binding.tilDescription.error = null
    }

    private fun showValidationMessage(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun validateIngredientRows(container: LinearLayout, sectionName: String): Boolean {
        if (container.childCount == 0) {
            showValidationMessage("$sectionName: добавьте хотя бы один ингредиент")
            return false
        }

        for (i in 0 until container.childCount) {
            val row = container.getChildAt(i)
            val nameLayout = row.findViewById<TextInputLayout>(R.id.layoutIngredientName)
            val quantityLayout = row.findViewById<TextInputLayout>(R.id.layoutIngredientQuantity)
            val name = row.findViewById<EditText>(R.id.etIngredientName).text.toString().trim()
            val quantity = row.findViewById<EditText>(R.id.etIngredientQuantity).text.toString().trim()
            val unit = row.findViewById<AutoCompleteTextView>(R.id.tvIngredientUnit).text.toString().trim()
            val isTaste = isTasteUnit(unit) || isTasteUnit(quantity)

            nameLayout.error = null
            quantityLayout.error = null

            if (name.isBlank()) {
                nameLayout.error = "Введите ингредиент"
                showValidationMessage("$sectionName: заполните название ингредиента")
                return false
            }

            if (unit.isBlank()) {
                showValidationMessage("$sectionName: выберите меру для ингредиента $name")
                return false
            }

            if (!isTaste && quantity.isBlank()) {
                quantityLayout.error = "Укажите количество"
                showValidationMessage("$sectionName: укажите количество для ингредиента $name")
                return false
            }
        }

        return true
    }

    private fun validateSteps(mainIngredientNames: Set<String>): Boolean {
        if (binding.stepsContainer.childCount == 0) {
            showValidationMessage("Добавьте хотя бы один шаг приготовления")
            return false
        }

        for (i in 0 until binding.stepsContainer.childCount) {
            val row = binding.stepsContainer.getChildAt(i)
            val stepNumber = i + 1
            val titleInput = row.findViewById<EditText>(R.id.etStepTitle)
            val descriptionInput = row.findViewById<EditText>(R.id.etStepDescription)
            val stepIngredientsContainer = row.findViewById<LinearLayout>(R.id.stepIngredientsContainer)

            titleInput.error = null
            descriptionInput.error = null

            if (titleInput.text.toString().trim().isBlank()) {
                titleInput.error = "Введите заголовок"
                showValidationMessage("Шаг $stepNumber: введите заголовок")
                return false
            }

            if (descriptionInput.text.toString().trim().isBlank()) {
                descriptionInput.error = "Введите описание"
                showValidationMessage("Шаг $stepNumber: введите описание")
                return false
            }

            if (!validateIngredientRows(stepIngredientsContainer, "Шаг $stepNumber")) return false

            for (j in 0 until stepIngredientsContainer.childCount) {
                val ingredientRow = stepIngredientsContainer.getChildAt(j)
                val nameLayout = ingredientRow.findViewById<TextInputLayout>(R.id.layoutIngredientName)
                val ingredientName = ingredientRow.findViewById<EditText>(R.id.etIngredientName).text.toString().trim()
                if (normalizeIngredientName(ingredientName) !in mainIngredientNames) {
                    nameLayout.error = "Нет в основном списке"
                    showValidationMessage("Шаг $stepNumber: используйте ингредиенты из основного списка рецепта")
                    return false
                }
            }
        }

        return true
    }

    private fun normalizeIngredientName(name: String): String {
        return name.trim().lowercase()
    }

    private fun collectIngredients(container: LinearLayout): List<CloudIngredient> {
        return (0 until container.childCount).map { i ->
            val v = container.getChildAt(i)
            val unitText = v.findViewById<AutoCompleteTextView>(R.id.tvIngredientUnit).text.toString()
            val quantityText = v.findViewById<EditText>(R.id.etIngredientQuantity).text.toString()
            val isTaste = isTasteUnit(unitText) || isTasteUnit(quantityText)
            CloudIngredient(
                v.findViewById<EditText>(R.id.etIngredientName).text.toString(),
                if (isTaste) "" else quantityText,
                if (isTaste) TASTE_UNIT else unitText
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
            binding.btnRemoveImage.visibility = View.VISIBLE
        } else {
            clearMainRecipeImage()
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
            val resolver = requireContext().contentResolver
            val options = BitmapFactory.Options().apply { inSampleSize = 2 }
            val bitmap = resolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, options)
            } ?: return null

            val orientation = resolver.openInputStream(uri)?.use { inputStream ->
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

            if (degrees == 0f) {
                bitmap
            } else {
                val matrix = Matrix().apply { postRotate(degrees) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            }
        } catch (e: Exception) {
            Log.e("AddRecipeFragment", "Bitmap failed", e)
            null
        }
    }

    companion object {
        private const val TASTE_UNIT = "по вкусу"
        private const val DISABLED_FIELD_ALPHA = 0.55f
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}