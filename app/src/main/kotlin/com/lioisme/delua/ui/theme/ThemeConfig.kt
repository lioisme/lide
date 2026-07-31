package com.lioisme.delua.ui.theme

enum class AppearanceMode {
    SYSTEM, LIGHT, DARK
}

data class ThemeConfig(
    val appearanceMode: AppearanceMode = AppearanceMode.SYSTEM,
    val useWallpaper: Boolean = true,
    val customSeedColor: Long = 0xFF1976D2,
)
