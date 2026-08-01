package com.lioisme.delua.ui.screen

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.lioisme.delua.R
import com.lioisme.delua.androlua.LuaConfigParser
import com.lioisme.delua.build.ApkBuilder
import com.lioisme.delua.model.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyFactory
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec

private data class BuildState(
    val appName: String,
    val packageName: String,
    val version: String,
    val versionCode: String,
    val permissionCount: Int,
)

private sealed interface BuildResult {
    data class Success(val apkFile: File) : BuildResult
    data class Failure(val message: String) : BuildResult
}

/**
 * 打包页面（WP-005）：读取 init.lua 配置 → 宿主 APK + 工程 Lua 装配
 * （main.lua 编译字节码、manifest 补丁、v1 签名）→ 写盘并分享。
 *
 * 构建在 Dispatchers.IO 执行；编译错误（含行号）与装配错误经 [BuildResult.Failure]
 * 展示；成功后可经 FileProvider 分享安装。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildApkScreen(
    project: Project,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<BuildState?>(null) }
    var building by remember { mutableStateOf(false) }
    var buildStage by remember { mutableStateOf<ApkBuilder.BuildStage?>(null) }
    var result by remember { mutableStateOf<BuildResult?>(null) }
    val strLoadFailed = stringResource(R.string.info_load_failed)
    val strBuildFail = stringResource(R.string.build_fail)
    val strNeedPackage = stringResource(R.string.build_need_package)
    val strBuildSuccess = stringResource(R.string.build_success)

    LaunchedEffect(project) {
        state = withContext(Dispatchers.IO) {
            val file = File(project.path, "init.lua")
            if (!file.exists()) {
                null
            } else {
                val content = file.readText()
                BuildState(
                    appName = LuaConfigParser.stringField(content, "appname") ?: project.name,
                    packageName = LuaConfigParser.stringField(content, "packagename").orEmpty(),
                    version = LuaConfigParser.stringField(content, "appver") ?: "1.0",
                    versionCode = LuaConfigParser.stringField(content, "appcode") ?: "1",
                    permissionCount = LuaConfigParser.stringArrayField(content, "user_permission").size,
                )
            }
        }
        if (state == null) {
            Toast.makeText(context, strLoadFailed, Toast.LENGTH_SHORT).show()
            onBack()
        }
    }

    fun startBuild(s: BuildState) {
        if (s.packageName.isBlank()) {
            Toast.makeText(context, strNeedPackage, Toast.LENGTH_SHORT).show()
            return
        }
        building = true
        buildStage = null
        result = null
        scope.launch {
            val res = withContext(Dispatchers.IO) {
                doBuild(context, project, s) { stage -> buildStage = stage }
            }
            building = false
            buildStage = null
            result = res
            when (res) {
                is BuildResult.Success -> {
                    Toast.makeText(context, strBuildSuccess, Toast.LENGTH_SHORT).show()
                    // 与 AndroLua Pro 一致：打包完成后自动调起系统安装器。
                    installApk(context, res.apkFile)
                }
                is BuildResult.Failure ->
                    Toast.makeText(context, String.format(strBuildFail, res.message), Toast.LENGTH_LONG).show()
            }
        }
    }

    // 构建进度对话框：阶段文本 + 不确定进度条，不可取消。
    if (building) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.build_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        stringResource(stageLabelRes(buildStage)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.build_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        }
    ) { padding ->
        val s = state
        if (s == null) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text(stringResource(R.string.build_summary_group), fontWeight = FontWeight.Medium) }
            item { Text(stringResource(R.string.build_hint), style = MaterialTheme.typography.bodySmall) }
            item {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(stringResource(R.string.build_appname))
                        Text(s.appName, style = MaterialTheme.typography.titleMedium)
                        HorizontalDivider()
                        Text(stringResource(R.string.build_package))
                        Text(
                            s.packageName.ifBlank { project.name },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        HorizontalDivider()
                        Text(stringResource(R.string.build_version, s.version, s.versionCode))
                        HorizontalDivider()
                        Text(stringResource(R.string.build_permissions, s.permissionCount))
                    }
                }
            }
            when (val r = result) {
                is BuildResult.Failure -> item {
                    Text(
                        r.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                is BuildResult.Success -> item {
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            r.apkFile.absolutePath,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                null -> Unit
            }
            item { Spacer(Modifier.height(8.dp)) }
            item {
                Button(
                    onClick = { startBuild(s) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !building,
                ) {
                    if (building) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.build_working))
                    } else {
                        Text(stringResource(R.string.build_button))
                    }
                }
            }
            if (result is BuildResult.Success && !building) {
                item {
                    OutlinedButton(
                        onClick = {
                            val apk = (result as BuildResult.Success).apkFile
                            shareApk(context, apk)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.build_share))
                    }
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

/** 装配 + 签名 + 写盘（IO 线程）。失败返回可读信息（编译错误带行号）。 */
private fun doBuild(
    context: android.content.Context,
    project: Project,
    s: BuildState,
    onStage: (ApkBuilder.BuildStage) -> Unit,
): BuildResult {
    return try {
        onStage(ApkBuilder.BuildStage.READ_HOST)
        val pm = context.packageManager
        val sourceDir = pm.getApplicationInfo(context.packageName, 0).sourceDir
        val hostApk = File(sourceDir).readBytes()

        val keyBytes = context.assets.open("keys/testkey.pk8").use { it.readBytes() }
        val pemBytes = context.assets.open("keys/testkey.x509.pem").use { it.readBytes() }
        val key = KeyFactory.getInstance("RSA")
            .generatePrivate(PKCS8EncodedKeySpec(keyBytes))
        val cert = CertificateFactory.getInstance("X.509")
            .generateCertificate(pemBytes.inputStream()) as java.security.cert.X509Certificate

        onStage(ApkBuilder.BuildStage.COLLECT_LUA)
        val sources = LinkedHashMap<String, ByteArray>()
        collectLuaFiles(File(project.path), "", sources)

        val input = ApkBuilder.ProjectInput(
            packageName = s.packageName,
            appName = s.appName,
            versionName = s.version,
            versionCode = s.versionCode.toIntOrNull() ?: 1,
            declaredPermissions = declaredPermissions(project),
            luaSources = sources,
        )
        val apkBytes = ApkBuilder.build(hostApk, input, key, cert, onStage = onStage)

        // 产物位置与 AndroLua Pro 一致：<luaExtDir>/apk/（即 /storage/emulated/0/AndroLua/apk/）。
        val luaExtDir = com.androlua.LuaApplication.getInstance().getLuaExtDir()
        val outDir = File(luaExtDir, "apk").apply { mkdirs() }
        val fileName = "${sanitizeName(s.packageName)}-${sanitizeName(s.version)}.apk"
        val outFile = File(outDir, fileName)
        outFile.writeBytes(apkBytes)
        BuildResult.Success(outFile)
    } catch (e: Exception) {
        BuildResult.Failure(e.message ?: e.javaClass.simpleName)
    }
}

