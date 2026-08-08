package com.krushna.divyadrishti.llm.engine

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context

class ModelLoader(
    private val context: Context
) {

    private lateinit var environment: OrtEnvironment
    private lateinit var session: OrtSession

    fun loadModel(modelPath: String) {

        environment = OrtEnvironment.getEnvironment()

        val modelBytes = context.assets.open(modelPath).readBytes()

        session = environment.createSession(modelBytes)

    }

    fun getSession(): OrtSession = session

    fun getEnvironment(): OrtEnvironment = environment
}