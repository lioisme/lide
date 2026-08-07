package com.lioisme.lide.ui.screen

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.lioisme.lide.R
import com.lioisme.lide.androlua.LuaConfigParser
import com.lioisme.lide.build.AfterBuild
import com.lioisme.lide.build.ApkBuilder
import com.lioisme.lide.build.DependencyAnalysis
import com.lioisme.lide.build.HostPolicy
import com.lioisme.lide.build.NativeDependencyAnalyzer
import com.lioisme.lide.build.SigningManager
import com.lioisme.lide.build.SigningScheme
import com.lioisme.lide.build.ZipUtils
import com.lioisme.lide.model.Project
import com.lioisme.lide.project.ProjectManager
import com.lioisme.lide.ui.theme.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 宿主 APK 原生库条目（UI 摘要估算用，仅需 ABI/文件名/未压缩大小）。 */
private typealias NativeLibEntry = ZipUtils.NativeLibEntry

/** 构建打包对话框草稿（IO 预载；签名方案为本次构建局部选择，不回写持久化）。 */
private data class PackagingDraft(
    val appName: String,
    val packageName: String,
    val version: String,
    val versionCode: String,
    val permissionCount: Int,
    /** 宿主 APK 实际携带的 ABI（lib/<abi>/ 目录）。 */
    val abis: List<String>,
    /** 用户勾选的 ABI 子集（默认 preferAbi ∩ 宿主，否则全选）。 */
    val abiSelection: Set<String>,
    /** 依赖分析预览（构建时重算，此处仅用于展示）。 */
    val analysis: DependencyAnalysis,
    /** 宿主原生库条目清单（摘要体积估算）。 */
    val nativeLibEntries: List<NativeLibEntry>,
    /** 宿主 IDE 专属内容剔除统计（HostPolicy 领域分类，与装配端同口径）。 */
    val hostOnlyDropped: HostOnlyStats,
    /** 默认签名档案展示名。 */
    val signingName: String,
)

