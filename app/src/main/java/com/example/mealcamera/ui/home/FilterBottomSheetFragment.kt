package com.example.mealcamera.ui.home

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.example.mealcamera.R
import com.example.mealcamera.data.model.DEFAULT_MAX_CALORIES_PER_PORTION
import com.example.mealcamera.data.model.FilterState
import com.example.mealcamera.databinding.BottomSheetFiltersBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import java.io.Serializable

class FilterBottomSheetFragment : BottomSheetDialogFragment() {

    var onApply: ((FilterState) -> Unit)? = null
    private var currentFilters: FilterState = FilterState()
    private var maxCaloriesLimit: Float = DEFAULT_MAX_CALORIES_PER_PORTION

    private var _binding: BottomSheetFiltersBinding? = null
    private val binding get() = _binding!!

    companion object {
        private const val ARG_FILTERS = "arg_filters"
        private const val DEFAULT_CATEGORY = "Все"
        private const val DEFAULT_CUISINE = "Все кухни"
        private const val FILTER_CHIP_STROKE_WIDTH_DP = 1f
        private const val ARG_MAX_CALORIES = "arg_max_calories"

        fun newInstance(filters: FilterState, maxCaloriesLimit: Float): FilterBottomSheetFragment {
            return FilterBottomSheetFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(ARG_FILTERS, filters as Serializable)
                    putFloat(ARG_MAX_CALORIES, maxCaloriesLimit)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        currentFilters = arguments?.getSerializable(ARG_FILTERS) as? FilterState ?: FilterState()
        maxCaloriesLimit = (arguments?.getFloat(ARG_MAX_CALORIES, DEFAULT_MAX_CALORIES_PER_PORTION)
            ?: DEFAULT_MAX_CALORIES_PER_PORTION).coerceAtLeast(DEFAULT_MAX_CALORIES_PER_PORTION)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetFiltersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupChipGroups()
        setupSliders()
        restoreFiltersState()
        binding.root.post { refreshChipAppearances() }

        binding.tvResetFilters.setOnClickListener { resetFilters() }
        binding.btnApply.setOnClickListener { applyAndDismiss() }
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.behavior?.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun setupChipGroups() {
        setupChipGroup(binding.categoryChipGroup, DEFAULT_CATEGORY)
        setupChipGroup(binding.cuisineChipGroup, DEFAULT_CUISINE)
    }

    private fun setupChipGroup(group: ChipGroup, defaultText: String) {
        for (i in 0 until group.childCount) {
            val chip = group.getChildAt(i) as? Chip ?: continue
            chip.isCheckable = true
            chip.checkedIcon = null
            chip.isCheckedIconVisible = false
            chip.setEnsureMinTouchTargetSize(true)
            chip.elevation = 0f
            chip.translationZ = 0f
            chip.stateListAnimator = null
            chip.updateFilterChipAppearance(isChecked = false)
            chip.setOnCheckedChangeListener { buttonView, isChecked ->
                val changedChip = buttonView as Chip
                handleChipCheckedChange(group, changedChip, isChecked, defaultText)
            }
        }
    }

    private fun handleChipCheckedChange(
        group: ChipGroup,
        changedChip: Chip,
        isChecked: Boolean,
        defaultText: String
    ) {
        val isDefaultChip = changedChip.text.toString() == defaultText

        if (isDefaultChip && isChecked) {
            uncheckNonDefaultChips(group, defaultText)
        }

        if (!isDefaultChip && isChecked) {
            findChipByText(group, defaultText)?.isChecked = false
        }

        if (!hasCheckedNonDefaultChips(group, defaultText) && !isDefaultChip) {
            findChipByText(group, defaultText)?.isChecked = true
        }

        if (isDefaultChip && !isChecked && !hasCheckedNonDefaultChips(group, defaultText)) {
            changedChip.isChecked = true
            return
        }

        refreshChipGroupAppearance(group)
    }

    private fun uncheckNonDefaultChips(group: ChipGroup, defaultText: String) {
        for (i in 0 until group.childCount) {
            val chip = group.getChildAt(i) as? Chip ?: continue
            if (chip.text.toString() != defaultText && chip.isChecked) {
                chip.isChecked = false
            }
        }
    }

    private fun hasCheckedNonDefaultChips(group: ChipGroup, defaultText: String): Boolean {
        for (i in 0 until group.childCount) {
            val chip = group.getChildAt(i) as? Chip ?: continue
            if (chip.text.toString() != defaultText && chip.isChecked) return true
        }
        return false
    }

    private fun findChipByText(group: ChipGroup, text: String): Chip? {
        for (i in 0 until group.childCount) {
            val chip = group.getChildAt(i) as? Chip ?: continue
            if (chip.text.toString() == text) return chip
        }
        return null
    }

    private val filterChipStrokeWidthPx: Float
        get() = FILTER_CHIP_STROKE_WIDTH_DP * resources.displayMetrics.density

    private fun refreshChipAppearances() {
        refreshChipGroupAppearance(binding.categoryChipGroup)
        refreshChipGroupAppearance(binding.cuisineChipGroup)
    }

    private fun refreshChipGroupAppearance(group: ChipGroup) {
        for (i in 0 until group.childCount) {
            (group.getChildAt(i) as? Chip)?.let { chip ->
                chip.updateFilterChipAppearance(chip.isChecked)
            }
        }
    }

    private fun Chip.updateFilterChipAppearance(isChecked: Boolean) {
        val backgroundColor = ContextCompat.getColor(
            context,
            if (isChecked) R.color.color_primary else R.color.surface_card
        )
        val strokeColor = ContextCompat.getColor(
            context,
            if (isChecked) android.R.color.transparent else R.color.border_light
        )
        val textColor = ContextCompat.getColor(
            context,
            if (isChecked) R.color.color_on_primary else R.color.text_main
        )

        chipBackgroundColor = ColorStateList.valueOf(backgroundColor)
        chipStrokeColor = ColorStateList.valueOf(strokeColor)
        setTextColor(textColor)
        chipStrokeWidth = if (isChecked) 0f else filterChipStrokeWidthPx
        invalidate()
    }

    private fun setupSliders() {
        binding.sliderPrepTime.setLabelFormatter { "${it.toInt()} мин" }

        binding.sliderCalories.valueTo = maxCaloriesLimit
        binding.sliderCalories.values = currentCaloriesSliderValues()
        binding.sliderCalories.setLabelFormatter { "${it.toInt()} ккал" }

        binding.sliderPrepTime.addOnChangeListener { _, _, _ -> updateLabels() }
        binding.sliderCalories.addOnChangeListener { _, _, _ -> updateLabels() }
    }


    private fun currentCaloriesSliderValues(): List<Float> {
        val minValue = currentFilters.minCalories.coerceIn(0f, maxCaloriesLimit)
        val requestedMax = if (currentFilters.maxCalories >= DEFAULT_MAX_CALORIES_PER_PORTION) {
            maxCaloriesLimit
        } else {
            currentFilters.maxCalories
        }
        val maxValue = requestedMax.coerceIn(minValue, maxCaloriesLimit)
        return listOf(minValue, maxValue)
    }

    private fun updateLabels() {
        val t = binding.sliderPrepTime.values
        val c = binding.sliderCalories.values
        if (t.size >= 2) binding.tvPrepTimeRange.text = "${t[0].toInt()} – ${t[1].toInt()} мин"
        if (c.size >= 2) {
            val maxLabel = if (c[1] >= maxCaloriesLimit) "${maxCaloriesLimit.toInt()}+" else c[1].toInt().toString()
            binding.tvCaloriesRange.text = "${c[0].toInt()} – $maxLabel ккал"
        }
    }

    private fun restoreFiltersState() {
        checkChips(binding.categoryChipGroup, currentFilters.selectedCategories, DEFAULT_CATEGORY)
        checkChips(binding.cuisineChipGroup, currentFilters.selectedCuisines, DEFAULT_CUISINE)

        binding.sliderPrepTime.values = listOf(currentFilters.minPrepTime, currentFilters.maxPrepTime)
        binding.sliderCalories.values = currentCaloriesSliderValues()

        updateLabels()
    }

    private fun checkChips(group: ChipGroup, selected: Set<String>, default: String) {
        for (i in 0 until group.childCount) {
            val chip = group.getChildAt(i) as Chip
            val txt = chip.text.toString()
            chip.isChecked = if (selected.isEmpty()) txt == default else selected.contains(txt)
            chip.updateFilterChipAppearance(chip.isChecked)
        }
    }

    private fun getSelected(group: ChipGroup, ignore: String): Set<String> {
        val set = mutableSetOf<String>()
        for (i in 0 until group.childCount) {
            val chip = group.getChildAt(i) as Chip
            if (chip.isChecked && chip.text != ignore) set.add(chip.text.toString())
        }
        return set
    }

    private fun resetFilters() {
        currentFilters = FilterState()
        restoreFiltersState()
    }

    private fun applyAndDismiss() {
        val st = FilterState(
            selectedCategories = getSelected(binding.categoryChipGroup, DEFAULT_CATEGORY),
            selectedCuisines = getSelected(binding.cuisineChipGroup, DEFAULT_CUISINE),
            minPrepTime = binding.sliderPrepTime.values[0],
            maxPrepTime = binding.sliderPrepTime.values[1],
            minCalories = binding.sliderCalories.values[0],
            maxCalories = binding.sliderCalories.values[1]
        )
        onApply?.invoke(st)
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}