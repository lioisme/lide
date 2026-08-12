package com.lioisme.lide.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme as platformIsDark
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.DynamicScheme
import com.materialkolor.scheme.SchemeTonalSpot

val LocalIsDarkTheme = compositionLocalOf { false }

val AppTypography =
    Typography(
        displayLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp),
        displayMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 45.sp, lineHeight = 52.sp),
        displaySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 36.sp, lineHeight = 44.sp),
        // E1（2026-08-12）：headline 系回归 M3 官方字阶（Normal 权重，原 SemiBold 属
        // 规范外加强，同层字体混排时视觉不一致）。
        headlineLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 32.sp, lineHeight = 40.sp),
        headlineMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 28.sp, lineHeight = 36.sp),
        headlineSmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 24.sp, lineHeight = 32.sp),
        titleLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp),
        titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
        titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
        bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
        bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
        bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
        labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
        labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
        labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    )

val AppShapes =
    Shapes(
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
    )

@Composable
fun resolveIsDark(appearanceMode: AppearanceMode): Boolean =
    when (appearanceMode) {
        AppearanceMode.SYSTEM -> platformIsDark()
        AppearanceMode.LIGHT -> false
        AppearanceMode.DARK -> true
    }

@Composable
fun resolveColorScheme(
    config: ThemeConfig,
    isDark: Boolean,
): ColorScheme {
    val context = LocalContext.current
    return if (config.useWallpaper && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // C3（2026-08-12）：壁纸模式走官方 dynamicColorScheme（系统全图量化调色板，
        // 覆盖全部 60+ token）——原实现单主色种子 + materialkolor，注释却声称官方
        // 函数，import 零调用点。官方动态色由系统配置驱动，无需自建缓存。
        if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        // 种子色模式：remember 缓存（LideTheme 重组不重复计算 tonal 色板）。
        val seed = config.customSeedColor
        remember(seed, isDark) { resolveColorSchemeNow(config, isDark, context) }
    }
}

/**
 * 非 Composable 色板计算（U4，2026-08-12）：与 [resolveColorScheme] 同一事实源，
 * 供 MainActivity 在 setContent 之前同步窗口背景色（recreate 首帧底色对齐主题，
 * 消除切换语言/外观时系统重建的白/黑闪帧）。
 *
 * C3 取舍说明（2026-08-12 注释如实化）：本函数为非 Composable 路径，无法调用官方
 * [dynamicDarkColorScheme]/[dynamicLightColorScheme]（@Composable）；壁纸模式用
 * 壁纸主色 + materialkolor 同算法（[generateLightScheme]/[generateDarkScheme]）
 * 生成近似色板——二者同一 TONAL_SPOT 色系，recreate 空档帧差异肉眼不可见
 * （仅动态壁纸模式，随后组合帧立即由 [resolveColorScheme] 官方色板接管）；
 * 种子色模式两路径完全同源零色差。
 */
fun resolveColorSchemeNow(
    config: ThemeConfig,
    isDark: Boolean,
    context: android.content.Context?,
): ColorScheme =
    when {
        config.useWallpaper &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            context != null -> {
            val seed = wallpaperPrimaryColor(context) ?: Color(config.customSeedColor)
            if (isDark) generateDarkScheme(seed) else generateLightScheme(seed)
        }

        else -> {
            val seed = Color(config.customSeedColor)
            if (isDark) generateDarkScheme(seed) else generateLightScheme(seed)
        }
    }

/** 壁纸主色（非 Composable，API 27+ `getWallpaperColors` 可用；更早恒 null 回落种子色）。 */
@Suppress("NewApi", "DEPRECATION")
private fun wallpaperPrimaryColor(context: android.content.Context): Color? =
    try {
        val manager = context.getSystemService(android.content.Context.WALLPAPER_SERVICE) as? android.app.WallpaperManager ?: return null
        val colors =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                manager.getWallpaperColors(android.app.WallpaperManager.FLAG_SYSTEM)
            } else {
                null
            }
        // getPrimaryColor 与 android.graphics.Color.toArgb 的 API 门槛（27/26）由
        // 外层 SDK_INT >= S 分支保证；lint 静态分析无法跟踪，显式 O_MR1 guard 消除。
        val primary =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) colors?.primaryColor else null
        primary?.let { Color(it.toArgb()) }
    } catch (_: Throwable) {
        // 降级：部分定制 ROM getWallpaperColors 抛异常 → 回落种子色（AGENTS.md 降级约定）。
        null
    }

