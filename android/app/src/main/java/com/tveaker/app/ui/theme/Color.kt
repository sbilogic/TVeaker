package com.tveaker.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Editorial signal palette. Background, surface, text, and border aliases resolve
// through MaterialTheme so legacy screens inherit light and dark mode automatically.
val StripeIris = Color(0xFF174CFF)
val StripeCyan = Color(0xFF00C6D7)
val StripeViolet = Color(0xFF4F73FF)
val StripeEmerald = Color(0xFF1D7A58)
val StripeAmber = Color(0xFFB36B00)
val StripeRose = Color(0xFFB9343D)
val StripePink = Color(0xFFC53E79)

// Cinema-grade multi-stop poster and backdrop scrim gradients for text legibility
val PosterScrimVertical = Brush.verticalGradient(
    0.0f to Color.Transparent,
    0.45f to Color.Black.copy(alpha = 0.35f),
    0.80f to Color.Black.copy(alpha = 0.82f),
    1.0f to Color.Black.copy(alpha = 0.96f)
)

val PosterScrimHorizontal = Brush.horizontalGradient(
    0.0f to Color.Black.copy(alpha = 0.90f),
    0.55f to Color.Black.copy(alpha = 0.60f),
    1.0f to Color.Transparent
)

val HeroArtworkScrim = Brush.verticalGradient(
    0.0f to Color.Black.copy(alpha = 0.20f),
    0.40f to Color.Transparent,
    0.75f to Color.Black.copy(alpha = 0.60f),
    1.0f to Color.Black.copy(alpha = 0.92f)
)


val BgBase: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.background
val BgPrimary: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.background
val BgSurface: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surface
val BgSurfaceElevated: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceVariant
val BgSurfaceHover: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceContainerHigh
val BgCardGlass: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surface

val TextPrimary: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onBackground
val TextSecondary: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant
val TextMuted: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.outline
val TextSubtle: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.outlineVariant

val Accent = StripeIris
val AccentCyan = StripeCyan
val AccentIndigo = StripeIris
val AccentPurple = StripeViolet
val AccentDark = Color(0xFF0A2DAF)
val Success = StripeEmerald
val Warning = StripeAmber
val Danger = StripeRose

val Border: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.outlineVariant
val BorderSubtle: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.outlineVariant
val BorderMedium: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.outline
val BorderBright: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurface
val BorderGlowIris = StripeIris
val BorderGlowCyan = StripeCyan

val StripeGradientBrush = Brush.horizontalGradient(listOf(StripeIris, StripeCyan))
val StripeSecondaryGradientBrush = Brush.horizontalGradient(listOf(StripeIris, StripePink))
val StripeCardMeshBrush = Brush.verticalGradient(listOf(Color(0xFF14234F), Color(0xFF10100F)))
