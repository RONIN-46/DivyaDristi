package com.krushna.divyadrishti.llm

import java.util.Locale

class IntentClassifier {

    fun classify(question: String): IntentType {

        val q = question.lowercase(Locale.getDefault())

        return when {

            // OCR
            q.contains("read") ||
                    q.contains("text") ||
                    q.contains("written") ||
                    q.contains("write") ->
                IntentType.OCR

            // FACE
            q.contains("who") ||
                    q.contains("person") ||
                    q.contains("face") ||
                    q.contains("recognize") ->
                IntentType.FACE

            // COLOR
            q.contains("color") ||
                    q.contains("colour") ->
                IntentType.COLOR

            // Currency
            q.contains("currency") ||
                    q.contains("money") ||
                    q.contains("note") ||
                    q.contains("rupee") ||
                    q.contains("coin") ->
                IntentType.CURRENCY

            // Object
            q.contains("where") ||
                    q.contains("object") ||
                    q.contains("chair") ||
                    q.contains("table") ||
                    q.contains("bottle") ->
                IntentType.OBJECT

            // Scene
            q.contains("scene") ||
                    q.contains("surrounding") ||
                    q.contains("surroundings") ||
                    q.contains("environment") ||
                    q.contains("describe") ||
                    q.contains("room") ->
                IntentType.SCENE

            q.isBlank() ->
                IntentType.UNKNOWN

            else ->
                IntentType.GENERAL
        }
    }
}