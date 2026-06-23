package com.krushna.divyadrishti.ocr

import android.content.Context
import android.graphics.Bitmap

class OCRManager(
    private val context: Context
) {

    private val modelLoader =
        ModelLoader(context)

    fun initialize() {

        modelLoader.verifyModels()

    }

    fun recognizeText(
        bitmap: Bitmap
    ): String {

        return """
            OCR Engine Ready
            Width = ${bitmap.width}
            Height = ${bitmap.height}
        """.trimIndent()
    }
}