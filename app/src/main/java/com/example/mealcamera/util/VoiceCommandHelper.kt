package com.example.mealcamera.util

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Toast
import java.util.Locale

class VoiceCommandHelper(
    private val context: Context,
    private val onCommand: (String) -> Unit
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var isPaused = false
    private val handler = Handler(Looper.getMainLooper())

    private val commandMap = mapOf(
        "следующий" to "next",
        "дальше" to "next",
        "вперед" to "next",
        "следующий шаг" to "next",
        "далее" to "next",

        "назад" to "previous",
        "предыдущий" to "previous",
        "предыдущий шаг" to "previous",
        "вернись" to "previous",

        "старт" to "start_timer",
        "запусти таймер" to "start_timer",
        "поставь таймер" to "start_timer",
        "запуск таймера" to "start_timer",
        "пуск" to "start_timer",

        "пауза" to "pause_timer",
        "пауза таймер" to "pause_timer",
        "останови таймер" to "pause_timer",

        "стоп" to "stop_timer",
        "сброс" to "stop_timer",
        "сбрось таймер" to "stop_timer",

        "повтори" to "repeat",
        "повтори шаг" to "repeat",
        "прочитай шаг" to "repeat",
        "зачитай шаг" to "repeat",
        "зачитай" to "repeat",
        "прочитай" to "repeat",

        "что дальше" to "next",
        "следующий после" to "next",

        "ингредиенты" to "ingredients",
        "зачитай ингредиенты" to "ingredients",
        "прочитай ингредиенты" to "ingredients",
        "что нужно" to "ingredients",
        "какие продукты" to "ingredients",
        "продукты" to "ingredients",

        "завершить" to "finish",
        "готово" to "finish",
        "закончить" to "finish",
        "все" to "finish",

        "помощь" to "help",
        "команды" to "help",
        "что ты умеешь" to "help"
    )

    private val wakeWords = listOf(
        "окей сплэш",
        "ok splash",
        "okay splash",
        "слушай",
        "meal camera",
        "мил камера"
    )

    fun initialize() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Toast.makeText(context, "Голосовое управление недоступно", Toast.LENGTH_SHORT).show()
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d("VoiceHelper", "Ready for speech")
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    val message = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio error"
                        SpeechRecognizer.ERROR_CLIENT -> "Client error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permissions error"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                        SpeechRecognizer.ERROR_NO_MATCH -> "No match"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Busy"
                        SpeechRecognizer.ERROR_SERVER -> "Server error"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                        else -> "Unknown error"
                    }
                    Log.e("VoiceHelper", "Error: $error ($message)")
                    
                    if (isListening && !isPaused) {
                        if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                            // If busy, wait a bit longer before restarting
                            handler.postDelayed({ restartListening() }, 1000)
                        } else {
                            restartListening()
                        }
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        processVoiceInput(matches[0])
                    }
                    if (isListening && !isPaused) {
                        restartListening()
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        checkForWakeWord(matches[0])
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun processVoiceInput(input: String) {
        val normalized = input.lowercase(Locale.getDefault()).trim()
        Log.d("VoiceHelper", "Input: $normalized")

        // Check if wake word is present
        val wakeWord = wakeWords.firstOrNull { normalized.contains(it) }

        val commandText = if (wakeWord != null) {
            normalized.substringAfter(wakeWord, "").trim()
        } else {
            normalized
        }

        if (commandText.isNotEmpty()) {
            executeCommand(commandText)
        }
    }

    private fun checkForWakeWord(partial: String) {
        val normalized = partial.lowercase(Locale.getDefault())
        if (wakeWords.any { normalized.contains(it) }) {
            Log.d("VoiceHelper", "Wake word detected in partial: $normalized")
        }
    }

    private fun executeCommand(command: String) {
        for ((key, value) in commandMap) {
            if (command.contains(key)) {
                Log.d("VoiceHelper", "Executing command: $value for text: $command")
                onCommand(value)
                return
            }
        }
    }

    fun startListening() {
        if (speechRecognizer == null) return

        isListening = true
        isPaused = false
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }

        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e("VoiceHelper", "startListening exception", e)
        }
    }

    fun stopListening() {
        isListening = false
        isPaused = false
        handler.removeCallbacksAndMessages(null)
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
        } catch (e: Exception) {
            Log.e("VoiceHelper", "stopListening exception", e)
        }
    }

    fun pauseListening() {
        isPaused = true
        try {
            speechRecognizer?.cancel()
        } catch (e: Exception) {
            Log.e("VoiceHelper", "pauseListening exception", e)
        }
    }

    fun resumeListening() {
        if (isListening) {
            isPaused = false
            startListening()
        }
    }

    private fun restartListening() {
        if (!isListening || isPaused) return

        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {}

        handler.postDelayed({
            if (isListening && !isPaused) {
                startListening()
            }
        }, 500) // Slightly increased delay for stability
    }

    fun destroy() {
        stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}
