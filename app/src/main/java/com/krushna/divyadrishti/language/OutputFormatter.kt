package com.krushna.divyadrishti.language

object OutputFormatter {

    fun color(color: String, language: String): String {

        return if (language == "hi") {

            when (color.lowercase()) {

                "red" -> "लाल"
                "blue" -> "नीला"
                "green" -> "हरा"
                "yellow" -> "पीला"
                "black" -> "काला"
                "white" -> "सफेद"
                "orange" -> "नारंगी"
                "pink" -> "गुलाबी"
                "purple" -> "बैंगनी"

                else -> color
            }

        } else {

            color
        }
    }
}