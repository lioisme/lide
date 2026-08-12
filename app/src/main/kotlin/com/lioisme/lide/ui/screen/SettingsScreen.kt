package com.lioisme.lide.ui.screen

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lioisme.lide.BuildConfig
import com.lioisme.lide.R
import com.lioisme.lide.i18n.LocaleManager
import com.lioisme.lide.ui.theme.*

// 主题色预设单一事实源 = ThemeSeeds.OPTIONS（ui/theme，与 ThemeContrastTest 门禁共用）。
private val colorOptions: List<Long> = ThemeSeeds.OPTIONS

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onEditorClick: () -> Unit = {},
    onAndroLuaClick: () -> Unit = {},
    onPermissionsClick: () -> Unit = {},
    onSigningClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val activity = context.resolveActivity()
    val config by SettingsManager.themeConfig.collectAsState()

    var showAppearanceSheet by remember { mutableStateOf(false) }
    var showColorSheet by remember { mutableStateOf(false) }
    var showLanguageSheet by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    val currentLanguageTag = LocaleManager.currentLanguageTag

    val languageSubtitle =
        when (currentLanguageTag) {
            LocaleManager.TAG_SYSTEM -> stringResource(R.string.system_default)
            LocaleManager.TAG_EN -> stringResource(R.string.lang_en)
            LocaleManager.TAG_ZH -> stringResource(R.string.lang_zh)
            else -> currentLanguageTag
        }

    Scaffold(
        topBar = {
            LideTopBar(
                title = stringResource(R.string.settings),
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
            SettingsSection(stringResource(R.string.display)) {
                SettingsItem(
                    icon =
                        when (config.appearanceMode) {
                            AppearanceMode.SYSTEM -> Icons.Default.PhoneAndroid
                            AppearanceMode.LIGHT -> Icons.Default.LightMode
                            AppearanceMode.DARK -> Icons.Default.DarkMode
                        },
                    title = stringResource(R.string.appearance),
                    subtitle =
                        when (config.appearanceMode) {
                            AppearanceMode.SYSTEM -> stringResource(R.string.follow_system)
                            AppearanceMode.LIGHT -> stringResource(R.string.light_mode)
                            AppearanceMode.DARK -> stringResource(R.string.dark_mode)
                        },
                    onClick = { showAppearanceSheet = true },
                )
                SettingsItem(
                    icon = Icons.Default.Palette,
                    title = stringResource(R.string.theme_color),
                    subtitle = if (config.useWallpaper) stringResource(R.string.follow_wallpaper) else stringResource(R.string.custom_accent),
                    onClick = { showColorSheet = true },
                )
            }
            Spacer(Modifier.height(24.dp))
            SettingsSection(stringResource(R.string.editor)) {
                // 编辑器设置独立入口页（EditorSettingsScreen，21 项设置）。
                SettingsItem(
                    icon = Icons.AutoMirrored.Filled.WrapText,
                    title = stringResource(R.string.editor),
                    subtitle = stringResource(R.string.editor_subtitle),
                    onClick = onEditorClick,
                )
            }
            Spacer(Modifier.height(24.dp))
            SettingsSection(stringResource(R.string.androlua)) {
                // AndroLua 合并页（2026-08-07 T1）：工具（API 浏览器/示例）+ 打包选项单入口。
                SettingsItem(
                    icon = Icons.Default.Android,
                    title = stringResource(R.string.androlua),
                    subtitle = stringResource(R.string.androlua_subtitle),
                    onClick = onAndroLuaClick,
                )
            }
            Spacer(Modifier.height(24.dp))
            SettingsSection(stringResource(R.string.general)) {
                // 权限管理为 LIDE 通用运行时能力，非 AndroLua 专属，置于通用分区。
                SettingsItem(
                    icon = Icons.Default.Lock,
                    title = stringResource(R.string.permission_management),
                    subtitle = stringResource(R.string.perm_subtitle),
                    onClick = onPermissionsClick,
                )
                // 签名管理独立界面（2026-08-07 T3）：开发者自管理签名档案，默认星标与打包页同步。
                SettingsItem(
                    icon = Icons.Default.VpnKey,
                    title = stringResource(R.string.signing_manage),
                    subtitle = stringResource(R.string.signing_manage_subtitle),
                    onClick = onSigningClick,
                )
                SettingsItem(
                    icon = Icons.Default.Language,
                    title = stringResource(R.string.language),
                    subtitle = languageSubtitle,
                    onClick = { showLanguageSheet = true },
                )
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = stringResource(R.string.about),
                    // T18（2026-08-12）：版本号模板化（应用名 + BuildConfig.VERSION_NAME）。
                    subtitle = stringResource(R.string.version, stringResource(R.string.app_name), BuildConfig.VERSION_NAME),
                    onClick = { showAboutDialog = true },
                )
            }
        }
    }

    if (showAppearanceSheet) {
        AppearanceSheet(
            current = config.appearanceMode,
            onSelect = { mode ->
                SettingsManager.update(config.copy(appearanceMode = mode), context)
                showAppearanceSheet = false
            },
            onDismiss = { showAppearanceSheet = false },
        )
    }

    if (showColorSheet) {
        ColorSheet(
            useWallpaper = config.useWallpaper,
            selectedColor = if (config.useWallpaper) null else Color(config.customSeedColor),
            onSelectWallpaper = {
                SettingsManager.update(config.copy(useWallpaper = true), context)
            },
            onSelectColor = { argb ->
                SettingsManager.update(
                    config.copy(useWallpaper = false, customSeedColor = argb),
                    context,
                )
            },
            onDismiss = { showColorSheet = false },
        )
    }

    if (showLanguageSheet) {
        LanguageSheet(
            currentTag = currentLanguageTag,
            onSelect = { tag ->
                LocaleManager.setLanguage(context, tag)
                showLanguageSheet = false
                // T21：API 33+ 由框架按 per-app locale 自动重建 Activity，
                // 手动 recreate 会双重重建；<33 才手动（wrap 生效路径）。
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    activity?.recreate()
                }
            },
            onDismiss = { showLanguageSheet = false },
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            icon = { Icon(Icons.Default.Info, null) },
            title = { Text(stringResource(R.string.about)) },
            text = {
                Column {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    // T18：版本号模板化（BuildConfig.VERSION_NAME）。
                    Text(
                        stringResource(R.string.version, stringResource(R.string.app_name), BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text(stringResource(R.string.confirm))
                }
            },
        )
    }
}

