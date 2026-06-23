package com.krushna.divyadrishti.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

class ImagePreprocessor {

    fun process(bitmap: Bitmap): Bitmap {

        var result = resize(bitmap)

        result = toGray(result)

        result = increaseContrast(result)

        return result
    }

    private fun resize(bitmap: Bitmap): Bitmap {

        return Bitmap.createScaledBitmap(
            bitmap,
            960,
            960,
            true
        )
    }

    private fun toGray(bitmap: Bitmap): Bitmap {

        val grayBitmap = Bitmap.createBitmap(
            bitmap.width,
            bitmap.height,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(grayBitmap)

        val paint = Paint()

        val colorMatrix = ColorMatrix()

        colorMatrix.setSaturation(0f)

        paint.colorFilter =
            ColorMatrixColorFilter(colorMatrix)

        canvas.drawBitmap(
            bitmap,
            0f,
            0f,
            paint
        )

        return grayBitmap
    }
    private fun increaseContrast(bitmap: Bitmap): Bitmap {

        val contrast = 1.5f

        val scale = contrast
        val translate = (-0.5f * scale + 0.5f) * 255f

        val cm = ColorMatrix(
            floatArrayOf(
                scale,0f,0f,0f,translate,
                0f,scale,0f,0f,translate,
                0f,0f,scale,0f,translate,
                0f,0f,0f,1f,0f
            )
        )

        val result = Bitmap.createBitmap(
            bitmap.width,
            bitmap.height,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(result)

        val paint = Paint()

        paint.colorFilter =
            ColorMatrixColorFilter(cm)

        canvas.drawBitmap(
            bitmap,
            0f,
            0f,
            paint
        )

        return result
    }
}