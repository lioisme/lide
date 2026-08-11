package com.lioisme.lide

import com.androlua.LuaApplication
import com.lioisme.lide.build.SigningManager
import com.lioisme.lide.editor.EditorPalette
import com.lioisme.lide.editor.TextMateRuntime
import com.lioisme.lide.i18n.LocaleManager
import com.lioisme.lide.install.InstallerServices
import com.lioisme.lide.ui.theme.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class LideApp : LuaApplication() {
    companion object {
        private const val TAG = "LideApp"
    }

    /** 应用级后台任务 scope：资产提取等幂等后台工作，独立于 Activity 生命周期。 */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        SettingsManager.init(this)
        SigningManager.init(this)
        LocaleManager.init(this)
        // 静默安装三后端（Root/Shizuku/Dhizuku）注册监听并后台刷新状态；
        // 幂等：重复调用不重置（SILENT_INSTALL，2026-08-09）。
        InstallerServices.init(this)
        // 资产提取移出主线程（P3-1 冷启动卫生）：三项均为幂等首启复制（已存在即跳过），
        // 后台完成后由各功能惰性消费，不阻塞 Application.onCreate 的首帧关键路径。
        bootstrapAssets()
        // TextMate 初始化有进程级一次性契约且禁止在 UI 线程调用（AGENTS.md 编辑器约定），
        // 经 TextMateRuntime 单一入口后台预热（C-4，幂等；失败进入 Failed 态供编辑器展示错误）。
        // 初始主题名按「当前方案 + 当前系统明暗」传入（R4：运行时不读宿主状态），
        // 编辑器打开后由 LuaEditor 按实际配置接管。
        val isSystemDark =
            resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        val initialThemeName =
            EditorPalette.themeNameFor(
                SettingsManager.editorConfig.value.themeScheme,
                isSystemDark,
            )
        TextMateRuntime.preload(applicationContext, initialThemeName)
    }

    /** 资产提取（后台协程）：布局助手模板（示例工程由 :app:generateExamples 生成）。 */
    private fun bootstrapAssets() {
        appScope.launch {
            extractLayoutTemplates()
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        LocaleManager.init(this)
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
            // 有意静默：可选资产（如 layouts 目录缺失）缺省即跳过，由上层尽力而为
            // 契约兜底（AGENTS.md 降级约定），调用方无感知，不产生日志噪声。
        }
    }
}