@Composable
internal fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
    Card(
        colors =
            CardDefaults.cardColors(
                // E1（2026-08-12）：surfaceVariant 已废弃——内容卡用 surfaceContainerLow。
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        shape = MaterialTheme.shapes.medium,
    ) { Column { content() } }
}

@Composable
internal fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String = "",
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
    }
}

@Composable
internal fun SettingsToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String = "",
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    // T4（2026-08-10）：互斥置灰（wordWrap↔stickyScroll）——禁用时整体淡化且不可点击。
    // E1（2026-08-12）：禁用态 M3 惯例 = onSurface@38%（原 onSurfaceVariant@50% 非规范值）。
    val titleColor =
        if (enabled) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = { onCheckedChange(!checked) })
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            null,
            tint =
                if (enabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

/**
 * WP-018 数值步进行：范围钳制 + 首尾禁用（大字号/小字号边界防误操作）。
 * T5（2026-08-10）：[step] 步进（undo 栈容量 100 步进）、[enabled] 互斥置灰。
 */
@Composable
internal fun SettingsStepperItem(
    icon: ImageVector,
    title: String,
    valueText: String,
    value: Int,
    range: IntRange,
    step: Int = 1,
    enabled: Boolean = true,
    onValueChange: (Int) -> Unit,
) {
    val titleColor =
        if (enabled) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            null,
            tint =
                if (enabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            Text(valueText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        FilledTonalIconButton(
            onClick = { onValueChange((value - step).coerceIn(range)) },
            enabled = enabled && value > range.first,
        ) {
            Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(4.dp))
        FilledTonalIconButton(
            onClick = { onValueChange((value + step).coerceIn(range)) },
            enabled = enabled && value < range.last,
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageSheet(
    currentTag: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text(
                stringResource(R.string.language),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            Spacer(Modifier.height(8.dp))

            val localeData =
                listOf(
                    Triple(LocaleManager.TAG_SYSTEM, Icons.Default.PhoneAndroid, R.string.system_default),
                    Triple(LocaleManager.TAG_EN, Icons.Default.Language, R.string.lang_en),
                    Triple(LocaleManager.TAG_ZH, Icons.Default.Language, R.string.lang_zh),
                )

            localeData.forEach { (tag, icon, labelRes) ->
                val label = stringResource(labelRes)
                val selected = tag == currentTag
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(tag) }
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        icon,
                        null,
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (selected) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceSheet(
    current: AppearanceMode,
    onSelect: (AppearanceMode) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text(
                stringResource(R.string.appearance),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            Spacer(Modifier.height(8.dp))
            AppearanceMode.entries.forEach { mode ->
                val icon =
                    when (mode) {
                        AppearanceMode.SYSTEM -> Icons.Default.PhoneAndroid
                        AppearanceMode.LIGHT -> Icons.Default.LightMode
                        AppearanceMode.DARK -> Icons.Default.DarkMode
                    }
                val label =
                    when (mode) {
                        AppearanceMode.SYSTEM -> stringResource(R.string.follow_system)
                        AppearanceMode.LIGHT -> stringResource(R.string.light_mode)
                        AppearanceMode.DARK -> stringResource(R.string.dark_mode)
                    }
                val selected = mode == current
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(mode) }
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (selected) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColorSheet(
    useWallpaper: Boolean,
    selectedColor: Color?,
    onSelectWallpaper: () -> Unit,
    onSelectColor: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text(
                stringResource(R.string.theme_color),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            Spacer(Modifier.height(8.dp))

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelectWallpaper() }
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Wallpaper,
                    null,
                    tint = if (useWallpaper) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.system_wallpaper),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (useWallpaper) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (useWallpaper) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    if (useWallpaper) {
                        Text(
                            stringResource(R.string.wallpaper_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (useWallpaper) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Palette, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(16.dp))
                Text(
                    stringResource(R.string.pick_accent),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            ) {
                items(colorOptions) { argb ->
                    val color = Color(argb)
                    val isSelected = !useWallpaper && color == selectedColor
                    // 选中标记色（U1 + R6，2026-08-12）：黑白双候选 WCAG 对比度取更高者
                    // 之上叠加明暗双因素——日间偏好黑、夜间偏好白，4.5:1 严格档兜底；
                    // 选中框与对号共用同一色（修「框黑对号白」不一致，替代 0.5 luminance
                    // 阈值，中灰块判色不出错）。
                    val markColor = selectedMarkColor(color, LocalIsDarkTheme.current)
                    Box(
                        modifier =
                            Modifier
                                .aspectRatio(1f)
                                .clip(CircleShape)
                                .background(color)
                                .then(
                                    if (isSelected) {
                                        Modifier.border(3.dp, markColor, CircleShape)
                                    } else {
                                        Modifier.border(1.dp, MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), CircleShape)
                                    },
                                )
                                .semantics { selected = isSelected }
                                .clickable { onSelectColor(argb) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSelected) {
                            Icon(
                                Icons.Default.Check,
                                null,
                                tint = markColor,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 选中标记色（U1 亮度判色 + R6 明暗双因素，2026-08-12）：宿主日间偏好黑、夜间
 * 偏好白；候选（偏好色、另一色）依序取首个满足 WCAG AA 4.5:1 严格档的；
 * 双候选均不满足时取对比度更高者（黑白为极端候选，任意色块至少一者达标，
 * 硬门槛兜底恒生效，无需降级）。
 */
private fun selectedMarkColor(color: Color, isDark: Boolean): Color {
    val l = color.luminance()
    val contrastWhite = 1.05f / (l + 0.05f)
    val contrastBlack = (l + 0.05f) / 0.05f
    val preferred = if (isDark) Color.White else Color.Black
    val fallback = if (isDark) Color.Black else Color.White
    val preferredContrast = if (isDark) contrastWhite else contrastBlack
    val fallbackContrast = if (isDark) contrastBlack else contrastWhite
    return when {
        preferredContrast >= 4.5f -> preferred
        fallbackContrast >= 4.5f -> fallback
        preferredContrast >= fallbackContrast -> preferred
        else -> fallback
    }
}

private tailrec fun Context.resolveActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.resolveActivity()
        else -> null
    }
