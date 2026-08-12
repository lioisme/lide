package com.lioisme.lide.ui.screen

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.lioisme.lide.R
import com.lioisme.lide.build.AfterBuild
import com.lioisme.lide.build.ApkBuilder
import com.lioisme.lide.build.AXmlManifest
import com.lioisme.lide.build.BuildErrorCode
import com.lioisme.lide.build.DependencyAnalysis
import com.lioisme.lide.build.NativeDependencyAnalyzer
import com.lioisme.lide.build.SigningManager
import com.lioisme.lide.build.ZipUtils
import com.lioisme.lide.engine.RuntimePaths
import com.lioisme.lide.install.InstallerBackend
import com.lioisme.lide.install.InstallerCoordinator
import com.lioisme.lide.model.Project
import com.lioisme.lide.project.LuaConfigParser
import com.lioisme.lide.project.ProjectManager
import com.lioisme.lide.ui.theme.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private sealed interface BuildResult {
    data class Success(
        val apkFile: File,
    ) : BuildResult

    data class Failure(
        /** 错误码（T17）：UI 经 [buildErrorDisplay] 映射本地化文案。 */
        val code: BuildErrorCode,
        val message: String,
    ) : BuildResult
}

/**
 * 失败文案（T17 错误消息资源化）：按错误码映射 `build_error_*` 本地化资源；
 * [BuildErrorCode.GENERAL]（未分类异常）与 [BuildErrorCode.LUA_COMPILE]
 * （编译器行号细节有诊断价值）以「本地化标题 + 技术明细」两段式展示
 * （C1 收口：明细保留英文技术文本，不再直出硬编码中文）。
 */
private fun buildErrorDisplay(
    context: android.content.Context,
    code: BuildErrorCode,
    message: String,
): String =
    if (message.isBlank()) {
        context.getString(buildErrorRes(code))
    } else {
        context.getString(buildErrorRes(code)) + "\n" + message
    }

private fun buildErrorRes(code: BuildErrorCode): Int =
    when (code) {
        BuildErrorCode.TEMPLATE_MANIFEST_MISSING -> R.string.build_error_template_manifest_missing
        BuildErrorCode.HOST_ABI_MISMATCH -> R.string.build_error_host_abi
        BuildErrorCode.HOST_MANIFEST_PARSE -> R.string.build_error_host_manifest_parse
        BuildErrorCode.HOST_PERMISSIONS_PARSE -> R.string.build_error_host_permissions_parse
        BuildErrorCode.MANIFEST_PATCH -> R.string.build_error_manifest_patch
        BuildErrorCode.PROJECT_MAIN_FILE_MISSING -> R.string.build_error_project_main_missing
        BuildErrorCode.LUA_COMPILE -> R.string.build_error_lua_compile
        BuildErrorCode.GENERAL -> R.string.build_error_general
    }

/**
 * 构建 APK 对话框（P5，2026-08-11：去配置对话框，打开即构建）：
 * 打开后立即按设置页持久化配置构建（[SettingsManager.buildOpts] 为唯一事实源：
 * ABI = preferAbi 或宿主全部、签名方案/档案、编译/加密开关、stripNativeLibs、
 * 安装方式、afterBuild；工程侧取 init.lua 包名/版本/SDK），无任何构建中可配置项。
 *
 * - 阶段进度对话框（stage 经 Channel 桥回主线程；不可取消）；
 * - 结果对话框：成功 = 路径 + 分享 + 按 afterBuild 自动安装；失败 = 原因（编译错误带行号）；
 * - 预检失败（包名缺失 / init.lua 不可读）Toast 后关闭，不闪配置界面。
 */
