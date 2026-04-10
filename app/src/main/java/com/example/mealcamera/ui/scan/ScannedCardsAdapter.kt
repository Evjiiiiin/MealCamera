package com.example.mealcamera.ui.scan

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.mealcamera.data.model.ScannedIngredient
import com.example.mealcamera.databinding.ItemScannedCardBinding

class ScannedCardsAdapter(
    private val onRemove: (ScannedIngredient) -> Unit
) : RecyclerView.Adapter<ScannedCardsAdapter.ViewHolder>() {

    private var items = listOf<ScannedIngredient>()

    fun submitList(newList: List<ScannedIngredient>) {
        items = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemScannedCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.tvIngredientName.text = item.name
        holder.binding.btnRemove.setOnClickListener { onRemove(item) }
    }

    override fun getItemCount() = items.size

    class ViewHolder(val binding: ItemScannedCardBinding) : RecyclerView.ViewHolder(binding.root)
}