/** 宿主 IDE 专属内容剔除统计（条目数 × 未压缩字节）。 */
private data class HostOnlyStats(
    val entries: Int,
    val bytes: Long,
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
 * 构建打包对话框（WP-005 整改：删除整页 BuildApkScreen，仅保留此对话框）：
 * 读取 init.lua 配置 → 宿主 APK + 工程 Lua 装配（main.lua 编译字节码、manifest 补丁
 * + 图标入口重定向、ABI × 原生库二维过滤、签名方案组合）→ 写盘并分享。
 *
 * - ABI 勾选仅列出宿主实际携带项（[ZipUtils.listAbis]，需求：宿主不含不显示）。
 * - 签名方案 v1/v2/v3 为经典 Checkbox（默认取设置页持久化值，本次构建内修改不回写）。
 * - 构建在 Dispatchers.IO 执行；编译错误（含行号）与装配错误经 [BuildResult.Failure]
 *   展示；成功后可经 FileProvider 分享安装。
 */
@Composable
fun BuildPackagingDialog(
    project: Project,
    projectManager: ProjectManager,
    onDismiss: () -> Unit,
    onManageSigning: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf<PackagingDraft?>(null) }
    var building by remember { mutableStateOf(false) }
    var buildStage by remember { mutableStateOf<ApkBuilder.BuildStage?>(null) }
    var result by remember { mutableStateOf<BuildResult?>(null) }
    // 签名方案本次构建局部状态（初始值 = 设置页持久化默认）。
    var signingScheme by remember { mutableStateOf(SettingsManager.buildOpts.value.signingScheme) }
    // 构建阶段回调经 Channel 桥回主线程：doBuild 在 IO 线程内回调 onStage，
    // 若直接写 buildStage 属 IO 线程写 Compose state，违反主线程约定。
    val stageChannel = remember { Channel<ApkBuilder.BuildStage>(Channel.BUFFERED) }
    LaunchedEffect(stageChannel) {
        for (stage in stageChannel) buildStage = stage
    }
    val strLoadFailed = stringResource(R.string.info_load_failed)
    val strNeedPackage = stringResource(R.string.build_need_package)
    val strNeedAbi = stringResource(R.string.build_need_abi)
    val strBuildSuccess = stringResource(R.string.build_success)
    val opts = SettingsManager.buildOpts

    LaunchedEffect(project) {
        draft =
            withContext(Dispatchers.IO) {
                // B-1：init.lua 读写收敛到 ProjectManager 域服务，UI 不自摸文件。
                val content = projectManager.readInitContent(project) ?: return@withContext null
                val projectFiles = LinkedHashMap<String, ByteArray>()
                val usingFiles = ArrayList<File>()
                collectProjectFiles(File(project.path), "", projectFiles, usingFiles)
                val hostApk =
                    File(
                        context.packageManager
                            .getApplicationInfo(context.packageName, 0)
                            .sourceDir,
                    ).readBytes()
                val hostEntries = ZipUtils.listEntryNames(hostApk)
                val abis = ZipUtils.listAbis(hostEntries)
                val nativeLibEntries = ZipUtils.listNativeLibEntries(hostEntries)
                val hostLuaSources = hostLuaSourceMap(context)
                val hostNativeLibs = nativeLibEntries.mapTo(HashSet()) { it.base }
                val analysis = analyzeDependencies(projectFiles, usingFiles, hostLuaSources, hostNativeLibs)
                // 宿主 IDE 专属内容统计（与装配端 HostPolicy.classifyHostEntry 同口径）。
                val hostOnly =
                    hostEntries
                        .filter { HostPolicy.classifyHostEntry(it.first) == HostPolicy.HostEntryDomain.HOST_ONLY }
                        .fold(HostOnlyStats(0, 0L)) { acc, (_, size) ->
                            acc.copy(entries = acc.entries + 1, bytes = acc.bytes + size)
                        }
                // 打包通用选项（设置页持久化）作为初始值。
                val buildOpts = opts.value
                val signingProfile = SigningManager.profile(buildOpts.signingProfileId)
                val abiInitial =
                    if (buildOpts.preferAbi in abis) setOf(buildOpts.preferAbi) else abis.toSet()
                PackagingDraft(
                    appName = LuaConfigParser.stringField(content, "appname") ?: project.name,
                    packageName = LuaConfigParser.stringField(content, "packagename").orEmpty(),
                    version = LuaConfigParser.stringField(content, "appver") ?: "1.0",
                    versionCode = LuaConfigParser.stringField(content, "appcode") ?: "1",
                    permissionCount = LuaConfigParser.stringArrayField(content, "user_permission").size,
                    abis = abis,
                    abiSelection = abiInitial,
                    analysis = analysis,
                    nativeLibEntries = nativeLibEntries,
                    hostOnlyDropped = hostOnly,
                    signingName = signingProfile.displayName,
                )
            }
        if (draft == null) {
            Toast.makeText(context, strLoadFailed, Toast.LENGTH_SHORT).show()
            onDismiss()
        }
    }

    fun startBuild(s: PackagingDraft) {
        if (s.packageName.isBlank()) {
            Toast.makeText(context, strNeedPackage, Toast.LENGTH_SHORT).show()
            return
        }
        if (s.abis.isNotEmpty() && s.abiSelection.isEmpty()) {
            Toast.makeText(context, strNeedAbi, Toast.LENGTH_SHORT).show()
            return
        }
        building = true
        buildStage = null
        result = null
        scope.launch {
            val res =
                withContext(Dispatchers.IO) {
                    doBuild(context, project, projectManager, s, signingScheme) { stage ->
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
                    if (SettingsManager.buildOpts.value.afterBuild == AfterBuild.INSTALL) {
                        installApk(context, res.apkFile)
                    }
                }

                is BuildResult.Failure -> {
                    Toast.makeText(context, context.getString(R.string.build_fail, res.message), Toast.LENGTH_LONG).show()
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

    val s = draft ?: return
    // 摘要估算与勾选实时联动（过滤口径与装配端 nativeLibKept 一致）。
    val keptLibs =
        remember(s, s.abiSelection) {
            s.nativeLibEntries.filter {
                it.abi in s.abiSelection &&
                    ApkBuilder.nativeLibKept(
                        it.base,
                        s.analysis.nativeLibs,
                        SettingsManager.buildOpts.value.stripNativeLibs,
                    )
            }
        }
    val keptLibCount = keptLibs.size
    val keptLibSize = keptLibs.sumOf { it.size }

    AlertDialog(
        onDismissRequest = { if (!building) onDismiss() },
        title = { Text(stringResource(R.string.build_title)) },
        text = {
            Column(
                modifier =
                    Modifier
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 工程摘要（init.lua）
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(s.appName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            s.packageName.ifBlank { project.name },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(R.string.build_version, s.version, s.versionCode),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(R.string.build_permissions, s.permissionCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // 依赖摘要（构建时重算，此处预览）
                Text(
                    stringResource(
                        R.string.build_dep_native,
                        keptLibCount,
                        android.text.format.Formatter.formatFileSize(context, keptLibSize),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.build_dep_abis, s.abiSelection.joinToString(" + ")),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (s.analysis.missingModules.isNotEmpty()) {
                    Text(
                        stringResource(
                            R.string.build_dep_missing,
                            s.analysis.missingModules.joinToString(", "),
                        ),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                HorizontalDivider()

                // ABI 多选：仅宿主实际携带项（需求：宿主不含不显示）。
                Text(
                    stringResource(R.string.build_abi_label),
                    style = MaterialTheme.typography.labelLarge,
                )
                s.abis.forEach { abi ->
                    val selected = abi in s.abiSelection
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                draft =
                                    s.copy(
                                        abiSelection =
                                            if (selected) {
                                                s.abiSelection - abi
                                            } else {
                                                s.abiSelection + abi
                                            },
                                    )
                            }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = selected, onCheckedChange = null)
                        Spacer(Modifier.width(8.dp))
                        Text(abi, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                // 签名方案：v1/v2/v3 经典 Checkbox（局部选择；全关 = 无签名产物，禁用取消）。
                Text(
                    stringResource(R.string.build_opt_scheme_title),
                    style = MaterialTheme.typography.labelLarge,
                )
                val flags =
                    listOf(
                        Triple("v1", signingScheme.v1, { f: Boolean -> SigningScheme.fromFlags(f, signingScheme.v2, signingScheme.v3) }),
                        Triple("v2", signingScheme.v2, { f: Boolean -> SigningScheme.fromFlags(signingScheme.v1, f, signingScheme.v3) }),
                        Triple("v3", signingScheme.v3, { f: Boolean -> SigningScheme.fromFlags(signingScheme.v1, signingScheme.v2, f) }),
                    )
                flags.forEach { (name, selected, build) ->
                    val enabled = build(!selected) != null
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = enabled) {
                                build(!selected)?.let { signingScheme = it }
                            }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = selected,
                            onCheckedChange = { checked -> build(checked)?.let { signingScheme = it } },
                            enabled = enabled,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            name,
                            style = MaterialTheme.typography.bodyMedium,
                            color =
                                if (enabled) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                },
                        )
                    }
                }

                // 签名档案：默认档案 + 管理入口（跳转签名管理页）。
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onManageSigning)
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.build_signature),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            stringResource(R.string.build_signature_value, s.signingName),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        stringResource(R.string.build_manage_signing),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                // 构建选项摘要（唯一入口在设置页，此处只读展示）。
                Text(
                    stringResource(
                        R.string.build_opts_summary,
                        onOffText(
                            SettingsManager.buildOpts.value.stripNativeLibs,
                            R.string.build_opt_native_title,
                            context,
                        ),
                        onOffText(
                            SettingsManager.buildOpts.value.compileLua,
                            R.string.build_opt_compile_label,
                            context,
                        ),
                        onOffText(
                            SettingsManager.buildOpts.value.encryptBytecode,
                            R.string.build_opt_encrypt_title,
                            context,
                        ),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                when (val r = result) {
                    is BuildResult.Failure -> {
                        Text(
                            r.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    is BuildResult.Success -> {
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

                    null -> {
                        Unit
                    }
                }
            }
        },
        confirmButton = {
            when {
                building -> {
                    TextButton(onClick = {}, enabled = false) { Text(stringResource(R.string.build_working)) }
                }

                result is BuildResult.Success -> {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.confirm)) }
                }

                else -> {
                    TextButton(onClick = { startBuild(s) }) { Text(stringResource(R.string.build_button)) }
                }
            }
        },
        dismissButton = {
            if (!building) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

private fun onOffText(
    on: Boolean,
    titleRes: Int,
    context: android.content.Context,
): String = "${context.getString(titleRes)}：${context.getString(if (on) R.string.setting_on else R.string.setting_off)}"

/** 装配 + 签名 + 写盘（IO 线程）。失败返回可读信息（编译错误带行号）。 */
private fun doBuild(
    context: android.content.Context,
    project: Project,
    projectManager: ProjectManager,
    s: PackagingDraft,
    signingScheme: SigningScheme,
    onStage: (ApkBuilder.BuildStage) -> Unit,
): BuildResult =
    try {
        onStage(ApkBuilder.BuildStage.READ_HOST)
        val pm = context.packageManager
        val sourceDir = pm.getApplicationInfo(context.packageName, 0).sourceDir
        val hostApk = File(sourceDir).readBytes()

        // 打包通用选项（设置页持久化，对话框只读消费）。
        val opts = SettingsManager.buildOpts.value

        // 签名材料：设置页默认档案（缺省/空 = 内置调试钥）。
        val material = SigningManager.loadMaterial(opts.signingProfileId)
        val key = material.key
        val cert = material.cert

        onStage(ApkBuilder.BuildStage.COLLECT_LUA)
        val projectFiles = LinkedHashMap<String, ByteArray>()
        val usingFiles = ArrayList<File>()
        collectProjectFiles(File(project.path), "", projectFiles, usingFiles)
        // 依赖分析为构建时权威结果（UI 预览仅展示，可能落后于文件变更）。
        val hostNativeLibs = ZipUtils.listNativeLibNames(ZipUtils.listEntryNames(hostApk))
        val analysis = analyzeDependencies(projectFiles, usingFiles, hostLuaSourceMap(context), hostNativeLibs)

        val input =
            ApkBuilder.ProjectInput(
                packageName = s.packageName,
                appName = s.appName,
                versionName = s.version,
                versionCode = s.versionCode.toIntOrNull() ?: 1,
                declaredPermissions = projectManager.loadUserPermissions(project),
                luaSources = projectFiles,
                engineLuaLibs = analysis.luaLibs.sorted(),
                requiredNativeLibs = analysis.nativeLibs,
                abiSelection = s.abiSelection,
                stripNativeLibs = opts.stripNativeLibs,
                compileLua = opts.compileLua,
                encryptBytecode = opts.encryptBytecode,
            )
        val apkBytes =
            ApkBuilder.build(hostApk, input, key, cert, signingScheme = signingScheme, onStage = onStage)

        // 产物位置：设置页 outputDir 优先（绝对路径，不可写回落默认），默认与 AndroLua Pro
        // 一致：<luaExtDir>/apk/（即 /storage/emulated/0/AndroLua/apk/）。
        val luaExtDir =
            com.androlua.LuaApplication
                .getInstance()
                .getLuaExtDir()
        val defaultOutDir = File(luaExtDir, "apk").apply { mkdirs() }
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
        val fileName = buildOutputName(s.packageName, s.version, opts.outputNaming)
        val outFile = File(outDir, fileName)
        outFile.writeBytes(apkBytes)
        BuildResult.Success(outFile)
    } catch (e: Exception) {
        BuildResult.Failure(e.message ?: e.javaClass.simpleName)
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