@Composable
fun BuildApkDialog(
    project: Project,
    projectManager: ProjectManager,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var building by remember { mutableStateOf(false) }
    var buildStage by remember { mutableStateOf<ApkBuilder.BuildStage?>(null) }
    var result by remember { mutableStateOf<BuildResult?>(null) }
    // 构建阶段回调经 Channel 桥回主线程：doBuild 在 IO 线程内回调 onStage，
    // 若直接写 buildStage 属 IO 线程写 Compose state，违反主线程约定。
    val stageChannel = remember { Channel<ApkBuilder.BuildStage>(Channel.BUFFERED) }
    LaunchedEffect(stageChannel) {
        for (stage in stageChannel) buildStage = stage
    }
    val strLoadFailed = stringResource(R.string.info_load_failed)
    val strNeedPackage = stringResource(R.string.build_need_package)
    val strBuildSuccess = stringResource(R.string.build_success)

    // 打开即构建：仅预检（init.lua 可读 + 包名非空）后直接进入构建，无配置 UI。
    LaunchedEffect(project) {
        val preflightFailed: String? =
            withContext(Dispatchers.IO) {
                val content = projectManager.readInitContent(project)
                when {
                    content == null -> "init"
                    LuaConfigParser.stringField(content, "packagename").isNullOrBlank() -> "package"
                    else -> null
                }
            }
        when (preflightFailed) {
            "init" -> {
                Toast.makeText(context, strLoadFailed, Toast.LENGTH_SHORT).show()
                onDismiss()
            }

            "package" -> {
                Toast.makeText(context, strNeedPackage, Toast.LENGTH_SHORT).show()
                onDismiss()
            }

            null -> {
                building = true
                scope.launch {
                    val res =
                        withContext(Dispatchers.IO) {
                            doBuild(context, project, projectManager) { stage ->
                                stageChannel.trySend(stage)
                            }
                        }
                    building = false
                    buildStage = null
                    result = res
                    when (res) {
                        is BuildResult.Success -> {
                            Toast.makeText(context, strBuildSuccess, Toast.LENGTH_SHORT).show()
                            // 产物输出方式（设置页持久化）：默认自动安装（pro 兼容）；SAVE = 仅写盘。
                            val opts = SettingsManager.buildOpts.value
                            if (opts.afterBuild == AfterBuild.INSTALL) {
                                val silent =
                                    opts.installBackend != InstallerBackend.DEFAULT &&
                                        InstallerCoordinator.installSilently(res.apkFile, opts.installBackend)
                                if (!silent) installApkViaSystem(context, res.apkFile)
                            }
                        }

                        is BuildResult.Failure -> {
                            Toast.makeText(
                                context,
                                context.getString(R.string.build_fail, buildErrorDisplay(context, res.code, res.message)),
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
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

    val r = result ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.build_title)) },
        text = {
            Column(
                modifier =
                    Modifier
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (r) {
                    is BuildResult.Failure -> {
                        Text(
                            stringResource(R.string.build_failure_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            buildErrorDisplay(context, r.code, r.message),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    is BuildResult.Success -> {
                        Text(
                            stringResource(R.string.build_success_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.build_output_path),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                r.apkFile.absolutePath,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        OutlinedButton(
                            onClick = { shareApk(context, r.apkFile) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.build_share))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.confirm)) }
        },
    )
}

/** 装配 + 签名 + 写盘（IO 线程）。失败返回可读信息（编译错误带行号）。 */
private fun doBuild(
    context: android.content.Context,
    project: Project,
    projectManager: ProjectManager,
    onStage: (ApkBuilder.BuildStage) -> Unit,
): BuildResult {
    try {
        onStage(ApkBuilder.BuildStage.READ_HOST)
        val pm = context.packageManager
        val sourceDir = pm.getApplicationInfo(context.packageName, 0).sourceDir
        val hostApk = File(sourceDir).readBytes()
        val hostEntries = ZipUtils.listEntryNames(hostApk)

        // 打包通用选项（设置页持久化，唯一事实源；P5 起对话框无可配置项）。
        val opts = SettingsManager.buildOpts.value

        // 签名材料：设置页默认档案（缺省/空 = 内置调试钥）。
        val material = SigningManager.loadMaterial(opts.signingProfileId)
        val key = material.key
        val cert = material.cert

        onStage(ApkBuilder.BuildStage.COLLECT_LUA)
        val projectFiles = LinkedHashMap<String, ByteArray>()
        val usingFiles = ArrayList<File>()
        collectProjectFiles(File(project.path), "", projectFiles, usingFiles)
        // 依赖分析为构建时权威结果。
        val hostNativeLibs = ZipUtils.listNativeLibNames(hostEntries)
        val analysis = analyzeDependencies(projectFiles, usingFiles, hostLuaSourceMap(context), hostNativeLibs)

        // 工程字段（init.lua）：包名/应用名/版本/版本号；P3：appsdk → 产物 targetSdkVersion
        // （pro 语义，缺失 = 保持宿主）；minsdk → 产物最低版本（缺失 = 宿主）。
        val initContent = projectManager.readInitContent(project) ?: ""
        val packageName = LuaConfigParser.stringField(initContent, "packagename").orEmpty()
        val appName = LuaConfigParser.stringField(initContent, "appname") ?: project.name
        val version = LuaConfigParser.stringField(initContent, "appver") ?: "1.0"
        val versionCode = LuaConfigParser.stringField(initContent, "appcode") ?: "1"
        val projectTargetSdk = LuaConfigParser.field(initContent, "appsdk")?.trim()?.toIntOrNull()
        val projectMinSdk = LuaConfigParser.field(initContent, "minsdk")?.trim()?.toIntOrNull()
        val hostMinSdk =
            try {
                ZipUtils
                    .readEntryData(hostApk, "AndroidManifest.xml")
                    ?.let { AXmlManifest.readUsesSdk(it).first }
                    ?: 0
            } catch (_: Exception) {
                0
            }

        // ABI = 设置页 preferAbi（宿主实际携带时）否则宿主全部（D-1：可用性以实测为准）。
        val abis = ZipUtils.listAbis(hostEntries)
        val abiSelection = if (opts.preferAbi in abis) setOf(opts.preferAbi) else abis.toSet()

        // UI1（2026-08-10）：最低系统版本 < 24 时旧设备仅能校验 v1，未勾 v1 阻止构建。
        val effectiveMinSdk = projectMinSdk ?: hostMinSdk
        if (effectiveMinSdk in 1..23 && !opts.signingScheme.v1) {
            return BuildResult.Failure(BuildErrorCode.GENERAL, context.getString(R.string.build_sdk_need_v1))
        }

        val input =
            ApkBuilder.ProjectInput(
                packageName = packageName,
                appName = appName,
                versionName = version,
                versionCode = versionCode.toIntOrNull() ?: 1,
                declaredPermissions = projectManager.loadUserPermissions(project),
                luaSources = projectFiles,
                engineLuaLibs = analysis.luaLibs.sorted(),
                requiredNativeLibs = analysis.nativeLibs,
                abiSelection = abiSelection,
                stripNativeLibs = opts.stripNativeLibs,
                compileLua = opts.compileLua,
                encryptBytecode = opts.encryptBytecode,
                minSdkVersion = projectMinSdk,
                targetSdkVersion = projectTargetSdk,
            )
        val apkBytes =
            ApkBuilder.build(
                hostApk,
                input,
                key,
                cert,
                signingScheme = opts.signingScheme,
                onStage = onStage,
            )

        // 产物位置：设置页 outputDir 优先（绝对路径，不可写回落默认），默认与 AndroLua Pro
        // 一致：<luaExtDir>/apk/（即 /storage/emulated/0/AndroLua/apk/）。
        val defaultOutDir = RuntimePaths.defaultApkDir().apply { mkdirs() }
        val outDir =
            if (opts.outputDir.isNotBlank()) {
                File(opts.outputDir).apply {
                    try {
                        mkdirs()
                        if (!canWrite()) throw java.io.IOException("not writable")
                    } catch (_: Exception) {
                        null
                    }
                } ?: defaultOutDir
            } else {
                defaultOutDir
            }
        val fileName = buildOutputName(packageName, version, opts.outputNaming)
        val outFile = File(outDir, fileName)
        outFile.writeBytes(apkBytes)
        return BuildResult.Success(outFile)
    } catch (e: Exception) {
        // T17：BuildException 携带错误码（UI 映射本地化文案），未分类异常回落 GENERAL。
        val code = (e as? ApkBuilder.BuildException)?.code ?: BuildErrorCode.GENERAL
        return BuildResult.Failure(code, e.message ?: e.javaClass.simpleName)
    }
}

/**
 * 产物文件名：设置页命名模板（`{package}` / `{version}` 占位符）→ sanitize；
 * 空模板 / 非法结果回落默认 `<包名>-<版本>.apk`。
 */
internal fun buildOutputName(
    packageName: String,
    version: String,
    template: String,
): String {
    val sanitized =
        if (template.isNotBlank()) {
            sanitizeName(template.replace("{package}", packageName).replace("{version}", version))
        } else {
            ""
        }
    return if (sanitized.isNotBlank()) sanitized else "${sanitizeName(packageName)}-${sanitizeName(version)}.apk"
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

/** 宿主 assets/lua 全部库的相对名 → 源码（依赖闭包扫描用；IO 线程调用）。 */
private fun hostLuaSourceMap(context: android.content.Context): Map<String, String> {
    val out = HashMap<String, String>()
    for (rel in hostLuaAssets(context)) {
        try {
            context.assets.open("lua/$rel").use { out[rel] = it.readBytes().toString(Charsets.UTF_8) }
        } catch (_: Throwable) {
            // 单个库读取失败不阻断打包（该库视为不存在，依赖推导自然回落）。
        }
    }
    return out
}

/**
 * 打包依赖分析（权威入口）：工程源码 + .using 内容 → 宿主 Lua 库传递闭包 +
 * 原生库候选 + 缺失模块警告。分析器纯 Kotlin，宿主/工程内容由本函数聚合。
 */
internal fun analyzeDependencies(
    projectFiles: Map<String, ByteArray>,
    usingFiles: List<File>,
    hostLua: Map<String, String>,
    hostNativeLibs: Set<String> = emptySet(),
): DependencyAnalysis {
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
    val projectOwned = projectFiles.keys.mapTo(HashSet()) { it.replace('\\', '/') }
    return NativeDependencyAnalyzer(hostLua, projectOwned, hostNativeLibs).analyze(srcs)
}

/**
 * 计算打包 APK 需要的宿主引擎 Lua 库（兼容旧接口，语义不变：单层扫描 +
 * 静态白名单 + 工程优先/宿主过滤，最终排序）；底层与 [analyzeDependencies]
 * 共用同一分析器，保证口径单一。
 */
internal fun engineLuaLibs(
    projectFiles: Map<String, ByteArray>,
    usingFiles: List<File>,
    hostLua: Set<String>,
): List<String> =
    analyzeDependencies(projectFiles, usingFiles, hostLua.associateWith { "" })
        .luaLibs
        .sorted()

// E3（2026-08-12）：打包输出文件名清理后的最大长度（防超长导致文件系统/分享失败）。
private const val SANITIZED_NAME_MAX_LENGTH = 60

private fun sanitizeName(s: String): String =
    s.replace(Regex("[^A-Za-z0-9_.-]"), "_").take(SANITIZED_NAME_MAX_LENGTH)

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
        // T17：分享失败消息资源兜底（异常细节仅日志，用户可见文案本地化）。
        android.util.Log.w(TAG_SHARE, "share apk failed", e)
        Toast.makeText(context, context.getString(R.string.build_share_failed), Toast.LENGTH_LONG).show()
    }
}

private const val TAG_SHARE = "BuildApkDialog"
