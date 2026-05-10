package com.example.mealcamera.ui.result

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mealcamera.R
import com.example.mealcamera.data.model.EditableIngredient
import com.example.mealcamera.databinding.ItemEditableIngredientBinding

class EditableIngredientAdapter(
    private val onDeleteClick: (EditableIngredient) -> Unit,
    private val onUpdateClick: (EditableIngredient) -> Unit
) : ListAdapter<EditableIngredient, EditableIngredientAdapter.IngredientViewHolder>(DiffCallback()) {

    private val units = listOf("г", "кг", "мл", "л", "шт", "ст.л.", "ч.л.", "зубчик", "щепотка")

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IngredientViewHolder {
        val binding = ItemEditableIngredientBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return IngredientViewHolder(binding)
    }

    override fun onBindViewHolder(holder: IngredientViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class IngredientViewHolder(private val binding: ItemEditableIngredientBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(ingredient: EditableIngredient) {
            binding.ingredient = ingredient
            
            // Настройка количества
            binding.ingredientQuantity.setText(ingredient.quantity)
            binding.ingredientQuantity.doAfterTextChanged { s ->
                val newVal = s?.toString() ?: ""
                if (newVal != ingredient.quantity) {
                    ingredient.quantity = newVal
                    onUpdateClick(ingredient)
                }
            }

            // Настройка единиц измерения (Меры)
            val unitAdapter = ArrayAdapter(binding.root.context, android.R.layout.simple_dropdown_item_1line, units)
            binding.unitAutoComplete.setAdapter(unitAdapter)
            binding.unitAutoComplete.setText(ingredient.unit, false)
            
            binding.unitAutoComplete.setOnItemClickListener { _, _, position, _ ->
                val newUnit = units[position]
                if (newUnit != ingredient.unit) {
                    ingredient.unit = newUnit
                    onUpdateClick(ingredient)
                }
            }

            binding.btnRemoveIngredient.setOnClickListener {
                onDeleteClick(ingredient)
            }

            binding.executePendingBindings()
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<EditableIngredient>() {
        override fun areItemsTheSame(oldItem: EditableIngredient, newItem: EditableIngredient) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: EditableIngredient, newItem: EditableIngredient) = oldItem == newItem
    }
}
