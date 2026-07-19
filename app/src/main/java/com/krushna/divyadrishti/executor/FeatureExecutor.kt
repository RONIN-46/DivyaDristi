package com.krushna.divyadrishti.executor

import com.krushna.divyadrishti.model.ContextManager
import com.krushna.divyadrishti.router.FeatureType

class FeatureExecutor {

    fun execute(feature: FeatureType): String {

        val context = ContextManager.getContext()

        return when (feature) {

            FeatureType.SCENE ->
                context.scene.description

            FeatureType.OCR ->
                if (context.ocr.available)
                    context.ocr.text
                else
                    "No text detected."

            FeatureType.FACE ->
                if (context.faces.persons.isNotEmpty())
                    context.faces.persons.joinToString(", ")
                else
                    "No known face detected."

            FeatureType.COLOR ->
                if (context.colors.colors.isNotEmpty())
                    context.colors.colors.entries.joinToString("\n") {
                        "${it.key}: ${it.value}"
                    }
                else
                    "No colors available."

            FeatureType.CURRENCY ->
                if (context.currency.notes.isNotEmpty())
                    context.currency.notes.joinToString(", ")
                else
                    "No currency detected."

            FeatureType.UNKNOWN ->
                "I didn't understand your request."
        }
    }
}