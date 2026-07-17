package com.krushna.divyadrishti.model

data class SceneContext(

    var sceneName: String = "",

    var confidence: Float = 0f,

    var objects: MutableList<DetectedObject> = mutableListOf()

)