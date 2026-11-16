package com.storagecloud.oilmark.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Warm "engine oil" amber on neutral, slightly warm surfaces.
private val Light = lightColorScheme(
    primary = Color(0xFF7A5900),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDEA1),
    onPrimaryContainer = Color(0xFF261900),
    secondary = Color(0xFF6B5D3F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF4E0BB),
    onSecondaryContainer = Color(0xFF241A04),
    tertiary = Color(0xFF496547),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCBEBC5),
    onTertiaryContainer = Color(0xFF062109),
    background = Color(0xFFFFF8F2),
    onBackground = Color(0xFF1F1B13),
    surface = Color(0xFFFFF8F2),
    onSurface = Color(0xFF1F1B13),
    surfaceVariant = Color(0xFFEDE1CF),
    onSurfaceVariant = Color(0xFF4D4639),
    outline = Color(0xFF7F7667),
    outlineVariant = Color(0xFFD0C5B4),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBF2E6),
    surfaceContainer = Color(0xFFF5ECE0),
    surfaceContainerHigh = Color(0xFFEFE7DB),
    surfaceContainerHighest = Color(0xFFE9E1D5),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFF5BF48),
    onPrimary = Color(0xFF402D00),
    primaryContainer = Color(0xFF5C4300),
    onPrimaryContainer = Color(0xFFFFDEA1),
    secondary = Color(0xFFD7C4A0),
    onSecondary = Color(0xFF3A2F15),
    secondaryContainer = Color(0xFF52452A),
    onSecondaryContainer = Color(0xFFF4E0BB),
    tertiary = Color(0xFFAFCFAA),
    onTertiary = Color(0xFF1B361C),
    tertiaryContainer = Color(0xFF324D31),
    onTertiaryContainer = Color(0xFFCBEBC5),
    background = Color(0xFF17130B),
    onBackground = Color(0xFFEBE1D4),
    surface = Color(0xFF17130B),
    onSurface = Color(0xFFEBE1D4),
    surfaceVariant = Color(0xFF4D4639),
    onSurfaceVariant = Color(0xFFD0C5B4),
    outline = Color(0xFF998F80),
    outlineVariant = Color(0xFF4D4639),
    surfaceContainerLowest = Color(0xFF110E07),
    surfaceContainerLow = Color(0xFF1F1B13),
    surfaceContainer = Color(0xFF231F17),
    surfaceContainerHigh = Color(0xFF2E2921),
    surfaceContainerHighest = Color(0xFF39342B),
)

@Composable
fun OilmarkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        content = content,
    )
}
