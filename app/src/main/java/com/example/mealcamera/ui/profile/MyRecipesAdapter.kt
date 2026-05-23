package com.example.mealcamera.ui.profile

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.mealcamera.R
import com.example.mealcamera.data.model.Recipe
import com.example.mealcamera.databinding.ItemMyRecipeBinding

class MyRecipesAdapter(
    private val onDeleteClick: (Recipe) -> Unit,
    private val onEditClick: (Recipe) -> Unit,
    private val onItemClick: (Recipe) -> Unit
) : ListAdapter<Recipe, MyRecipesAdapter.ViewHolder>(RecipeDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMyRecipeBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemMyRecipeBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(recipe: Recipe) {
            binding.tvRecipeName.text = recipe.name
            binding.tvRecipeCategory.text = "${recipe.category} • ${recipe.cuisine}"

            Glide.with(binding.root.context)
                .load(recipe.imagePath)
                .centerCrop()
                .placeholder(R.drawable.ic_recipe_placeholder)
                .error(R.drawable.ic_recipe_placeholder)
                .fallback(R.drawable.ic_recipe_placeholder)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .dontAnimate()
                .into(binding.ivRecipe)

            binding.root.setOnClickListener { onItemClick(recipe) }
            binding.btnDelete.setOnClickListener { onDeleteClick(recipe) }
            binding.btnEdit.setOnClickListener { onEditClick(recipe) }
        }
    }

    class RecipeDiffCallback : DiffUtil.ItemCallback<Recipe>() {
        override fun areItemsTheSame(oldItem: Recipe, newItem: Recipe) =
            oldItem.recipeId == newItem.recipeId

        override fun areContentsTheSame(oldItem: Recipe, newItem: Recipe) =
            oldItem == newItem
    }
}