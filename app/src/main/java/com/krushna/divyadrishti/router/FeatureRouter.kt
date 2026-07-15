package com.krushna.divyadrishti.router

import com.krushna.divyadrishti.llm.IntentType

class FeatureRouter {

    fun route(intent: IntentType): FeatureType {

        return when (intent) {

            IntentType.SCENE ->
                FeatureType.SCENE

            IntentType.OBJECT ->
                FeatureType.OBJECT

            IntentType.OCR ->
                FeatureType.OCR

            IntentType.FACE ->
                FeatureType.FACE

            IntentType.COLOR ->
                FeatureType.COLOR

            IntentType.CURRENCY ->
                FeatureType.CURRENCY

            IntentType.GENERAL,
            IntentType.UNKNOWN ->
                FeatureType.GENERAL
        }
    }
}