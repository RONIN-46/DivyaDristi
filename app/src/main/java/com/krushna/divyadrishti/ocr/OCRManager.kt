package com.krushna.divyadrishti.ocr

import android.graphics.Bitmap

object OCRManager {

    private val preprocessor = ImagePreprocessor()

    fun recognize(bitmap: Bitmap): String {

        val processedBitmap =
            preprocessor.process(bitmap)

        return """
            OCR Engine Ready
            
            Original:
            ${bitmap.width} x ${bitmap.height}
            
            Processed:
            ${processedBitmap.width} x ${processedBitmap.height}
        """.trimIndent()
    }
}