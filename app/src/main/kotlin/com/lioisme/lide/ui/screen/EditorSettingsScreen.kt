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
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lioisme.lide.R
import com.lioisme.lide.editor.EditorConfig
import kotlin.math.roundToInt
import com.lioisme.lide.editor.TipIconButton
import com.lioisme.lide.ui.theme.LideTopBar
import com.lioisme.lide.ui.theme.SettingsManager

/**
 * 编辑器设置页：编辑器 21 项设置全集（分组见 [EditorConfig]，
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
                // T5（2026-08-10）：undo 栈容量（sora Content.setMaxUndoStackSize，
                // 默认 500 对齐 sora；大文件降级 min(容量, 200) 由 LuaEditor 强制）。
                SettingsStepperItem(
                    icon = Icons.Default.Refresh,
                    title = stringResource(R.string.undo_stack_size),
                    valueText = stringResource(R.string.undo_stack_size_value, config.undoStackSize),
                    value = config.undoStackSize,
                    range = 100..5000,
                    step = 100,
                    onValueChange = { update(config.copy(undoStackSize = it)) },
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
                // T5（2026-08-10）：粘性滚动保留行数（sora props.stickyScrollMaxLines）。
                SettingsStepperItem(
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    title = stringResource(R.string.sticky_scroll_max_lines),
                    valueText = stringResource(R.string.sticky_scroll_max_lines_value, config.stickyScrollMaxLines),
                    value = config.stickyScrollMaxLines,
                    range = 1..10,
                    enabled = config.stickyScroll && !config.wordWrap,
                    onValueChange = { update(config.copy(stickyScrollMaxLines = it)) },
                )
                SettingsToggleItem(
                    icon = Icons.Default.GridView,
                    title = stringResource(R.string.line_numbers),
                    subtitle = onOff(config.lineNumbers),
                    checked = config.lineNumbers,
                    onCheckedChange = { update(config.copy(lineNumbers = it)) },
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
                SettingsToggleItem(
                    icon = Icons.Default.CheckCircle,
                    title = stringResource(R.string.ln_panel),
                    subtitle = onOff(config.lnPanel),
                    checked = config.lnPanel,
                    onCheckedChange = { update(config.copy(lnPanel = it)) },
                )
                // UI1（2026-08-10）：固定行号 / 行号分割线（sora 上限能力接线）。
                SettingsToggleItem(
                    icon = Icons.Default.Lock,
                    title = stringResource(R.string.pin_line_number),
                    subtitle =
                        if (config.pinLineNumber) {
                            stringResource(R.string.pin_line_number_hint)
                        } else {
                            onOff(config.pinLineNumber)
                        },
                    checked = config.pinLineNumber,
                    onCheckedChange = { update(config.copy(pinLineNumber = it)) },
                )
                SettingsStepperItem(
                    icon = Icons.Default.GridView,
                    title = stringResource(R.string.line_number_divider),
                    valueText =
                        stringResource(
                            R.string.line_number_divider_value,
                            config.lineNumberDividerWidth,
                        ),
                    value = config.lineNumberDividerWidth,
                    range = 0..6,
                    onValueChange = { update(config.copy(lineNumberDividerWidth = it)) },
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
                    icon = Icons.Default.KeyboardArrowUp,
                    title = stringResource(R.string.cursor_animation),
                    subtitle = onOff(config.cursorAnimation),
                    checked = config.cursorAnimation,
                    onCheckedChange = { update(config.copy(cursorAnimation = it)) },
                )
                SettingsToggleItem(
                    icon = Icons.Default.KeyboardArrowDown,
                    title = stringResource(R.string.disable_soft_kbd_if_hard),
                    subtitle = onOff(config.disableSoftKbdIfHardKbd),
                    checked = config.disableSoftKbdIfHardKbd,
                    onCheckedChange = { update(config.copy(disableSoftKbdIfHardKbd = it)) },
                )
                // T5（2026-08-10）：行高倍率（sora setLineSpacingMultiplier，1.0 = 默认）。
                // 内部以整百分数驱动步进（100..160，步 5），写回浮点倍率。
                SettingsStepperItem(
                    icon = Icons.Default.FormatSize,
                    title = stringResource(R.string.line_spacing),
                    valueText =
                        stringResource(
                            R.string.line_spacing_value,
                            "%.2f".format(config.lineSpacingMultiplier),
                        ),
                    value = (config.lineSpacingMultiplier * 100).roundToInt(),
                    range = 100..160,
                    step = 5,
                    onValueChange = {
                        update(config.copy(lineSpacingMultiplier = (it / 100f).coerceIn(1.0f, 1.6f)))
                    },
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
}

/** 字体族标签（T5）：id → 本地化文案（设置行尾值）。 */
@Composable
private fun fontFamilyLabel(id: Int): String =
    stringResource(
        when (id) {
            EditorConfig.FONT_FAMILY_MONOSPACE -> R.string.font_family_monospace
            EditorConfig.FONT_FAMILY_SANS -> R.string.font_family_sans
            EditorConfig.FONT_FAMILY_SERIF -> R.string.font_family_serif
            else -> R.string.font_family_default
        },
    )

/** 字体族选择面板（T5，2026-08-10）：与语言选择同款 ModalBottomSheet。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FontFamilySheet(
    currentId: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text(
                stringResource(R.string.font_family),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            val options =
                listOf(
                    EditorConfig.FONT_FAMILY_DEFAULT to stringResource(R.string.font_family_default),
                    EditorConfig.FONT_FAMILY_MONOSPACE to stringResource(R.string.font_family_monospace),
                    EditorConfig.FONT_FAMILY_SANS to stringResource(R.string.font_family_sans),
                    EditorConfig.FONT_FAMILY_SERIF to stringResource(R.string.font_family_serif),
                )
            options.forEach { (id, label) ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(id) }
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        color =
                            if (id == currentId) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        fontWeight = if (id == currentId) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
