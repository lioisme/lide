package com.lioisme.lide.editor

import android.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

/**
 * 宿主主题派生色值对象（全部 ARGB Int，纯值，可跨线程）。
 *
 * 由 [rememberHostEditorColors] 从宿主 Material3 色板生成；经
 * [LideHostColorScheme.updateHost] 应用到编辑器色槽——编辑器 chrome 部件
 * （当前行/选区/光标/浮层/滚动条/minimap）与宿主 UI 同源。
 * 2026-08-12 方案可选反转：编辑器文本区明暗输入源 = 宿主（`LocalIsDarkTheme`），
 * 配色方案可在编辑器设置页选择；文本区独立着色 = 双模配色库
 * （[EditorPalette]：按方案索引 + 明暗取对应侧主题）——背景/前景槽一律由
 * 当前 TextMate 主题提供，本宿主色板**不覆盖** WHOLE_BACKGROUND /
 * LINE_NUMBER_BACKGROUND / TEXT_NORMAL（T19 FOLLOW 时代文本区背景 =
 * 宿主 surfaceContainerHigh 的语义已随治理删除）；行号文字属 chrome 部件色
 * （sora TM 主题路径不读行号键），仍由宿主提供。
 */
data class HostEditorColors(
    val foreground: Int,
    val lineNumber: Int,
    val lineNumberActive: Int,
    val currentLine: Int,
    val caret: Int,
    val selection: Int,
    val handle: Int,
    val divider: Int,
    val blockLine: Int,
    val blockLineCurrent: Int,
    val completionBackground: Int,
    val completionCurrent: Int,
    val completionMatched: Int,
    val tooltipBackground: Int,
    val tooltipBrief: Int,
    val tooltipDetailed: Int,
    val tooltipAction: Int,
    val scrollThumb: Int,
    val scrollThumbPressed: Int,
    val scrollTrack: Int,
    val minimapBackground: Int,
    val minimapViewport: Int,
    val minimapViewportBorder: Int,
)

/**
 * 从当前 Material3 色板派生 [HostEditorColors]。
 *
 * 组合期调用（LuaEditor 内），`remember(MaterialTheme.colorScheme)` 键控：
 * 宿主主题（壁纸/种子/明暗）变化 → 色板变化 → 派生值更新 → 经
 * [LideHostColorScheme.updateHost] 动态刷新，编辑器零重建。
 * 色彩映射对照 sora [EditorColorScheme] 槽位语义，全部取 Material3 语义色。
 * 治理后：文本区背景/前景由 TextMate 主题提供；行号文字/浮层（补全窗/
 * tooltip）= 宿主色（浮层 > 画布 > chrome 三层递进）。
 */
@Composable
fun rememberHostEditorColors(): HostEditorColors {
    val scheme = MaterialTheme.colorScheme
    return androidx.compose.runtime.remember(scheme) {
        HostEditorColors(
            foreground = scheme.onSurface.toArgb(),
            lineNumber = scheme.onSurfaceVariant.toArgb(),
            lineNumberActive = scheme.primary.toArgb(),
            currentLine = scheme.surfaceVariant.copy(alpha = 0.45f).toArgb(),
            caret = scheme.primary.toArgb(),
            selection = scheme.primaryContainer.copy(alpha = 0.7f).toArgb(),
            handle = scheme.primary.toArgb(),
            divider = scheme.outlineVariant.toArgb(),
            blockLine = scheme.outlineVariant.copy(alpha = 0.6f).toArgb(),
            blockLineCurrent = scheme.outline.copy(alpha = 0.7f).toArgb(),
            completionBackground = scheme.surfaceContainerHighest.toArgb(),
            completionCurrent = scheme.secondaryContainer.copy(alpha = 0.8f).toArgb(),
            completionMatched = scheme.primary.toArgb(),
            tooltipBackground = scheme.surfaceContainerHighest.toArgb(),
            tooltipBrief = scheme.onSurface.toArgb(),
            tooltipDetailed = scheme.onSurfaceVariant.toArgb(),
            tooltipAction = scheme.primary.toArgb(),
            scrollThumb = scheme.outlineVariant.toArgb(),
            scrollThumbPressed = scheme.outline.toArgb(),
            scrollTrack = scheme.surfaceContainerLow.copy(alpha = 0.5f).toArgb(),
            minimapBackground = scheme.surfaceContainerLow.toArgb(),
            minimapViewport = scheme.primaryContainer.copy(alpha = 0.5f).toArgb(),
            minimapViewportBorder = scheme.outlineVariant.toArgb(),
        )
    }
}

