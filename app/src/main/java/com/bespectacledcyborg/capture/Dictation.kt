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
    private val onHeard: (String) -> Unit,
    private val onSegment: (String) -> Unit,
    private val onListening: (Boolean) -> Unit,
) {
    private var recognizer: SpeechRecognizer? = null

    private var language: String? = LANGUAGE

    private var quietRounds = 0

    val listening: Boolean
        get() = recognizer != null

    fun start() {
        release()
        quietRounds = 0
        val created = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = created
        created.setRecognitionListener(listener)
        listen()
        onListening(true)
    }

    fun stop() {
        if (!listening) return
        release()
        onListening(false)
    }

    private fun release() {
        val current = recognizer ?: return
        recognizer = null
        current.destroy()
    }

    private fun listen() {
        val current = recognizer ?: return
        val request = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        language?.let { request.putExtra(RecognizerIntent.EXTRA_LANGUAGE, it) }
        current.startListening(request)
        onStatus(if (language == null) "Listening…" else "Listening ($language)…")
    }

    private fun listenAgainAfterSilence() {
        quietRounds++
        if (quietRounds < MAX_QUIET_ROUNDS) {
            listen()
            return
        }
        stop()
        onStatus("Stopped after a long silence. Tap Talk to go on.")
    }

    private val listener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            if (!listening) return
            val text = best(results)
            if (text.isNullOrBlank()) {
                listenAgainAfterSilence()
                return
            }
            quietRounds = 0
            onSegment(text)
            listen()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (!listening) return
            val heard = best(partialResults)?.takeIf { it.isNotBlank() } ?: return
            quietRounds = 0
            onHeard(heard)
        }

        override fun onError(error: Int) {
            if (!listening) return
            when {
                error in SILENCE -> listenAgainAfterSilence()
                language != null && error in UNSUPPORTED_LANGUAGE -> {
                    language = null
                    start()
                }
                else -> {
                    stop()
                    onStatus(
                        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                            "Microphone permission is off."
                        } else {
                            "Speech recognition error $error. Tap Talk to try again."
                        },
                    )
                }
            }
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

    private companion object {
        const val LANGUAGE = "en-SG"
        const val MAX_QUIET_ROUNDS = 12
        val SILENCE = setOf(SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
        val UNSUPPORTED_LANGUAGE = setOf(
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
        )
    }
}
