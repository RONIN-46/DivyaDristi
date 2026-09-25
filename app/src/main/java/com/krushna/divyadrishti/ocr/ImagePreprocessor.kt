package com.krushna.divyadrishti.ocr

import android.graphics.Bitmap

class ImagePreprocessor {

    fun process(bitmap: Bitmap): Bitmap {

        return resizePreservingAspectRatio(bitmap)
    }

    private fun resizePreservingAspectRatio(bitmap: Bitmap): Bitmap {

        val maxDimension = 1600

        val originalWidth = bitmap.width
        val originalHeight = bitmap.height

        // No resizing if the image is already small enough
        if (
            originalWidth <= maxDimension &&
            originalHeight <= maxDimension
        ) {
            return bitmap
        }

        val scale =
            maxDimension.toFloat() /
                    maxOf(originalWidth, originalHeight)

        val newWidth =
            (originalWidth * scale).toInt()

        val newHeight =
            (originalHeight * scale).toInt()

        return Bitmap.createScaledBitmap(
            bitmap,
            newWidth,
            newHeight,
            true
        )
    }
}