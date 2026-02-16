package com.example.mealcamera.ui.catalog

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.mealcamera.data.model.Ingredient
import com.example.mealcamera.databinding.ItemIngredientSelectableBinding

class IngredientAdapter(private val onSelectionChanged: (String, Boolean) -> Unit) :
    RecyclerView.Adapter<IngredientAdapter.IngredientViewHolder>() {

    private var items = listOf<Ingredient>()
    private var filteredItems = listOf<Ingredient>()
    private val selectedNames = mutableSetOf<String>()

    // Ручное определение ViewHolder без внутренних ошибок
    class IngredientViewHolder(val binding: ItemIngredientSelectableBinding) :
        RecyclerView.ViewHolder(binding.root)

    fun setData(newList: List<Ingredient>) {
        items = newList
        filteredItems = newList
        notifyDataSetChanged()
    }

    fun filter(query: String) {
        filteredItems = if (query.isEmpty()) items
        else items.filter { it.name.contains(query, ignoreCase = true) }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IngredientViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemIngredientSelectableBinding.inflate(inflater, parent, false)
        return IngredientViewHolder(binding)
    }

    override fun onBindViewHolder(holder: IngredientViewHolder, position: Int) {
        val ingredient = filteredItems[position]

        holder.binding.cbIngredient.text = ingredient.name

        // Отключаем слушатель перед сменой состояния, чтобы не было ложных срабатываний
        holder.binding.cbIngredient.setOnCheckedChangeListener(null)
        holder.binding.cbIngredient.isChecked = selectedNames.contains(ingredient.name)

        holder.binding.cbIngredient.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedNames.add(ingredient.name)
            } else {
                selectedNames.remove(ingredient.name)
            }
            onSelectionChanged(ingredient.name, isChecked)
        }
    }

    override fun getItemCount() = filteredItems.size
}