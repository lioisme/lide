package com.lioisme.delua

import com.androlua.LuaApplication
import com.lioisme.delua.i18n.LocaleManager
import com.lioisme.delua.ui.theme.SettingsManager
import java.io.File
import java.io.FileOutputStream

class DeluaApp : LuaApplication() {
    override fun onCreate() {
        super.onCreate()
        SettingsManager.init(this)
        LocaleManager.init(this)
        com.lioisme.delua.androlua.common.DependencyManager(this).ensureDirsExist()
        extractSamples()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        LocaleManager.init(this)
    }

    private fun extractSamples() {
        val projectDir = File(luaExtDir, "project")
        projectDir.mkdirs()
        try {
            val sampleNames = assets.list("samples") ?: return
            for (sampleName in sampleNames) {
                val outDir = File(projectDir, sampleName)
                outDir.mkdirs()
                val files = assets.list("samples/$sampleName") ?: continue
                for (name in files) {
                    val subFiles = assets.list("samples/$sampleName/$name")
                    if (subFiles != null && subFiles.isNotEmpty()) {
                        val subDir = File(outDir, name)
                        subDir.mkdirs()
                        for (subName in subFiles) {
                            copyAsset("samples/$sampleName/$name/$subName", File(subDir, subName))
                        }
                    } else {
                        copyAsset("samples/$sampleName/$name", File(outDir, name))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun copyAsset(assetPath: String, outFile: File) {
        try {
            assets.open(assetPath).use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (_: Exception) {
        }
    }
}
