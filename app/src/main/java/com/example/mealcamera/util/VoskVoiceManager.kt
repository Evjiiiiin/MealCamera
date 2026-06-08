package com.example.mealcamera.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.IOException
import java.util.Locale

class VoskVoiceManager(
    private val context: Context,
    private val onListeningStateChanged: (Boolean) -> Unit = {},
    private val onCommand: (VoiceCommand) -> Unit
) : TextToSpeech.OnInitListener {

    enum class VoiceCommand {
        NEXT,
        PREVIOUS,
        REPEAT,
        STOP,
        TIMER,
        TIMER_PAUSE,
        TIMER_RESET,
        READ_STEP,
        READ_INGREDIENTS,
        UNKNOWN
    }

    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var speechService: SpeechService? = null
    private var tts: TextToSpeech? = null

    private var isModelReady = false
    private var isListening = false
    private var isTtsReady = false
    private var destroyed = false
    private var pausedByStopCommand = false

    private var wakeWordActiveUntil = 0L
    private var lastCommandTimestamp = 0L

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("ru")
            isTtsReady = true
        }
    }

    fun initializeAndStart() {
        pausedByStopCommand = false
        if (isModelReady) {
            startListening()
            return
        }

        StorageService.unpack(
            context,
            MODEL_ASSET_DIR,
            MODEL_STORAGE_DIR,
            { unpackedModel ->
                if (destroyed) return@unpack
                model = unpackedModel
                isModelReady = true
                startListening()
            },
            { exception ->
                Log.e(TAG, "Vosk model unpack error", exception)
            }
        )
    }

    fun startListening() {
        if (!isModelReady || isListening || destroyed || pausedByStopCommand) return
        val localModel = model ?: return

        try {
            recognizer?.close()
            recognizer = Recognizer(localModel, SAMPLE_RATE).apply { setWords(false) }
            speechService = SpeechService(recognizer, SAMPLE_RATE)
            speechService?.startListening(listener)
            isListening = true
            onListeningStateChanged(true)
            Log.d(TAG, "Vosk listening started")
        } catch (e: IOException) {
            Log.e(TAG, "Vosk start error", e)
        }
    }

    fun stopListening() {
        if (!isListening) return
        speechService?.stop()
        speechService?.shutdown()
        speechService = null
        recognizer?.close()
        recognizer = null
        isListening = false
        onListeningStateChanged(false)
        Log.d(TAG, "Vosk listening stopped")
    }

    fun stopAll() {
        pausedByStopCommand = true
        stopListening()
        tts?.stop()
    }

    fun speak(text: String) {
        if (!isTtsReady || destroyed) return
        stopListening()
        pausedByStopCommand = false
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "vosk_tts")
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (!destroyed && !pausedByStopCommand) startListening()
        }, 1200L)
    }

    fun destroy() {
        destroyed = true
        stopListening()
        model?.close()
        model = null
        isModelReady = false
        tts?.shutdown()
        tts = null
    }

    private val listener = object : RecognitionListener {
        override fun onPartialResult(hypothesis: String?) = handleResult(hypothesis)
        override fun onResult(hypothesis: String?) = handleResult(hypothesis)
        override fun onFinalResult(hypothesis: String?) = handleResult(hypothesis)

        override fun onError(e: Exception?) {
            Log.e(TAG, "Vosk error", e)
            if (!destroyed && !pausedByStopCommand) {
                stopListening()
                startListening()
            }
        }

        override fun onTimeout() {
            if (!destroyed && !pausedByStopCommand) {
                stopListening()
                startListening()
            }
        }
    }

    private fun handleResult(hypothesis: String?) {
        val text = extractText(hypothesis)
        if (text.isBlank()) return

        val normalized = text.lowercase(Locale.ROOT).replace('ё', 'е').trim()
        if (containsWakeWord(normalized)) {
            wakeWordActiveUntil = System.currentTimeMillis() + WAKE_WORD_ACTIVE_MS
        }
        if (System.currentTimeMillis() > wakeWordActiveUntil) return

        val commandText = removeWakeWords(normalized).trim()
        if (commandText.isBlank() || !canAcceptCommand()) return

        val command = parseCommand(commandText)
        if (command != VoiceCommand.UNKNOWN) {
            lastCommandTimestamp = System.currentTimeMillis()
            onCommand(command)
        }
    }

    private fun extractText(hypothesis: String?): String {
        if (hypothesis.isNullOrBlank()) return ""
        return try {
            val json = JSONObject(hypothesis)
            when {
                json.has("text") -> json.optString("text", "")
                json.has("partial") -> json.optString("partial", "")
                else -> ""
            }
        } catch (_: Exception) { "" }
    }

    private fun containsWakeWord(text: String): Boolean = WAKE_WORDS.any { text.contains(it) }

    private fun removeWakeWords(text: String): String {
        var result = text
        WAKE_WORDS.forEach { wake -> result = result.replace(wake, "") }
        return result
    }

    private fun parseCommand(text: String): VoiceCommand = when {
        containsAny(text, "дальше", "следующий", "следующая", "далее", "вперед") -> VoiceCommand.NEXT
        containsAny(text, "назад", "предыдущий", "предыдущая") -> VoiceCommand.PREVIOUS
        containsAny(text, "ингредиенты", "зачитай ингредиенты", "прочитай ингредиенты", "скажи ингредиенты", "какие ингредиенты", "что нужно") -> VoiceCommand.READ_INGREDIENTS
        containsAny(text, "сбросить таймер", "сбрось таймер", "обнулить таймер", "сбросить", "сбрось") -> VoiceCommand.TIMER_RESET
        containsAny(text, "пауза", "поставь на паузу", "останови таймер", "стоп таймер") -> VoiceCommand.TIMER_PAUSE
        containsAny(text, "запустить таймер", "запусти таймер", "старт таймер", "старт", "таймер", "продолжить таймер", "продолжи таймер", "продолжить") -> VoiceCommand.TIMER
        containsAny(text, "повтори", "еще раз") -> VoiceCommand.REPEAT
        containsAny(text, "прочитай шаг", "зачитай шаг", "озвучь шаг", "что делать", "шаг") -> VoiceCommand.READ_STEP
        containsAny(text, "стоп", "хватит") -> VoiceCommand.STOP
        else -> VoiceCommand.UNKNOWN
    }

    private fun containsAny(text: String, vararg phrases: String): Boolean = phrases.any { text.contains(it) }
    private fun canAcceptCommand(): Boolean = System.currentTimeMillis() - lastCommandTimestamp > COMMAND_COOLDOWN_MS

    companion object {
        private const val TAG = "VoskVoiceManager"
        private const val MODEL_ASSET_DIR = "model-ru"
        private const val MODEL_STORAGE_DIR = "vosk-model-ru"
        private const val SAMPLE_RATE = 16000.0f

        private val WAKE_WORDS = listOf("сплеш", "сплэш", "окей сплеш", "окей сплэш", "ок сплеш", "ок сплэш", "эй сплеш", "эй сплэш")
        private const val COMMAND_COOLDOWN_MS = 1_000L
        private const val WAKE_WORD_ACTIVE_MS = 8_000L
    }
}