package com.lioisme.lide.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lioisme.lide.R
import com.lioisme.lide.build.AfterBuild
import com.lioisme.lide.build.BuildOpts
import com.lioisme.lide.build.SigningScheme
import com.lioisme.lide.build.ZipUtils
import com.lioisme.lide.ui.theme.LideTopBar
import com.lioisme.lide.ui.theme.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * AndroLua 合并页（2026-08-07 设置简约化 T1）：
 * AndroLua 工具（API 浏览器 / 示例）+ 打包选项（自动安装 / 优化打包 / 编译 / 加密 / 默认 ABI）。
 * 打包选项持久化唯一事实源 = [SettingsManager]（`lide_build` 域，经 [SettingsManager.updateBuild] 写回）。
 * 签名方案 / 默认 ABI 采用经典 Checkbox 选择（2026-08-08 打包 UI 重构）：
 * ABI 候选由宿主实际携带驱动（[ZipUtils.listAbis]），宿主未携带的架构置灰禁用。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AndroLuaSettingsScreen(
    onBack: () -> Unit,
    onApiBrowserClick: () -> Unit = {},
    onExamplesClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val opts by SettingsManager.buildOpts.collectAsState()
    // 宿主 APK 实际携带的 ABI（lib/<abi>/ 目录，与打包装配端同口径）：
    // IO 线程解析自身 APK 中央目录（不解压数据），未加载成功时回落空列表（仅显示「宿主全部」）。
    var hostAbis by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) {
        hostAbis =
            withContext(Dispatchers.IO) {
                runCatching {
                    val sourceDir =
                        context.packageManager
                            .getApplicationInfo(context.packageName, 0)
                            .sourceDir
                    ZipUtils.listAbis(ZipUtils.listEntryNames(File(sourceDir).readBytes()))
                }.getOrDefault(emptyList())
            }
    }

    fun persist(mutate: (BuildOpts) -> BuildOpts) {
        SettingsManager.updateBuild(mutate(opts), context)
    }

    @Composable
    fun onOff(checked: Boolean) = stringResource(if (checked) R.string.setting_on else R.string.setting_off)

    Scaffold(
        topBar = {
            LideTopBar(
                title = stringResource(R.string.androlua),
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(8.dp))

            SettingsSection(stringResource(R.string.tools)) {
                SettingsItem(
                    icon = Icons.Default.GridView,
                    title = stringResource(R.string.api_browser),
                    onClick = onApiBrowserClick,
                )
                Spacer(Modifier.height(8.dp))
                SettingsItem(
                    icon = Icons.Default.PlayArrow,
                    title = stringResource(R.string.examples),
                    onClick = onExamplesClick,
                )
            }

            Spacer(Modifier.height(24.dp))
            SettingsSection(stringResource(R.string.packaging)) {
                SettingsToggleItem(
                    icon = Icons.Default.Check,
                    title = stringResource(R.string.build_opt_auto_install),
                    subtitle = onOff(opts.afterBuild == AfterBuild.INSTALL),
                    checked = opts.afterBuild == AfterBuild.INSTALL,
                    onCheckedChange = { checked ->
                        persist { it.copy(afterBuild = if (checked) AfterBuild.INSTALL else AfterBuild.SAVE) }
                    },
                )
                HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                SettingsToggleItem(
                    icon = Icons.Default.Build,
                    title = stringResource(R.string.build_opt_native_title),
                    subtitle = stringResource(R.string.build_opt_native_subtitle),
                    checked = opts.stripNativeLibs,
                    onCheckedChange = { checked -> persist { it.copy(stripNativeLibs = checked) } },
                )
                HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                SettingsToggleItem(
                    icon = Icons.Default.Check,
                    title = stringResource(R.string.build_opt_compile_label),
                    subtitle = onOff(opts.compileLua),
                    checked = opts.compileLua,
                    onCheckedChange = { checked -> persist { it.copy(compileLua = checked) } },
                )
                HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                SettingsToggleItem(
                    icon = Icons.Default.Lock,
                    title = stringResource(R.string.build_opt_encrypt_title),
                    subtitle = onOff(opts.encryptBytecode),
                    checked = opts.encryptBytecode,
                    onCheckedChange = { checked -> persist { it.copy(encryptBytecode = checked) } },
                )
            }

            Spacer(Modifier.height(24.dp))
            SettingsSection(stringResource(R.string.build_opt_scheme_title)) {
                Text(
                    stringResource(R.string.build_opt_scheme_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                // 签名方案 = v1/v2/v3 独立开关（经典 Checkbox 多选组合）；
                // 最后一个勾选项禁用取消（全关 = 无签名产物，非法，不回写）。
                val scheme = opts.signingScheme
                val flags =
                    listOf(
                        Triple("v1", scheme.v1, { f: Boolean -> SigningScheme.fromFlags(f, scheme.v2, scheme.v3) }),
                        Triple("v2", scheme.v2, { f: Boolean -> SigningScheme.fromFlags(scheme.v1, f, scheme.v3) }),
                        Triple("v3", scheme.v3, { f: Boolean -> SigningScheme.fromFlags(scheme.v1, scheme.v2, f) }),
                    )
                flags.forEach { (name, selected, build) ->
                    CheckboxRow(
                        label = name,
                        checked = selected,
                        enabled = build(!selected) != null,
                        onCheckedChange = { checked ->
                            build(checked)?.let { next -> persist { it.copy(signingScheme = next) } }
                        },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            SettingsSection(stringResource(R.string.build_opt_abi_title)) {
                Text(
                    stringResource(R.string.build_opt_abi_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                // 默认 ABI 单选（auto = 宿主全部），经典 Checkbox 呈现：
                // 候选 = 宿主实际携带（唯一事实源 ZipUtils.listAbis）+ 标准架构名，
                // 宿主未携带的架构置灰禁用（仅展示不可选，不作可用性判定）。
                val abiOptions =
                    listOf("" to stringResource(R.string.build_opt_abi_auto)) +
                        (STANDARD_ABIS + hostAbis).distinct().map { it to it }
                abiOptions.forEach { (value, label) ->
                    val available = value.isEmpty() || value in hostAbis
                    CheckboxRow(
                        label = label,
                        checked = opts.preferAbi == value,
                        enabled = available,
                        onCheckedChange = { checked ->
                            persist { it.copy(preferAbi = if (checked) value else "") }
                        },
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

/** 标准 Android ABI 展示候选（与宿主实际携带取并集；仅用于置灰展示，不做可用性判定）。 */
private val STANDARD_ABIS = listOf("arm64-v8a", "x86_64", "armeabi-v7a", "x86")

/**
 * 经典复选框行（2026-08-08 打包 UI 重构）：Checkbox + 标签文字。
 * [enabled] = false 时整体置灰禁用（宿主未携带的 ABI / 最后一个签名开关）。
 */
@Composable
private fun CheckboxRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { onCheckedChange(!checked) }
                .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onCheckedChange(it) },
            enabled = enabled,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
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