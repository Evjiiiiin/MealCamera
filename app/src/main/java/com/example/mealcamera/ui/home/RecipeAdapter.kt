package com.example.mealcamera.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.mealcamera.R
import com.example.mealcamera.data.model.Recipe
import com.example.mealcamera.databinding.ItemRecipeCardBinding

class RecipeAdapter(
    private val onItemClick: (Recipe) -> Unit,
    private val onFavoriteClick: (Recipe, Boolean) -> Unit
) : ListAdapter<Recipe, RecipeAdapter.ViewHolder>(RecipeDiffCallback()) {

    private val favoriteIds = mutableSetOf<Long>()

    fun setFavoriteIds(ids: Set<Long>) {
        favoriteIds.clear()
        favoriteIds.addAll(ids)
        notifyDataSetChanged()
    }

    fun updateFavoriteStatus(recipeId: Long, isFavorite: Boolean) {
        if (isFavorite) {
            favoriteIds.add(recipeId)
        } else {
            favoriteIds.remove(recipeId)
        }
        val position = currentList.indexOfFirst { it.recipeId == recipeId }
        if (position != -1) {
            notifyItemChanged(position)
        }
    }

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

        @Suppress("UNUSED_PARAMETER")
        fun bind(recipe: Recipe) {
            binding.recipeNameTextView.text = recipe.name
            binding.recipeTypeTextView.text = recipe.category

            Glide.with(binding.root.context)
                .load(recipe.imagePath)
                .centerCrop()
                .placeholder(R.drawable.ic_recipe_placeholder)
                .into(binding.recipeImageView)

            val isFavorite = favoriteIds.contains(recipe.recipeId)
            binding.btnFavorite.setImageDrawable(
                ContextCompat.getDrawable(
                    binding.root.context,
                    if (isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_outline
                )
            )

            binding.root.setOnClickListener { onItemClick(recipe) }

            binding.btnFavorite.setOnClickListener {
                val newState = !isFavorite
                onFavoriteClick(recipe, newState)
            }
        }
    }

    class RecipeDiffCallback : DiffUtil.ItemCallback<Recipe>() {
        override fun areItemsTheSame(oldItem: Recipe, newItem: Recipe) =
            oldItem.recipeId == newItem.recipeId

        override fun areContentsTheSame(oldItem: Recipe, newItem: Recipe) =
            oldItem == newItem
    }
}