/**
 * 编辑器配色方案（2026-08-12 方案可选反转后使用）。
 *
 * chrome 槽（当前行/选区/光标/行号文字/补全窗/诊断提示/滚动条/minimap/分隔线）
 * 取宿主派生色；文本区背景/前景槽与语法高亮 token 一律来自当前
 * TextMate 主题（[EditorPalette] 双模配色库：构造时
 * [ThemeRegistry.getCurrentThemeModel] 的 token 色与背景，明暗/方案随宿主
 * 切换时由调用方先 `setTheme(EditorPalette.themeNameFor(方案, 明暗))` 再更新 scheme）。
 *
 * 实现 [ThemeRegistry.ThemeChangeListener]（TextMateColorScheme 内建）：
 * 主题广播时自动重算 applyDefault；宿主色变化经 [updateHost] 主动刷新。
 */
class LideHostColorScheme(
    themeRegistry: ThemeRegistry,
    host: HostEditorColors,
) : TextMateColorScheme(themeRegistry, themeRegistry.getCurrentThemeModel()) {

    // Java 超类构造器会调用虚方法 applyDefault()，彼时本属性尚未赋值（null），
    // 故置空安全 + init 块补一次全量刷新（2026-08-11 T17.4 设备实测抓到的 NPE）。
    private var host: HostEditorColors? = host

    init {
        applyDefault()
    }

    /** 宿主色板更新（组合重组时调用；内部重算全部 chrome 槽，零重建）。 */
    fun updateHost(newHost: HostEditorColors) {
        if (newHost == host) return
        host = newHost
        applyDefault()
    }

    override fun applyDefault() {
        super.applyDefault()
        val h = host ?: return
        // 治理契约（2026-08-12）：文本区背景/前景槽由 TextMate 主题提供，
        // 宿主不得覆盖（WHOLE_BACKGROUND / LINE_NUMBER_BACKGROUND /
        // TEXT_NORMAL 均保持 super 的 applyTMTheme/applyVSCTheme 结果）；
        // 行号文字为 chrome 部件色（sora TM 路径无行号键），宿主提供。
        setColor(EditorColorScheme.LINE_NUMBER, h.lineNumber)
        setColor(EditorColorScheme.LINE_NUMBER_CURRENT, h.lineNumberActive)
        setColor(EditorColorScheme.CURRENT_LINE, h.currentLine)
        setColor(EditorColorScheme.SELECTION_INSERT, h.caret)
        setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND, h.selection)
        setColor(EditorColorScheme.SELECTION_HANDLE, h.handle)
        setColor(EditorColorScheme.LINE_DIVIDER, h.divider)
        setColor(EditorColorScheme.STICKY_SCROLL_DIVIDER, h.divider)
        setColor(EditorColorScheme.BLOCK_LINE, h.blockLine)
        setColor(EditorColorScheme.BLOCK_LINE_CURRENT, h.blockLineCurrent)
        setColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_UNDERLINE, Color.TRANSPARENT)
        setColor(EditorColorScheme.COMPLETION_WND_BACKGROUND, h.completionBackground)
        setColor(EditorColorScheme.COMPLETION_WND_ITEM_CURRENT, h.completionCurrent)
        setColor(EditorColorScheme.COMPLETION_WND_TEXT_PRIMARY, h.foreground)
        setColor(EditorColorScheme.COMPLETION_WND_TEXT_SECONDARY, halfAlpha(h.foreground))
        setColor(EditorColorScheme.COMPLETION_WND_TEXT_MATCHED, h.completionMatched)
        setColor(EditorColorScheme.DIAGNOSTIC_TOOLTIP_BACKGROUND, h.tooltipBackground)
        setColor(EditorColorScheme.DIAGNOSTIC_TOOLTIP_BRIEF_MSG, h.tooltipBrief)
        setColor(EditorColorScheme.DIAGNOSTIC_TOOLTIP_DETAILED_MSG, h.tooltipDetailed)
        setColor(EditorColorScheme.DIAGNOSTIC_TOOLTIP_ACTION, h.tooltipAction)
        setColor(EditorColorScheme.SCROLL_BAR_THUMB, h.scrollThumb)
        setColor(EditorColorScheme.SCROLL_BAR_THUMB_PRESSED, h.scrollThumbPressed)
        setColor(EditorColorScheme.SCROLL_BAR_TRACK, h.scrollTrack)
        setColor(EditorColorScheme.MINIMAP_BACKGROUND, h.minimapBackground)
        setColor(EditorColorScheme.MINIMAP_VIEWPORT, h.minimapViewport)
        setColor(EditorColorScheme.MINIMAP_VIEWPORT_BORDER, h.minimapViewportBorder)
    }

    private fun halfAlpha(color: Int): Int = color and 0x00FFFFFF or ((Color.alpha(color) / 2) shl 24)
}
