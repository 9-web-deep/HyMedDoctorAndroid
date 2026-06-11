package com.hymed.doctorapp

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import java.util.Locale

object LocaleHelper {
    private const val PREFS_KEY = "app_locale"
    const val LANG_EN = "en"
    const val LANG_ZH = "zh"

    fun getLocale(prefs: SharedPreferences): String =
        prefs.getString(PREFS_KEY, LANG_ZH) ?: LANG_ZH

    fun setLocale(prefs: SharedPreferences, lang: String) {
        prefs.edit().putString(PREFS_KEY, lang).apply()
    }

    fun wrapContext(context: Context, lang: String): Context {
        return try {
            val locale = Locale(lang)
            Locale.setDefault(locale)
            val config = Configuration(context.resources.configuration)
            config.setLocale(locale)
            context.createConfigurationContext(config)
        } catch (_: Exception) {
            context
        }
    }
}
