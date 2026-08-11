package com.lioisme.lide.editor

import android.graphics.Color
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import org.eclipse.tm4e.core.internal.theme.raw.RawTheme

/**
 * LIDE TextMate 色彩方案（P3，2026-08-11 行号分界线修复）。
 *
 * sora 原生 [TextMateColorScheme] 无条件把 [EditorColorScheme.LINE_DIVIDER] 置为透明
 * （`applyVSCTheme`/`applyTMTheme` 均 `setColor(LINE_DIVIDER, TRANSPARENT)`），
 * 导致行号列与内容之间无分界线；本方案在 `super.applyDefault()` 之后按主题
 * JSON 的 divider 键回填分割线颜色（映射 [EditorColorScheme.LINE_DIVIDER] 与
 * [EditorColorScheme.STICKY_SCROLL_DIVIDER]，对齐 Squircle `SquircleScheme`）：
 * - VSC 格式（quietlight.json）：`colors["editor.lineNumber.divider"]`；
 * - TM 格式（darcula.json）：`settings[0].settings["lineNumberDivider"]`。
 *
 * 键缺失时保持 sora 默认（不绘制分界线），读取失败一律降级不抛异常。
 * [ThemeRegistry] 的 `setTheme` 先更新 currentThemeModel 再广播（ThemeRegistry.java:133-157），
 * 因此本方案在 `onChangeTheme → setTheme → applyDefault` 期间读到的模型已是新主题。
 * 构造沿用 vendored sora 双参构造（themeRegistry + 当前 themeModel）。
 */
class LideTextMateColorScheme(
    themeRegistry: ThemeRegistry,
) : TextMateColorScheme(themeRegistry, themeRegistry.getCurrentThemeModel()) {

    override fun applyDefault() {
        super.applyDefault()
        val divider = dividerColor() ?: return
        setColor(EditorColorScheme.LINE_DIVIDER, divider)
        setColor(EditorColorScheme.STICKY_SCROLL_DIVIDER, divider)
    }

    private fun dividerColor(): Int? {
        val raw = ThemeRegistry.getInstance().currentThemeModel.rawTheme as? RawTheme ?: return null
        // VSC 格式优先（colors 段），回落 TM 格式（settings[0].settings）。
        val colors = raw["colors"] as? RawTheme
        (colors?.get("editor.lineNumber.divider") as? String)
            ?.let { return parseColorOrNull(it) }
        val settings = raw["settings"] as? List<*> ?: return null
        val first = settings.firstOrNull() as? RawTheme ?: return null
        val setting = first["settings"] as? RawTheme ?: return null
        return (setting["lineNumberDivider"] as? String)?.let { parseColorOrNull(it) }
    }

    private fun parseColorOrNull(value: String): Int? =
        try {
            Color.parseColor(value)
        } catch (_: IllegalArgumentException) {
            null
        }
}