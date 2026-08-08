package com.lioisme.lide.ui.theme

import android.os.Build
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.isSystemInDarkTheme as platformIsDark

val LocalIsDarkTheme = compositionLocalOf { false }

val AppTypography =
    Typography(
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
): ColorScheme =
    when {
        config.useWallpaper && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        else -> {
            val seed = Color(config.customSeedColor)
            if (isDark) generateDarkScheme(seed) else generateLightScheme(seed)
        }
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

private const val BG_LIGHT = 0xFFFDFCFF
private const val BG_DARK = 0xFF1A1C1E
private const val ON_BG_LIGHT = 0xFF1A1C1E
private const val ON_BG_DARK = 0xFFE2E2E6

internal fun generateLightScheme(primary: Color): ColorScheme {
    val s = rotateHue(primary, 30f, 0.6f)
    val t = rotateHue(primary, -30f, 0.4f)
    return lightColorScheme(
        primary = primary,
        onPrimary = contrastingColor(primary),
        primaryContainer = lighten(primary, 0.85f),
        onPrimaryContainer = darken(primary, 0.3f),
        secondary = s,
        onSecondary = contrastingColor(s),
        secondaryContainer = lighten(s, 0.85f),
        onSecondaryContainer = darken(s, 0.3f),
        tertiary = t,
        onTertiary = contrastingColor(t),
        tertiaryContainer = lighten(t, 0.85f),
        onTertiaryContainer = darken(t, 0.3f),
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = Color(BG_LIGHT),
        onBackground = Color(ON_BG_LIGHT),
        surface = Color(BG_LIGHT),
        onSurface = Color(ON_BG_LIGHT),
        surfaceVariant = Color(0xFFE0E2EC),
        onSurfaceVariant = Color(0xFF44474E),
        outline = Color(0xFF74777F),
        outlineVariant = Color(0xFFC4C6D0),
        inverseSurface = Color(0xFF2F3033),
        inverseOnSurface = Color(0xFFF1F0F4),
        inversePrimary = lighten(primary, 0.4f),
        surfaceTint = primary,
        surfaceDim = Color(0xFFD8DAE0),
        surfaceBright = Color(0xFFFDFCFF),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF2F2F7),
        surfaceContainer = Color(0xFFECECF1),
        surfaceContainerHigh = Color(0xFFE6E7EC),
    )
}

internal fun generateDarkScheme(primary: Color): ColorScheme {
    val s = rotateHue(primary, 30f, 0.6f)
    val t = rotateHue(primary, -30f, 0.4f)
    val dp = lighten(primary, 0.25f)
    val ds = lighten(s, 0.25f)
    val dt = lighten(t, 0.25f)
    return darkColorScheme(
        primary = dp,
        onPrimary = darken(primary, 0.7f),
        primaryContainer = darken(primary, 0.7f),
        onPrimaryContainer = lighten(primary, 0.85f),
        secondary = ds,
        onSecondary = darken(s, 0.7f),
        secondaryContainer = darken(s, 0.7f),
        onSecondaryContainer = lighten(s, 0.85f),
        tertiary = dt,
        onTertiary = darken(t, 0.7f),
        tertiaryContainer = darken(t, 0.7f),
        onTertiaryContainer = lighten(t, 0.85f),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(BG_DARK),
        onBackground = Color(ON_BG_DARK),
        surface = Color(BG_DARK),
        onSurface = Color(ON_BG_DARK),
        surfaceVariant = Color(0xFF44474E),
        onSurfaceVariant = Color(0xFFC4C6D0),
        outline = Color(0xFF8E9099),
        outlineVariant = Color(0xFF44474E),
        inverseSurface = Color(ON_BG_DARK),
        inverseOnSurface = Color(ON_BG_LIGHT),
        inversePrimary = primary,
        surfaceTint = dp,
        surfaceDim = Color(0xFF141518),
        surfaceBright = Color(0xFF3A3B3E),
        surfaceContainerLowest = Color(0xFF0F1013),
        surfaceContainerLow = Color(0xFF1C1D21),
        surfaceContainer = Color(0xFF1A1C1E),
        surfaceContainerHigh = Color(0xFF252629),
    )
}

private fun lighten(
    c: Color,
    a: Float,
) = lerp(c, Color.White, a)

private fun darken(
    c: Color,
    a: Float,
) = lerp(c, Color.Black, a)

private fun contrastingColor(bg: Color): Color {
    val y = 0.299f * bg.red + 0.587f * bg.green + 0.114f * bg.blue
    return if (y > 0.5f) Color(ON_BG_LIGHT) else Color.White
}

private fun rotateHue(
    c: Color,
    deg: Float,
    sf: Float,
): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (c.red * 255).toInt(),
        (c.green * 255).toInt(),
        (c.blue * 255).toInt(),
        hsv,
    )
    hsv[0] = (hsv[0] + deg) % 360f
    hsv[1] = (hsv[1] * sf).coerceIn(0f, 1f)
    hsv[2] = hsv[2] * 0.85f
    return Color(android.graphics.Color.HSVToColor(hsv))
}
