package space.ghstmail.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val GhstMailColorScheme = darkColorScheme(
    primary = GhstColors.Amber,
    onPrimary = GhstColors.OnAmber,
    secondary = GhstColors.Amber,
    onSecondary = GhstColors.OnAmber,
    background = GhstColors.Background,
    onBackground = GhstColors.Text,
    surface = GhstColors.Background,
    onSurface = GhstColors.Text,
    surfaceVariant = GhstColors.Raised,
    onSurfaceVariant = GhstColors.TextMuted,
    surfaceContainerLowest = GhstColors.Inset,
    surfaceContainerLow = GhstColors.Card,
    surfaceContainer = GhstColors.Card,
    surfaceContainerHigh = GhstColors.Raised,
    surfaceContainerHighest = GhstColors.Raised,
    inverseSurface = GhstColors.Text,
    inverseOnSurface = GhstColors.Background,
    inversePrimary = GhstColors.OnAmber,
    outline = GhstColors.Border,
    outlineVariant = GhstColors.Border,
    error = GhstColors.Burn,
    onError = GhstColors.Background,
)

@Composable
fun GhstMailTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GhstMailColorScheme,
        typography = GhstTypography,
        content = content,
    )
}
