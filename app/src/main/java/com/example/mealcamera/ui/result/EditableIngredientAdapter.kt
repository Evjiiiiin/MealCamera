package com.example.mealcamera.ui.result

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.mealcamera.data.model.EditableIngredient
import com.example.mealcamera.databinding.ItemEditableIngredientBinding

class EditableIngredientAdapter(
    // Список ингредиентов теперь MutableList, чтобы он мог быть изменен при необходимости
    private val ingredients: MutableList<EditableIngredient>
) : RecyclerView.Adapter<EditableIngredientAdapter.IngredientViewHolder>() {

    inner class IngredientViewHolder(private val binding: ItemEditableIngredientBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(ingredient: EditableIngredient) {
            // Привязываем объект EditableIngredient к XML-разметке.
            // Благодаря двусторонней привязке (text="@={ingredient.quantity}"),
            // любые изменения в EditText будут автоматически обновлять поля quantity и unit
            // в объекте 'ingredient'.
            binding.ingredient = ingredient
            binding.executePendingBindings() // Это важно для немедленного обновления UI
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IngredientViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemEditableIngredientBinding.inflate(inflater, parent, false)
        return IngredientViewHolder(binding)
    }

    override fun onBindViewHolder(holder: IngredientViewHolder, position: Int) {
        holder.bind(ingredients[position])
    }

    override fun getItemCount() = ingredients.size

    /**
     * Метод для получения списка потенциально отредактированных ингредиентов.
     * Возвращает тот же MutableList, который был передан адаптеру.
     * Благодаря двусторонней привязке, все изменения, внесенные пользователем,
     * уже будут отражены в элементах этого списка.
     */
    fun getEditedIngredients(): List<EditableIngredient> {
        return ingredients
    }

    /**
     * Метод для обновления списка адаптера, если внешний источник (например, ViewModel) изменился.
     * Используется для инициализации или полного обновления списка.
     */
    fun updateIngredients(newIngredients: List<EditableIngredient>) {
        ingredients.clear()
        ingredients.addAll(newIngredients)
        notifyDataSetChanged() // Или используйте DiffUtil для лучшей производительности
    }
}
