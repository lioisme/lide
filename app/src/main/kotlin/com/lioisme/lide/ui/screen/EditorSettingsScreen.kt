package com.lioisme.lide.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lioisme.lide.R
import com.lioisme.lide.editor.EditorConfig
import com.lioisme.lide.editor.EditorPalette
import com.lioisme.lide.editor.LuaFonts
import com.lioisme.lide.editor.TipIconButton
import com.lioisme.lide.ui.theme.LideTopBar
import com.lioisme.lide.ui.theme.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 编辑器设置页：21 项设置（2026-08-11 P4 瘦身 27 → 20，移除 7 项「最佳配置」，
 * 见 [EditorConfig] KDoc；2026-08-12 治理红线曾移除「编辑器主题」手选项 → 20，
 * 同日用户拍板反转：配色**方案**可选（明暗双模跟随宿主，见 [EditorPalette]）→ 21；
 * 持久化经 [SettingsManager.updateEditor]，含恢复默认）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorSettingsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val config by SettingsManager.editorConfig.collectAsState()

    fun update(c: EditorConfig) = SettingsManager.updateEditor(c, context)

    @Composable
    fun onOff(checked: Boolean) = stringResource(if (checked) R.string.setting_on else R.string.setting_off)

    // T5（2026-08-10）：字体族选择面板开关（设置行尾值点击触发）。
    var showFontFamilyPicker by remember { mutableStateOf(false) }

    // T10（2026-08-12 用户拍板反转）：配色方案选择面板开关（Squircle CE 同款方案选择器）。
    var showSchemePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            LideTopBar(
                title = stringResource(R.string.editor),
                onBack = onBack,
                actions = {
                    TipIconButton(
                        tooltip = stringResource(R.string.restore_defaults),
                        onClick = {
                            SettingsManager.resetEditor(context)
                            android.widget.Toast
                                .makeText(context, context.getString(R.string.restore_defaults_done), android.widget.Toast.LENGTH_SHORT)
                                .show()
                        },
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                    }
                },
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

            SettingsSection(stringResource(R.string.general)) {
                SettingsStepperItem(
                    icon = Icons.Default.FormatSize,
                    title = stringResource(R.string.font_size),
                    valueText = stringResource(R.string.font_size_value, config.fontSize),
                    value = config.fontSize,
                    range = 8..32,
                    onValueChange = { update(config.copy(fontSize = it)) },
                )
                SettingsStepperItem(
                    icon = Icons.Default.SpaceBar,
                    title = stringResource(R.string.tab_size),
                    valueText = stringResource(R.string.tab_size_value, config.tabSize),
                    value = config.tabSize,
                    range = 1..8,
                    onValueChange = { update(config.copy(tabSize = it)) },
                )
                // T5（2026-08-10）：字体族选择（sora typefaceText/typefaceLineNumber）。
                SettingsItem(
                    icon = Icons.Default.FormatSize,
                    title = stringResource(R.string.font_family),
                    subtitle = fontFamilyLabel(config.fontFamily),
                    onClick = { showFontFamilyPicker = true },
                )
                // T10（2026-08-12 用户拍板反转）：配色方案选择（明暗双模跟随宿主）。
                SettingsItem(
                    icon = Icons.Default.Wallpaper,
                    title = stringResource(R.string.editor_color_scheme),
                    subtitle = schemeLabel(config.themeScheme),
                    onClick = { showSchemePicker = true },
                )
                SettingsToggleItem(
                    icon = Icons.Default.Check,
                    title = stringResource(R.string.use_tab),
                    subtitle = onOff(config.useTab),
                    checked = config.useTab,
                    onCheckedChange = { update(config.copy(useTab = it)) },
                )
            }

            Spacer(Modifier.height(24.dp))
            SettingsSection(stringResource(R.string.editor_settings_editing)) {
                SettingsToggleItem(
                    icon = Icons.Default.Edit,
                    title = stringResource(R.string.auto_indent),
                    subtitle = onOff(config.autoIndent),
                    checked = config.autoIndent,
                    onCheckedChange = { update(config.copy(autoIndent = it)) },
                )
                SettingsToggleItem(
                    icon = Icons.Default.FilterList,
                    title = stringResource(R.string.auto_close_brackets),
                    subtitle = onOff(config.autoCloseBrackets),
                    checked = config.autoCloseBrackets,
                    onCheckedChange = { update(config.copy(autoCloseBrackets = it)) },
                )
                SettingsToggleItem(
                    icon = Icons.Default.Search,
                    title = stringResource(R.string.auto_completion),
                    subtitle = onOff(config.autoCompletion),
                    checked = config.autoCompletion,
                    onCheckedChange = { update(config.copy(autoCompletion = it)) },
                )
                SettingsToggleItem(
                    icon = Icons.Default.Search,
                    title = stringResource(R.string.identifier_completion),
                    subtitle = onOff(config.identifierCompletion),
                    checked = config.identifierCompletion,
                    onCheckedChange = { update(config.copy(identifierCompletion = it)) },
                )
                SettingsToggleItem(
                    icon = Icons.Default.Error,
                    title = stringResource(R.string.lexical_diagnostics),
                    subtitle = onOff(config.lexicalDiagnostics),
                    checked = config.lexicalDiagnostics,
                    onCheckedChange = { update(config.copy(lexicalDiagnostics = it)) },
                )
                SettingsToggleItem(
                    icon = Icons.Default.Lock,
                    title = stringResource(R.string.read_only),
                    subtitle = onOff(config.readOnly),
                    checked = config.readOnly,
                    onCheckedChange = { update(config.copy(readOnly = it)) },
                )
            }

            Spacer(Modifier.height(24.dp))
            SettingsSection(stringResource(R.string.editor_settings_view)) {
                SettingsToggleItem(
                    icon = Icons.AutoMirrored.Filled.WrapText,
                    title = stringResource(R.string.word_wrap),
                    subtitle = onOff(config.wordWrap),
                    checked = config.wordWrap,
                    onCheckedChange = { checked ->
                        // T4（2026-08-10）：自动换行与粘性滚动互斥（sora 渲染器在
                        // wordwrap 时静默忽略 stickyScroll，Squircle 同款强制联动）——
                        // 开启自动换行时自动关闭粘性滚动，避免「看似开启实际无效」。
                        update(
                            if (checked && config.stickyScroll) {
                                config.copy(wordWrap = true, stickyScroll = false)
                            } else {
                                config.copy(wordWrap = checked)
                            },
                        )
                    },
                )
                SettingsToggleItem(
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    title = stringResource(R.string.sticky_scroll),
                    subtitle =
                        if (config.wordWrap) {
                            stringResource(R.string.sticky_scroll_word_wrap_hint)
                        } else {
                            onOff(config.stickyScroll)
                        },
                    checked = config.stickyScroll,
                    enabled = !config.wordWrap,
                    onCheckedChange = { update(config.copy(stickyScroll = it)) },
                )
                SettingsToggleItem(
                    icon = Icons.Default.GridView,
                    title = stringResource(R.string.line_numbers),
                    subtitle = onOff(config.lineNumbers),
                    checked = config.lineNumbers,
                    onCheckedChange = { update(config.copy(lineNumbers = it)) },
                )
                // L1（2026-08-12）：固定行号（sora 原生 setPinLineNumber，水平滚动时
                // 行号栏固定于左侧）。行号关闭时固定属性无效，开关置灰 + hint
                // （仿 sticky_scroll × wordWrap 联动模式）。
                SettingsToggleItem(
                    icon = Icons.Default.GridView,
                    title = stringResource(R.string.fixed_line_numbers),
                    subtitle =
                        if (config.lineNumbers) {
                            onOff(config.fixedLineNumbers)
                        } else {
                            stringResource(R.string.fixed_line_numbers_hint)
                        },
                    checked = config.fixedLineNumbers,
                    enabled = config.lineNumbers,
                    onCheckedChange = { update(config.copy(fixedLineNumbers = it)) },
                )
                SettingsToggleItem(
                    icon = Icons.Default.Info,
                    title = stringResource(R.string.highlight_current_line),
                    subtitle = onOff(config.highlightCurrentLine),
                    checked = config.highlightCurrentLine,
                    onCheckedChange = { update(config.copy(highlightCurrentLine = it)) },
                )
                SettingsToggleItem(
                    icon = Icons.Default.FilterList,
                    title = stringResource(R.string.highlight_bracket_pair),
                    subtitle = onOff(config.highlightBracketPair),
                    checked = config.highlightBracketPair,
                    onCheckedChange = { update(config.copy(highlightBracketPair = it)) },
                )
                SettingsToggleItem(
                    icon = Icons.Default.Description,
                    title = stringResource(R.string.highlight_current_block),
                    subtitle = onOff(config.highlightCurrentBlock),
                    checked = config.highlightCurrentBlock,
                    onCheckedChange = { update(config.copy(highlightCurrentBlock = it)) },
                )
                SettingsToggleItem(
                    icon = Icons.Default.Wallpaper,
                    title = stringResource(R.string.minimap),
                    subtitle = onOff(config.minimap),
                    checked = config.minimap,
                    onCheckedChange = { update(config.copy(minimap = it)) },
                )
            }

            Spacer(Modifier.height(24.dp))
            SettingsSection(stringResource(R.string.editor_settings_input)) {
                SettingsToggleItem(
                    icon = Icons.Default.SpaceBar,
                    title = stringResource(R.string.show_whitespace),
                    subtitle = onOff(config.showWhitespace),
                    checked = config.showWhitespace,
                    onCheckedChange = { update(config.copy(showWhitespace = it)) },
                )
                SettingsToggleItem(
                    icon = Icons.Default.Add,
                    title = stringResource(R.string.scalable),
                    subtitle = onOff(config.scalable),
                    checked = config.scalable,
                    onCheckedChange = { update(config.copy(scalable = it)) },
                )
                SettingsToggleItem(
                    icon = Icons.Default.KeyboardArrowDown,
                    title = stringResource(R.string.disable_soft_kbd_if_hard),
                    subtitle = onOff(config.disableSoftKbdIfHardKbd),
                    checked = config.disableSoftKbdIfHardKbd,
                    onCheckedChange = { update(config.copy(disableSoftKbdIfHardKbd = it)) },
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showFontFamilyPicker) {
        FontFamilySheet(
            currentId = config.fontFamily,
            onSelect = { id ->
                showFontFamilyPicker = false
                update(config.copy(fontFamily = id))
            },
            onDismiss = { showFontFamilyPicker = false },
        )
    }

    if (showSchemePicker) {
        SchemeSheet(
            currentScheme = config.themeScheme,
            onSelect = { scheme ->
                showSchemePicker = false
                update(config.copy(themeScheme = scheme))
            },
            onDismiss = { showSchemePicker = false },
        )
    }
}

/** 配色方案标签（T10）：方案索引 → 本地化文案（设置行尾值）。 */
@Composable
private fun schemeLabel(scheme: Int): String =
    stringResource(
        when (scheme) {
            EditorPalette.SCHEME_SOLARIZED -> R.string.editor_scheme_solarized
            EditorPalette.SCHEME_TOMORROW -> R.string.editor_scheme_tomorrow
            else -> R.string.editor_scheme_classic
        },
    )

/** 字体族标签（L2，2026-08-12 收敛）：id → 本地化文案（设置行行尾值）。
 * 字体仅支持内置 + 用户导入；系统族（1/2/3）不再是可选项，仅作终极兜底
 * （老用户遗留持久化值经 [LuaFonts.resolve] 仍可渲染，此处回落内置默认文案）。
 * 单一事实源 = [LuaFonts]（builtinAssets / builtinLabelRes / externalLabel）。 */
@Composable
private fun fontFamilyLabel(id: Int): String {
    val context = LocalContext.current
    return when {
        id >= EditorConfig.FONT_FAMILY_EXTERNAL_BASE -> {
            LuaFonts.externalLabel(context, id) ?: stringResource(R.string.font_jetbrains_mono)
        }

        id in LuaFonts.builtinAssets.keys -> {
            stringResource(LuaFonts.builtinLabelRes(id))
        }

        // 系统族遗留值（历史持久化）：回落内置默认（JetBrains Mono）。
        else -> {
            stringResource(R.string.font_jetbrains_mono)
        }
    }
}

/** 字体族选择面板（L2，2026-08-12 收敛）：内置 6 款（24sp 等宽预览）+
 * SAF 导入入口（与语言选择同款 ModalBottomSheet）。
 * 注释：系统 3 族已按「系统字体仅终极兜底」红线从面板移除（不再作为可选项）；
 * 历史持久化 id（1/2/3）渲染仍可用，见 [fontFamilyLabel] 回落语义。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FontFamilySheet(
    currentId: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // T11：导入成功后 +1 触发 produceState 重查（清 LuaFonts 缓存 + IO 重扫）。
    val refresh = remember { mutableIntStateOf(0) }
    // T11：外部字体列表走 produceState + IO：主线程不扫 files/fonts 目录。
    val importedFonts by
        produceState(initialValue = emptyList<Pair<Int, String>>(), refresh.intValue, context) {
            value = withContext(Dispatchers.IO) { LuaFonts.externalLabels(context) }
        }
    // T11：外部字体导入（SAF 选 ttf → 复制 files/fonts/ → 清 LuaFonts 缓存）。
    val fontPicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri != null) {
                scope.launch {
                    // 复制在 IO 线程执行：SAF 回调是主线程，大 ttf 复制不得阻塞 UI。
                    val ok =
                        try {
                            withContext(Dispatchers.IO) {
                                val dir = File(context.filesDir, "fonts").apply { mkdirs() }
                                val name =
                                    uri.lastPathSegment?.substringAfterLast('/') ?: "imported.ttf"
                                context.contentResolver.openInputStream(uri)?.use { input ->
                                    File(dir, name).outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                    true
                                } ?: false
                            }
                        } catch (_: Throwable) {
                            false
                        }
                    LuaFonts.invalidate()
                    refresh.intValue++
                    android.widget.Toast
                        .makeText(
                            context,
                            context.getString(
                                if (ok) R.string.font_import_done else R.string.font_import_failed,
                            ),
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                }
            }
        }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text(
                stringResource(R.string.font_family),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            val sectionTitle: @Composable (Int) -> Unit = { titleRes ->
                Text(
                    stringResource(titleRes),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            sectionTitle(R.string.font_section_builtin)
            builtinFontIds.forEach { id ->
                FontOptionRow(
                    label = stringResource(LuaFonts.builtinLabelRes(id)),
                    current = id == currentId,
                    onClick = { onSelect(id) },
                    preview = true,
                )
            }
            val externalCount = importedFonts.size
            if (externalCount > 0) {
                sectionTitle(R.string.font_section_imported)
                for ((id, label) in importedFonts) {
                    FontOptionRow(
                        label = label,
                        current = id == currentId,
                        onClick = { onSelect(id) },
                        preview = true,
                    )
                }
            }
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            fontPicker.launch(arrayOf("application/x-font-ttf", "font/ttf"))
                        }.padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.font_import),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** 配色方案选择面板（T10，2026-08-12 用户拍板反转）：Squircle CE / Terminus
 * 同款方案选择器——每行方案名 + 亮/暗双模背景预览色条（[EditorPalette.schemeBackgrounds]
 * 单一事实源，一致性由 EditorPaletteContrastTest 守护）；选择即持久化，明暗随宿主。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SchemeSheet(
    currentScheme: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text(
                stringResource(R.string.editor_color_scheme),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            for (scheme in 0 until EditorPalette.schemeCount) {
                val (lightBg, darkBg) = EditorPalette.schemeBackgrounds[scheme]
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(scheme) }
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = schemeLabel(scheme),
                        style = MaterialTheme.typography.bodyLarge,
                        color =
                            if (scheme == currentScheme) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        fontWeight = if (scheme == currentScheme) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                    )
                    if (scheme == currentScheme) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Row(modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp)) {
                    SchemePreviewBar(Color(lightBg), label = stringResource(R.string.scheme_preview_light))
                    Spacer(Modifier.width(8.dp))
                    SchemePreviewBar(Color(darkBg), label = stringResource(R.string.scheme_preview_dark))
                }
            }
        }
    }
}

/** 双模预览色条：背景色块 + 侧标（亮/暗）。[RowScope] 接收者提供等宽分割。 */
@Composable
private fun RowScope.SchemePreviewBar(
    background: Color,
    label: String,
) {
    Column(modifier = Modifier.weight(1f)) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    // E1（2026-08-12）：硬编码圆角 → MaterialTheme.shapes（small = 8dp）。
                    .clip(MaterialTheme.shapes.small)
                    .background(background)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 内置字体 id 序（面板展示序：JetBrains Mono 在前，与默认 id=0 语义一致）。 */
private val builtinFontIds: List<Int> =
    listOf(
        EditorConfig.FONT_FAMILY_DEFAULT,
        EditorConfig.FONT_FAMILY_DROID_SANS_MONO,
        EditorConfig.FONT_FAMILY_FIRA_CODE,
        EditorConfig.FONT_FAMILY_SOURCE_CODE_PRO,
        EditorConfig.FONT_FAMILY_ANONYMOUS_PRO,
        EditorConfig.FONT_FAMILY_DEJAVU_SANS_MONO,
    )

/** 字体选项行：[preview] = 24sp 等宽预览（T11：内置/导入字体展示实际字形）。 */
@Composable
private fun FontOptionRow(
    label: String,
    current: Boolean,
    onClick: () -> Unit,
    preview: Boolean,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style =
                if (preview) {
                    MaterialTheme.typography.titleMedium
                } else {
                    MaterialTheme.typography.bodyLarge
                },
            fontFamily = if (preview) FontFamily.Monospace else FontFamily.Default,
            fontSize = if (preview) 24.sp else 16.sp,
            color =
                if (current) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
    }
}
