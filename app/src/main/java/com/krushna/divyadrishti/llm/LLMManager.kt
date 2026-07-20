package com.krushna.divyadrishti.llm

import android.content.Context

class LLMManager(
    private val context: Context
) {

    var state = LLMState.IDLE
        private set

    fun initialize() {
        state = LLMState.LOADING

        // llama.cpp initialization will be added here

        state = LLMState.READY
    }

    fun generate(
        prompt: String,
        callback: LLMCallback
    ) {

        state = LLMState.GENERATING

        // llama.cpp generation will be added later

        callback.onComplete("LLM placeholder response")

        state = LLMState.READY
    }
}