package com.example.mealcamera.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.view.ContextThemeWrapper
import com.example.mealcamera.R
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

    private var _binding: BottomSheetFiltersBinding? = null
    private val binding get() = _binding!!

    private val categories = listOf("Все", "Завтрак", "Обед", "Ужин", "Десерт", "Напитки", "Перекус")
    private val cuisines = listOf("Все кухни", "Русская", "Итальянская", "Испанская", "Французская", "Американская", "Азиатская")

    companion object {
        private const val ARG_FILTERS = "arg_filters"

        fun newInstance(filters: FilterState): FilterBottomSheetFragment {
            return FilterBottomSheetFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(ARG_FILTERS, filters as? Serializable)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        currentFilters = arguments?.getSerializable(ARG_FILTERS) as? FilterState ?: FilterState()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetFiltersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initAllChipGroups()
        setupSliders()
        restoreFiltersState()
        
        binding.tvResetFilters.setOnClickListener { resetFilters() }
        binding.btnApply.setOnClickListener { applyAndDismiss() }
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.behavior?.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun initAllChipGroups() {
        fillGroup(binding.categoryChipGroup, categories)
        fillGroup(binding.cuisineChipGroup, cuisines)
    }

    private fun fillGroup(group: ChipGroup, list: List<String>) {
        group.removeAllViews()
        list.forEach { title ->
            // Применяем стиль Widget_MealCamera_FilterChip через ContextThemeWrapper
            val themedContext = ContextThemeWrapper(requireContext(), R.style.Widget_MealCamera_FilterChip)
            val chip = Chip(themedContext).apply {
                text = title
                isCheckable = true
                setEnsureMinTouchTargetSize(true)
            }
            group.addView(chip)
        }
    }

    private fun setupSliders() {
        binding.sliderPrepTime.setLabelFormatter { "${it.toInt()} мин" }
        binding.sliderCalories.setLabelFormatter { "${it.toInt()} ккал" }

        binding.sliderPrepTime.addOnChangeListener { _, _, _ -> updateLabels() }
        binding.sliderCalories.addOnChangeListener { _, _, _ -> updateLabels() }
    }

    private fun updateLabels() {
        val t = binding.sliderPrepTime.values
        val c = binding.sliderCalories.values
        if (t.size >= 2) binding.tvPrepTimeRange.text = "${t[0].toInt()} – ${t[1].toInt()} мин"
        if (c.size >= 2) binding.tvCaloriesRange.text = "${c[0].toInt()} – ${c[1].toInt()} ккал"
    }

    private fun restoreFiltersState() {
        checkChips(binding.categoryChipGroup, currentFilters.selectedCategories, "Все")
        checkChips(binding.cuisineChipGroup, currentFilters.selectedCuisines, "Все кухни")

        currentFilters.prepTimeRange?.let { 
            binding.sliderPrepTime.values = listOf(it.start, it.endInclusive) 
        }
        currentFilters.caloriesRange?.let { 
            binding.sliderCalories.values = listOf(it.start, it.endInclusive) 
        }
        updateLabels()
    }

    private fun checkChips(group: ChipGroup, selected: Set<String>, default: String) {
        for (i in 0 until group.childCount) {
            val chip = group.getChildAt(i) as Chip
            val txt = chip.text.toString()
            chip.isChecked = if (selected.isEmpty()) txt == default else selected.contains(txt)
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
            selectedCategories = getSelected(binding.categoryChipGroup, "Все"),
            selectedCuisines = getSelected(binding.cuisineChipGroup, "Все кухни"),
            prepTimeRange = binding.sliderPrepTime.values.let { it[0]..it[1] },
            caloriesRange = binding.sliderCalories.values.let { it[0]..it[1] }
        )
        onApply?.invoke(st)
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}