package com.krushna.divyadrishti.llm

import android.content.Context
import java.io.File

object ModelManager {

    private const val MODEL_NAME = "qwen2.5-0.5b-instruct-q4_k_m.gguf"

    fun getModelPath(context: Context): String {

        val modelDir = File(context.filesDir, "models")
        if (!modelDir.exists()) {
            modelDir.mkdirs()
        }

        val modelFile = File(modelDir, MODEL_NAME)

        if (!modelFile.exists()) {
            context.assets.open("models/$MODEL_NAME").use { input ->
                modelFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }

        return modelFile.absolutePath
    }
}