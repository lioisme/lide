package com.delua

import android.app.Application
import com.delua.i18n.LocaleManager
import com.delua.ui.theme.SettingsManager

class DeluaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SettingsManager.init(this)
        LocaleManager.init(this)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        LocaleManager.init(this)
    }
}
