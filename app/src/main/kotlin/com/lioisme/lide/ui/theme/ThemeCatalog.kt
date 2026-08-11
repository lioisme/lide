package com.lioisme.lide.ui.theme

import com.lioisme.lide.R

/**
 * 工程主题目录（2026-08-11，P2）：pro `projectinfo/main.lua:63-70` 同款——
 * 运行时反射 `android.R.style` 全部 `Theme_*` 字段作为下拉候选；常见主题映射
 * 友好别名（[COMMON_ALIASES]，中英文案资源化）；[resolveDark] 为编辑器深色跟随
 * （WP-014）提供纯函数推断（legacy `dark`/`light` + `Theme_*` 名启发式）。
 *
 * 与打包产物运行时语义对齐：产物内 `LuaActivity.java:1508-1513` 按
 * `android.R.style.class.getField(theme)` 反射 setTheme，只认 `Theme_*` 字段名。
 */
object ThemeCatalog {

    private const val THEME_PREFIX = "Theme_"

    /** 主题选项（下拉候选项）。labelRes 非空 = 常见主题别名；否则展示原名。 */
    data class ThemeOption(
        val value: String,
        val labelRes: Int? = null,
        val dark: Boolean? = null,
    )

    /**
     * 常见主题别名映射：值 →（文案资源, 深色判定）。仅覆盖各系最常见入口
     * （DeviceDefault / Material / Holo / 经典），其余 `Theme_*` 原样展示。
     */
    val COMMON_ALIASES: Map<String, Pair<Int, Boolean?>> =
        mapOf(
            "Theme" to (R.string.theme_alias_classic to false),
            "Theme_Black" to (R.string.theme_alias_black to true),
            "Theme_Light" to (R.string.theme_alias_classic_light to false),
            "Theme_DeviceDefault" to (R.string.theme_alias_device_dark to true),
            "Theme_DeviceDefault_Light" to (R.string.theme_alias_device_light to false),
            "Theme_DeviceDefault_DayNight" to (R.string.theme_alias_device_daynight to null),
            "Theme_DeviceDefault_NoActionBar" to (R.string.theme_alias_device_dark_no_title to true),
            "Theme_DeviceDefault_Light_NoActionBar" to (R.string.theme_alias_device_light_no_title to false),
            "Theme_Material" to (R.string.theme_alias_material_dark to true),
            "Theme_Material_Light" to (R.string.theme_alias_material_light to false),
            "Theme_Material_NoActionBar" to (R.string.theme_alias_material_dark_no_title to true),
            "Theme_Material_Light_NoActionBar" to (R.string.theme_alias_material_light_no_title to false),
            "Theme_Holo" to (R.string.theme_alias_holo_dark to true),
            "Theme_Holo_Light" to (R.string.theme_alias_holo_light to false),
            "Theme_Holo_NoActionBar" to (R.string.theme_alias_holo_dark_no_title to true),
            "Theme_Holo_Light_NoActionBar" to (R.string.theme_alias_holo_light_no_title to false),
        )

    /** 反射失败/空白时降级的精选候选（与 [COMMON_ALIASES] 交集保证有友好名）。 */
    val FALLBACK_NAMES: List<String> =
        listOf(
            "Theme",
            "Theme_DeviceDefault",
            "Theme_DeviceDefault_Light",
            "Theme_DeviceDefault_DayNight",
            "Theme_DeviceDefault_NoActionBar",
            "Theme_DeviceDefault_Light_NoActionBar",
            "Theme_Material",
            "Theme_Material_Light",
            "Theme_Material_NoActionBar",
            "Theme_Material_Light_NoActionBar",
            "Theme_Holo",
            "Theme_Holo_Light",
            "Theme_Holo_NoActionBar",
            "Theme_Holo_Light_NoActionBar",
            "Theme_Black",
            "Theme_Light",
        )

    /**
     * 反射 `android.R.style` 全部 `Theme_*` 字段名（pro 同款，排序去重）。
     * 反射失败返回空表（调用方回落 [FALLBACK_NAMES]）；进程级缓存。
     */
    val reflectedThemeNames: List<String> by lazy {
        try {
            android.R.style::class.java.fields
                .map { it.name }
                .filter { it.startsWith(THEME_PREFIX) }
                .distinct()
                .sorted()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    /** 反射全量 → 下拉选项；反射为空时回落精选表。 */
    fun options(names: List<String> = reflectedThemeNames): List<ThemeOption> =
        (names.ifEmpty { FALLBACK_NAMES }).map { name ->
            val alias = COMMON_ALIASES[name]
            ThemeOption(
                value = name,
                labelRes = alias?.first,
                dark = alias?.second,
            )
        }

    /**
     * 工程 theme 字段 → 编辑器深色覆盖（WP-014 纯函数，JVM 可测）：
     * - legacy `dark` / `light` 直判（2026-08-10 旧值兼容）；
     * - 先查常见别名表（下拉选择的 `Theme_*` 深色判定与其同源），未命中再走
     *   `Theme_*` 名启发式：DayNight→跟随宿主(null)、含 `Light`→浅色、
     *   `Black/Dark/Night`→深色、其余 null（跟随宿主）；
     * - 空/未知 → null（不干预）。
     */
    fun resolveDark(theme: String?): Boolean? {
        if (theme.isNullOrBlank()) return null
        return when (theme) {
            "dark" -> true
            "light" -> false
            else -> COMMON_ALIASES[theme]?.second ?: resolveDarkFromName(theme)
        }
    }

    private fun resolveDarkFromName(name: String): Boolean? {
        if (!name.startsWith(THEME_PREFIX)) return null
        if (name.contains("DayNight")) return null
        if (name.contains("Light")) return false
        if (name.contains("Black") || name.contains("Dark") || name.contains("Night")) return true
        return null
    }
}
