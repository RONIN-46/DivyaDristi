package com.krushna.divyadrishti.llm

import android.content.Context
import android.util.Log

class LLMManager(
    private val context: Context
) {

    var state = LLMState.IDLE
        private set

    fun initialize() {

        state = LLMState.LOADING

        val modelPath = ModelManager.getModelPath(context)

        val loaded = LLMNative.loadModel(modelPath)
        Log.d("LLM", "loadModel() called")

        state =
            if (loaded)
                LLMState.READY
            else
                LLMState.ERROR
    }
    @Synchronized
    fun generate(
        prompt: String,
        callback: LLMCallback
    ) {

        if (state != LLMState.READY) {

            callback.onError("LLM not ready")
            return
        }

        state = LLMState.GENERATING

        Thread {

            try {

                val response = LLMNative.generate(prompt)

                callback.onComplete(response)

            } catch (e: Exception) {

                callback.onError(
                    e.message ?: "Generation failed"
                )

            } finally {

                state = LLMState.READY
            }

        }.start()
    }

    fun release() {

        LLMNative.release()

        state = LLMState.IDLE
    }
}