@Composable
fun LideTheme(
    config: ThemeConfig = ThemeConfig(),
    content: @Composable () -> Unit,
) {
    val isDark = resolveIsDark(config.appearanceMode)
    val colorScheme = resolveColorScheme(config, isDark)

    CompositionLocalProvider(
        LocalIsDarkTheme provides isDark,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

/**
 * Material3 官方 HCT 色板（2026-08-12 T-2 整改）。
 *
 * 原实现为手工 HSL 派生（lighten/darken/rotateHue + 0.5 亮度阈值对比色），
 * 中间调 seed 下 onPrimary 等 token 可低于 WCAG AA 4.5:1 且无门禁守护。
 * 现改用 Google material-color-utilities（Kotlin 移植）官方 TONAL_SPOT 动态
 * 色板（Compose dynamicColorScheme 同款算法）：任意 seed 生成的 token 对
 * 保证 Material3 官方对比度规范，由 [ThemeContrastTest] 门禁守护
 * （与生产同算法的确定性断言：HCT 无平台差异，JVM 直测）。
 *
 * 亮/暗各生成一份 [DynamicScheme]（tonal 色板相同、tone 目标不同），
 * 映射到 Compose [ColorScheme] 全量 60+ token。
 */
internal fun generateLightScheme(primary: Color): ColorScheme = generateScheme(primary, isDark = false)

internal fun generateDarkScheme(primary: Color): ColorScheme = generateScheme(primary, isDark = true)

private fun generateScheme(
    primary: Color,
    isDark: Boolean,
): ColorScheme {
    val scheme =
        SchemeTonalSpot(
            sourceColorHct = Hct.fromInt(primary.toArgb()),
            isDark = isDark,
            contrastLevel = 0.0,
        )
    return if (isDark) {
        darkColorScheme(
            primary = Color(scheme.primary),
            onPrimary = Color(scheme.onPrimary),
            primaryContainer = Color(scheme.primaryContainer),
            onPrimaryContainer = Color(scheme.onPrimaryContainer),
            secondary = Color(scheme.secondary),
            onSecondary = Color(scheme.onSecondary),
            secondaryContainer = Color(scheme.secondaryContainer),
            onSecondaryContainer = Color(scheme.onSecondaryContainer),
            tertiary = Color(scheme.tertiary),
            onTertiary = Color(scheme.onTertiary),
            tertiaryContainer = Color(scheme.tertiaryContainer),
            onTertiaryContainer = Color(scheme.onTertiaryContainer),
            error = Color(scheme.error),
            onError = Color(scheme.onError),
            errorContainer = Color(scheme.errorContainer),
            onErrorContainer = Color(scheme.onErrorContainer),
            background = Color(scheme.background),
            onBackground = Color(scheme.onBackground),
            surface = Color(scheme.surface),
            onSurface = Color(scheme.onSurface),
            surfaceVariant = Color(scheme.surfaceVariant),
            onSurfaceVariant = Color(scheme.onSurfaceVariant),
            outline = Color(scheme.outline),
            outlineVariant = Color(scheme.outlineVariant),
            inverseSurface = Color(scheme.inverseSurface),
            inverseOnSurface = Color(scheme.inverseOnSurface),
            inversePrimary = Color(scheme.inversePrimary),
            surfaceTint = Color(scheme.surfaceTint),
            surfaceDim = Color(scheme.surfaceDim),
            surfaceBright = Color(scheme.surfaceBright),
            surfaceContainerLowest = Color(scheme.surfaceContainerLowest),
            surfaceContainerLow = Color(scheme.surfaceContainerLow),
            surfaceContainer = Color(scheme.surfaceContainer),
            surfaceContainerHigh = Color(scheme.surfaceContainerHigh),
            surfaceContainerHighest = Color(scheme.surfaceContainerHighest),
        )
    } else {
        lightColorScheme(
            primary = Color(scheme.primary),
            onPrimary = Color(scheme.onPrimary),
            primaryContainer = Color(scheme.primaryContainer),
            onPrimaryContainer = Color(scheme.onPrimaryContainer),
            secondary = Color(scheme.secondary),
            onSecondary = Color(scheme.onSecondary),
            secondaryContainer = Color(scheme.secondaryContainer),
            onSecondaryContainer = Color(scheme.onSecondaryContainer),
            tertiary = Color(scheme.tertiary),
            onTertiary = Color(scheme.onTertiary),
            tertiaryContainer = Color(scheme.tertiaryContainer),
            onTertiaryContainer = Color(scheme.onTertiaryContainer),
            error = Color(scheme.error),
            onError = Color(scheme.onError),
            errorContainer = Color(scheme.errorContainer),
            onErrorContainer = Color(scheme.onErrorContainer),
            background = Color(scheme.background),
            onBackground = Color(scheme.onBackground),
            surface = Color(scheme.surface),
            onSurface = Color(scheme.onSurface),
            surfaceVariant = Color(scheme.surfaceVariant),
            onSurfaceVariant = Color(scheme.onSurfaceVariant),
            outline = Color(scheme.outline),
            outlineVariant = Color(scheme.outlineVariant),
            inverseSurface = Color(scheme.inverseSurface),
            inverseOnSurface = Color(scheme.inverseOnSurface),
            inversePrimary = Color(scheme.inversePrimary),
            surfaceTint = Color(scheme.surfaceTint),
            surfaceDim = Color(scheme.surfaceDim),
            surfaceBright = Color(scheme.surfaceBright),
            surfaceContainerLowest = Color(scheme.surfaceContainerLowest),
            surfaceContainerLow = Color(scheme.surfaceContainerLow),
            surfaceContainer = Color(scheme.surfaceContainer),
            surfaceContainerHigh = Color(scheme.surfaceContainerHigh),
            surfaceContainerHighest = Color(scheme.surfaceContainerHighest),
        )
    }
}
