package com.example.mealcamera.ui.cooking

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.CountDownTimer
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import android.widget.Toast
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

    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var recognizerIntent: Intent
    private var isVoiceListeningEnabled = true

    companion object {
        const val EXTRA_RECIPE_ID = "recipe_id"
        const val EXTRA_RECIPE_NAME = "recipe_name"
        const val EXTRA_PORTIONS = "portions"

        private const val RECORD_AUDIO_REQUEST_CODE = 301
        private val WAKE_PHRASES = listOf("окей сплэш", "ok splash", "okay splash", "окей splash")
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
        loadSteps(recipeId, portions)
        setupListeners()
        setupVoiceCommands()
    }

    private fun setupToolbar(recipeName: String) {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = recipeName
        binding.toolbar.setNavigationOnClickListener { finish() }
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

    private fun setupVoiceCommands() {
        binding.btnVoiceCommand.text = "🎤 Голос: ВКЛ (фраза: 'Окей Сплэш')"
        binding.btnVoiceCommand.setOnClickListener {
            isVoiceListeningEnabled = !isVoiceListeningEnabled
            if (isVoiceListeningEnabled) {
                binding.btnVoiceCommand.text = "🎤 Голос: ВКЛ (фраза: 'Окей Сплэш')"
                startVoiceRecognitionIfReady()
            } else {
                binding.btnVoiceCommand.text = "🎤 Голос: ВЫЛ"
                stopVoiceRecognition()
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            initSpeechRecognizer()
            startVoiceRecognitionIfReady()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                RECORD_AUDIO_REQUEST_CODE
            )
        }
    }

    private fun initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            binding.btnVoiceCommand.text = "🎤 Голос не поддерживается"
            binding.btnVoiceCommand.isEnabled = false
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit

                override fun onError(error: Int) {
                    restartListeningWithDelay()
                }

                override fun onResults(results: Bundle?) {
                    val matches = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        .orEmpty()
                    if (matches.isNotEmpty()) {
                        handleVoiceTranscript(matches.first())
                    }
                    restartListeningWithDelay()
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        .orEmpty()
                    if (matches.isNotEmpty()) {
                        handleVoiceTranscript(matches.first())
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }

        recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
    }

    private fun handleVoiceTranscript(raw: String) {
        val normalized = raw.lowercase(Locale.getDefault()).trim()
        val wakePhrase = WAKE_PHRASES.firstOrNull { normalized.contains(it) } ?: return

        val commandText = normalized.substringAfter(wakePhrase, "").trim()
        if (commandText.isBlank()) {
            Toast.makeText(this, "Слушаю команду после фразы 'Окей Сплэш'", Toast.LENGTH_SHORT).show()
            return
        }

        handleVoiceCommand(commandText)
    }

    private fun handleVoiceCommand(command: String) {
        when {
            command.contains("след") || command.contains("дальше") -> binding.btnNext.performClick()
            command.contains("назад") || command.contains("пред") -> binding.btnPrevious.performClick()
            command.contains("старт") || command.contains("запусти") -> binding.btnStartTimer.performClick()
            command.contains("пауза") -> binding.btnPauseTimer.performClick()
            command.contains("стоп") || command.contains("останов") -> binding.btnStopTimer.performClick()
            command.contains("заверш") || command.contains("готово") -> completeCooking()
            command.contains("повтори") -> displayStep(currentStepIndex)
            else -> Toast.makeText(this, "Команда не распознана: $command", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startVoiceRecognitionIfReady() {
        if (!isVoiceListeningEnabled) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) return

        runCatching { speechRecognizer?.startListening(recognizerIntent) }
    }

    private fun stopVoiceRecognition() {
        runCatching {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
        }
    }

    private fun restartListeningWithDelay() {
        if (!isVoiceListeningEnabled) return
        binding.root.postDelayed({
            startVoiceRecognitionIfReady()
        }, 450)
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
            timer = object : CountDownTimer(totalTime, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    val secondsLeft = millisUntilFinished / 1000
                    val minutesLeft = secondsLeft / 60
                    val seconds = secondsLeft % 60
                    binding.tvTimerDisplay.text = String.format(Locale.getDefault(), "%02d:%02d", minutesLeft, seconds)
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
            binding.tvTimerDisplay.text = String.format(Locale.getDefault(), "%02d:%02d", minutes, 0)

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
            val currentTimer = timer ?: return@setOnClickListener
            currentTimer.start()
            binding.btnStartTimer.isEnabled = false
            binding.btnStopTimer.isEnabled = true
            binding.btnPauseTimer.isEnabled = true
        }

        binding.btnPauseTimer.setOnClickListener {
            timer?.cancel()
            binding.btnStartTimer.isEnabled = true
            binding.btnStopTimer.isEnabled = false
            binding.btnPauseTimer.isEnabled = false
        }

        binding.btnStopTimer.setOnClickListener {
            timer?.cancel()
            if (stepsList.isNotEmpty() && currentStepIndex in stepsList.indices) {
                setupTimer(stepsList[currentStepIndex].step.timerMinutes)
            }
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
            val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            AppStatsManager(this@CookingActivity).registerCookedRecipe(userId, recipeId, recipeName)

            Toast.makeText(
                this@CookingActivity,
                "🎉 Поздравляем! Вы приготовили $recipeName",
                Toast.LENGTH_LONG
            ).show()
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        startVoiceRecognitionIfReady()
    }

    override fun onPause() {
        super.onPause()
        stopVoiceRecognition()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECORD_AUDIO_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initSpeechRecognizer()
                startVoiceRecognitionIfReady()
            } else {
                binding.btnVoiceCommand.text = "🎤 Нет доступа к микрофону"
                binding.btnVoiceCommand.isEnabled = false
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        stopVoiceRecognition()
        runCatching { speechRecognizer?.destroy() }
        speechRecognizer = null
    }
}