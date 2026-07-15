package com.krushna.divyadrishti.face.model

import android.graphics.Rect

data class FaceInfo(
    val boundingBox: Rect,
    val confidence: Float
)