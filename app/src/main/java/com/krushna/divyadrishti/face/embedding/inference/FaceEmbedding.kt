package com.krushna.divyadrishti.face.embedding.inference

import android.content.Context
import android.graphics.Bitmap
import com.krushna.divyadrishti.face.embedding.FacePreprocessor
import com.krushna.divyadrishti.face.embedding.tflite.ModelLoader

class FaceEmbedding(
    context: Context
) {

    private val interpreter =
        ModelLoader(context).interpreter

    fun getEmbedding(faceBitmap: Bitmap): FloatArray {

        val input =
            FacePreprocessor.preprocess(faceBitmap)

        val output = Array(1) {
            FloatArray(128)
        }

        interpreter.run(input, output)

        return output[0]
    }
}