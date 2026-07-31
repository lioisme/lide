package com.lioisme.delua

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.rememberNavController
import com.lioisme.delua.androlua.AlpManager
import com.lioisme.delua.i18n.LocaleManager
import com.lioisme.delua.ui.navigation.DeluaNavHost
import com.lioisme.delua.ui.theme.DeluaTheme
import com.lioisme.delua.ui.theme.SettingsManager
import java.io.File

class MainActivity : ComponentActivity() {

    private val pendingImport: MutableSet<String> = mutableSetOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleAlpIntent(intent)

        setContent {
            val config by SettingsManager.themeConfig.collectAsState()
            DeluaTheme(config = config) {
                val navController = rememberNavController()
                DeluaNavHost(
                    navController = navController,
                    pendingImportPaths = pendingImport.toList().also { pendingImport.clear() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAlpIntent(intent)
    }

    private fun handleAlpIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        val path = resolveAlpPath(uri) ?: return
        val alpDir = AlpManager(this).import(path)
        if (alpDir != null) {
            Log.d("MainActivity", "imported .alp to $alpDir")
            pendingImport.add(path)
        }
    }

    private fun resolveAlpPath(uri: Uri): String? {
        return when (uri.scheme) {
            "file" -> uri.path
            "content" -> {
                try {
                    val cursor = contentResolver.query(uri, arrayOf("_data"), null, null, null)
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val idx = it.getColumnIndex("_data")
                            if (idx >= 0) return it.getString(idx)
                        }
                    }
                } catch (_: Exception) { }
                uri.path
            }
            else -> uri.path
        }
    }

    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(LocaleManager.wrap(base))
    }
}
