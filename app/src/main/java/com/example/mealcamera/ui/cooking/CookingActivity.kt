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
import com.example.mealcamera.util.VoiceAssistantManager
import kotlinx.coroutines.launch

class CookingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCookingBinding
    private val viewModel: CookingViewModel by lazy {
        ViewModelProvider(this, (application as MealCameraApplication).viewModelFactory)
            .get(CookingViewModel::class.java)
    }

    private var currentStepIndex = 0
    private var stepsList = listOf<CookingStepWithIngredients>()
    private var timer: CountDownTimer? = null
    private var isVoiceEnabled = true
    private var voiceAssistant: VoiceAssistantManager? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) initVoiceAssistant()
        else Toast.makeText(this, "Голосовое управление недоступно без микрофона", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCookingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        isVoiceEnabled = prefs.getBoolean("voice_enabled", true)

        setupToolbar(intent.getStringExtra(EXTRA_RECIPE_NAME) ?: "Готовка")
        loadSteps(intent.getLongExtra(EXTRA_RECIPE_ID, -1), intent.getIntExtra(EXTRA_PORTIONS, 1))
        setupListeners()

        if (isVoiceEnabled) checkVoicePermissions()
    }

    private fun checkVoicePermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            initVoiceAssistant()
        }
    }

    private fun initVoiceAssistant() {
        voiceAssistant = VoiceAssistantManager(
            context = this,
            onListeningStateChanged = { isListening ->
                runOnUiThread {
                    binding.listeningIndicator.visibility = if (isListening) View.VISIBLE else View.GONE
                }
            },
            onCommand = { command ->
                runOnUiThread { handleVoiceCommand(command) }
            }
        )
        // Начинаем слушать после небольшой задержки или первого озвучивания
        voiceAssistant?.startListening()
    }

    private fun handleVoiceCommand(command: VoiceAssistantManager.VoiceCommand) {
        when (command) {
            VoiceAssistantManager.VoiceCommand.NEXT -> binding.btnNext.performClick()
            VoiceAssistantManager.VoiceCommand.PREVIOUS -> binding.btnPrevious.performClick()
            VoiceAssistantManager.VoiceCommand.REPEAT, VoiceAssistantManager.VoiceCommand.READ_STEP -> readCurrentStep()
            VoiceAssistantManager.VoiceCommand.STOP -> voiceAssistant?.stopAll()
            VoiceAssistantManager.VoiceCommand.TIMER -> startStepTimer()
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
        binding.btnStartTimer.setOnClickListener { startStepTimer() }
        binding.btnStopTimer.setOnClickListener { stopStepTimer() }
    }

    private fun startStepTimer() {
        // Простая реализация таймера на 5 минут для теста или из данных шага
        stopStepTimer()
        binding.timerContainer.visibility = View.VISIBLE
        val timeMillis = 5 * 60 * 1000L 
        timer = object : CountDownTimer(timeMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val sec = (millisUntilFinished / 1000) % 60
                val min = (millisUntilFinished / 1000) / 60
                binding.tvTimerDisplay.text = String.format("%02d:%02d", min, sec)
            }
            override fun onFinish() {
                binding.tvTimerDisplay.text = "Готово!"
                voiceAssistant?.speak("Таймер завершен!")
            }
        }.start()
    }

    private fun stopStepTimer() {
        timer?.cancel()
        binding.timerContainer.visibility = View.GONE
    }

    private fun readCurrentStep() {
        if (!isVoiceEnabled || stepsList.isEmpty()) return
        val step = stepsList[currentStepIndex].step
        val text = "Шаг ${step.stepNumber}. ${step.title}. ${step.instruction}"
        voiceAssistant?.speak(text)
    }

    private fun displayStep(index: Int) {
        if (index !in stepsList.indices) return
        val stepWithIngs = stepsList[index]
        val step = stepWithIngs.step

        binding.tvStepNumber.text = "Шаг ${step.stepNumber}"
        binding.tvStepTitle.text = step.title
        binding.tvStepDescription.text = step.instruction

        if (step.imagePath.isNotBlank()) {
            Glide.with(this).load(step.imagePath).placeholder(R.drawable.ic_recipe_placeholder).into(binding.ivStepImage)
            binding.ivStepImage.visibility = View.VISIBLE
        } else {
            binding.ivStepImage.visibility = View.GONE
        }

        binding.ingredientsContainer.removeAllViews()
        if (stepWithIngs.ingredients.isNotEmpty()) {
            binding.stepIngredientsCard.visibility = View.VISIBLE
            for (item in stepWithIngs.ingredients) {
                val view = LayoutInflater.from(this).inflate(R.layout.item_cooking_ingredient, binding.ingredientsContainer, false)
                view.findViewById<TextView>(R.id.ingredientName).text = item.ingredient.name
                view.findViewById<TextView>(R.id.ingredientQuantity).text = "${item.quantity} ${item.unit}".trim()
                binding.ingredientsContainer.addView(view)
            }
        } else {
            binding.stepIngredientsCard.visibility = View.GONE
        }

        updateProgress(index + 1, stepsList.size)
        binding.btnNext.text = if (index == stepsList.size - 1) "Завершить" else "Далее"
    }

    private fun loadSteps(recipeId: Long, portions: Int) {
        lifecycleScope.launch {
            viewModel.getStepsWithIngredients(recipeId, portions).collect { steps ->
                stepsList = steps
                if (steps.isNotEmpty()) {
                    displayStep(currentStepIndex)
                    // Озвучиваем первый шаг автоматически при входе
                    if (currentStepIndex == 0) readCurrentStep()
                }
            }
        }
    }

    private fun updateProgress(curr: Int, total: Int) {
        binding.progressBar.max = total
        binding.progressBar.progress = curr
        binding.tvProgress.text = "$curr из $total"
    }

    private fun finishCookingFlow() {
        lifecycleScope.launch {
            viewModel.saveToHistory(intent.getLongExtra(EXTRA_RECIPE_ID, -1), binding.tvToolbarTitle.text.toString())
            Toast.makeText(this@CookingActivity, "Приятного аппетита!", Toast.LENGTH_LONG).show()
            startActivity(Intent(this@CookingActivity, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            })
            finish()
        }
    }

    private fun setupToolbar(name: String) {
        binding.tvToolbarTitle.text = name
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceAssistant?.destroy()
        timer?.cancel()
    }

    companion object {
        const val EXTRA_RECIPE_ID = "recipe_id"
        const val EXTRA_RECIPE_NAME = "recipe_name"
        const val EXTRA_PORTIONS = "portions"
    }
}