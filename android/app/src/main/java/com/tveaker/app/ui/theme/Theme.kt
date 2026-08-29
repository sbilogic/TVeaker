package com.tveaker.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = StripeIris,
    secondary = StripeCyan,
    tertiary = StripeViolet,
    background = BgBase,
    surface = BgSurface,
    onPrimary = BgBase,
    onSecondary = BgBase,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    surfaceVariant = BgSurfaceElevated,
    outline = Border
)

@Composable
fun TVeakerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
