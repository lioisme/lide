package com.delua.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.delua.ui.theme.*

private val colorOptions = listOf(
    0xFFF44336, 0xFFE91E63, 0xFF9C27B0, 0xFF673AB7,
    0xFF3F51B5, 0xFF2196F3, 0xFF03A9F4, 0xFF00BCD4,
    0xFF009688, 0xFF4CAF50, 0xFF8BC34A, 0xFFCDDC39,
    0xFFFFEB3B, 0xFFFFC107, 0xFFFF9800, 0xFFFF5722,
    0xFF795548, 0xFF607D8B, 0xFF9E9E9E, 0xFF000000,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val config by SettingsManager.themeConfig.collectAsState()

    var showAppearanceSheet by remember { mutableStateOf(false) }
    var showColorSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            SettingsSection("Display") {
                SettingsItem(
                    icon = when (config.appearanceMode) {
                        AppearanceMode.SYSTEM -> Icons.Default.PhoneAndroid
                        AppearanceMode.LIGHT -> Icons.Default.LightMode
                        AppearanceMode.DARK -> Icons.Default.DarkMode
                    },
                    title = "Appearance",
                    subtitle = when (config.appearanceMode) {
                        AppearanceMode.SYSTEM -> "Follow system"
                        AppearanceMode.LIGHT -> "Light mode"
                        AppearanceMode.DARK -> "Dark mode"
                    },
                    onClick = { showAppearanceSheet = true },
                )
                SettingsItem(
                    icon = Icons.Default.Palette,
                    title = "Theme color",
                    subtitle = if (config.useWallpaper) "Follow wallpaper" else "Custom accent",
                    onClick = { showColorSheet = true },
                )
            }
            Spacer(Modifier.height(24.dp))
            SettingsSection("General") {
                SettingsItem(
                    icon = Icons.Default.Language,
                    title = "Language",
                    subtitle = "System default",
                    onClick = { /* TODO */ },
                )
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "About",
                    subtitle = "delua v1.0",
                    onClick = { /* TODO */ },
                )
            }
        }
    }

    if (showAppearanceSheet) {
        AppearanceSheet(
            current = config.appearanceMode,
            onSelect = { mode ->
                SettingsManager.update(config.copy(appearanceMode = mode), context)
                showAppearanceSheet = false
            },
            onDismiss = { showAppearanceSheet = false },
        )
    }

    if (showColorSheet) {
        ColorSheet(
            useWallpaper = config.useWallpaper,
            selectedColor = if (config.useWallpaper) null else Color(config.customSeedColor),
            onSelectWallpaper = {
                SettingsManager.update(config.copy(useWallpaper = true), context)
            },
            onSelectColor = { color ->
                SettingsManager.update(
                    config.copy(useWallpaper = false, customSeedColor = color.value.toLong() and 0xFFFFFFFFL),
                    context,
                )
            },
            onDismiss = { showColorSheet = false },
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
        shape = MaterialTheme.shapes.medium,
    ) { Column { content() } }
}

@Composable
private fun SettingsItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceSheet(current: AppearanceMode, onSelect: (AppearanceMode) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text(
                "Appearance", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            Spacer(Modifier.height(8.dp))
            AppearanceMode.entries.forEach { mode ->
                val icon = when (mode) {
                    AppearanceMode.SYSTEM -> Icons.Default.PhoneAndroid
                    AppearanceMode.LIGHT -> Icons.Default.LightMode
                    AppearanceMode.DARK -> Icons.Default.DarkMode
                }
                val label = when (mode) {
                    AppearanceMode.SYSTEM -> "Follow system"
                    AppearanceMode.LIGHT -> "Light mode"
                    AppearanceMode.DARK -> "Dark mode"
                }
                val selected = mode == current
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(mode) }
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(label, style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (selected) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColorSheet(
    useWallpaper: Boolean,
    selectedColor: Color?,
    onSelectWallpaper: () -> Unit,
    onSelectColor: (Color) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text(
                "Theme color", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth().clickable { onSelectWallpaper() }
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Wallpaper, null,
                    tint = if (useWallpaper) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("System wallpaper",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (useWallpaper) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (useWallpaper) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    if (useWallpaper) {
                        Text("Dynamic color from wallpaper",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (useWallpaper) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Palette, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(16.dp))
                Text("Pick an accent color", style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface)
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            ) {
                items(colorOptions) { argb ->
                    val color = Color(argb)
                    val isSelected = !useWallpaper && color == selectedColor
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .background(color)
                            .then(
                                if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                else Modifier.border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                            )
                            .clickable { onSelectColor(color) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSelected) {
                            Icon(Icons.Default.Check, null,
                                tint = if (color.luminance() > 0.5f) Color(0xFF1A1C1E) else Color.White,
                                modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun Color.luminance(): Float {
    return 0.299f * red + 0.587f * green + 0.114f * blue
}
