package com.delua

import android.app.Application
import com.delua.ui.theme.SettingsManager

class DeluaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SettingsManager.init(this)
    }
}
