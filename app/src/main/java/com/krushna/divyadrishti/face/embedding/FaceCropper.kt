package com.krushna.divyadrishti.face.embedding

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.face.Face

class FaceCropper {

    fun crop(bitmap: Bitmap, face: Face): Bitmap {

        val box = face.boundingBox

        val left = box.left.coerceAtLeast(0)
        val top = box.top.coerceAtLeast(0)

        val right = box.right.coerceAtMost(bitmap.width)
        val bottom = box.bottom.coerceAtMost(bitmap.height)

        return Bitmap.createBitmap(
            bitmap,
            left,
            top,
            right - left,
            bottom - top
        )
    }
}