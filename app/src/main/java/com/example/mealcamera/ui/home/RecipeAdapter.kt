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
import com.example.mealcamera.data.util.UnitHelper
import com.example.mealcamera.databinding.ItemRecipeCardBinding
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class RecipeAdapter(
    private val onItemClick: (Recipe) -> Unit,
    private val onFavoriteClick: (Recipe, Boolean) -> Unit,
    private val onAddToShoppingList: ((List<MissingIngredientData>) -> Unit)? = null
) : ListAdapter<Any, RecipeAdapter.RecipeViewHolder>(DiffCallback()) {

    private companion object {
        const val DEFAULT_CARD_HEIGHT_DP = 240
        const val RESULT_CARD_HEIGHT_DP = 272
        const val DEFAULT_GRADIENT_HEIGHT_DP = 112
        const val RESULT_GRADIENT_HEIGHT_DP = 168
    }

    private var favoriteIds: Set<Long> = emptySet()

    fun setFavoriteIds(ids: Set<Long>) {
        val previousFavoriteIds = favoriteIds
        favoriteIds = ids

        currentList.forEachIndexed { index, item ->
            val recipeId = when (item) {
                is Recipe -> item.recipeId
                is RecipeResult -> item.recipe.recipeId
                else -> return@forEachIndexed
            }
            if (previousFavoriteIds.contains(recipeId) != favoriteIds.contains(recipeId)) {
                notifyItemChanged(index)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecipeViewHolder {
        val binding = ItemRecipeCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RecipeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecipeViewHolder, position: Int) {
        val item = getItem(position)
        when (item) {
            is Recipe -> holder.bind(item, null, emptyList(), null, 1)
            is RecipeResult -> holder.bind(
                item.recipe,
                item.missingIngredients,
                item.structuredMissingIngredients,
                item.availablePortions,
                item.targetPortions
            )
        }
    }

    inner class RecipeViewHolder(private val binding: ItemRecipeCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(
            recipe: Recipe,
            missingIngredients: List<String>?,
            structuredMissing: List<MissingIngredientData>,
            availablePortions: Int?,
            targetPortions: Int
        ) {
            binding.recipeNameTextView.text = recipe.name
            binding.recipeTypeTextView.text = recipe.category

            val missingText = binding.tvMissingIngredients
            val portionsHint = binding.tvAvailablePortionsHint
            val btnAddShopping = binding.btnAddToShoppingList
            val shouldShowPortionsHint = availablePortions != null &&
                    availablePortions > 0 &&
                    targetPortions > availablePortions
            val shouldAlignShoppingButtonToBottom = !missingIngredients.isNullOrEmpty() &&
                    onAddToShoppingList != null &&
                    structuredMissing.isNotEmpty()
            updateShoppingButtonLayout(shouldAlignShoppingButtonToBottom)

            if (shouldShowPortionsHint) {
                portionsHint.visibility = View.VISIBLE
                portionsHint.text = "$availablePortions/$targetPortions"
            } else {
                portionsHint.visibility = View.GONE
            }

            if (!missingIngredients.isNullOrEmpty()) {
                missingText.visibility = View.VISIBLE
                missingText.text = binding.root.context.getString(
                    R.string.missing_ingredients_format,
                    "\n${formatMissingIngredients(missingIngredients, structuredMissing)}"
                )

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

        private fun updateShoppingButtonLayout(alignToBottom: Boolean) {
            val content = binding.cardContentLayout
            val density = content.resources.displayMetrics.density
            val targetHeight = if (alignToBottom) {
                (RESULT_CARD_HEIGHT_DP * density).toInt()
            } else {
                (DEFAULT_CARD_HEIGHT_DP * density).toInt()
            }
            val gradientHeight = if (alignToBottom) {
                (RESULT_GRADIENT_HEIGHT_DP * density).toInt()
            } else {
                (DEFAULT_GRADIENT_HEIGHT_DP * density).toInt()
            }

            if (content.layoutParams.height != targetHeight) {
                content.layoutParams = content.layoutParams.apply {
                    height = targetHeight
                }
            }
            if (binding.imageGradientOverlay.layoutParams.height != gradientHeight) {
                binding.imageGradientOverlay.layoutParams = binding.imageGradientOverlay.layoutParams.apply {
                    height = gradientHeight
                }
            }
        }

        private fun formatMissingIngredients(
            missingIngredients: List<String>,
            structuredMissing: List<MissingIngredientData>
        ): String {
            if (structuredMissing.isEmpty()) return missingIngredients.joinToString(separator = "\n")
            return structuredMissing.joinToString(separator = "\n") { missing ->
                "${missing.name}: нужно еще ${formatMissingQuantity(missing)}"
            }
        }

        private fun formatMissingQuantity(missingIngredient: MissingIngredientData): String {
            val volumeMl = UnitHelper.convert(
                missingIngredient.quantity,
                missingIngredient.unit,
                "мл",
                missingIngredient.name
            )

            return if (isVolumeUnit(missingIngredient.unit) && !volumeMl.isNaN() && volumeMl > 0.0) {
                formatVolumeQuantity(volumeMl)
            } else {
                "${UnitHelper.formatQuantity(missingIngredient.quantity)} ${missingIngredient.unit}"
            }
        }

        private fun formatVolumeQuantity(milliliters: Double): String {
            val roundedMilliliters = UnitHelper.formatQuantity(milliliters)
            val spoonText = when {
                isNearly(milliliters, 5.0) -> "1 ч.л."
                isNearly(milliliters, 15.0) -> "1 ст.л."
                isNearlyMultiple(milliliters, 5.0) && milliliters < 15.0 -> {
                    val teaspoons = (milliliters / 5.0).roundToInt()
                    "$teaspoons ч.л."
                }
                milliliters < 15.0 -> "около ${formatFraction(milliliters / 15.0)} ст.л."
                isNearlyMultiple(milliliters, 15.0) -> {
                    val tablespoons = (milliliters / 15.0).roundToInt()
                    "$tablespoons ст.л."
                }
                else -> "около ${UnitHelper.formatQuantity(milliliters / 15.0)} ст.л."
            }
            return "$roundedMilliliters мл ($spoonText)"
        }

        private fun isVolumeUnit(unit: String): Boolean {
            val normalized = unit.lowercase(Locale.ROOT)
                .replace("ё", "е")
                .replace(".", "")
                .replace(" ", "")
            return normalized in listOf("мл", "л", "стл", "чл") ||
                    normalized.contains("лож") ||
                    normalized.contains("стакан")
        }

        private fun isNearly(value: Double, target: Double): Boolean {
            return abs(value - target) < 0.1
        }

        private fun isNearlyMultiple(value: Double, step: Double): Boolean {
            val nearest = (value / step).roundToInt() * step
            return abs(value - nearest) < 0.1
        }

        private fun formatFraction(value: Double): String {
            return when {
                isNearly(value, 1.0 / 3.0) -> "1/3"
                isNearly(value, 0.5) -> "1/2"
                isNearly(value, 2.0 / 3.0) -> "2/3"
                isNearly(value, 0.25) -> "1/4"
                isNearly(value, 0.75) -> "3/4"
                else -> UnitHelper.formatQuantity(value)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Any>() {
        override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean {
            val oldRecipeId = getRecipeId(oldItem)
            val newRecipeId = getRecipeId(newItem)
            return oldRecipeId == newRecipeId && oldRecipeId != -1L
        }

        override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean {
            return if (oldItem is RecipeResult && newItem is RecipeResult) {
                oldItem.recipe == newItem.recipe &&
                        oldItem.missingIngredients.hasSameItemsAs(newItem.missingIngredients) &&
                        oldItem.structuredMissingIngredients.hasSameItemsAs(newItem.structuredMissingIngredients) &&
                        oldItem.availablePortions == newItem.availablePortions &&
                        oldItem.targetPortions == newItem.targetPortions
            } else {
                oldItem == newItem
            }
        }

        private fun getRecipeId(item: Any): Long {
            return when (item) {
                is Recipe -> item.recipeId
                is RecipeResult -> item.recipe.recipeId
                else -> -1L
            }
        }

        private fun <T> List<T>.hasSameItemsAs(other: List<T>): Boolean {
            return size == other.size && indices.all { index -> this[index] == other[index] }
        }
    }
}