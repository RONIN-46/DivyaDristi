package com.krushna.divyadrishti.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

class SpeechManager(
    private val context: Context,
    private val listener: VoiceCommandListener
) {

    private val speechRecognizer =
        SpeechRecognizer.createSpeechRecognizer(context)

    init {

        speechRecognizer.setRecognitionListener(object : RecognitionListener {

            override fun onReadyForSpeech(params: Bundle?) {}

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {}

            override fun onPartialResults(partialResults: Bundle?) {
            // Do nothing
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onResults(results: Bundle?) {
                val spokenText = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (spokenText != null) {
                    listener.onCommandRecognized(spokenText)
                } else {
                    listener.onError("Nothing recognized")
                }
            }
            override fun onError(error: Int) {
                val message = when (error) {
                    SpeechRecognizer.ERROR_AUDIO ->
                        "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT ->
                        "Client error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        "Microphone permission denied"
                    SpeechRecognizer.ERROR_NETWORK ->
                        "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH ->
                        "No speech recognized"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                        "Recognizer busy"
                    SpeechRecognizer.ERROR_SERVER ->
                        "Speech server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        "Speech timeout"
                    else ->
                        "Unknown error"
                }
                listener.onError(message)
            }
        })
    }
    fun startListening() {

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            listener.onError("Speech recognition is not available")
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)

        intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )

        intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE,
            Locale.getDefault()
        )

        intent.putExtra(
            RecognizerIntent.EXTRA_MAX_RESULTS,
            1
        )

        speechRecognizer.startListening(intent)
    }

    fun stopListening() {

        speechRecognizer.stopListening()

    }

    fun destroy() {

        speechRecognizer.destroy()

    }
}