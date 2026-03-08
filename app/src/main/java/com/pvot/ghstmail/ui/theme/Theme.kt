package com.pvot.ghstmail.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val GhstMailColorScheme = darkColorScheme(
    primary = Amber,
    onPrimary = Background,
    secondary = AmberLight,
    background = Background,
    surface = Surface,
    surfaceVariant = SurfaceVariant,
    onBackground = OnBackground,
    onSurface = OnSurface,
    outline = Border,
    error = Error,
)

@Composable
fun GhstMailTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GhstMailColorScheme,
        typography = Typography,
        content = content
    )
}
