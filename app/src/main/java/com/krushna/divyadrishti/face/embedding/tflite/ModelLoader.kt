package com.krushna.divyadrishti.face.embedding.tflite

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class ModelLoader(context: Context) {

    val interpreter: Interpreter

    init {
        interpreter = Interpreter(loadModelFile(context))
    }

    private fun loadModelFile(context: Context): MappedByteBuffer {

        val fileDescriptor =
            context.assets.openFd("facenet.tflite")

        val inputStream =
            fileDescriptor.createInputStream()

        val fileChannel =
            inputStream.channel

        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
    }
}