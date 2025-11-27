package com.example.mealcamera.ui.detail

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mealcamera.databinding.ItemIngredientBinding
import com.example.mealcamera.data.model.IngredientWithDetails

class IngredientsAdapter : ListAdapter<IngredientWithDetails, IngredientsAdapter.ViewHolder>(DiffCallback) {

    class ViewHolder(private val binding: ItemIngredientBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: IngredientWithDetails) {
            binding.ingredientName.text = item.ingredient.name
            binding.ingredientQuantityUnit.text = "${item.quantity} ${item.unit}".trim()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemIngredientBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object DiffCallback : DiffUtil.ItemCallback<IngredientWithDetails>() {
        override fun areItemsTheSame(old: IngredientWithDetails, new: IngredientWithDetails) =
            old.ingredient.ingredientId == new.ingredient.ingredientId

        override fun areContentsTheSame(old: IngredientWithDetails, new: IngredientWithDetails) =
            old == new
    }
}