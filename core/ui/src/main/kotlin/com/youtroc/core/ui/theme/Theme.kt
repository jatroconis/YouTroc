package com.youtroc.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val YouTrocColorScheme = darkColorScheme(
    primary = YouTrocRed,
    onPrimary = Color.White,
    background = AlmostBlack,
    onBackground = OnDark,
    surface = AlmostBlack,
    onSurface = OnDark,
    surfaceVariant = ElevatedSurface,
    onSurfaceVariant = OnDarkMuted,
)

/**
 * The youtroc design-system theme: dark, red-accented, tuned for TV. All UI is
 * built with androidx.tv.material3 — never mixed with the mobile material3.
 */
@Composable
fun YouTrocTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = YouTrocColorScheme,
        content = content,
    )
}
