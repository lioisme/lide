package com.lioisme.lide

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.lioisme.lide.engine.EngineActivity
import com.lioisme.lide.engine.RunEngineLauncher
import com.lioisme.lide.engine.RuntimePaths
import com.lioisme.lide.i18n.LocaleManager
import com.lioisme.lide.model.PendingOpenFile
import com.lioisme.lide.model.Project
import com.lioisme.lide.project.AlpManager
import com.lioisme.lide.project.ProjectEntryOps
import com.lioisme.lide.project.ProjectManager
import com.lioisme.lide.ui.navigation.LideNavHost
import com.lioisme.lide.ui.screen.InstallMethodDialog
import com.lioisme.lide.ui.theme.AppearanceMode
import com.lioisme.lide.ui.theme.LideTheme
import com.lioisme.lide.ui.theme.LocalIsDarkTheme
import com.lioisme.lide.ui.theme.SettingsManager
import com.lioisme.lide.ui.theme.resolveColorSchemeNow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger("MainActivity")

class MainActivity : ComponentActivity() {

    // 启动意图产物以 Compose state 承载（P2#11 修复）：.alp 解压导入 / 独立 .lua 收养
    // 原在 onCreate/onNewIntent 主线程同步执行（冷启动与点按卡顿），现移入
    // lifecycleScope + Dispatchers.IO，完成后写 state 触发重组。
    // 附带修复：onNewIntent 在 setContent 之后到达时，旧实现（plain MutableSet/List）
    // 追加的条目永远不会被组合消费——state 承载后迟到条目仍会被读取。
    private var pendingImportPaths by mutableStateOf<List<String>>(emptyList())
    private var pendingOpenFiles by mutableStateOf<List<PendingOpenFile>>(emptyList())

    // 系统「用 LIDE 打开 APK」→ 安装方式选择（T16，2026-08-10）。
    private var pendingApkFile by mutableStateOf<File?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 打包模式（WP-005 产物）：运行时包名已被补丁为工程 packagename，与宿主
        // BuildConfig 包名不一致 → 直入引擎页（无 projectDir → EngineActivity 回退
        // filesDir/lua 运行 assets 内工程）。宿主安装包二者恒等，编辑器正常展示。
        // super.onCreate 必须先调用，否则 Android 会抛 SuperNotCalledException 杀进程。
        if (packageName != BuildConfig.APPLICATION_ID) {
            startActivity(Intent(this, EngineActivity::class.java))
            finish()
            return
        }
        enableEdgeToEdge(
            // T5B（2026-08-12）：系统栏图标明暗按**应用外观模式**显式指定而非
            // 系统深浅色——应用强制 LIGHT/DARK 时（Theme.kt resolveIsDark），
            // auto 模式会按系统 uiMode 给出相反图标色（浅底深色图标不可见）。
            // 外观模式切换不重建 Activity，setContent 内 LaunchedEffect(isDark)
            // 再同步一次（见下）；scrim 均透明（纯 edge-to-edge）。
            statusBarStyle = systemBarStyleFor(currentIsDark()),
            navigationBarStyle = systemBarStyleFor(currentIsDark()),
        )

        applyWindowBackground()

        handleLaunchIntent(intent)

