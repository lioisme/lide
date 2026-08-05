package com.lioisme.lide.i18n

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

object LocaleManager {
    private const val PREF_NAME = "lide_locale"
    private const val KEY_LANG = "language"

    /** 语言标签常量（P3-3）：SettingsScreen / 持久化 / 比较共用，禁止散落魔法字符串。 */
    const val TAG_SYSTEM = "system"
    const val TAG_EN = "en"
    const val TAG_ZH = "zh"

    var currentLanguageTag: String = TAG_SYSTEM
        private set

    val supportedTags: List<String> = listOf(TAG_SYSTEM, TAG_EN, TAG_ZH)

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        currentLanguageTag = prefs.getString(KEY_LANG, TAG_SYSTEM) ?: TAG_SYSTEM
    }

    fun setLanguage(
        context: Context,
        languageTag: String,
    ) {
        currentLanguageTag = languageTag
        context
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANG, languageTag)
            .apply()
    }

    fun wrap(base: Context): Context {
        val tag = currentLanguageTag
        if (tag == TAG_SYSTEM) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return base.createConfigurationContext(config)
        }
        // API 24-27 无 createConfigurationContext，仅此旧分支回退全局 updateConfiguration。
        @Suppress("DEPRECATION")
        base.resources.updateConfiguration(config, base.resources.displayMetrics)
        return base
    }
}
