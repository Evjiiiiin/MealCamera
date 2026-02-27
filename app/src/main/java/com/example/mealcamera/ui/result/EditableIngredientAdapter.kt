package com.example.mealcamera.ui.result

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.mealcamera.data.model.EditableIngredient
import com.example.mealcamera.databinding.ItemEditableIngredientBinding

class EditableIngredientAdapter(
    private val ingredients: MutableList<EditableIngredient>,
    private val onDeleteClick: (EditableIngredient) -> Unit
) : RecyclerView.Adapter<EditableIngredientAdapter.IngredientViewHolder>() {

    inner class IngredientViewHolder(private val binding: ItemEditableIngredientBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(ingredient: EditableIngredient) {
            binding.ingredient = ingredient
            binding.btnRemoveIngredient.setOnClickListener {
                onDeleteClick(ingredient)
            }
            binding.executePendingBindings()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IngredientViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemEditableIngredientBinding.inflate(inflater, parent, false)
        return IngredientViewHolder(binding)
    }

    override fun onBindViewHolder(holder: IngredientViewHolder, position: Int) {
        holder.bind(ingredients[position])
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
