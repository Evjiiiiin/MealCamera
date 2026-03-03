package com.example.mealcamera.ui.cooking

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.mealcamera.data.model.StepWithIngredients
import com.example.mealcamera.databinding.ItemCookingStepBinding

class CookingStepsAdapter : RecyclerView.Adapter<CookingStepsAdapter.StepViewHolder>() {

    private var steps = listOf<StepWithIngredients>()

    fun submitList(newSteps: List<StepWithIngredients>) {
        steps = newSteps
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StepViewHolder {
        val binding = ItemCookingStepBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return StepViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StepViewHolder, position: Int) {
        holder.bind(steps[position])
    }

    override fun getItemCount() = steps.size

    class StepViewHolder(
        private val binding: ItemCookingStepBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(stepWithIngredients: StepWithIngredients) {
            val step = stepWithIngredients.step
            binding.tvStepTitle.text = step.title
            binding.tvStepDescription.text = step.instruction

            // ИСПРАВЛЕНО: прямой доступ к ingredient.name
            val ingredientsText = stepWithIngredients.ingredients.joinToString("\n") {
                "• ${it.ingredient.name}: ${it.quantity} ${it.unit}"
            }
            binding.tvStepIngredients.text = ingredientsText
        }
    }
}