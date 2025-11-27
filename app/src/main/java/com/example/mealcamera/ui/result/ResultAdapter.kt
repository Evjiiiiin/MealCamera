package com.example.mealcamera.ui.result

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.mealcamera.R
import com.example.mealcamera.data.model.RecipeResult
import com.example.mealcamera.databinding.ItemRecipeResultBinding

class ResultAdapter(
    private val onClick: (RecipeResult) -> Unit
) : ListAdapter<RecipeResult, ResultAdapter.ViewHolder>(ResultDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecipeResultBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemRecipeResultBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(result: RecipeResult) {
            // ИСПОЛЬЗУЕМ ПРАВИЛЬНЫЕ ID ИЗ XML
            // id: recipeName -> binding.recipeName
            binding.recipeName.text = result.recipe.name

            // id: recipeImage -> binding.recipeImage
            Glide.with(binding.root.context)
                .load(result.recipe.imagePath)
                .centerCrop()
                .placeholder(R.drawable.ic_recipe_placeholder)
                .into(binding.recipeImage)

            // id: missingIngredients -> binding.missingIngredients
            if (result.missingIngredients.isNotEmpty()) {
                binding.missingIngredients.visibility = View.VISIBLE
                binding.missingIngredients.text = "Не хватает: ${result.missingIngredients.joinToString(", ")}"
            } else {
                // Если недостающих ингредиентов нет, скрываем это поле
                binding.missingIngredients.visibility = View.GONE
            }

            // Удалены обращения к tvDescription и tvPrepTime, так как их нет в XML

            binding.root.setOnClickListener { onClick(result) }
        }
    }

    class ResultDiffCallback : DiffUtil.ItemCallback<RecipeResult>() {
        override fun areItemsTheSame(oldItem: RecipeResult, newItem: RecipeResult) =
            oldItem.recipe.recipeId == newItem.recipe.recipeId

        override fun areContentsTheSame(oldItem: RecipeResult, newItem: RecipeResult) =
            oldItem == newItem
    }
}
