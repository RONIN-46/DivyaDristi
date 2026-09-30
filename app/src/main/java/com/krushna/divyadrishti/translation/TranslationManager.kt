package com.krushna.divyadrishti.translation

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import android.util.Log
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateRemoteModel

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
            .build()

        translator!!
            .downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                hindiReady = true

                Log.d(
                    "HINDI_MODEL",
                    "downloadModelIfNeeded SUCCESS"
                )

                onReady()
            }
            .addOnFailureListener { exception ->

                hindiReady = false

                Log.e(
                    "HINDI_MODEL",
                    "downloadModelIfNeeded FAILED",
                    exception
                )

                onError(exception)
            }
    }

    fun checkHindiModel(
        onResult: (Boolean) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val modelManager = RemoteModelManager.getInstance()

        modelManager
            .getDownloadedModels(TranslateRemoteModel::class.java)
            .addOnSuccessListener { models ->

                val hindiModelDownloaded = models.any {
                    it.language == TranslateLanguage.HINDI
                }

                onResult(hindiModelDownloaded)
            }
            .addOnFailureListener { exception ->
                onError(exception)
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

        if (hindiReady && translator != null) {
            translator!!
                .translate(text)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onError)

            return
        }

        prepareHindi(
            onReady = {
                translator!!
                    .translate(text)
                    .addOnSuccessListener(onSuccess)
                    .addOnFailureListener(onError)
            },
            onError = onError
        )
    }

    fun close() {
        translator?.close()
        translator = null
        hindiReady = false
    }
}