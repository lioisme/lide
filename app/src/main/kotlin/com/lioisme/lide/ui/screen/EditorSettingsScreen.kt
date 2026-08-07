package com.lioisme.lide.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lioisme.lide.R
import com.lioisme.lide.editor.EditorConfig
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
                    onCheckedChange = { update(config.copy(wordWrap = it)) },
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
                    icon = Icons.Default.MenuBook,
                    title = stringResource(R.string.sticky_scroll),
                    subtitle = onOff(config.stickyScroll),
                    checked = config.stickyScroll,
                    onCheckedChange = { update(config.copy(stickyScroll = it)) },
                )
                SettingsToggleItem(
                    icon = Icons.Default.CheckCircle,
                    title = stringResource(R.string.ln_panel),
                    subtitle = onOff(config.lnPanel),
                    checked = config.lnPanel,
                    onCheckedChange = { update(config.copy(lnPanel = it)) },
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
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
