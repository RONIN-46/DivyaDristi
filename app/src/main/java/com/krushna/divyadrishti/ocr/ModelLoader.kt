package com.krushna.divyadrishti.ocr

import android.content.Context
import android.util.Log

class ModelLoader(
    private val context: Context
) {

    fun verifyModels() {

        try {

            val detFiles =
                context.assets.list("paddleocr/det")

            val recFiles =
                context.assets.list("paddleocr/rec_en")

            Log.d(
                "OCR",
                "DET = ${detFiles?.joinToString()}"
            )

            Log.d(
                "OCR",
                "REC = ${recFiles?.joinToString()}"
            )

        } catch (e: Exception) {

            Log.e(
                "OCR",
                "Model loading failed",
                e
            )
        }
    }
}