package com.example.mealcamera.ui.catalog

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.mealcamera.data.model.Ingredient
import com.example.mealcamera.databinding.ItemIngredientSelectableBinding

class IngredientAdapter(
    private val selectedNames: Set<String>,
    private val userAllergens: List<String> = emptyList(),
    private val onSelectionChanged: (String, Boolean) -> Unit
) : RecyclerView.Adapter<IngredientAdapter.IngredientViewHolder>() {

    private var items = listOf<Ingredient>()
    private var filteredItems = listOf<Ingredient>()

    private fun normalize(name: String): String {
        return name.trim().lowercase().replace("ё", "е")
    }

    private val normalizedSelectedNames: Set<String>
        get() = selectedNames.map { normalize(it) }.toSet()

    class IngredientViewHolder(val binding: ItemIngredientSelectableBinding) :
        RecyclerView.ViewHolder(binding.root)

    fun setData(newList: List<Ingredient>) {
        items = newList
        filteredItems = newList
        notifyDataSetChanged()
    }

    fun filter(query: String) {
        val normalizedQuery = normalize(query)
        filteredItems = if (query.isEmpty()) items
        else items.filter { normalize(it.name).contains(normalizedQuery) }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IngredientViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemIngredientSelectableBinding.inflate(inflater, parent, false)
        return IngredientViewHolder(binding)
    }

    override fun onBindViewHolder(holder: IngredientViewHolder, position: Int) {
        val ingredient = filteredItems[position]
        val normalizedName = normalize(ingredient.name)
        val isAllergen = userAllergens.any { normalizedName.contains(it) }

        holder.binding.cbIngredient.text = if (isAllergen) "${ingredient.name} (Аллерген!)" else ingredient.name
        holder.binding.cbIngredient.isEnabled = !isAllergen
        holder.binding.cbIngredient.alpha = if (isAllergen) 0.5f else 1.0f
        
        holder.binding.cbIngredient.setOnCheckedChangeListener(null)
        holder.binding.cbIngredient.isChecked = normalizedSelectedNames.contains(normalizedName)
        
        holder.binding.cbIngredient.setOnCheckedChangeListener { _, isChecked ->
            onSelectionChanged(ingredient.name, isChecked)
        }
    }

    override fun getItemCount() = filteredItems.size
}