        setContent {
            val config by SettingsManager.themeConfig.collectAsState()
            LideTheme(config = config) {
                val isDarkTheme = LocalIsDarkTheme.current
                // T5B 兜底：外观模式在应用内切换（设置页）不重建 Activity，
                // 此效应在 isDark 变化时同步系统栏图标明暗（幂等，重建后亦执行一次）。
                LaunchedEffect(isDarkTheme) {
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    controller.isAppearanceLightStatusBars = !isDarkTheme
                    controller.isAppearanceLightNavigationBars = !isDarkTheme
                }
                val navController = rememberNavController()
                // 一次性消费语义：组合期读取并立即清空（与旧 pendingImport.clear() 等价）；
                // 异步完成后到达的意图产物经 state 变更触发重组，仍会被本次读取消费。
                val imports = pendingImportPaths
                pendingImportPaths = emptyList()
                val openFiles = pendingOpenFiles
                LideNavHost(
                    navController = navController,
                    pendingImportPaths = imports,
                    pendingOpenFiles = openFiles,
                    onOpenFilesConsumed = { pendingOpenFiles = emptyList() },
                )
                // 系统「用 LIDE 打开 APK」（T16）：一次性消费，展示安装方式选择对话框
                // （普通系统安装器 / Shizuku / Dhizuku / Root）。
                val apk = pendingApkFile
                if (apk != null) {
                    InstallMethodDialog(
                        apkFile = apk,
                        onDismiss = { pendingApkFile = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleLaunchIntent(intent)
    }

    private fun handleLaunchIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW && intent?.action != Intent.ACTION_EDIT) return
        val uri = intent.data ?: return

        if (uri.scheme == "androlua") {
            handleAndroluaScheme(uri)
            return
        }

        // A4（2026-08-12）：URI 解析整体移 IO 线程——content:// 分支做 query +
        // openInputStream + 全量拷贝（大 .alp/.apk 可达百 MB），原在 onCreate/
        // onNewIntent 主线程同步执行，冷启动被系统拉起大文件时首帧卡死/ANR。
        // 文件类型分派与导入/收养同在线程，完成后写 state 触发重组。
        lifecycleScope.launch {
            val path = withContext(Dispatchers.IO) { resolveFilePath(uri) } ?: return@launch
            when {
                path.endsWith(".alp", ignoreCase = true) -> {
                    // 解压落盘在 IO 线程（P2#11），完成后经 state 通知列表刷新。
                    val result =
                        withContext(Dispatchers.IO) {
                            // WP-015：系统拉起属自动导入路径，冲突时改名（-1/-2）而非覆盖，
                            // 避免静默删除用户已有工程。
                            AlpManager(this@MainActivity).import(path, AlpManager.ImportMode.RENAME)
                        }
                    if (result != null) {
                        pendingImportPaths = pendingImportPaths + path
                    }
                }

                path.endsWith(".lua", ignoreCase = true) -> {
                    openOrAdoptLuaFile(path)
                }

                // APK 安装（2026-08-10 T16）：系统「打开方式」将 APK 交给 LIDE 时展示
                // 安装方式选择（普通系统安装器 / Shizuku / Dhizuku / Root 静默安装）。
                path.endsWith(".apk", ignoreCase = true) -> {
                    logger.debug { "handleLaunchIntent() — apk install: $path" }
                    pendingApkFile = File(path)
                }
            }
        }
    }

    /**
     * androlua://<host>/<luaExtDir 内相对路径>（pro 兼容格式）。
     * - 指向 main.lua → 直接拉起引擎运行（脚本拉起）
     * - 其他 .lua → 打开编辑；目录 → 取目录内 main.lua 运行
     */
    private fun handleAndroluaScheme(uri: Uri) {
        val relPath = uri.path?.trimStart('/') ?: return
        val extRoot = RuntimePaths.luaExtDir()
        val target = File(extRoot, relPath)
        // B5（2026-08-12）：containment 校验——`..` 段可越出引擎根读取/运行任意
        // .lua（androlua:// scheme 无签名保护，任意应用可发；引擎进程带宿主全量
        // 权限），归一化后目标必须位于 luaExtDir 之内，越界直接拒绝。
        if (!ProjectEntryOps.isInsideRoot(File(extRoot), target.absolutePath)) {
            logger.warn { "androlua:// rejected (target outside luaExtDir): $relPath" }
            return
        }
        if (target.isDirectory) {
            val main = File(target, Project.DEFAULT_MAIN_FILE)
            if (main.isFile) {
                RunEngineLauncher(this).launch(target.absolutePath, Project.DEFAULT_MAIN_FILE)
            }
        } else if (target.isFile && target.name.endsWith(".lua", ignoreCase = true)) {
            if (target.name.equals(Project.DEFAULT_MAIN_FILE, ignoreCase = true)) {
                target.parentFile?.let {
                    RunEngineLauncher(this).launch(it.absolutePath, target.name)
                }
            } else {
                openOrAdoptLuaFile(target.absolutePath)
            }
        }
    }

    /**
     * .lua 打开：若文件位于已知工程目录（含 init.lua 的祖先目录）→ 直接打开编辑；
     * 独立文件 → 收养为单文件工程（复制进 project/<name>/ 并生成 init.lua）再打开。
     * 文件树回溯与收养（拷贝 + init.lua 生成）在 IO 线程执行（P2#11）。
     */
    private fun openOrAdoptLuaFile(path: String) {
        val file = File(path)
        if (!file.isFile) return
        lifecycleScope.launch {
            val opened =
                withContext(Dispatchers.IO) {
                    val projectDir = findProjectDir(file)
                    if (projectDir != null) {
                        val relative = file.absolutePath.substring(projectDir.absolutePath.length + 1)
                        PendingOpenFile(projectDir.absolutePath, relative)
                    } else {
                        adoptStandaloneFile(file)
                    }
                }
            if (opened != null) {
                pendingOpenFiles = pendingOpenFiles + opened
            }
        }
    }

    private fun findProjectDir(file: File): File? {
        var dir: File? = file.parentFile ?: return null
        while (dir != null) {
            if (File(dir, "init.lua").isFile) return dir
            dir = dir.parentFile
        }
        return null
    }

    private fun adoptStandaloneFile(file: File): PendingOpenFile? =
        try {
            val baseName = file.nameWithoutExtension
            val projectBase = RuntimePaths.projectsDir()
            projectBase.mkdirs()
            var projectDir = File(projectBase, baseName)
            var counter = 2
            while (projectDir.exists()) {
                projectDir = File(projectBase, "$baseName-$counter")
                counter++
            }
            projectDir.mkdirs()
            val dest = File(projectDir, file.name)
            file.copyTo(dest, overwrite = true)
            // C7：包名生成单一事实源（ProjectManager.packageNameFor 清洗收敛）。
            val packageName = ProjectManager.packageNameFor(baseName)
            File(projectDir, "init.lua").writeText(ProjectManager.initLuaTemplate(baseName, packageName))
            PendingOpenFile(projectDir.absolutePath, file.name)
        } catch (e: Exception) {
            logger.error { "adopt standalone lua failed: ${e.message}" }
            null
        }

    /**
     * URI → 可读取的本地文件路径。
     *
     * `content://` 一律经 [openInputStream] 落盘到应用缓存（`_data` 列自
     * API 29 起对 SAF 文档恒为 null / 不可用），随后按扩展名消费；
     * `file://` 直接取路径；未知 scheme 回落 [Uri.getPath]。
     */
    private fun resolveFilePath(uri: Uri): String? =
        when (uri.scheme) {
            "file" -> {
                uri.path
            }

            "content" -> {
                try {
                    val displayName =
                        contentResolver.query(uri, null, null, null, null)?.use { c ->
                            if (c.moveToFirst()) {
                                c
                                    .getColumnIndex("_display_name")
                                    .takeIf { it >= 0 }
                                    ?.let { c.getString(it) }
                            } else {
                                null
                            }
                        }
                    val base = displayName?.substringAfterLast('.')?.lowercase() ?: "bin"
                    val file = File.createTempFile("incoming_", ".$base", cacheDir)
                    contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                    file.absolutePath
                } catch (e: Exception) {
                    // M3（2026-08-12）：解析/拷贝失败返回 null 而非回落 uri.path
                    // （/document/123 不是真实文件，后续按扩展名分派全部落空，
                    // 且会以无效路径进安装/导入流程）。
                    logger.warn(e) { "resolve content uri failed: $uri" }
                    null
                }
            }

            else -> {
                uri.path
            }
        }

    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(LocaleManager.wrap(base))
    }

    /**
     * 当前明暗（U4/T5B）：与 Compose 侧 [LideTheme] 判定同源——SYSTEM 跟随系统
     * uiMode，LIGHT/DARK 强制。MainActivity 非 Composable 上下文用，
     * 供窗口背景与系统栏样式计算。
     */
    private fun currentIsDark(): Boolean =
        when (SettingsManager.themeConfig.value.appearanceMode) {
            AppearanceMode.SYSTEM -> {
                (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
            }

            AppearanceMode.LIGHT -> {
                false
            }

            AppearanceMode.DARK -> {
                true
            }
        }

    /**
     * U4（2026-08-12）：窗口背景对齐主题 surface 色（HCT 同源计算，见
     * Theme.kt resolveColorSchemeNow）。Activity 重建（切换语言 per-app
     * locale / 外观模式）期间，窗口正文区域以本背景绘制，不闪默认白/黑帧；
     * 随后 Compose 内容同色接管。
     */
    private fun applyWindowBackground() {
        try {
            val config = SettingsManager.themeConfig.value
            val scheme = resolveColorSchemeNow(config, currentIsDark(), this)
            window.setBackgroundDrawable(scheme.surface.toArgb().toDrawable())
        } catch (_: Exception) {
            // 降级：背景设置失败保持系统默认（无害，仅 recreate 帧底色差异）。
        }
    }

    /**
     * T5B：系统栏样式映射（透明 scrim；浅色 = 深色图标，深色 = 浅色图标）。
     */
    private fun systemBarStyleFor(isDark: Boolean): SystemBarStyle =
        if (isDark) {
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        }
}
