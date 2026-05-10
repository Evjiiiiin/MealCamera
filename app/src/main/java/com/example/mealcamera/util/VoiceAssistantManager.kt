package com.example.mealcamera.util

import android.content.Context
import android.content.Intent
import android.os.Bundle
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
        NEXT, PREVIOUS, REPEAT, STOP, TIMER, READ_STEP, UNKNOWN
    }

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isTtsReady = false
    private var isDestroyed = false

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
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                stopListeningInternal()
            }

            override fun onDone(utteranceId: String?) {
                if (!isDestroyed) startListeningInternal()
            }

            override fun onError(utteranceId: String?) {
                // Keep for compatibility but prioritize the version with error code
                if (!isDestroyed) startListeningInternal()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.e("VoiceAssistant", "TTS Error code: $errorCode for $utteranceId")
                if (!isDestroyed) startListeningInternal()
            }
        })
    }

    private fun setupSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { onListeningStateChanged(true) }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { onListeningStateChanged(false) }
                override fun onError(error: Int) {
                    Log.e("VoiceAssistant", "Speech Error: $error")
                    onListeningStateChanged(false)
                    if (!isDestroyed && !ttsIsSpeaking()) startListeningInternal()
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val command = parseCommand(matches?.firstOrNull())
                    if (command != VoiceCommand.UNKNOWN) onCommand(command) else startListeningInternal()
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun ttsIsSpeaking(): Boolean = tts?.isSpeaking ?: false

    private fun parseCommand(input: String?): VoiceCommand {
        val text = input?.lowercase() ?: return VoiceCommand.UNKNOWN
        return when {
            text.contains("дальше") || text.contains("следующий") || text.contains("далее") -> VoiceCommand.NEXT
            text.contains("назад") || text.contains("предыдущий") -> VoiceCommand.PREVIOUS
            text.contains("повтори") || text.contains("еще раз") -> VoiceCommand.REPEAT
            text.contains("стоп") || text.contains("хватит") -> VoiceCommand.STOP
            text.contains("таймер") || text.contains("запусти") -> VoiceCommand.TIMER
            text.contains("прочитай") || text.contains("текст") -> VoiceCommand.READ_STEP
            else -> VoiceCommand.UNKNOWN
        }
    }

    fun speak(text: String) {
        if (!isTtsReady || isDestroyed) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utterance_id")
    }

    fun startListening() { if (!isDestroyed) startListeningInternal() }

    fun stopAll() {
        tts?.stop()
        stopListeningInternal()
    }

    private fun startListeningInternal() {
        if (isDestroyed || ttsIsSpeaking()) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
        }
        try { speechRecognizer?.startListening(intent) } catch (e: Exception) { Log.e("VoiceAssistant", "Error: ${e.message}") }
    }

    private fun stopListeningInternal() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
        } catch (e: Exception) { Log.e("VoiceAssistant", "Error: ${e.message}") }
        onListeningStateChanged(false)
    }

    fun destroy() {
        isDestroyed = true
        tts?.shutdown()
        speechRecognizer?.destroy()
    }
}