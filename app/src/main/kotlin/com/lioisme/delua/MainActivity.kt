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
import com.androlua.LuaApplication
import com.lioisme.delua.androlua.AlpManager
import com.lioisme.delua.androlua.AlpManager.AlpKind
import com.lioisme.delua.engine.RunEngineLauncher
import com.lioisme.delua.i18n.LocaleManager
import com.lioisme.delua.ui.navigation.DeluaNavHost
import com.lioisme.delua.ui.theme.DeluaTheme
import com.lioisme.delua.ui.theme.SettingsManager
import java.io.File

class MainActivity : ComponentActivity() {

    private val pendingImport: MutableSet<String> = mutableSetOf()
    private val pendingOpenFiles: MutableList<Pair<String, String>> = mutableListOf()
    private var pendingPluginImport: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleLaunchIntent(intent)

        setContent {
            val config by SettingsManager.themeConfig.collectAsState()
            DeluaTheme(config = config) {
                val navController = rememberNavController()
                DeluaNavHost(
                    navController = navController,
                    pendingImportPaths = pendingImport.toList().also { pendingImport.clear() },
                    pendingOpenFiles = pendingOpenFiles.toList().also { pendingOpenFiles.clear() },
                    pendingPluginImport = pendingPluginImport.also { pendingPluginImport = false },
                )
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
                val result = AlpManager(this).import(path)
                if (result != null) {
                    Log.d("MainActivity", "imported .alp to ${result.dir} (${result.kind})")
                    if (result.kind == AlpKind.PLUGIN) {
                        pendingPluginImport = true
                    } else {
                        pendingImport.add(path)
                    }
                }
            }
            path.endsWith(".lua", ignoreCase = true) -> openOrAdoptLuaFile(path)
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
            val main = File(target, "main.lua")
            if (main.isFile) {
                RunEngineLauncher(this).launch(target.absolutePath, "main.lua")
            }
        } else if (target.isFile && target.name.endsWith(".lua", ignoreCase = true)) {
            if (target.name.equals("main.lua", ignoreCase = true)) {
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
     */
    private fun openOrAdoptLuaFile(path: String) {
        val file = File(path)
        if (!file.isFile) return
        val projectDir = findProjectDir(file)
        if (projectDir != null) {
            val relative = file.absolutePath.substring(projectDir.absolutePath.length + 1)
            pendingOpenFiles.add(projectDir.absolutePath to relative)
            return
        }
        val adopted = adoptStandaloneFile(file)
        if (adopted != null) {
            pendingOpenFiles.add(adopted.first to adopted.second)
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

    private fun adoptStandaloneFile(file: File): Pair<String, String>? {
        return try {
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
            val packageName = "com.lioisme.delua.$baseName"
            File(projectDir, "init.lua").writeText(
                """
                appname = "$baseName"
                appver = "1.0"
                packagename = "$packageName"
                icon = nil
                pages = {"main"}
                user_permission = {}
                dependencies = {
                    libs = {},
                    modules = {}
                }
                """.trimIndent()
            )
            projectDir.absolutePath to file.name
        } catch (e: Exception) {
            Log.e("MainActivity", "adopt standalone lua failed: ${e.message}")
            null
        }
    }

    private fun resolveFilePath(uri: Uri): String? {
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
                } catch (_: Exception) {
                }
                uri.path
            }
            else -> uri.path
        }
    }

    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(LocaleManager.wrap(base))
    }
}
