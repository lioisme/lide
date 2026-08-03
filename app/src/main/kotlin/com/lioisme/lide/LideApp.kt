package com.lioisme.lide

import com.androlua.LuaApplication
import com.lioisme.lide.editor.TextMateInitializer
import com.lioisme.lide.i18n.LocaleManager
import com.lioisme.lide.ui.theme.SettingsManager
import java.io.File
import java.io.FileOutputStream

class LideApp : LuaApplication() {
    override fun onCreate() {
        super.onCreate()
        SettingsManager.init(this)
        LocaleManager.init(this)
        extractExamplePlugin()
        extractLayoutTemplates()
        extractLuadoc()
        preloadTextMate()
    }

    private fun preloadTextMate() {
        Thread {
            TextMateInitializer.initialize(applicationContext)
        }.apply {
            name = "TextMatePreload"
            isDaemon = true
        }.start()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        LocaleManager.init(this)
    }

    /** 示例插件（G4 收口）：asset 中的 plugin.json 配套 main.lua 提取到 plugin/example/，已存在即跳过。 */
    private fun extractExamplePlugin() {
        try {
            val outDir = File(luaExtDir, "plugin/example")
            if (outDir.exists()) return
            outDir.mkdirs()
            copyAsset("plugin/plugin.json", File(outDir, "plugin.json"))
            File(outDir, "main.lua").writeText(
                "-- Example plugin entry\n" +
                    "local luadir, luapath = ...\n" +
                    "print(\"Hello from the example plugin!\")\n" +
                    "print(\"context: \" .. tostring(luadir) .. \" / \" .. tostring(luapath))\n"
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** 布局助手模板（pro 的 javaapi 目录 .aly 文件）：提取到 filesDir/layouts/，已存在即跳过。 */
    private fun extractLayoutTemplates() {
        try {
            val outDir = File(filesDir, "layouts")
            if (outDir.exists() && outDir.listFiles()?.isNotEmpty() == true) return
            outDir.mkdirs()
            copyAssetDir("layouts", outDir)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** 递归复制 assets 目录。 */
    private fun copyAssetDir(assetDir: String, outDir: File) {
        val entries = assets.list(assetDir) ?: return
        outDir.mkdirs()
        for (entry in entries) {
            val assetPath = "$assetDir/$entry"
            val outFile = File(outDir, entry)
            val subEntries = assets.list(assetPath)
            if (subEntries != null && subEntries.isNotEmpty()) {
                copyAssetDir(assetPath, outFile)
            } else {
                copyAsset(assetPath, outFile)
            }
        }
    }

    private fun copyAsset(assetPath: String, outFile: File) {
        try {
            assets.open(assetPath).use { input ->
                outFile.parentFile?.mkdirs()
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun extractLuadoc() {
        try {
            val outDir = File(filesDir, "luadoc")
            outDir.mkdirs()
            val names = assets.list("luadoc") ?: return
            for (name in names) {
                val outFile = File(outDir, name)
                if (outFile.exists() && outFile.length() > 0) continue
                copyAsset("luadoc/$name", outFile)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
