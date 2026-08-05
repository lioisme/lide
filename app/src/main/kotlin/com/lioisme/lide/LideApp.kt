package com.lioisme.lide

import com.androlua.LuaApplication
import com.lioisme.lide.editor.TextMateRuntime
import com.lioisme.lide.i18n.LocaleManager
import com.lioisme.lide.ui.theme.SettingsManager
import java.io.File
import java.io.FileOutputStream

class LideApp : LuaApplication() {
    companion object {
        private const val TAG = "LideApp"
    }

    override fun onCreate() {
        super.onCreate()
        SettingsManager.init(this)
        LocaleManager.init(this)
        // 资产提取移出主线程（P3-1 冷启动卫生）：三项均为幂等首启复制（已存在即跳过），
        // 后台完成后由各功能惰性消费，不阻塞 Application.onCreate 的首帧关键路径。
        bootstrapAssets()
        // TextMate 初始化有进程级一次性契约且禁止在 UI 线程调用（AGENTS.md 编辑器约定），
        // 经 TextMateRuntime 单一入口后台预热（C-4，幂等；失败进入 Failed 态供编辑器展示错误）。
        TextMateRuntime.preload(applicationContext)
    }

    /** 资产提取（后台线程）：示例插件 / 布局助手模板 / luadoc 离线文档。 */
    private fun bootstrapAssets() {
        Thread {
            extractExamplePlugin()
            extractLayoutTemplates()
            extractLuadoc()
        }.apply {
            name = "LideAppAssets"
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
                    "print(\"context: \" .. tostring(luadir) .. \" / \" .. tostring(luapath))\n",
            )
        } catch (e: Exception) {
            android.util.Log.w(TAG, "extractExamplePlugin failed", e)
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
            android.util.Log.w(TAG, "extractLayoutTemplates failed", e)
        }
    }

    /** 递归复制 assets 目录。 */
    private fun copyAssetDir(
        assetDir: String,
        outDir: File,
    ) {
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

    private fun copyAsset(
        assetPath: String,
        outFile: File,
    ) {
        try {
            assets.open(assetPath).use { input ->
                outFile.parentFile?.mkdirs()
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (_: Exception) {
            // 有意静默：可选资产（如 layouts/luadoc 目录缺失）缺省即跳过，由上层尽力而为
            // 契约兜底（AGENTS.md 降级约定），调用方无感知，不产生日志噪声。
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
            android.util.Log.w(TAG, "extractLuadoc failed", e)
        }
    }
}
