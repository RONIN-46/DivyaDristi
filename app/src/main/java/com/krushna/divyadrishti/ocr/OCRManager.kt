package com.krushna.divyadrishti.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.text.Text
import kotlin.math.abs

class OCRManager {

    private val recognizer =
        TextRecognition.getClient(
            TextRecognizerOptions.DEFAULT_OPTIONS
        )

    fun recognize(
        bitmap: Bitmap,
        onResult: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {

        val image =
            InputImage.fromBitmap(bitmap, 0)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->

                val allLines = mutableListOf<Text.Line>()

                // Collect all detected lines
                visionText.textBlocks.forEach { block ->
                    allLines.addAll(block.lines)
                }

                // Sort from top to bottom
                val sortedLines = allLines.sortedBy {
                    it.boundingBox?.top ?: 0
                }

                // Group lines having similar Y coordinate
                val groupedLines = mutableListOf<MutableList<Text.Line>>()

                val yThreshold = 20

                for (line in sortedLines) {

                    if (groupedLines.isEmpty()) {
                        groupedLines.add(mutableListOf(line))
                    } else {

                        val lastGroup = groupedLines.last()

                        val lastTop = lastGroup.first().boundingBox?.top ?: 0
                        val currentTop = line.boundingBox?.top ?: 0

                        if (abs(currentTop - lastTop) <= yThreshold) {

                            lastGroup.add(line)

                        } else {

                            groupedLines.add(mutableListOf(line))

                        }
                    }
                }

                // Sort each row from left to right
                val finalText = groupedLines.joinToString("\n") { row ->

                    row.sortedBy {
                        it.boundingBox?.left ?: 0
                    }.joinToString(" ") {
                        it.text
                    }

                }

                onResult(finalText)

            }
            .addOnFailureListener {

                onError(it)

            }
    }
}

