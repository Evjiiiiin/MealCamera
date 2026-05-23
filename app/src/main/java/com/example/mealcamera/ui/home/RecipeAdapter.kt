package com.example.mealcamera.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.mealcamera.R
import com.example.mealcamera.data.model.MissingIngredientData
import com.example.mealcamera.data.model.Recipe
import com.example.mealcamera.data.model.RecipeResult
import com.example.mealcamera.databinding.ItemRecipeCardBinding

class RecipeAdapter(
    private val onItemClick: (Recipe) -> Unit,
    private val onFavoriteClick: (Recipe, Boolean) -> Unit,
    private val onAddToShoppingList: ((List<MissingIngredientData>) -> Unit)? = null
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
            is Recipe -> holder.bind(item, null, emptyList())
            is RecipeResult -> holder.bind(item.recipe, item.missingIngredients, item.structuredMissingIngredients)
        }
    }

    inner class RecipeViewHolder(private val binding: ItemRecipeCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(recipe: Recipe, missingIngredients: List<String>?, structuredMissing: List<MissingIngredientData>) {
            binding.recipeNameTextView.text = recipe.name
            binding.recipeTypeTextView.text = recipe.category

            val missingText = binding.tvMissingIngredients
            val btnAddShopping = binding.btnAddToShoppingList

            if (!missingIngredients.isNullOrEmpty()) {
                missingText.visibility = View.VISIBLE
                missingText.text = "Не хватает: " + missingIngredients.joinToString(", ")

                if (onAddToShoppingList != null && structuredMissing.isNotEmpty()) {
                    btnAddShopping.visibility = View.VISIBLE
                    btnAddShopping.setOnClickListener { onAddToShoppingList.invoke(structuredMissing) }
                } else {
                    btnAddShopping.visibility = View.GONE
                }
            } else {
                missingText.visibility = View.GONE
                btnAddShopping.visibility = View.GONE
            }

            val isFavorite = favoriteIds.contains(recipe.recipeId)
            binding.btnFavorite.setImageResource(
                if (isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_outlined
            )

            Glide.with(binding.recipeImageView)
                .load(recipe.imagePath)
                .placeholder(R.drawable.ic_recipe_placeholder)
                .error(R.drawable.ic_recipe_placeholder)
                .fallback(R.drawable.ic_recipe_placeholder)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .dontAnimate()
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
                oldItem.recipe == newItem.recipe &&
                        oldItem.missingIngredients == newItem.missingIngredients &&
                        oldItem.structuredMissingIngredients == newItem.structuredMissingIngredients
            } else {
                oldItem == newItem
            }
        }
    }
}