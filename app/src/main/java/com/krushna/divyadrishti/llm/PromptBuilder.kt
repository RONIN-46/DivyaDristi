package com.krushna.divyadrishti.llm

import com.krushna.divyadrishti.model.UnifiedContext

object PromptBuilder {

    fun build(
        command: String,
        context: UnifiedContext
    ): String {

        val scene = context.scene

        val faces = context.faces.persons.joinToString(", ")

        val objects =
            scene.objects.joinToString("\n") {

                "- ${it.label} (${it.position})"

            }

        val colors =
            context.colors.colors.entries.joinToString("\n") {

                "- ${it.key}: ${it.value}"

            }

        val currency =
            if (context.currency.notes.isEmpty())
                "None"
            else
                context.currency.notes.joinToString(", ")

        val ocr =
            if (context.ocr.available)
                context.ocr.text
            else
                "None"

        return """
You are Drishti.

Current Scene

Location:
${scene.sceneName}

Objects:
$objects

Recognized People:
${if (faces.isBlank()) "None" else faces}

Colors:
${if (colors.isBlank()) "None" else colors}

Currency:
$currency

Detected Text:
$ocr

User Question:
$command

Answer only using the available information.
If information is unavailable, clearly say so.
""".trimIndent()
    }
}