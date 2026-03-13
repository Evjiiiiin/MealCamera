package com.example.mealcamera.util

import android.content.Context
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import java.util.Locale

class VoiceCommandHelper(
    private val context: Context,
    private val onCommand: (String) -> Unit
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    private val commandMap = mapOf(
        "следующий" to "next",
        "дальше" to "next",
        "вперед" to "next",
        "следующий шаг" to "next",

        "назад" to "previous",
        "предыдущий" to "previous",
        "предыдущий шаг" to "previous",

        "старт" to "start_timer",
        "запусти таймер" to "start_timer",
        "поставь таймер" to "start_timer",
        "запуск таймера" to "start_timer",

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

        "что дальше" to "next_step",
        "следующий после" to "next_step",

        "ингредиенты" to "ingredients",
        "зачитай ингредиенты" to "ingredients",
        "прочитай ингредиенты" to "ingredients",
        "что нужно" to "ingredients",
        "какие продукты" to "ingredients",

        "завершить" to "finish",
        "готово" to "finish",
        "закончить" to "finish",

        "помощь" to "help",
        "команды" to "help",
        "что ты умеешь" to "help"
    )

    private val wakeWords = listOf(
        "окей сплэш",
        "ok splash",
        "okay splash",
        "слушай",
        "алло"
    )

    fun initialize() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Toast.makeText(context, "Голосовое управление недоступно", Toast.LENGTH_SHORT).show()
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    val errorMessage = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "Не распознано"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Таймаут"
                        SpeechRecognizer.ERROR_NETWORK -> "Ошибка сети"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Распознаватель занят"
                        else -> "Ошибка $error"
                    }

                    if (isListening) {
                        if (error != SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                            Toast.makeText(context, "Голос: $errorMessage", Toast.LENGTH_SHORT).show()
                        }
                        restartListening()
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        processVoiceInput(matches[0])
                    }
                    restartListening()
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

        val wakeWord = wakeWords.firstOrNull { normalized.contains(it) }

        val commandText = if (wakeWord != null) {
            normalized.substringAfter(wakeWord, "").trim()
        } else {
            normalized
        }

        if (commandText.isNotEmpty()) {
            executeCommand(commandText)
        } else if (wakeWord != null) {
            Toast.makeText(context, "Скажите команду", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkForWakeWord(partial: String) {
        val normalized = partial.lowercase(Locale.getDefault())
        if (wakeWords.any { normalized.contains(it) }) {
            Toast.makeText(context, "Слушаю...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun executeCommand(command: String) {
        for ((key, value) in commandMap) {
            if (command.contains(key)) {
                onCommand(value)
                return
            }
        }

        if (command.isNotBlank()) {
            Toast.makeText(context, "Команда не распознана: $command", Toast.LENGTH_SHORT).show()
            showHelp()
        }
    }

    private fun showHelp() {
        val commands = """
            Доступные команды:
            • следующий / дальше
            • назад / предыдущий
            • старт / пауза / стоп (таймер)
            • ингредиенты / что нужно
            • повтори / прочитай шаг
            • готово / завершить
            • помощь / команды
        """.trimIndent()
        Toast.makeText(context, commands, Toast.LENGTH_LONG).show()
    }

    fun startListening() {
        if (speechRecognizer == null) return

        isListening = true
        val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }

        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopListening() {
        isListening = false
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun restartListening() {
        if (!isListening) return

        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {
        }

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (isListening) {
                startListening()
            }
        }, 500)
    }

    fun destroy() {
        stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}