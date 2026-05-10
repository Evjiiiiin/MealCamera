package com.example.mealcamera.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.mealcamera.R
import com.example.mealcamera.data.model.Recipe
import com.example.mealcamera.data.model.RecipeResult
import com.example.mealcamera.databinding.ItemRecipeCardBinding

class RecipeAdapter(
    private val onItemClick: (Recipe) -> Unit,
    private val onFavoriteClick: (Recipe, Boolean) -> Unit
) : ListAdapter<Any, RecipeAdapter.RecipeViewHolder>(DiffCallback()) {

    private var favoriteIds: Set<Long> = emptySet()

    fun setFavoriteIds(ids: Set<Long>) {
        favoriteIds = ids
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecipeViewHolder {
        val binding = ItemRecipeCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RecipeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecipeViewHolder, position: Int) {
        val item = getItem(position)
        when (item) {
            is Recipe -> holder.bind(item, null)
            is RecipeResult -> holder.bind(item.recipe, item.missingIngredients)
        }
    }

    inner class RecipeViewHolder(private val binding: ItemRecipeCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(recipe: Recipe, missingIngredients: List<String>?) {
            binding.recipeNameTextView.text = recipe.name
            binding.recipeTypeTextView.text = recipe.category

            val missingText = binding.tvMissingIngredients
            if (!missingIngredients.isNullOrEmpty()) {
                missingText.visibility = View.VISIBLE
                missingText.text = "Не хватает: " + missingIngredients.joinToString(", ")
            } else {
                missingText.visibility = View.GONE
            }

            val isFavorite = favoriteIds.contains(recipe.recipeId)
            binding.btnFavorite.setImageResource(
                if (isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_outlined
            )

            Glide.with(binding.recipeImageView)
                .load(recipe.imagePath)
                .placeholder(R.drawable.ic_recipe_placeholder)
                .error(R.drawable.ic_recipe_placeholder)
                .into(binding.recipeImageView)

            binding.root.setOnClickListener { onItemClick(recipe) }
            binding.btnFavorite.setOnClickListener { onFavoriteClick(recipe, !isFavorite) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Any>() {
        override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean {
            val oldId = when(oldItem) {
                is Recipe -> oldItem.recipeId
                is RecipeResult -> oldItem.recipe.recipeId
                else -> -1L
            }
            val newId = when(newItem) {
                is Recipe -> newItem.recipeId
                is RecipeResult -> newItem.recipe.recipeId
                else -> -1L
            }
            return oldId == newId && oldId != -1L
        }

        override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean {
            return if (oldItem is RecipeResult && newItem is RecipeResult) {
                oldItem.recipe == newItem.recipe && oldItem.missingIngredients == newItem.missingIngredients
            } else {
                oldItem == newItem
            }
        }
    }
}
