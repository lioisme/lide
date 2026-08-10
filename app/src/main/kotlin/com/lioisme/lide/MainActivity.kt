package com.lioisme.lide

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.androlua.LuaApplication
import com.lioisme.lide.engine.EngineActivity
import com.lioisme.lide.engine.RunEngineLauncher
import com.lioisme.lide.i18n.LocaleManager
import com.lioisme.lide.model.PendingOpenFile
import com.lioisme.lide.model.Project
import com.lioisme.lide.project.AlpManager
import com.lioisme.lide.project.ProjectManager
import com.lioisme.lide.ui.navigation.LideNavHost
import com.lioisme.lide.ui.screen.InstallMethodDialog
import com.lioisme.lide.ui.theme.LideTheme
import com.lioisme.lide.ui.theme.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
        enableEdgeToEdge()

        handleLaunchIntent(intent)

        setContent {
            val config by SettingsManager.themeConfig.collectAsState()
            LideTheme(config = config) {
                val navController = rememberNavController()
                // 一次性消费语义：组合期读取并立即清空（与旧 pendingImport.clear() 等价）；
                // 异步完成后到达的意图产物经 state 变更触发重组，仍会被本次读取消费。
                val imports = pendingImportPaths
                pendingImportPaths = emptyList()
                val openFiles = pendingOpenFiles
                pendingOpenFiles = emptyList()
                LideNavHost(
                    navController = navController,
                    pendingImportPaths = imports,
                    pendingOpenFiles = openFiles,
                )
                // 系统「用 LIDE 打开 APK」（T16）：一次性消费，展示安装方式选择对话框
                //（普通系统安装器 / Shizuku / Dhizuku / Root）。
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

        val path = resolveFilePath(uri) ?: return
        when {
            path.endsWith(".alp", ignoreCase = true) -> {
                // 解压落盘在 IO 线程（P2#11），完成后经 state 通知列表刷新。
                lifecycleScope.launch {
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
            }

            path.endsWith(".lua", ignoreCase = true) -> {
                openOrAdoptLuaFile(path)
            }

            // APK 安装（2026-08-10 T16）：系统「打开方式」将 APK 交给 LIDE 时展示
            // 安装方式选择（普通系统安装器 / Shizuku / Dhizuku / Root 静默安装）。
            path.endsWith(".apk", ignoreCase = true) -> {
                Log.d("act", "handleLaunchIntent() — apk install: $path")
                pendingApkFile = File(path)
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
        val target = File(LuaApplication.getInstance().luaExtDir, relPath)
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
            val projectBase = File(LuaApplication.getInstance().luaExtDir, "project")
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
            val packageName = "com.lioisme.lide.$baseName"
            File(projectDir, "init.lua").writeText(ProjectManager.initLuaTemplate(baseName, packageName))
            PendingOpenFile(projectDir.absolutePath, file.name)
        } catch (e: Exception) {
            Log.e("MainActivity", "adopt standalone lua failed: ${e.message}")
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
                } catch (_: Exception) {
                    uri.path
                }
            }

            else -> {
                uri.path
            }
        }

    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(LocaleManager.wrap(base))
    }
}
