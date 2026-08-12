package com.lioisme.lide.ui.theme

import com.lioisme.lide.R

/**
 * 工程主题目录（2026-08-11，P2）：pro `projectinfo/main.lua:63-70` 同款——
 * 运行时反射 `android.R.style` 全部 `Theme_*` 字段作为下拉候选；常见主题映射
 * 友好别名（[COMMON_ALIASES]，中英文案资源化）。
 *
 * 2026-08-12（DEAD_CODE_CLEANUP T5）：`resolveDark` / `resolveDarkFromName` /
 * `ThemeOption.dark` / `COMMON_ALIASES` 深色判定轴经审计为 WP-014（工程 theme
 * 驱动编辑器深色）反转删除后的测试残留——生产代码零消费（ProjectInfoScreen 仅
 * 读 `value`/`labelRes`），随死代码清理一并删除。
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
    )

    /**
     * 常见主题别名文案映射（值 → 文案资源）。仅覆盖各系最常见入口
     * （DeviceDefault / Material / Holo / 经典），其余 `Theme_*` 原样展示。
     */
    val COMMON_ALIASES: Map<String, Int> =
        mapOf(
            "Theme" to R.string.theme_alias_classic,
            "Theme_Black" to R.string.theme_alias_black,
            "Theme_Light" to R.string.theme_alias_classic_light,
            "Theme_DeviceDefault" to R.string.theme_alias_device_dark,
            "Theme_DeviceDefault_Light" to R.string.theme_alias_device_light,
            "Theme_DeviceDefault_DayNight" to R.string.theme_alias_device_daynight,
            "Theme_DeviceDefault_NoActionBar" to R.string.theme_alias_device_dark_no_title,
            "Theme_DeviceDefault_Light_NoActionBar" to R.string.theme_alias_device_light_no_title,
            "Theme_Material" to R.string.theme_alias_material_dark,
            "Theme_Material_Light" to R.string.theme_alias_material_light,
            "Theme_Material_NoActionBar" to R.string.theme_alias_material_dark_no_title,
            "Theme_Material_Light_NoActionBar" to R.string.theme_alias_material_light_no_title,
            "Theme_Holo" to R.string.theme_alias_holo_dark,
            "Theme_Holo_Light" to R.string.theme_alias_holo_light,
            "Theme_Holo_NoActionBar" to R.string.theme_alias_holo_dark_no_title,
            "Theme_Holo_Light_NoActionBar" to R.string.theme_alias_holo_light_no_title,
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
            ThemeOption(
                value = name,
                labelRes = COMMON_ALIASES[name],
            )
        }
}
