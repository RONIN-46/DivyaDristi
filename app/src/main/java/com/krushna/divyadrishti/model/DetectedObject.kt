package com.krushna.divyadrishti.model

data class DetectedObject(

    val label: String,

    val confidence: Float,

    val position: String,

    var color: String? = null

)