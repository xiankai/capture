package com.bespectacledcyborg.capture

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class Dictation(
    private val context: Context,
    private val onStatus: (String) -> Unit,
    private val onText: (String) -> Unit,
    private val onFinal: (String) -> Unit,
) {
    private var recognizer: SpeechRecognizer? = null

    fun start() {
        stop()
        val created = if (SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
        recognizer = created
        created.setRecognitionListener(listener)
        created.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2_000L),
        )
        onStatus("Listening…")
    }

    fun stop() {
        recognizer?.destroy()
        recognizer = null
    }

    private val listener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            val text = best(results)
            stop()
            if (text.isNullOrBlank()) {
                onStatus("Didn't catch that. Tap Talk to try again.")
            } else {
                onText(text)
                onFinal(text)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            best(partialResults)?.takeIf { it.isNotBlank() }?.let(onText)
        }

        override fun onError(error: Int) {
            stop()
            onStatus(
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        "Didn't catch that. Tap Talk to try again."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is off."
                    else -> "Speech recognition error $error. Tap Talk to try again."
                },
            )
        }

        override fun onEndOfSpeech() = onStatus("Working…")
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun best(results: Bundle?): String? =
        results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
}
