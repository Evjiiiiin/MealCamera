package com.example.mealcamera.ui.cooking

import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mealcamera.MealCameraApplication
import com.example.mealcamera.data.model.StepWithIngredients
import com.example.mealcamera.databinding.ActivityCookingBinding
import kotlinx.coroutines.launch

class CookingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCookingBinding
    private val viewModel: CookingViewModel by lazy {
        ViewModelProvider(this, (application as MealCameraApplication).viewModelFactory)
            .get(CookingViewModel::class.java)
    }

    private lateinit var stepsAdapter: CookingStepsAdapter
    private var currentStepIndex = 0
    private var stepsList = listOf<StepWithIngredients>()
    private var timer: CountDownTimer? = null
    private var timerRunning = false

    companion object {
        const val EXTRA_RECIPE_ID = "recipe_id"
        const val EXTRA_RECIPE_NAME = "recipe_name"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCookingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val recipeId = intent.getLongExtra(EXTRA_RECIPE_ID, -1)
        val recipeName = intent.getStringExtra(EXTRA_RECIPE_NAME) ?: ""

        setupToolbar(recipeName)
        setupRecyclerView()
        loadSteps(recipeId)
        setupListeners()
    }

    private fun setupToolbar(recipeName: String) {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = recipeName
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        stepsAdapter = CookingStepsAdapter()
        binding.stepsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.stepsRecyclerView.adapter = stepsAdapter
    }

    private fun loadSteps(recipeId: Long) {
        lifecycleScope.launch {
            viewModel.getStepsWithIngredients(recipeId).collect { steps ->
                stepsList = steps
                if (steps.isNotEmpty()) {
                    displayStep(currentStepIndex)
                }
            }
        }
    }

    private fun displayStep(index: Int) {
        if (index < 0 || index >= stepsList.size) return

        val stepWithIngredients = stepsList[index]
        val step = stepWithIngredients.step

        // Обновляем UI
        binding.tvStepNumber.text = "Шаг ${step.stepNumber}"
        binding.tvStepTitle.text = step.title
        binding.tvStepDescription.text = step.instruction

        // Показываем ингредиенты для этого шага
        if (stepWithIngredients.ingredients.isNotEmpty()) {
            binding.ingredientsContainer.visibility = View.VISIBLE
            val ingredientsText = stepWithIngredients.ingredients.joinToString("\n") {
                "• ${it.name}: ${it.quantity} ${it.unit}"
            }
            binding.tvStepIngredients.text = ingredientsText
        } else {
            binding.ingredientsContainer.visibility = View.GONE
        }

        // Настраиваем таймер
        setupTimer(step.timerMinutes)

        // Обновляем кнопки навигации
        binding.btnPrevious.isEnabled = index > 0
        binding.btnNext.text = if (index == stepsList.size - 1) "Завершить" else "Следующий шаг"

        // Обновляем прогресс
        updateProgress(index + 1, stepsList.size)
    }

    private fun setupTimer(minutes: Int) {
        timer?.cancel()

        if (minutes > 0) {
            binding.timerContainer.visibility = View.VISIBLE
            binding.tvTimerLabel.text = "Таймер: ${minutes} мин"

            val totalTime = minutes * 60 * 1000L
            timer = object : CountDownTimer(totalTime, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    val secondsLeft = millisUntilFinished / 1000
                    val minutesLeft = secondsLeft / 60
                    val seconds = secondsLeft % 60
                    binding.tvTimerDisplay.text = String.format("%02d:%02d", minutesLeft, seconds)
                }

                override fun onFinish() {
                    binding.tvTimerDisplay.text = "00:00"
                    binding.btnStartTimer.isEnabled = true
                    binding.btnStopTimer.isEnabled = false
                    binding.btnPauseTimer.isEnabled = false
                    Toast.makeText(this@CookingActivity, "Время вышло!", Toast.LENGTH_SHORT).show()
                }
            }

            binding.btnStartTimer.isEnabled = true
            binding.btnStopTimer.isEnabled = false
            binding.btnPauseTimer.isEnabled = false
            binding.tvTimerDisplay.text = String.format("%02d:%02d", minutes, 0)

        } else {
            binding.timerContainer.visibility = View.GONE
        }
    }

    private fun setupListeners() {
        binding.btnPrevious.setOnClickListener {
            if (currentStepIndex > 0) {
                timer?.cancel()
                currentStepIndex--
                displayStep(currentStepIndex)
            }
        }

        binding.btnNext.setOnClickListener {
            timer?.cancel()
            if (currentStepIndex == stepsList.size - 1) {
                completeCooking()
            } else {
                currentStepIndex++
                displayStep(currentStepIndex)
            }
        }

        binding.btnStartTimer.setOnClickListener {
            timer?.start()
            timerRunning = true
            binding.btnStartTimer.isEnabled = false
            binding.btnStopTimer.isEnabled = true
            binding.btnPauseTimer.isEnabled = true
        }

        binding.btnPauseTimer.setOnClickListener {
            timer?.cancel()
            timerRunning = false
            binding.btnStartTimer.isEnabled = true
            binding.btnStopTimer.isEnabled = false
            binding.btnPauseTimer.isEnabled = false
        }

        binding.btnStopTimer.setOnClickListener {
            timer?.cancel()
            timerRunning = false
            setupTimer(stepsList[currentStepIndex].step.timerMinutes)
            binding.btnStartTimer.isEnabled = true
            binding.btnStopTimer.isEnabled = false
            binding.btnPauseTimer.isEnabled = false
        }
    }

    private fun updateProgress(current: Int, total: Int) {
        binding.progressBar.max = total
        binding.progressBar.progress = current
        binding.tvProgress.text = "$current из $total"
    }

    private fun completeCooking() {
        val recipeId = intent.getLongExtra(EXTRA_RECIPE_ID, -1)
        val recipeName = intent.getStringExtra(EXTRA_RECIPE_NAME) ?: ""

        lifecycleScope.launch {
            viewModel.saveToHistory(recipeId, recipeName)
            Toast.makeText(
                this@CookingActivity,
                "🎉 Поздравляем! Вы приготовили $recipeName",
                Toast.LENGTH_LONG
            ).show()
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
    }
}