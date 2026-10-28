package com.krushna.divyadrishti.llm

import android.content.Context
import java.io.File

object ModelManager {

    private const val MODEL_NAME = "qwen2.5-0.5b-instruct-q4_k_m.gguf"

    @Synchronized
    fun getModelPath(context: Context): String {

        val modelDir = File(context.filesDir, "models")
        if (!modelDir.exists()) {
            modelDir.mkdirs()
        }

        val modelFile = File(modelDir, MODEL_NAME)

        // Only copy if file doesn't exist or is empty
        if (!modelFile.exists() || modelFile.length() == 0L) {
            val tempFile = File(modelDir, "$MODEL_NAME.tmp")
            try {
                context.assets.open("models/$MODEL_NAME").use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                if (tempFile.exists() && tempFile.length() > 0L) {
                    tempFile.renameTo(modelFile)
                }
            } catch (e: Exception) {
                tempFile.delete()
                throw e
            }
        }

        return modelFile.absolutePath
    }
}