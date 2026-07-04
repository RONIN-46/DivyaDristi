package com.krushna.divyadrishti.face.embedding

import android.graphics.Bitmap
import java.nio.ByteBuffer
import java.nio.ByteOrder

object FacePreprocessor {

    fun preprocess(bitmap: Bitmap): ByteBuffer {

        val resized = Bitmap.createScaledBitmap(
            bitmap,
            160,
            160,
            true
        )

        val buffer = ByteBuffer.allocateDirect(
            1 * 160 * 160 * 3 * 4
        )

        buffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(160 * 160)

        resized.getPixels(
            pixels,
            0,
            160,
            0,
            0,
            160,
            160
        )

        for (pixel in pixels) {

            val r = ((pixel shr 16) and 0xFF) / 255f
            val g = ((pixel shr 8) and 0xFF) / 255f
            val b = (pixel and 0xFF) / 255f

            buffer.putFloat(r)
            buffer.putFloat(g)
            buffer.putFloat(b)
        }

        buffer.rewind()

        return buffer
    }
}