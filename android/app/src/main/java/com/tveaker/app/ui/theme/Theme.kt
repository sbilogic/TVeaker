package com.tveaker.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalCompactMode = staticCompositionLocalOf { false }
val LocalOnCompactModeChange = staticCompositionLocalOf<(Boolean) -> Unit> { {} }

private val EditorialLightColors = lightColorScheme(
    primary = StripeIris,
    onPrimary = Color.White,
    secondary = StripeCyan,
    onSecondary = Color.White,
    tertiary = StripeViolet,
    background = Color(0xFFF4F0E7),
    onBackground = Color(0xFF111111),
    surface = Color(0xFFFBF8F1),
    onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFECE7DC),
    onSurfaceVariant = Color(0xFF56544F),
    outline = Color(0xFF8D8981),
    outlineVariant = Color(0xFFC9C3B8),
    error = Color(0xFFB9343D)
)

private val EditorialDarkColors = darkColorScheme(
    primary = Color(0xFF4F73FF),
    onPrimary = Color.White,
    secondary = Color(0xFF7892FF),
    onSecondary = Color(0xFF080E26),
    tertiary = Color(0xFF9EAEFF),
    background = Color(0xFF0B0B0A),
    onBackground = Color(0xFFF1EDE4),
    surface = Color(0xFF121210),
    onSurface = Color(0xFFF1EDE4),
    surfaceVariant = Color(0xFF191816),
    onSurfaceVariant = Color(0xFFAAA69E),
    outline = Color(0xFF716E68),
    outlineVariant = Color(0xFF34322E),
    error = Color(0xFFE06C73)
)

@Composable
fun TVeakerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    compactMode: Boolean = false,
    onCompactModeChange: (Boolean) -> Unit = {},
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalCompactMode provides compactMode,
        LocalOnCompactModeChange provides onCompactModeChange
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) EditorialDarkColors else EditorialLightColors,
            typography = Typography,
            content = content
        )
    }
}
