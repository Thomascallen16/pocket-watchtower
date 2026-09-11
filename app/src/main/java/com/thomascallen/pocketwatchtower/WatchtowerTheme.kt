package com.thomascallen.pocketwatchtower

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

private val WatchtowerColors = darkColorScheme(
    primary = Color(0xFF00E5FF),
    onPrimary = Color(0xFF001014),
    primaryContainer = Color(0xFF003B46),
    onPrimaryContainer = Color(0xFF9DF5FF),
    secondary = Color(0xFFB388FF),
    onSecondary = Color(0xFF17002F),
    secondaryContainer = Color(0xFF35205C),
    onSecondaryContainer = Color(0xFFE8D9FF),
    tertiary = Color(0xFF69F0AE),
    onTertiary = Color(0xFF002116),
    tertiaryContainer = Color(0xFF005235),
    onTertiaryContainer = Color(0xFF8AFFC7),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF2B0000),
    background = Color(0xFF080D16),
    onBackground = Color(0xFFE7F0FA),
    surface = Color(0xFF0D1420),
    onSurface = Color(0xFFE7F0FA),
    surfaceVariant = Color(0xFF172033),
    onSurfaceVariant = Color(0xFFB8C5D6),
    outline = Color(0xFF30405A),
    outlineVariant = Color(0xFF202C40)
)

@Composable
internal fun WatchtowerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = WatchtowerColors) {
        Surface(
            modifier = Modifier.fillMaxSize().background(Color(0xFF080D16)),
            color = WatchtowerColors.background,
            contentColor = WatchtowerColors.onBackground
        ) {
            Box(Modifier.fillMaxSize()) {
                content()
            }
        }
    }
}
