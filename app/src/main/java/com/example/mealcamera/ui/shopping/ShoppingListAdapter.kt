package com.example.mealcamera.ui.shopping

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mealcamera.data.model.ShoppingListItem
import com.example.mealcamera.databinding.ItemShoppingListBinding

class ShoppingListAdapter(
    private val onCheckedChanged: (ShoppingListItem, Boolean) -> Unit
) : ListAdapter<ShoppingListItem, ShoppingListAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemShoppingListBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemShoppingListBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ShoppingListItem) {
            binding.tvIngredientName.text = item.name
            val unitText = item.unit.ifBlank { "шт" }
            binding.tvIngredientQuantity.text = "${item.quantity} $unitText".trim()

            binding.cbDone.setOnCheckedChangeListener(null)
            binding.cbDone.isChecked = item.isChecked
            binding.cbDone.setOnCheckedChangeListener { _, isChecked ->
                onCheckedChanged(item, isChecked)
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<ShoppingListItem>() {
        override fun areItemsTheSame(oldItem: ShoppingListItem, newItem: ShoppingListItem): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: ShoppingListItem, newItem: ShoppingListItem): Boolean =
            oldItem == newItem
    }
}