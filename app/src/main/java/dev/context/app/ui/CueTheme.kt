package dev.context.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Cue brand palette (cue-brand-kit): ink/surface core pair, lime accent kept sparse. */
object CueColors {
    val Ink = Color(0xFF171817)
    val Surface = Color(0xFFF1F0EC)
    val Accent = Color(0xFFA8E66B)
    val AccentLight = Color(0xFFC7D9B1)
    val Secondary = Color(0xFF2E312E)
    val Muted = Color(0xFF6B6E6A)
    val DarkSurface = Color(0xFF222522)
    val DarkMuted = Color(0xFFA5A8A2)
    val DarkBorder = Color(0xFF383B37)
    val LightBorder = Color(0xFFDADBD5)
}

private val CueLightScheme = lightColorScheme(
    primary = CueColors.Ink,
    onPrimary = CueColors.Surface,
    primaryContainer = CueColors.Accent,
    onPrimaryContainer = CueColors.Ink,
    secondary = CueColors.Secondary,
    onSecondary = CueColors.Surface,
    secondaryContainer = CueColors.Accent,
    onSecondaryContainer = CueColors.Ink,
    tertiary = CueColors.Muted,
    background = CueColors.Surface,
    onBackground = CueColors.Ink,
    surface = Color.White,
    onSurface = CueColors.Ink,
    surfaceVariant = CueColors.Surface,
    onSurfaceVariant = CueColors.Muted,
    outline = CueColors.Muted,
    outlineVariant = CueColors.LightBorder,
)

private val CueDarkScheme = darkColorScheme(
    primary = CueColors.Surface,
    onPrimary = CueColors.Ink,
    primaryContainer = CueColors.Accent,
    onPrimaryContainer = CueColors.Ink,
    secondary = CueColors.AccentLight,
    onSecondary = CueColors.Ink,
    secondaryContainer = CueColors.Secondary,
    onSecondaryContainer = CueColors.Accent,
    tertiary = CueColors.DarkMuted,
    background = CueColors.Ink,
    onBackground = CueColors.Surface,
    surface = CueColors.DarkSurface,
    onSurface = CueColors.Surface,
    surfaceVariant = CueColors.Secondary,
    onSurfaceVariant = CueColors.DarkMuted,
    outline = CueColors.DarkMuted,
    outlineVariant = CueColors.DarkBorder,
)

private val CueShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(16.dp),
)

@Composable
fun CueTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    androidx.compose.material3.MaterialTheme(
        colorScheme = if (darkTheme) CueDarkScheme else CueLightScheme,
        shapes = CueShapes,
        content = content,
    )
}
