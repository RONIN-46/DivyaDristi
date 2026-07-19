package com.krushna.divyadrishti.llm

import com.krushna.divyadrishti.model.ContextManager

object PromptBuilder {

    fun build(userQuestion: String): String {

        val context = ContextManager.getContext()

        return buildString {

            appendLine("Scene:")
            appendLine(context.scene.description)
            appendLine()

            appendLine("Objects:")

            if (context.scene.objects.isEmpty()) {

                appendLine("None")

            } else {

                context.scene.objects.forEach {

                    appendLine(
                        "- ${it.label}, Color: ${it.color}, Position: ${it.position}"
                    )

                }

            }

            appendLine()

            appendLine("Recognized Faces:")

            if (context.faces.persons.isEmpty())
                appendLine("None")
            else
                appendLine(context.faces.persons.joinToString())

            appendLine()

            appendLine("Detected Text:")

            if (context.ocr.available)
                appendLine(context.ocr.text)
            else
                appendLine("None")

            appendLine()

            appendLine("Detected Currency:")

            if (context.currency.notes.isEmpty())
                appendLine("None")
            else
                appendLine(context.currency.notes.joinToString())

            appendLine()

            appendLine("User Question:")

            appendLine(userQuestion)
        }
    }
}