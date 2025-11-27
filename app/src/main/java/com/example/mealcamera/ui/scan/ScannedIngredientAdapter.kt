package com.example.mealcamera.ui.scan

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.mealcamera.R
import com.example.mealcamera.data.model.ScannedIngredient
import com.example.mealcamera.databinding.ItemScannedIngredientBinding

class ScannedIngredientAdapter(
    private val onDeleteClick: (ScannedIngredient) -> Unit // Слушатель для удаления
) : ListAdapter<ScannedIngredient, ScannedIngredientAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(private val binding: ItemScannedIngredientBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ScannedIngredient, onDeleteClick: (ScannedIngredient) -> Unit) {
            binding.tvIngredientName.text = item.name

            Glide.with(binding.root.context)
                .load(item.imagePath.ifEmpty { R.drawable.ic_recipe_placeholder })
                .centerCrop()
                .into(binding.ivIngredientThumb) // <-- Это имя должно быть правильным

            // Устанавливаем слушатели
            binding.ivDelete.setOnClickListener { onDeleteClick(item) }
            binding.root.setOnLongClickListener {
                onDeleteClick(item)
                true // Возвращаем true, чтобы показать, что событие обработано
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemScannedIngredientBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onDeleteClick)
    }

    class DiffCallback : DiffUtil.ItemCallback<ScannedIngredient>() {
        override fun areItemsTheSame(old: ScannedIngredient, new: ScannedIngredient) = old.timestamp == new.timestamp
        override fun areContentsTheSame(old: ScannedIngredient, new: ScannedIngredient) = old == new
    }
}
