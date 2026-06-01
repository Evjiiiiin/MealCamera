package com.example.mealcamera.ui.cooking

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.mealcamera.MealCameraApplication
import com.example.mealcamera.R
import com.example.mealcamera.data.model.CookingStepWithIngredients
import com.example.mealcamera.databinding.ActivityCookingBinding
import com.example.mealcamera.ui.home.MainActivity
import com.example.mealcamera.util.VoskVoiceManager
import kotlinx.coroutines.launch

class CookingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCookingBinding

    private val viewModel: CookingViewModel by lazy {
        ViewModelProvider(
            this,
            (application as MealCameraApplication).viewModelFactory
        ).get(CookingViewModel::class.java)
    }

    private val sharedViewModel by lazy {
        (application as MealCameraApplication).sharedViewModel
    }

    private var currentStepIndex = 0
    private var stepsList = listOf<CookingStepWithIngredients>()

    private var timer: CountDownTimer? = null

    private var isVoiceEnabled = true
    private var voiceAssistant: VoiceAssistantManager? = null
    private var voskVoiceManager: VoskVoiceManager? = null

    private var isTimerRunning = false
    private var isTimerPaused = false
    private var remainingTimeMillis: Long = 0L

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            initVoiceAssistant()
        } else {
            Toast.makeText(
                this,
                "Голосовое управление недоступно без микрофона",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityCookingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        isVoiceEnabled = prefs.getBoolean("voice_enabled", true)

        setupToolbar(
            intent.getStringExtra(EXTRA_RECIPE_NAME) ?: "Готовка"
        )

        loadSteps(
            intent.getLongExtra(EXTRA_RECIPE_ID, -1),
            intent.getIntExtra(EXTRA_PORTIONS, 1)
        )

        setupListeners()

        if (isVoiceEnabled) {
            checkVoicePermissions()
        }
    }

    private fun checkVoicePermissions() {
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            initVoiceAssistant()
        }
    }

    private fun initVoiceAssistant() {
        if (voskVoiceManager == null) {
            voskVoiceManager = VoskVoiceManager(
                context = this,
                onListeningStateChanged = { isListening ->
                    runOnUiThread {
                        binding.listeningIndicator.visibility =
                            if (isListening) View.VISIBLE else View.GONE
                    }
                },
                onCommand = { command ->
                    runOnUiThread {
                        handleVoiceCommand(command)
                    }
                }
            )
        }

        voskVoiceManager?.initializeAndStart()
    }

    private fun handleVoiceCommand(
        command: VoskVoiceManager.VoiceCommand
    ) {
        when (command) {
            VoskVoiceManager.VoiceCommand.NEXT ->
                binding.btnNext.performClick()

            VoskVoiceManager.VoiceCommand.PREVIOUS ->
                binding.btnPrevious.performClick()

            VoskVoiceManager.VoiceCommand.REPEAT,
            VoskVoiceManager.VoiceCommand.READ_STEP ->
                readCurrentStep()

            VoskVoiceManager.VoiceCommand.STOP ->
                voskVoiceManager?.stopAll()

            // Озвучить ингредиенты текущего шага
            VoskVoiceManager.VoiceCommand.READ_INGREDIENTS ->
                readCurrentIngredients()

            // Запустить или продолжить таймер
            VoskVoiceManager.VoiceCommand.TIMER ->
                startOrResumeTimerByVoice()

            // Поставить таймер на паузу
            VoskVoiceManager.VoiceCommand.TIMER_PAUSE ->
                pauseTimerByVoice()

            // Сбросить таймер
            VoskVoiceManager.VoiceCommand.TIMER_RESET ->
                resetTimerByVoice()

            else -> {}
        }
    }

    private fun setupListeners() {
        binding.btnNext.setOnClickListener {
            if (currentStepIndex < stepsList.size - 1) {
                currentStepIndex++
                displayStep(currentStepIndex)
                readCurrentStep()
            } else {
                finishCookingFlow()
            }
        }

        binding.btnPrevious.setOnClickListener {
            if (currentStepIndex > 0) {
                currentStepIndex--
                displayStep(currentStepIndex)
                readCurrentStep()
            }
        }

        binding.btnStartTimer.setOnClickListener {
            if (currentStepIndex in stepsList.indices) {
                val timeToStart =
                    if (isTimerPaused) {
                        remainingTimeMillis
                    } else {
                        stepsList[currentStepIndex]
                            .step
                            .timerMinutes * 60 * 1000L
                    }

                startStepTimer(timeToStart)
            }
        }

        binding.btnPauseTimer.setOnClickListener {
            if (isTimerPaused) {
                startStepTimer(remainingTimeMillis)
            } else {
                pauseStepTimer()
            }
        }

        binding.btnStopTimer.setOnClickListener {
            if (currentStepIndex in stepsList.indices) {
                resetTimerUI(
                    stepsList[currentStepIndex].step.timerMinutes
                )
            }
        }
    }

    /**
     * Запуск или продолжение таймера голосовой командой.
     */
    private fun startOrResumeTimerByVoice() {
        if (
            binding.timerContainer.visibility != View.VISIBLE ||
            currentStepIndex !in stepsList.indices
        ) {
            voskVoiceManager?.speak("В этом шаге таймер не указан")
            return
        }

        if (!isTimerRunning || isTimerPaused) {
            binding.btnStartTimer.performClick()
        }
    }

    /**
     * Пауза таймера голосовой командой.
     */
    private fun pauseTimerByVoice() {
        if (
            binding.timerContainer.visibility != View.VISIBLE ||
            !isTimerRunning ||
            isTimerPaused
        ) {
            voskVoiceManager?.speak("Таймер сейчас не запущен")
            return
        }

        pauseStepTimer()
    }

    /**
     * Сброс таймера голосовой командой.
     */
    private fun resetTimerByVoice() {
        if (
            binding.timerContainer.visibility != View.VISIBLE ||
            currentStepIndex !in stepsList.indices
        ) {
            voskVoiceManager?.speak("В этом шаге таймер не указан")
            return
        }

        resetTimerUI(
            stepsList[currentStepIndex].step.timerMinutes
        )
    }

    private fun startStepTimer(timeMillis: Long) {
        if (timeMillis <= 0) return

        timer?.cancel()

        isTimerRunning = true
        isTimerPaused = false

        timer = object : CountDownTimer(timeMillis, 1000) {

            override fun onTick(millisUntilFinished: Long) {
                remainingTimeMillis = millisUntilFinished

                val sec = (millisUntilFinished / 1000) % 60
                val min = (millisUntilFinished / 1000) / 60

                binding.tvTimerDisplay.text =
                    String.format("%02d:%02d", min, sec)
            }

            override fun onFinish() {
                isTimerRunning = false
                isTimerPaused = false

                binding.tvTimerDisplay.text = "Готово!"

                voskVoiceManager?.speak(
                    "Время вышло! Перехожу к следующему шагу."
                )

                binding.btnNext.performClick()
            }
        }.start()

        binding.btnStartTimer.visibility = View.GONE
        binding.btnPauseTimer.visibility = View.VISIBLE
        binding.btnPauseTimer.text = "Пауза"
    }

    private fun pauseStepTimer() {
        if (isTimerRunning && !isTimerPaused) {
            timer?.cancel()
            isTimerPaused = true
            binding.btnPauseTimer.text = "Продолжить"
        }
    }

    private fun resetTimerUI(minutes: Int) {
        timer?.cancel()

        isTimerRunning = false
        isTimerPaused = false
        remainingTimeMillis = 0L

        binding.tvTimerDisplay.text =
            String.format("%02d:00", minutes)

        binding.btnStartTimer.visibility = View.VISIBLE
        binding.btnStartTimer.text = "Старт"

        binding.btnPauseTimer.visibility = View.GONE
    }

    private fun readCurrentStep() {
        if (!isVoiceEnabled || stepsList.isEmpty()) return

        val step = stepsList[currentStepIndex].step

        val text =
            "Шаг ${step.stepNumber}. " +
                    "${step.title}. " +
                    "${step.instruction}"

        voskVoiceManager?.speak(text)
    }

    /**
     * Озвучивает ингредиенты текущего шага.
     */
    private fun readCurrentIngredients() {
        if (
            !isVoiceEnabled ||
            stepsList.isEmpty() ||
            currentStepIndex !in stepsList.indices
        ) {
            return
        }

        val ingredients =
            stepsList[currentStepIndex].ingredients

        if (ingredients.isEmpty()) {
            voskVoiceManager?.speak(
                "Для этого шага ингредиенты не указаны"
            )
            return
        }

        val text = ingredients.joinToString(", ") { item ->
            val amount =
                "${item.quantity} ${item.unit}".trim()

            if (amount.isBlank()) {
                item.ingredient.name
            } else {
                "${item.ingredient.name}: $amount"
            }
        }

        voskVoiceManager?.speak(
            "Ингредиенты для шага: $text"
        )
    }

    private fun displayStep(index: Int) {
        if (index !in stepsList.indices) return

        val stepWithIngs = stepsList[index]
        val step = stepWithIngs.step

        binding.tvStepNumber.text =
            "Шаг ${step.stepNumber}"

        binding.tvStepTitle.text = step.title
        binding.tvStepDescription.text =
            step.instruction

        binding.ivStepImage.visibility = View.VISIBLE

        if (step.imagePath.isNotBlank()) {
            Glide.with(this)
                .load(step.imagePath)
                .placeholder(
                    R.drawable.ic_recipe_placeholder
                )
                .error(
                    R.drawable.ic_recipe_placeholder
                )
                .into(binding.ivStepImage)
        } else {
            binding.ivStepImage.setImageResource(
                R.drawable.ic_recipe_placeholder
            )
        }

        if (step.timerMinutes > 0) {
            binding.timerContainer.visibility =
                View.VISIBLE
            resetTimerUI(step.timerMinutes)
        } else {
            timer?.cancel()
            isTimerRunning = false
            isTimerPaused = false
            remainingTimeMillis = 0L
            binding.timerContainer.visibility =
                View.GONE
        }

        binding.ingredientsContainer.removeAllViews()

        if (stepWithIngs.ingredients.isNotEmpty()) {
            binding.stepIngredientsCard.visibility =
                View.VISIBLE

            for (item in stepWithIngs.ingredients) {
                val view = LayoutInflater
                    .from(this)
                    .inflate(
                        R.layout.item_cooking_ingredient,
                        binding.ingredientsContainer,
                        false
                    )

                view.findViewById<TextView>(
                    R.id.ingredientName
                ).text = item.ingredient.name

                view.findViewById<TextView>(
                    R.id.ingredientQuantity
                ).text =
                    "${item.quantity} ${item.unit}".trim()

                binding.ingredientsContainer
                    .addView(view)
            }
        } else {
            binding.stepIngredientsCard.visibility =
                View.GONE
        }

        updateProgress(index + 1, stepsList.size)

        binding.btnNext.text =
            if (index == stepsList.size - 1) {
                "Завершить"
            } else {
                "Далее"
            }

        updateNavigationButtons(index)
    }

    private fun updateNavigationButtons(index: Int) {
        val canGoBack = index > 0
        binding.btnPrevious.isEnabled = canGoBack
        binding.btnPrevious.alpha = if (canGoBack) 1f else DISABLED_BUTTON_ALPHA

        val canGoNext = stepsList.isNotEmpty()
        binding.btnNext.isEnabled = canGoNext
        binding.btnNext.alpha = if (canGoNext) 1f else DISABLED_BUTTON_ALPHA
    }


    private fun loadSteps(
        recipeId: Long,
        portions: Int
    ) {
        lifecycleScope.launch {
            viewModel
                .getStepsWithIngredients(
                    recipeId,
                    portions
                )
                .collect { steps ->
                    stepsList = steps

                    if (steps.isNotEmpty()) {
                        currentStepIndex = currentStepIndex.coerceIn(0, steps.lastIndex)
                        displayStep(currentStepIndex)

                        if (currentStepIndex == 0) {
                            readCurrentStep()
                        }
                    } else {
                        updateNavigationButtons(0)
                    }
                }
        }
    }

    private fun updateProgress(
        curr: Int,
        total: Int
    ) {
        binding.progressBar.max = total
        binding.progressBar.progress = curr
        binding.tvProgress.text = "$curr из $total"
    }

    private fun finishCookingFlow() {
        lifecycleScope.launch {
            viewModel.saveToHistory(
                intent.getLongExtra(
                    EXTRA_RECIPE_ID,
                    -1
                ),
                binding.tvToolbarTitle.text.toString()
            )

            // Очистка временных ингредиентов
            sharedViewModel.endSession()

            Toast.makeText(
                this@CookingActivity,
                "Приятного аппетита!",
                Toast.LENGTH_LONG
            ).show()

            // Возврат на главный экран
            val intent = Intent(
                this@CookingActivity,
                MainActivity::class.java
            )

            intent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            )

            intent.putExtra(
                "navigate_home",
                true
            )

            startActivity(intent)
            finish()
        }
    }

    private fun setupToolbar(name: String) {
        binding.tvToolbarTitle.text = name
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        voskVoiceManager?.destroy()
        timer?.cancel()
    }

    companion object {
        const val EXTRA_RECIPE_ID = "recipe_id"
        const val EXTRA_RECIPE_NAME = "recipe_name"
        const val EXTRA_PORTIONS = "portions"
        private const val DISABLED_BUTTON_ALPHA = 0.45f
    }
}