/** 构建阶段 → 文案资源。 */
private fun stageLabelRes(stage: ApkBuilder.BuildStage?): Int = when (stage) {
    null, ApkBuilder.BuildStage.READ_HOST -> R.string.build_stage_read_host
    ApkBuilder.BuildStage.COLLECT_LUA -> R.string.build_stage_collect
    ApkBuilder.BuildStage.COMPILE_LUA -> R.string.build_stage_compile
    ApkBuilder.BuildStage.ASSEMBLE -> R.string.build_stage_assemble
    ApkBuilder.BuildStage.SIGN -> R.string.build_stage_sign
}

/**
 * 自动安装：ACTION_INSTALL_PACKAGE 直连系统安装器（InstallStart）。
 * 不用 ACTION_VIEW——宿主自身的 .alp/.lua 导入过滤会匹配任何 content:// 文件，
 * 会弹出"打开方式"选择器而非直接安装。
 * 未授予"安装未知来源应用"时系统会先弹授权页，授予后直达安装确认。
 */
private fun installApk(context: android.content.Context, apkFile: File) {
    try {
        val uri: Uri = FileProvider.getUriForFile(context, context.packageName, apkFile)
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, e.message ?: "install failed", Toast.LENGTH_LONG).show()
    }
}

private fun declaredPermissions(project: Project): List<String> = try {
    val initFile = File(project.path, "init.lua")
    if (initFile.exists()) {
        com.lioisme.delua.androlua.PermissionResolver.declared(initFile.readText())
    } else emptyList()
} catch (_: Exception) {
    emptyList()
}

private fun collectLuaFiles(dir: File, relPrefix: String, out: MutableMap<String, ByteArray>) {
    val entries = dir.listFiles() ?: return
    for (entry in entries) {
        val rel = if (relPrefix.isEmpty()) entry.name else "$relPrefix/${entry.name}"
        if (entry.isDirectory) {
            collectLuaFiles(entry, rel, out)
        } else if (entry.isFile && entry.name.endsWith(".lua", ignoreCase = true)) {
            out[rel] = entry.readBytes()
        }
    }
}

private fun sanitizeName(s: String): String =
    s.replace(Regex("[^A-Za-z0-9_.-]"), "_").take(60)

private fun shareApk(context: android.content.Context, apkFile: File) {
    try {
        val uri: Uri = FileProvider.getUriForFile(context, context.packageName, apkFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, null))
    } catch (e: Exception) {
        Toast.makeText(context, e.message ?: "share failed", Toast.LENGTH_LONG).show()
    }
}
