package com.lioisme.lide.i18n

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

object LocaleManager {

    private const val PREF_NAME = "lide_locale"
    private const val KEY_LANG = "language"

    var currentLanguageTag: String = "system"
        private set

    val supportedTags: List<String> = listOf("system", "en", "zh")

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        currentLanguageTag = prefs.getString(KEY_LANG, "system") ?: "system"
    }

    fun setLanguage(context: Context, languageTag: String) {
        currentLanguageTag = languageTag
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANG, languageTag)
            .apply()
    }

    fun wrap(base: Context): Context {
        val tag = currentLanguageTag
        if (tag == "system") return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return base.createConfigurationContext(config)
        }
        @Suppress("DEPRECATION")
        base.resources.updateConfiguration(config, base.resources.displayMetrics)
        return base
    }
}
