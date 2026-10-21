package com.krushna.divyadrishti.language

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object LanguageManager {

    const val ENGLISH = "en"
    const val HINDI = "hi"

    private var currentLang: String? = null

    fun setLanguage(language: String) {
        currentLang = language
        val locales = LocaleListCompat.forLanguageTags(language)
        AppCompatDelegate.setApplicationLocales(locales)
    }

    fun getLanguage(context: Context): String {
        currentLang?.let { return it }

        val locales = AppCompatDelegate.getApplicationLocales()

        if (!locales.isEmpty) {
            val lang = locales[0]?.language ?: ENGLISH
            currentLang = lang
            return lang
        }

        val lang = context.resources.configuration.locales[0].language
        currentLang = lang
        return lang
    }

    fun isHindi(context: Context): Boolean {
        return getLanguage(context) == HINDI
    }

    fun toggle(context: Context) {

        if (isHindi(context)) {
            setLanguage(ENGLISH)
        } else {
            setLanguage(HINDI)
        }
    }
}