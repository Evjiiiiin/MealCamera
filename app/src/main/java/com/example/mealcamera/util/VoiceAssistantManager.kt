package com.example.mealcamera.util

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

class VoiceAssistantManager(
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

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null

    private var isTtsReady = false
    private var isDestroyed = false
    private var isListening = false
    private var recognitionRestartPending = false
    private var isPausedByStopCommand = false

    private var lastCommandTimestamp = 0L
    private var wakeWordActiveUntil = 0L

    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        tts = TextToSpeech(context, this)
        setupSpeechRecognizer()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("ru")
            isTtsReady = true
            setupTtsListener()
        }
    }

    private fun setupTtsListener() {
        tts?.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {

                override fun onStart(utteranceId: String?) {
                    stopListeningInternal()
                }

                override fun onDone(utteranceId: String?) {
                    scheduleListeningRestart()
                }

                @Deprecated("Deprecated in Android TextToSpeech API")
                override fun onError(utteranceId: String?) {
                    scheduleListeningRestart()
                }

                override fun onError(
                    utteranceId: String?,
                    errorCode: Int
                ) {
                    Log.e(
                        "VoiceAssistant",
                        "TTS Error code: $errorCode for $utteranceId"
                    )
                    scheduleListeningRestart()
                }
            }
        )
    }

    private fun setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            return
        }

        speechRecognizer =
            SpeechRecognizer.createSpeechRecognizer(context)

        speechRecognizer?.setRecognitionListener(
            object : RecognitionListener {

                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    onListeningStateChanged(true)
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    isListening = false
                    onListeningStateChanged(false)
                }

                override fun onError(error: Int) {
                    Log.e(
                        "VoiceAssistant",
                        "Speech Error: $error"
                    )

                    isListening = false
                    onListeningStateChanged(false)

                    if (!isDestroyed && !ttsIsSpeaking()) {
                        scheduleListeningRestart()
                    }
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    onListeningStateChanged(false)

                    val matches =
                        results?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        ).orEmpty()

                    val command =
                        matches
                            .asSequence()
                            .map { parseCommand(it) }
                            .firstOrNull {
                                it != VoiceCommand.UNKNOWN
                            }
                            ?: VoiceCommand.UNKNOWN

                    if (
                        command != VoiceCommand.UNKNOWN &&
                        canAcceptCommand()
                    ) {
                        lastCommandTimestamp =
                            System.currentTimeMillis()

                        onCommand(command)

                        mainHandler.postDelayed(
                            {
                                if (
                                    !isDestroyed &&
                                    !isPausedByStopCommand &&
                                    !ttsIsSpeaking()
                                ) {
                                    scheduleListeningRestart(
                                        delayMs = 0L
                                    )
                                }
                            },
                            POST_COMMAND_RESTART_DELAY_MS
                        )
                    } else if (
                        !isDestroyed &&
                        !ttsIsSpeaking()
                    ) {
                        scheduleListeningRestart()
                    }
                }

                override fun onPartialResults(
                    partialResults: Bundle?
                ) {
                }

                override fun onEvent(
                    eventType: Int,
                    params: Bundle?
                ) {
                }
            }
        )
    }

    private fun ttsIsSpeaking(): Boolean {
        return tts?.isSpeaking ?: false
    }

    private fun canAcceptCommand(): Boolean {
        return System.currentTimeMillis() -
                lastCommandTimestamp >
                COMMAND_COOLDOWN_MS
    }

    private fun parseCommand(
        input: String?
    ): VoiceCommand {
        val originalText =
            input
                ?.lowercase(Locale.ROOT)
                ?.replace('ё', 'е')
                ?: return VoiceCommand.UNKNOWN

        // Проверяем активационную фразу
        val wakeDetected = hasWakeWord(originalText)
        if (wakeDetected) {
            wakeWordActiveUntil =
                System.currentTimeMillis() +
                        WAKE_WORD_ACTIVE_MS
        }

        // Удаляем активационную фразу
        val text =
            removeWakeWord(originalText).trim()

        if (text.isBlank()) {
            return VoiceCommand.UNKNOWN
        }

        return when {
            containsAny(
                text,
                "дальше",
                "следующий",
                "следующая",
                "далее",
                "вперед"
            ) -> VoiceCommand.NEXT

            containsAny(
                text,
                "назад",
                "предыдущий",
                "предыдущая"
            ) -> VoiceCommand.PREVIOUS

            containsAny(
                text,
                "ингредиенты",
                "зачитай ингредиенты",
                "прочитай ингредиенты",
                "скажи ингредиенты"
            ) -> VoiceCommand.READ_INGREDIENTS

            containsAny(
                text,
                "сбросить таймер",
                "сбрось таймер",
                "обнулить таймер",
                "сбросить",
                "сбрось"
            ) -> VoiceCommand.TIMER_RESET

            containsAny(
                text,
                "пауза",
                "поставь на паузу",
                "останови таймер",
                "стоп таймер"
            ) -> VoiceCommand.TIMER_PAUSE

            containsAny(
                text,
                "запустить таймер",
                "запусти таймер",
                "старт таймер",
                "старт",
                "таймер",
                "продолжить таймер",
                "продолжи таймер"
            ) -> VoiceCommand.TIMER

            containsAny(
                text,
                "повтори",
                "еще раз"
            ) -> VoiceCommand.REPEAT

            containsAny(
                text,
                "прочитай шаг",
                "прочитай",
                "текст",
                "шаг"
            ) -> VoiceCommand.READ_STEP

            containsAny(
                text,
                "стоп",
                "хватит"
            ) -> VoiceCommand.STOP

            else -> VoiceCommand.UNKNOWN
        }
    }

    private fun hasWakeWord(text: String): Boolean {
        return containsAny(
            text,
            "окей сплеш",
            "окей сплэш",
            "ок сплеш",
            "ок сплэш",
            "эй сплеш",
            "эй сплэш"
        )
    }

    private fun removeWakeWord(text: String): String {
        return text
            .replace("окей сплеш", "")
            .replace("окей сплэш", "")
            .replace("ок сплеш", "")
            .replace("ок сплэш", "")
            .replace("эй сплеш", "")
            .replace("эй сплэш", "")
    }

    private fun containsAny(
        text: String,
        vararg phrases: String
    ): Boolean {
        return phrases.any { phrase ->
            text.contains(phrase)
        }
    }

    fun speak(text: String) {
        if (!isTtsReady || isDestroyed) return

        isPausedByStopCommand = false
        stopListeningInternal()

        tts?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            STEP_UTTERANCE_ID
        )
    }

    fun startListening() {
        isPausedByStopCommand = false
        scheduleListeningRestart(delayMs = 0L)
    }

    fun stopAll() {
        isPausedByStopCommand = true
        tts?.stop()
        stopListeningInternal()
    }

    private fun scheduleListeningRestart(
        delayMs: Long = LISTENING_RESTART_DELAY_MS
    ) {
        if (
            isDestroyed ||
            isPausedByStopCommand ||
            recognitionRestartPending
        ) {
            return
        }

        recognitionRestartPending = true

        mainHandler.postDelayed(
            {
                recognitionRestartPending = false
                startListeningInternal()
            },
            delayMs
        )
    }

    private fun startListeningInternal() {
        if (
            isDestroyed ||
            isPausedByStopCommand ||
            ttsIsSpeaking() ||
            isListening
        ) {
            return
        }

        val intent =
            Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            ).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    "ru-RU"
                )
                putExtra(
                    RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                    false
                )
                putExtra(
                    RecognizerIntent.EXTRA_MAX_RESULTS,
                    5
                )
                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
                    MIN_LISTENING_SESSION_MS
                )
                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                    COMPLETE_SILENCE_MS
                )
                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                    POSSIBLE_SILENCE_MS
                )
            }

        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(
                "VoiceAssistant",
                "Error: ${e.message}"
            )

            isListening = false
            onListeningStateChanged(false)
        }
    }

    private fun stopListeningInternal() {
        mainHandler.removeCallbacksAndMessages(null)
        recognitionRestartPending = false

        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
        } catch (e: Exception) {
            Log.e(
                "VoiceAssistant",
                "Error: ${e.message}"
            )
        }

        isListening = false
        onListeningStateChanged(false)
    }

    fun destroy() {
        isDestroyed = true
        mainHandler.removeCallbacksAndMessages(null)

        tts?.shutdown()
        speechRecognizer?.destroy()
    }

    companion object {
        private const val STEP_UTTERANCE_ID =
            "cooking_step_utterance"

        private const val LISTENING_RESTART_DELAY_MS =
            350L

        private const val COMMAND_COOLDOWN_MS =
            1_200L

        private const val POST_COMMAND_RESTART_DELAY_MS =
            500L

        private const val WAKE_WORD_ACTIVE_MS =
            8_000L

        private const val MIN_LISTENING_SESSION_MS =
            10_000L

        private const val COMPLETE_SILENCE_MS =
            1_500L

        private const val POSSIBLE_SILENCE_MS =
            1_000L
    }
}