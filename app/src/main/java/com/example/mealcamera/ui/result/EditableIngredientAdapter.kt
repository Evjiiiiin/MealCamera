package com.example.mealcamera.ui.result

import android.app.AlertDialog
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import com.example.mealcamera.data.model.EditableIngredient
import com.example.mealcamera.data.util.UnitHelper
import com.example.mealcamera.databinding.ItemEditableIngredientBinding

class EditableIngredientAdapter(
    private var ingredients: List<EditableIngredient>,
    private val onDeleteClick: (EditableIngredient) -> Unit,
    private val onUpdateClick: (EditableIngredient) -> Unit,
    private val fragmentActivity: FragmentActivity
) : RecyclerView.Adapter<EditableIngredientAdapter.IngredientViewHolder>() {

    inner class IngredientViewHolder(private val binding: ItemEditableIngredientBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var currentWatcher: TextWatcher? = null

        fun bind(ingredient: EditableIngredient) {
            binding.ingredient = ingredient
            
            binding.ingredientQuantity.removeTextChangedListener(currentWatcher)
            binding.ingredientQuantity.setText(ingredient.quantity)

            currentWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val newVal = s?.toString() ?: ""
                    if (newVal != ingredient.quantity) {
                        ingredient.quantity = newVal
                        onUpdateClick(ingredient)
                    }
                }
                override fun afterTextChanged(s: Editable?) {}
            }
            binding.ingredientQuantity.addTextChangedListener(currentWatcher)

            binding.unitContainer.setOnClickListener {
                val availableUnits = UnitHelper.getAvailableUnits(ingredient.name)
                // Используем adapterPosition для совместимости
                showUnitDialog(ingredient, availableUnits, adapterPosition)
            }

            binding.btnRemoveIngredient.setOnClickListener {
                onDeleteClick(ingredient)
            }

            binding.executePendingBindings()
        }

        private fun showUnitDialog(ingredient: EditableIngredient, units: List<String>, position: Int) {
            AlertDialog.Builder(fragmentActivity)
                .setTitle("Выберите единицу")
                .setItems(units.toTypedArray()) { dialog, which ->
                    ingredient.unit = units[which]
                    onUpdateClick(ingredient)
                    notifyItemChanged(position)
                    dialog.dismiss()
                }
                .setNegativeButton("Отмена", null)
                .show()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IngredientViewHolder {
        val binding = ItemEditableIngredientBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        // Исправлено: имя класса IngredientViewHolder
        return IngredientViewHolder(binding)
    }

    override fun onBindViewHolder(holder: IngredientViewHolder, position: Int) {
        holder.bind(ingredients[position])
    }

    override fun getItemCount() = ingredients.size

    fun updateIngredients(newIngredients: List<EditableIngredient>) {
        this.ingredients = newIngredients
        notifyDataSetChanged()
    }
}