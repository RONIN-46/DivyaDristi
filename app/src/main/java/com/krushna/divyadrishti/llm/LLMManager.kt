package com.krushna.divyadrishti.llm

import android.content.Context
import com.krushna.divyadrishti.llm.engine.ModelLoader

class LLMManager(
    context: Context
) {

    private val modelLoader = ModelLoader(context)

    fun initialize() {

        // Model will be loaded here later

    }

    fun generate(prompt: String): String {

        return "LLM not integrated yet."

    }
}