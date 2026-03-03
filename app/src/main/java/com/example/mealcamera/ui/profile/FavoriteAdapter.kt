package com.example.mealcamera.ui.profile

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.mealcamera.R
import com.example.mealcamera.data.model.Recipe
import com.example.mealcamera.databinding.ItemFavoriteRecipeBinding

class FavoriteAdapter(
    private val onItemClick: (Recipe) -> Unit,
    private val onFavoriteClick: (Recipe) -> Unit
) : ListAdapter<Recipe, FavoriteAdapter.FavoriteViewHolder>(FavoriteDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FavoriteViewHolder {
        val binding = ItemFavoriteRecipeBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return FavoriteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FavoriteViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class FavoriteViewHolder(private val binding: ItemFavoriteRecipeBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(recipe: Recipe) {
            binding.recipeName.text = recipe.name
            binding.recipeCategory.text = recipe.category
            binding.recipePrepTime.text = recipe.prepTime

            Glide.with(binding.root.context)
                .load(recipe.imagePath)
                .centerCrop()
                .placeholder(R.drawable.ic_recipe_placeholder)
                .into(binding.recipeImage)

            // В избранном всегда показываем заполненное сердечко
            binding.btnFavorite.setImageDrawable(
                ContextCompat.getDrawable(binding.root.context, R.drawable.ic_favorite_filled)
            )

            binding.root.setOnClickListener { onItemClick(recipe) }

            binding.btnFavorite.setOnClickListener {
                onFavoriteClick(recipe)
            }
        }
    }

    class FavoriteDiffCallback : DiffUtil.ItemCallback<Recipe>() {
        override fun areItemsTheSame(oldItem: Recipe, newItem: Recipe) =
            oldItem.recipeId == newItem.recipeId

        override fun areContentsTheSame(oldItem: Recipe, newItem: Recipe) =
            oldItem == newItem
    }
}