package com.example.mealcamera.ui.result

import android.app.AlertDialog
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import com.example.mealcamera.data.model.EditableIngredient
import com.example.mealcamera.data.util.UnitHelper
import com.example.mealcamera.databinding.ItemEditableIngredientBinding

class EditableIngredientAdapter(
    private val ingredients: MutableList<EditableIngredient>,
    private val onDeleteClick: (EditableIngredient) -> Unit,
    private val fragmentActivity: FragmentActivity
) : RecyclerView.Adapter<EditableIngredientAdapter.IngredientViewHolder>() {

    inner class IngredientViewHolder(private val binding: ItemEditableIngredientBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(ingredient: EditableIngredient, position: Int) {
            binding.ingredient = ingredient

            // 👇 КЛИК НА КОНТЕЙНЕР С ЕДИНИЦЕЙ
            binding.unitContainer.setOnClickListener {
                val availableUnits = UnitHelper.getAvailableUnits(ingredient.name)
                showUnitDialog(ingredient, availableUnits, position)
            }

            binding.btnRemoveIngredient.setOnClickListener {
                onDeleteClick(ingredient)
            }
            binding.executePendingBindings()
        }

        private fun showUnitDialog(
            ingredient: EditableIngredient,
            units: List<String>,
            position: Int
        ) {
            AlertDialog.Builder(fragmentActivity)
                .setTitle("Выберите единицу для ${ingredient.name}")
                .setItems(units.toTypedArray()) { dialog, which ->
                    ingredient.unit = units[which]
                    notifyItemChanged(position)
                    dialog.dismiss()
                }
                .setNegativeButton("Отмена", null)
                .show()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IngredientViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemEditableIngredientBinding.inflate(inflater, parent, false)
        return IngredientViewHolder(binding)
    }

    override fun onBindViewHolder(holder: IngredientViewHolder, position: Int) {
        holder.bind(ingredients[position], position)
    }

    override fun getItemCount() = ingredients.size

    fun getEditedIngredients(): List<EditableIngredient> {
        return ingredients
    }

    fun updateIngredients(newIngredients: List<EditableIngredient>) {
        val snapshot = newIngredients.toList()
        ingredients.clear()
        ingredients.addAll(snapshot)
        notifyDataSetChanged()
    }
}