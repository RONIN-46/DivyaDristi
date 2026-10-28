package com.krushna.divyadrishti.color

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min

object ColorDetector {

    data class ColorResult(val name: String, val confidence: Int)

    private val supportedColors = listOf(
        "Red", "Orange", "Yellow", "Green", "Blue", "Purple", "Pink",
        "Brown", "Black", "White", "Gray", "Unknown"
    )

    fun detectDominantColor(bitmap: Bitmap): String = analyze(bitmap).name

    fun analyze(bitmap: Bitmap): ColorResult {
        val startX = (bitmap.width * 0.15f).toInt()
        val endX = (bitmap.width * 0.85f).toInt()
        val startY = (bitmap.height * 0.15f).toInt()
        val endY = (bitmap.height * 0.85f).toInt()
        return analyzeArea(bitmap, startX, startY, endX - startX, endY - startY)
    }

    /**
     * Fast area color analysis using batch getPixels() instead of individual JNI getPixel() calls.
     */
    fun analyzeArea(bitmap: Bitmap, startX: Int, startY: Int, width: Int, height: Int): ColorResult {
        if (width <= 0 || height <= 0 || bitmap.isRecycled) return ColorResult("Unknown", 0)

        val safeStartX = startX.coerceIn(0, bitmap.width - 1)
        val safeStartY = startY.coerceIn(0, bitmap.height - 1)
        val safeWidth = min(width, bitmap.width - safeStartX)
        val safeHeight = min(height, bitmap.height - safeStartY)

        if (safeWidth <= 0 || safeHeight <= 0) return ColorResult("Unknown", 0)

        val pixels = IntArray(safeWidth * safeHeight)
        bitmap.getPixels(pixels, 0, safeWidth, safeStartX, safeStartY, safeWidth, safeHeight)

        val counts = supportedColors.associateWith { 0 }.toMutableMap()
        val hsv = FloatArray(3)
        val step = max(2, min(safeWidth, safeHeight) / 20)

        for (y in 0 until safeHeight step step) {
            val rowOffset = y * safeWidth
            for (x in 0 until safeWidth step step) {
                Color.colorToHSV(pixels[rowOffset + x], hsv)
                val (h, s, v) = hsv
                val color = when {
                    v < 0.18f -> "Black"
                    s < 0.15f && v > 0.85f -> "White"
                    s < 0.15f -> "Gray"
                    h in 15f..35f && v < 0.65f -> "Brown"
                    h < 15f || h >= 345f -> "Red"
                    h in 15f..35f -> "Orange"
                    h in 35f..70f -> "Yellow"
                    h in 70f..170f -> "Green"
                    h in 170f..260f -> "Blue"
                    h in 260f..300f -> "Purple"
                    h in 300f..345f -> "Pink"
                    else -> "Unknown"
                }
                counts[color] = counts.getValue(color) + 1
            }
        }

        val total = counts.values.sum()
        val winner = counts.maxByOrNull { it.value } ?: return ColorResult("Unknown", 0)
        val confidence = if (total == 0) 0 else winner.value * 100 / total
        return if (winner.key == "Unknown" || confidence < 20) {
            ColorResult("Unknown", confidence)
        } else {
            ColorResult(winner.key, confidence)
        }
    }
}

