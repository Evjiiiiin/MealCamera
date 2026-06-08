package com.example.mealcamera.ui.shopping

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mealcamera.data.model.ShoppingListItem
import com.example.mealcamera.databinding.ItemShoppingListBinding

class ShoppingListAdapter(
    private val onCheckedChanged: (ShoppingListItem, Boolean) -> Unit,
    private val onRemoveItem: (ShoppingListItem) -> Unit
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
            binding.tvItemName.text = item.name
            val unitText = item.unit.ifBlank { "шт" }
            binding.tvQuantity.text = item.quantity
            binding.tvUnit.text = unitText

            binding.checkbox.setOnCheckedChangeListener(null)
            binding.checkbox.isChecked = item.isChecked

            binding.checkbox.setOnCheckedChangeListener { _, isChecked ->
                onCheckedChanged(item, isChecked)
            }
            binding.btnRemove.setOnClickListener {
                onRemoveItem(item)
            }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<ShoppingListItem>() {
        override fun areItemsTheSame(oldItem: ShoppingListItem, newItem: ShoppingListItem): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: ShoppingListItem, newItem: ShoppingListItem): Boolean =
            oldItem == newItem
    }
}