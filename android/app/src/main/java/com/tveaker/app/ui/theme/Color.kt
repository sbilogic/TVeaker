package com.tveaker.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Stripe Signature Deep Canvas Palette
val BgBase = Color(0xFF070913)
val BgPrimary = Color(0xFF0B0E1D)
val BgSurface = Color(0xE610152B)
val BgSurfaceElevated = Color(0xF2161E38)
val BgSurfaceHover = Color(0xFF1E284A)
val BgCardGlass = Color(0xCC0E1326)

// Stripe Crisp Typography
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted = Color(0xFF64748B)
val TextSubtle = Color(0xFF475569)

// Stripe Iconic Luminous Accents
val StripeIris = Color(0xFF635BFF)
val StripeCyan = Color(0xFF00D4FF)
val StripeViolet = Color(0xFF7A73FF)
val StripeEmerald = Color(0xFF00D97E)
val StripeAmber = Color(0xFFFFB800)
val StripeRose = Color(0xFFFF3B70)
val StripePink = Color(0xFFEC4899)

// Legacy alias mappings for backward compatibility
val Accent = StripeIris
val AccentCyan = StripeCyan
val AccentIndigo = StripeIris
val AccentPurple = StripeViolet
val AccentDark = Color(0xFF4B45C6)

val Success = StripeEmerald
val Warning = StripeAmber
val Danger = StripeRose

// Stripe Specular Hairline Borders & Drop Shadows
val Border = Color(0xFF1E2942)
val BorderSubtle = Color(0x18FFFFFF)
val BorderMedium = Color(0x28FFFFFF)
val BorderBright = Color(0x45FFFFFF)
val BorderGlowIris = Color(0x40635BFF)
val BorderGlowCyan = Color(0x4000D4FF)

// Stripe Multi-Stop Linear & Sweep Gradients
val StripeGradientBrush = Brush.linearGradient(
    colors = listOf(StripeIris, StripeViolet, StripeCyan)
)

val StripeSecondaryGradientBrush = Brush.linearGradient(
    colors = listOf(StripeIris, StripePink)
)

val StripeCardMeshBrush = Brush.verticalGradient(
    colors = listOf(Color(0x1A635BFF), Color(0x0A00D4FF), Color(0x00000000))
)
