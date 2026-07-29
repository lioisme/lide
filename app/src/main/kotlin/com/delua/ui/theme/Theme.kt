package com.delua.ui.theme

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

val LocalThemeConfig = compositionLocalOf { ThemeConfig() }
val LocalIsDarkTheme = compositionLocalOf { false }

val AppTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 57.sp, lineHeight = 64.sp),
    displayMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 45.sp, lineHeight = 52.sp),
    displaySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 36.sp, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
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

val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
)

@Composable
fun resolveIsDark(appearanceMode: AppearanceMode): Boolean = when (appearanceMode) {
    AppearanceMode.SYSTEM -> platformIsDark()
    AppearanceMode.LIGHT -> false
    AppearanceMode.DARK -> true
}

@Composable
fun resolveColorScheme(config: ThemeConfig, isDark: Boolean): ColorScheme {
    if (config.useWallpaper && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        return if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    val seed = Color(config.customSeedColor)
    return if (isDark) tonalDarkScheme(seed) else tonalLightScheme(seed)
}

@Composable
fun DeluaTheme(config: ThemeConfig = ThemeConfig(), content: @Composable () -> Unit) {
    val isDark = resolveIsDark(config.appearanceMode)
    val colorScheme = resolveColorScheme(config, isDark)

    CompositionLocalProvider(
        LocalThemeConfig provides config,
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

// ---- Tonal palette generator (Material3-style) ----

private const val BG_LIGHT = 0xFFFDFCFF
private const val BG_DARK = 0xFF1A1C1E
private const val ON_BG_LIGHT = 0xFF1A1C1E
private const val ON_BG_DARK = 0xFFE2E2E6
private const val SURF_VAR_LIGHT = 0xFFE0E2EC
private const val SURF_VAR_DARK = 0xFF44474E
private const val OUTLINE = 0xFF74777F
private const val OUTLINE_DARK = 0xFF8E9099
private const val OUTLINE_VAR = 0xFFC4C6D0

private val ERROR_LIGHT = Color(0xFFBA1A1A)
private val ON_ERROR_LIGHT = Color.White
private val ERROR_CONTAINER_LIGHT = Color(0xFFFFDAD6)
private val ON_ERROR_CONTAINER_LIGHT = Color(0xFF410002)
private val ERROR_DARK = Color(0xFFFFB4AB)
private val ON_ERROR_DARK = Color(0xFF690005)
private val ERROR_CONTAINER_DARK = Color(0xFF93000A)
private val ON_ERROR_CONTAINER_DARK = Color(0xFFFFDAD6)

internal fun tonalLightScheme(seed: Color): ColorScheme {
    val hue = seed.hue()
    val c = seed.chroma()
    val h2 = (hue + 30f) % 360f
    val h3 = (hue + 330f) % 360f
    return lightColorScheme(
        primary = tonal(hue, c * 0.7f, 40),
        onPrimary = Color.White,
        primaryContainer = tonal(hue, c * 0.5f, 90),
        onPrimaryContainer = tonal(hue, c * 0.7f, 10),
        secondary = tonal(h2, c * 0.4f, 40),
        onSecondary = Color.White,
        secondaryContainer = tonal(h2, c * 0.3f, 90),
        onSecondaryContainer = tonal(h2, c * 0.4f, 10),
        tertiary = tonal(h3, c * 0.35f, 40),
        onTertiary = Color.White,
        tertiaryContainer = tonal(h3, c * 0.25f, 90),
        onTertiaryContainer = tonal(h3, c * 0.35f, 10),
        error = ERROR_LIGHT,
        onError = ON_ERROR_LIGHT,
        errorContainer = ERROR_CONTAINER_LIGHT,
        onErrorContainer = ON_ERROR_CONTAINER_LIGHT,
        background = Color(BG_LIGHT),
        onBackground = Color(ON_BG_LIGHT),
        surface = Color(BG_LIGHT),
        onSurface = Color(ON_BG_LIGHT),
        surfaceVariant = Color(SURF_VAR_LIGHT),
        onSurfaceVariant = Color(0xFF44474E),
        outline = Color(OUTLINE),
        outlineVariant = Color(OUTLINE_VAR),
        inverseSurface = Color(0xFF2F3033),
        inverseOnSurface = Color(0xFFF1F0F4),
        inversePrimary = tonal(hue, c * 0.7f, 80),
        surfaceTint = tonal(hue, c * 0.7f, 40),
    )
}

internal fun tonalDarkScheme(seed: Color): ColorScheme {
    val hue = seed.hue()
    val c = seed.chroma()
    val h2 = (hue + 30f) % 360f
    val h3 = (hue + 330f) % 360f
    return darkColorScheme(
        primary = tonal(hue, c * 0.7f, 80),
        onPrimary = tonal(hue, c * 0.5f, 20),
        primaryContainer = tonal(hue, c * 0.5f, 30),
        onPrimaryContainer = tonal(hue, c * 0.4f, 90),
        secondary = tonal(h2, c * 0.4f, 80),
        onSecondary = tonal(h2, c * 0.3f, 20),
        secondaryContainer = tonal(h2, c * 0.3f, 30),
        onSecondaryContainer = tonal(h2, c * 0.25f, 90),
        tertiary = tonal(h3, c * 0.35f, 80),
        onTertiary = tonal(h3, c * 0.25f, 20),
        tertiaryContainer = tonal(h3, c * 0.25f, 30),
        onTertiaryContainer = tonal(h3, c * 0.2f, 90),
        error = ERROR_DARK,
        onError = ON_ERROR_DARK,
        errorContainer = ERROR_CONTAINER_DARK,
        onErrorContainer = ON_ERROR_CONTAINER_DARK,
        background = Color(BG_DARK),
        onBackground = Color(ON_BG_DARK),
        surface = Color(BG_DARK),
        onSurface = Color(ON_BG_DARK),
        surfaceVariant = Color(SURF_VAR_DARK),
        onSurfaceVariant = Color(0xFFC4C6D0),
        outline = Color(OUTLINE_DARK),
        outlineVariant = Color(SURF_VAR_DARK),
        inverseSurface = Color(ON_BG_DARK),
        inverseOnSurface = Color(ON_BG_LIGHT),
        inversePrimary = tonal(hue, c * 0.7f, 40),
        surfaceTint = tonal(hue, c * 0.7f, 80),
    )
}

/**
 * Generate a tonal color for a given hue, chroma strength, and tone (0-100).
 * Uses a simplified HCT-like model: hue drives the base color,
 * chromaCurve peaks at mid-tones (tone ~50) and falls to near-zero at extremes.
 */
private fun tonal(hue: Float, chroma: Float, tone: Int): Color {
    val t = (tone.coerceIn(0, 100)) / 100f
    val curve = (1f - 4f * (t - 0.5f) * (t - 0.5f)).coerceIn(0f, 1f)
    val effectiveChroma = chroma * curve
    val s = (effectiveChroma / (t + 0.001f)).coerceIn(0f, 1f)
    val v = (t + effectiveChroma * (1f - t)).coerceIn(0f, 1f)
    return Color.hsv(hue, s, v)
}

private fun Color.hue(): Float {
    val (h, _, _) = toHsv()
    return h
}

private fun Color.chroma(): Float {
    val (_, s, v) = toHsv()
    return s * v
}

private fun Color.toHsv(): FloatArray {
    val r = red; val g = green; val b = blue
    val max = maxOf(r, g, b); val min = minOf(r, g, b)
    val delta = max - min
    val h = when {
        delta == 0f -> 0f
        max == r -> 60f * (((g - b) / delta) % 6f)
        max == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }
    val s = if (max == 0f) 0f else delta / max
    return floatArrayOf((h + 360f) % 360f, s, max)
}

private fun Color.Companion.hsv(h: Float, s: Float, v: Float): Color {
    val hi = ((h / 60f).toInt() % 6)
    val f = h / 60f - hi
    val p = v * (1f - s)
    val q = v * (1f - f * s)
    val t = v * (1f - (1f - f) * s)
    val (r, g, b) = when (hi) {
        0 -> Triple(v, t, p)
        1 -> Triple(q, v, p)
        2 -> Triple(p, v, t)
        3 -> Triple(p, q, v)
        4 -> Triple(t, p, v)
        else -> Triple(v, p, q)
    }
    return Color(r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f))
}
