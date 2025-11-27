package com.example.mealcamera.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.mealcamera.R
import com.example.mealcamera.data.model.Recipe
import com.example.mealcamera.databinding.ItemRecipeCardBinding

class RecipeAdapter(
    private val onClick: (Recipe) -> Unit
) : ListAdapter<Recipe, RecipeAdapter.ViewHolder>(RecipeDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecipeCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemRecipeCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(recipe: Recipe) {
            // ИСПОЛЬЗУЕМ ПРАВИЛЬНЫЕ ID ИЗ XML
            // android:id="@+id/recipeNameTextView" -> binding.recipeNameTextView
            binding.recipeNameTextView.text = recipe.name

            // android:id="@+id/recipeTypeTextView" -> binding.recipeTypeTextView
            // В этот TextView мы будем выводить категорию (завтрак, обед и т.д.)
            binding.recipeTypeTextView.text = recipe.category

            // android:id="@+id/recipeImageView" -> binding.recipeImageView
            Glide.with(binding.root.context)
                .load(recipe.imagePath)
                .centerCrop()
                .placeholder(R.drawable.ic_recipe_placeholder)
                .into(binding.recipeImageView)

            binding.root.setOnClickListener { onClick(recipe) }
        }
    }

    class RecipeDiffCallback : DiffUtil.ItemCallback<Recipe>() {
        override fun areItemsTheSame(oldItem: Recipe, newItem: Recipe) =
            oldItem.recipeId == newItem.recipeId

        override fun areContentsTheSame(oldItem: Recipe, newItem: Recipe) =
            oldItem == newItem
    }
}
