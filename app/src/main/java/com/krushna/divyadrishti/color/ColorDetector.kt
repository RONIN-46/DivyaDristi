package com.krushna.divyadrishti.color

import android.graphics.Bitmap
import android.graphics.Color

object ColorDetector {

    data class ColorResult(val name: String, val confidence: Int)

    private val supportedColors = listOf(
        "Red", "Orange", "Yellow", "Green", "Blue", "Purple", "Pink",
        "Brown", "Black", "White", "Gray", "Unknown"
    )

    fun detectDominantColor(bitmap: Bitmap): String = analyze(bitmap).name

    fun analyze(bitmap: Bitmap): ColorResult {
        val counts = supportedColors.associateWith { 0 }.toMutableMap()
        val startX = (bitmap.width * 0.15f).toInt()
        val endX = (bitmap.width * 0.85f).toInt()
        val startY = (bitmap.height * 0.15f).toInt()
        val endY = (bitmap.height * 0.85f).toInt()
        val hsv = FloatArray(3)

        for (x in startX until endX step 4) {
            for (y in startY until endY step 4) {
                Color.colorToHSV(bitmap.getPixel(x, y), hsv)
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
