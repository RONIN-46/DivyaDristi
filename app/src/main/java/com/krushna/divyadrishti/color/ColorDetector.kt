package com.krushna.divyadrishti.color

import android.graphics.Bitmap
import android.graphics.Color

object ColorDetector {

    private val supportedColors = listOf(
        "Red",
        "Orange",
        "Yellow",
        "Green",
        "Blue",
        "Purple",
        "Pink",
        "Brown",
        "Black",
        "White",
        "Gray",
        "Unknown"
    )

    fun detectDominantColor(
        bitmap: Bitmap
    ): String {

        val colorCounts = mutableMapOf<String, Int>()

        supportedColors.forEach {
            colorCounts[it] = 0
        }
        val width = bitmap.width
        val height = bitmap.height
        val startX = (width * 0.15f).toInt()
        val endX = (width * 0.85f).toInt()

        val startY = (height * 0.15f).toInt()
        val endY = (height * 0.85f).toInt()

        for (x in startX until endX step 4) {
            for (y in startY until endY step 4)
                {

                    val pixel = bitmap.getPixel(x, y)

                    val hsv = FloatArray(3)

                    Color.colorToHSV(
                        pixel,
                        hsv
                    )
                    val h = hsv[0]
                    val s = hsv[1]
                    val v = hsv[2]

                    when {

                        // ---------- Black ----------
                        v < 0.18f -> {
                            colorCounts["Black"] =
                                colorCounts["Black"]!! + 1
                        }

                        // ---------- White ----------
                        s < 0.15f && v > 0.85f -> {
                            colorCounts["White"] =
                                colorCounts["White"]!! + 1
                        }

                        // ---------- Gray ----------
                        s < 0.15f -> {
                            colorCounts["Gray"] =
                                colorCounts["Gray"]!! + 1
                        }

                        // ---------- Brown ----------
                        h in 15f..35f && v < 0.65f -> {
                            colorCounts["Brown"] =
                                colorCounts["Brown"]!! + 1
                        }

                        // ---------- Red ----------
                        h < 15f || h >= 345f -> {
                            colorCounts["Red"] =
                                colorCounts["Red"]!! + 1
                        }

                        // ---------- Orange ----------
                        h in 15f..35f -> {
                            colorCounts["Orange"] =
                                colorCounts["Orange"]!! + 1
                        }

                        // ---------- Yellow ----------
                        h in 35f..70f -> {
                            colorCounts["Yellow"] =
                                colorCounts["Yellow"]!! + 1
                        }

                        // ---------- Green ----------
                        h in 70f..170f -> {
                            colorCounts["Green"] =
                                colorCounts["Green"]!! + 1
                        }

                        // ---------- Blue ----------
                        h in 170f..260f -> {
                            colorCounts["Blue"] =
                                colorCounts["Blue"]!! + 1
                        }

                        // ---------- Purple ----------
                        h in 260f..300f -> {
                            colorCounts["Purple"] =
                                colorCounts["Purple"]!! + 1
                        }

                        // ---------- Pink ----------
                        h in 300f..345f -> {
                            colorCounts["Pink"] =
                                colorCounts["Pink"]!! + 1
                        }

                        else -> {
                            colorCounts["Unknown"] =
                                colorCounts["Unknown"]!! + 1
                        }
                    }
                }

            }

            return colorCounts.maxByOrNull {
                it.value
            }?.key ?: "Unknown"

        }

    }
