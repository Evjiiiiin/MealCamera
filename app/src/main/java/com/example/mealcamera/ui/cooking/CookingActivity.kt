package com.example.mealcamera.ui.cooking

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.mealcamera.MealCameraApplication
import com.example.mealcamera.R
import com.example.mealcamera.data.local.AppStatsManager
import com.example.mealcamera.data.model.CookingStepWithIngredients
import com.example.mealcamera.databinding.ActivityCookingBinding
import com.example.mealcamera.util.VoiceCommandHelper
import kotlinx.coroutines.launch
import java.util.Locale

class CookingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCookingBinding
    private val viewModel: CookingViewModel by lazy {
        ViewModelProvider(this, (application as MealCameraApplication).viewModelFactory)
            .get(CookingViewModel::class.java)
    }

    private var currentStepIndex = 0
    private var stepsList = listOf<CookingStepWithIngredients>()
    private var timer: CountDownTimer? = null
    private var timerRunning = false
    private var timeLeftInMillis: Long = 0

    // Голосовое управление
    private var voiceCommandHelper: VoiceCommandHelper? = null
    private var textToSpeech: TextToSpeech? = null
    private var isVoiceEnabled = false

    companion object {
        const val EXTRA_RECIPE_ID = "recipe_id"
        const val EXTRA_RECIPE_NAME = "recipe_name"
        const val EXTRA_PORTIONS = "portions"

        private const val RECORD_AUDIO_REQUEST_CODE = 301
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCookingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val recipeId = intent.getLongExtra(EXTRA_RECIPE_ID, -1)
        val recipeName = intent.getStringExtra(EXTRA_RECIPE_NAME) ?: ""
        val portions = intent.getIntExtra(EXTRA_PORTIONS, 1).coerceIn(1, 10)

        if (recipeId == -1L) {
            Toast.makeText(this, "Ошибка: рецепт не найден", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupToolbar(recipeName)
        checkVoicePermission()
        loadSteps(recipeId, portions)
        setupListeners()
    }

    private fun setupToolbar(recipeName: String) {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = recipeName
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun checkVoicePermission() {
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale("ru")
            }
        }

        // Просто проверяем разрешение, без диалогов
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            enableVoiceControl()
        } else {
            // Запрашиваем разрешение один раз как для камеры
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                RECORD_AUDIO_REQUEST_CODE
            )
        }
    }

    private fun enableVoiceControl() {
        isVoiceEnabled = true
        voiceCommandHelper = VoiceCommandHelper(this) { command ->
            runOnUiThread { handleVoiceCommand(command) }
        }
        voiceCommandHelper?.initialize()
        voiceCommandHelper?.startListening()
        showVoiceIndicator()
    }

    private fun showVoiceIndicator() {
        binding.voiceIndicator.visibility = View.VISIBLE
        binding.voiceIndicator.text = "🎤 Микрофон активен"
        // Индикатор исчезает через 3 секунды
        binding.root.postDelayed({
            binding.voiceIndicator.visibility = View.GONE
        }, 3000)
    }

    private fun handleVoiceCommand(command: String) {
        when (command) {
            "next" -> goToNextStep()
            "previous" -> goToPreviousStep()
            "start_timer" -> startTimer()
            "pause_timer" -> pauseTimer()
            "stop_timer" -> stopTimer()
            "repeat" -> repeatCurrentStep()
            "next_step" -> speakNextStep()
            "ingredients" -> speakIngredients()
            "finish" -> completeCooking()
            "help" -> speakHelp()
        }
    }

    // ИСПРАВЛЕНО: правильная обработка TextToSpeech для всех версий Android
    private fun speak(text: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // Для Android 5.0 и выше
                textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
            } else {
                // Для старых версий Android
                @Suppress("DEPRECATION")
                textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun goToNextStep() {
        if (currentStepIndex < stepsList.size - 1) {
            timer?.cancel()
            currentStepIndex++
            displayStep(currentStepIndex)
            speak("Шаг ${currentStepIndex + 1}: ${stepsList[currentStepIndex].step.title}")
        } else {
            speak("Это последний шаг")
        }
    }

    private fun goToPreviousStep() {
        if (currentStepIndex > 0) {
            timer?.cancel()
            currentStepIndex--
            displayStep(currentStepIndex)
            speak("Шаг ${currentStepIndex + 1}: ${stepsList[currentStepIndex].step.title}")
        } else {
            speak("Это первый шаг")
        }
    }

    private fun repeatCurrentStep() {
        displayStep(currentStepIndex)
        speak("Повторяю шаг ${currentStepIndex + 1}: ${stepsList[currentStepIndex].step.instruction}")
    }

    private fun speakNextStep() {
        if (currentStepIndex < stepsList.size - 1) {
            val nextStep = stepsList[currentStepIndex + 1]
            speak("Следующий шаг: ${nextStep.step.title}")
        } else {
            speak("Это последний шаг. Для завершения скажите готово")
        }
    }

    private fun speakIngredients() {
        val step = stepsList[currentStepIndex]
        if (step.ingredients.isNotEmpty()) {
            val ingredientsText = step.ingredients.joinToString(", ") {
                "${it.ingredient.name} ${it.quantity} ${it.unit}"
            }
            speak("Ингредиенты для этого шага: $ingredientsText")
        } else {
            speak("Для этого шага не требуются дополнительные ингредиенты")
        }
    }

    private fun speakHelp() {
        val commands = listOf(
            "следующий",
            "предыдущий",
            "старт",
            "пауза",
            "стоп",
            "повтори",
            "ингредиенты",
            "готово"
        ).joinToString(", ")
        speak("Доступные команды: $commands")
    }

    private fun loadSteps(recipeId: Long, portions: Int) {
        lifecycleScope.launch {
            viewModel.getStepsWithIngredients(recipeId, portions).collect { steps ->
                stepsList = steps
                if (steps.isNotEmpty()) {
                    currentStepIndex = currentStepIndex.coerceIn(0, steps.lastIndex)
                    displayStep(currentStepIndex)
                    binding.btnNext.isEnabled = true
                } else {
                    showEmptyStepsState()
                }
            }
        }
    }

    private fun showEmptyStepsState() {
        binding.tvStepNumber.text = "Шаги отсутствуют"
        binding.tvStepTitle.text = "Для этого рецепта пока нет шагов"
        binding.tvStepDescription.text = "Вы можете вернуться и выбрать другой рецепт."
        binding.ingredientsContainer.visibility = View.GONE
        binding.timerContainer.visibility = View.GONE
        binding.ivStepImage.visibility = View.GONE
        binding.btnPrevious.isEnabled = false
        binding.btnNext.isEnabled = false
        updateProgress(0, 1)
    }

    private fun displayStep(index: Int) {
        if (index < 0 || index >= stepsList.size) return

        val stepWithIngredients = stepsList[index]
        val step = stepWithIngredients.step

        binding.tvStepNumber.text = "Шаг ${step.stepNumber}"
        binding.tvStepTitle.text = step.title
        binding.tvStepDescription.text = step.instruction

        binding.ivStepImage.visibility = View.VISIBLE
        val imageSource: Any = if (step.imagePath.isNotBlank()) {
            step.imagePath
        } else {
            R.drawable.ic_recipe_placeholder
        }

        Glide.with(this)
            .load(imageSource)
            .placeholder(R.drawable.ic_recipe_placeholder)
            .error(R.drawable.ic_recipe_placeholder)
            .into(binding.ivStepImage)

        if (stepWithIngredients.ingredients.isNotEmpty()) {
            binding.ingredientsContainer.visibility = View.VISIBLE
            val ingredientsText = stepWithIngredients.ingredients.joinToString("\n") {
                val unit = it.unit.ifBlank { "шт" }
                "• ${it.ingredient.name}: ${it.quantity} $unit".trim()
            }
            binding.tvStepIngredients.text = ingredientsText
        } else {
            binding.ingredientsContainer.visibility = View.GONE
        }

        setupTimer(step.timerMinutes)

        binding.btnPrevious.isEnabled = index > 0
        binding.btnNext.text = if (index == stepsList.size - 1) "Завершить" else "Следующий шаг"

        updateProgress(index + 1, stepsList.size)
    }

    private fun setupTimer(minutes: Int) {
        timer?.cancel()

        if (minutes > 0) {
            binding.timerContainer.visibility = View.VISIBLE
            binding.tvTimerLabel.text = "Таймер: ${minutes} мин"

            val totalTime = minutes * 60 * 1000L
            timeLeftInMillis = totalTime

            timer = object : CountDownTimer(totalTime, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    timeLeftInMillis = millisUntilFinished
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
                    timerRunning = false
                    speak("Время вышло!")
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

    private fun startTimer() {
        if (timer != null && !timerRunning) {
            timer = object : CountDownTimer(timeLeftInMillis, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    timeLeftInMillis = millisUntilFinished
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
                    timerRunning = false
                    speak("Время вышло!")
                }
            }
            timer?.start()
            timerRunning = true
            binding.btnStartTimer.isEnabled = false
            binding.btnPauseTimer.isEnabled = true
            binding.btnStopTimer.isEnabled = true
            speak("Таймер запущен")
        }
    }

    private fun pauseTimer() {
        if (timerRunning) {
            timer?.cancel()
            timerRunning = false
            binding.btnStartTimer.isEnabled = true
            binding.btnPauseTimer.isEnabled = false
            speak("Таймер на паузе")
        }
    }

    private fun stopTimer() {
        timer?.cancel()
        timerRunning = false
        val step = stepsList[currentStepIndex]
        setupTimer(step.step.timerMinutes)
        speak("Таймер сброшен")
    }

    private fun setupListeners() {
        binding.btnPrevious.setOnClickListener {
            goToPreviousStep()
        }

        binding.btnNext.setOnClickListener {
            if (currentStepIndex == stepsList.size - 1) {
                completeCooking()
            } else {
                goToNextStep()
            }
        }

        binding.btnStartTimer.setOnClickListener {
            startTimer()
        }

        binding.btnPauseTimer.setOnClickListener {
            pauseTimer()
        }

        binding.btnStopTimer.setOnClickListener {
            stopTimer()
        }
    }

    private fun updateProgress(current: Int, total: Int) {
        binding.progressBar.max = total
        binding.progressBar.progress = current
        binding.tvProgress.text = "$current из $total"
    }

    private fun completeCooking() {
        AlertDialog.Builder(this)
            .setTitle("Завершить приготовление?")
            .setMessage("Поздравляем с завершением рецепта!")
            .setPositiveButton("Да") { _, _ ->
                val recipeId = intent.getLongExtra(EXTRA_RECIPE_ID, -1)
                val recipeName = intent.getStringExtra(EXTRA_RECIPE_NAME) ?: ""

                lifecycleScope.launch {
                    viewModel.saveToHistory(recipeId, recipeName)
                    val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                    AppStatsManager(this@CookingActivity).registerCookedRecipe(userId, recipeId, recipeName)

                    speak("Поздравляю! Вы приготовили $recipeName")

                    Toast.makeText(
                        this@CookingActivity,
                        "🎉 Поздравляем! Вы приготовили $recipeName",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
            }
            .setNegativeButton("Нет", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        if (isVoiceEnabled) {
            voiceCommandHelper?.startListening()
        }
    }

    override fun onPause() {
        super.onPause()
        voiceCommandHelper?.stopListening()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECORD_AUDIO_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableVoiceControl()
            } else {
                Toast.makeText(this, "Голосовое управление недоступно", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        voiceCommandHelper?.destroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }
}