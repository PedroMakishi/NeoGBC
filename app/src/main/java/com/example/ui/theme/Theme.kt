package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NeoGbcColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = OledBlack,
    primaryContainer = Color(0xFF003B32),
    onPrimaryContainer = NeonCyan,
    secondary = AtomicPurple,
    onSecondary = TextPrimary,
    secondaryContainer = AtomicPurpleDark,
    onSecondaryContainer = Color(0xFFE3D0FF),
    tertiary = BerryMagenta,
    onTertiary = OledBlack,
    background = OledBlack,
    onBackground = TextPrimary,
    surface = OledSurface,
    onSurface = TextPrimary,
    surfaceVariant = OledSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = OledCardBorder,
    error = CoralError,
    onError = OledBlack
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = NeoGbcColorScheme,
        typography = Typography,
        content = content
    )
}
