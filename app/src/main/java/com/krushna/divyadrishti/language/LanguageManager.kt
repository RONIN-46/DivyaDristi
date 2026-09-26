package com.krushna.divyadrishti.language

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object LanguageManager {

    const val ENGLISH = "en"
    const val HINDI = "hi"

    fun setLanguage(language: String) {

        val locales = LocaleListCompat.forLanguageTags(language)

        AppCompatDelegate.setApplicationLocales(locales)
    }

    fun getLanguage(context: Context): String {

        val locales = AppCompatDelegate.getApplicationLocales()

        if (!locales.isEmpty) {
            return locales[0]?.language ?: ENGLISH
        }

        return context.resources.configuration.locales[0].language
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