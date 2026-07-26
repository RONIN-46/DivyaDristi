package com.krushna.divyadrishti.llm

object LLMNative {

    init {
        System.loadLibrary("llm_native")
    }

    external fun loadModel(modelPath: String): Boolean

    external fun generate(prompt: String): String

    external fun release()
}