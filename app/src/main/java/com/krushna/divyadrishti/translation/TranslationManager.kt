package com.krushna.divyadrishti.translation

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions

class TranslationManager {

    private var translator: Translator? = null
    private var hindiReady = false

    fun prepareHindi(
        onReady: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (hindiReady && translator != null) {
            onReady()
            return
        }

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.HINDI)
            .build()

        translator?.close()

        translator = Translation.getClient(options)

        val conditions = DownloadConditions.Builder()
            .requireWifi()
            .build()

        translator!!
            .downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                hindiReady = true
                onReady()
            }
            .addOnFailureListener {
                hindiReady = false
                onError(it)
            }
    }

    fun translate(
        text: String,
        onSuccess: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (text.isBlank()) {
            onSuccess(text)
            return
        }

        if (!hindiReady || translator == null) {
            onError(
                IllegalStateException("Hindi translation model is not ready")
            )
            return
        }

        translator!!
            .translate(text)
            .addOnSuccessListener(onSuccess)
            .addOnFailureListener(onError)
    }

    fun close() {
        translator?.close()
        translator = null
        hindiReady = false
    }
}