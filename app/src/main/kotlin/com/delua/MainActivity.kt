package com.delua

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.rememberNavController
import com.delua.i18n.LocaleManager
import com.delua.ui.navigation.DeluaNavHost
import com.delua.ui.theme.DeluaTheme
import com.delua.ui.theme.SettingsManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val config by SettingsManager.themeConfig.collectAsState()
            DeluaTheme(config = config) {
                val navController = rememberNavController()
                DeluaNavHost(navController = navController)
            }
        }
    }

    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(LocaleManager.wrap(base))
    }
}
