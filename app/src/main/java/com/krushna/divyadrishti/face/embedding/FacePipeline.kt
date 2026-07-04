package com.krushna.divyadrishti.face.embedding

import android.content.Context
import android.graphics.Bitmap
import com.krushna.divyadrishti.face.detection.FaceDetector
import com.krushna.divyadrishti.face.embedding.inference.FaceEmbedding

class FacePipeline(

    private val context: Context

) {

    private val detector = FaceDetector()

    private val cropper = FaceCropper()

    private val embeddingEngine = FaceEmbedding(context)

}