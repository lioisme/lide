package com.lioisme.lide.ui.screen

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
import com.lioisme.lide.R
import com.lioisme.lide.androlua.LuaConfigParser
import com.lioisme.lide.build.ApkBuilder
import com.lioisme.lide.editor.TipIconButton
import com.lioisme.lide.model.Project
import com.lioisme.lide.project.ProjectManager
import com.lioisme.lide.ui.theme.LideTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
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
    data class Success(
        val apkFile: File,
    ) : BuildResult

    data class Failure(
        val message: String,
    ) : BuildResult
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
    projectManager: ProjectManager,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<BuildState?>(null) }
    var building by remember { mutableStateOf(false) }
    var buildStage by remember { mutableStateOf<ApkBuilder.BuildStage?>(null) }
    var result by remember { mutableStateOf<BuildResult?>(null) }
    // 构建阶段回调经 Channel 桥回主线程（P2#10）：doBuild 在 IO 线程内回调 onStage，
    // 若直接写 buildStage 属 IO 线程写 Compose state，违反主线程约定；Channel 由下方
    // LaunchedEffect（Main）消费，BUFFERED 避免回调侧背压（trySend 永不挂起）。
    val stageChannel = remember { Channel<ApkBuilder.BuildStage>(Channel.BUFFERED) }
    LaunchedEffect(stageChannel) {
        for (stage in stageChannel) buildStage = stage
    }
    val strLoadFailed = stringResource(R.string.info_load_failed)
    val strBuildFail = stringResource(R.string.build_fail)
    val strNeedPackage = stringResource(R.string.build_need_package)
    val strBuildSuccess = stringResource(R.string.build_success)

    LaunchedEffect(project) {
        state =
            withContext(Dispatchers.IO) {
                // B-1：init.lua 读写收敛到 ProjectManager 域服务，UI 不自摸文件。
                val content = projectManager.readInitContent(project) ?: return@withContext null
                BuildState(
                    appName = LuaConfigParser.stringField(content, "appname") ?: project.name,
                    packageName = LuaConfigParser.stringField(content, "packagename").orEmpty(),
                    version = LuaConfigParser.stringField(content, "appver") ?: "1.0",
                    versionCode = LuaConfigParser.stringField(content, "appcode") ?: "1",
                    permissionCount = LuaConfigParser.stringArrayField(content, "user_permission").size,
                )
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
            val res =
                withContext(Dispatchers.IO) {
                    doBuild(context, project, projectManager, s) { stage -> stageChannel.trySend(stage) }
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

                is BuildResult.Failure -> {
                    Toast.makeText(context, String.format(strBuildFail, res.message), Toast.LENGTH_LONG).show()
                }
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
            LideTopBar(
                title = stringResource(R.string.build_title),
                onBack = onBack,
            )
        },
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
                is BuildResult.Failure -> {
                    item {
                        Text(
                            r.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                is BuildResult.Success -> {
                    item {
                        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                r.apkFile.absolutePath,
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                null -> {
                    Unit
                }
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
    projectManager: ProjectManager,
    s: BuildState,
    onStage: (ApkBuilder.BuildStage) -> Unit,
): BuildResult =
    try {
        onStage(ApkBuilder.BuildStage.READ_HOST)
        val pm = context.packageManager
        val sourceDir = pm.getApplicationInfo(context.packageName, 0).sourceDir
        val hostApk = File(sourceDir).readBytes()

        val keyBytes = context.assets.open("keys/testkey.pk8").use { it.readBytes() }
        val pemBytes = context.assets.open("keys/testkey.x509.pem").use { it.readBytes() }
        val key =
            KeyFactory
                .getInstance("RSA")
                .generatePrivate(PKCS8EncodedKeySpec(keyBytes))
        val cert =
            CertificateFactory
                .getInstance("X.509")
                .generateCertificate(pemBytes.inputStream()) as java.security.cert.X509Certificate

        onStage(ApkBuilder.BuildStage.COLLECT_LUA)
        val projectFiles = LinkedHashMap<String, ByteArray>()
        val usingFiles = ArrayList<File>()
        collectProjectFiles(File(project.path), "", projectFiles, usingFiles)
        val engineLibs = engineLuaLibs(projectFiles, usingFiles, hostLuaAssets(context))

        val input =
            ApkBuilder.ProjectInput(
                packageName = s.packageName,
                appName = s.appName,
                versionName = s.version,
                versionCode = s.versionCode.toIntOrNull() ?: 1,
                declaredPermissions = projectManager.loadUserPermissions(project),
                luaSources = projectFiles,
                engineLuaLibs = engineLibs,
            )
        val apkBytes = ApkBuilder.build(hostApk, input, key, cert, onStage = onStage)

        // 产物位置与 AndroLua Pro 一致：<luaExtDir>/apk/（即 /storage/emulated/0/AndroLua/apk/）。
        val luaExtDir =
            com.androlua.LuaApplication
                .getInstance()
                .getLuaExtDir()
        val outDir = File(luaExtDir, "apk").apply { mkdirs() }
        val fileName = "${sanitizeName(s.packageName)}-${sanitizeName(s.version)}.apk"
        val outFile = File(outDir, fileName)
        outFile.writeBytes(apkBytes)
        BuildResult.Success(outFile)
    } catch (e: Exception) {
        BuildResult.Failure(e.message ?: e.javaClass.simpleName)
    }

/** 构建阶段 → 文案资源。 */
private fun stageLabelRes(stage: ApkBuilder.BuildStage?): Int =
    when (stage) {
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
private fun installApk(
    context: android.content.Context,
    apkFile: File,
) {
    try {
        val uri: Uri = FileProvider.getUriForFile(context, context.packageName, apkFile)
        val intent =
            Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, e.message ?: context.getString(R.string.build_install_failed), Toast.LENGTH_LONG).show()
    }
}

/**
 * 收集工程目录全部文件（注入 assets/lua/<相对路径>，打包后即工程目录）：
 * 跳过隐藏项（`.` 开头）与中间产物（.apk/.luac/.odex/.vdex，pro 口径）。
 * `.using` 文件不打包，但内容送入 [usingFiles] 参与依赖扫描。
 */
internal fun collectProjectFiles(
    dir: File,
    relPrefix: String,
    out: MutableMap<String, ByteArray>,
    usingFiles: MutableList<File>,
) {
    val entries = dir.listFiles() ?: return
    for (entry in entries) {
        val name = entry.name
        if (entry.isFile && name == ".using") {
            usingFiles += entry
            continue
        }
        if (name.startsWith(".")) continue
        if (entry.isFile) {
            val lower = name.lowercase()
            if (lower.endsWith(".apk") || lower.endsWith(".luac") ||
                lower.endsWith(".odex") || lower.endsWith(".vdex")
            ) {
                continue
            }
        }
        val rel = if (relPrefix.isEmpty()) name else "$relPrefix/$name"
        if (entry.isDirectory) {
            collectProjectFiles(entry, rel, out, usingFiles)
        } else if (entry.isFile) {
            out[rel] = entry.readBytes()
        }
    }
}

/** 打包必须保留的宿主引擎 Lua 库（import.lua 无条件加载这三者，permission 供权限提示）。 */
private val ENGINE_LUA_WHITELIST =
    setOf(
        "import.lua",
        "loadlayout.lua",
        "loadlayout2.lua",
        "loadlayout3.lua",
        "loadbitmap.lua",
        "loadmenu.lua",
        "permission.lua",
    )

/** 宿主 assets/lua 下的全部库相对名（目录无点则递归，与 LuaApplication.extractLuaDir 同口径）。 */
private fun hostLuaAssets(context: android.content.Context): Set<String> {
    val out = HashSet<String>()

    fun walk(
        assetDir: String,
        relPrefix: String,
    ) {
        val names =
            try {
                context.assets.list(assetDir) ?: emptyArray()
            } catch (_: Throwable) {
                return
            }
        for (n in names) {
            val rel = if (relPrefix.isEmpty()) n else "$relPrefix/$n"
            if (n.contains('.')) out += rel else walk("$assetDir/$n", rel)
        }
    }
    walk("lua", "")
    return out
}

/**
 * 计算打包 APK 需要的宿主引擎 Lua 库：
 * 静态白名单 ∪ 工程源码/`.using` 中 `require "x"`、`import "x.y"` 命中的模块
 * （与 pro bin.lua 的 checklib 同口径；x.y → x/y.lua），
 * 过滤掉工程自带同名文件（工程优先）与宿主不存在的模块。
 */
internal fun engineLuaLibs(
    projectFiles: Map<String, ByteArray>,
    usingFiles: List<File>,
    hostLua: Set<String>,
): List<String> {
    val required = HashSet(ENGINE_LUA_WHITELIST)
    val srcs = ArrayList<String>(projectFiles.size + usingFiles.size)
    for ((rel, bytes) in projectFiles) {
        if (rel.endsWith(".lua", ignoreCase = true)) {
            srcs += String(bytes, Charsets.UTF_8)
        }
    }
    for (f in usingFiles) {
        try {
            srcs += f.readText()
        } catch (_: Throwable) {
        }
    }
    val re = Regex("(?:require|import)\\s*\\(?\\s*['\"]([A-Za-z0-9_]+)\\.?([A-Za-z0-9_]*)['\"]")
    for (src in srcs) {
        for (m in re.findAll(src)) {
            val a = m.groupValues[1]
            val b = m.groupValues[2]
            required += if (b.isEmpty()) "$a.lua" else "$a/$b.lua"
        }
    }
    val projectNames = projectFiles.keys.mapTo(HashSet()) { it.replace('\\', '/') }
    return required
        .filter { it !in projectNames && it in hostLua }
        .sorted()
}

private fun sanitizeName(s: String): String = s.replace(Regex("[^A-Za-z0-9_.-]"), "_").take(60)

private fun shareApk(
    context: android.content.Context,
    apkFile: File,
) {
    try {
        val uri: Uri = FileProvider.getUriForFile(context, context.packageName, apkFile)
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        context.startActivity(Intent.createChooser(intent, null))
    } catch (e: Exception) {
        Toast.makeText(context, e.message ?: context.getString(R.string.build_share_failed), Toast.LENGTH_LONG).show()
    }
}
