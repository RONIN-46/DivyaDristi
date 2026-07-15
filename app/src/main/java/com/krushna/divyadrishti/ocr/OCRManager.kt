package com.krushna.divyadrishti.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.text.Text
import kotlin.math.abs

class OCRManager {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    fun recognize(
        bitmap: Bitmap,
        onResult: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        // 1. FIX: Convert HARDWARE bitmap if necessary
        val compatibleBitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
            bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            bitmap
        }

        // 2. FIX: Use the compatibleBitmap here, not the original bitmap
        val image = InputImage.fromBitmap(compatibleBitmap, 0)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                // INSERTED HERE: Immediate check for empty results
                if (visionText.textBlocks.isEmpty()) {
                    onResult("No text found in this image.")
                    return@addOnSuccessListener
                }

                // Process and return the sorted text
                onResult(processVisionText(visionText))
            }
            .addOnFailureListener { onError(it) }
    }

    fun recognizeFromUri(
        context: Context,
        uri: Uri,
        onResult: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        try {
            // fromFilePath handles rotation and hardware configurations automatically
            val image = InputImage.fromFilePath(context, uri)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    if (visionText.textBlocks.isEmpty()) {
                        onResult("No text found in this image.")
                        return@addOnSuccessListener
                    }
                    onResult(processVisionText(visionText))
                }
                .addOnFailureListener { onError(it) }
        } catch (e: Exception) {
            onError(e)
        }
    }

    /**
     * Helper function to handle the sorting and grouping logic
     * so it doesn't have to be repeated in both methods.
     */
    private fun processVisionText(visionText: Text): String {
        val allLines = mutableListOf<Text.Line>()

        visionText.textBlocks.forEach { block ->
            allLines.addAll(block.lines)
        }

        val sortedLines = allLines.sortedBy { it.boundingBox?.top ?: 0 }
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

        return groupedLines.joinToString("\n") { row ->
            row.sortedBy { it.boundingBox?.left ?: 0 }
                .joinToString(" ") { it.text }
        }
    }
}