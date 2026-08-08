package com.krushna.divyadrishti.llm

interface LLMCallback {
    fun onToken(token: String)
    fun onComplete(response: String)
    fun onError(message: String)
}