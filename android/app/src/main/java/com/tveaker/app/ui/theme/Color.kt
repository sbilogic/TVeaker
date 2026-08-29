package com.tveaker.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Stripe Solid Premium Palette (No murky semi-transparencies)
val BgBase = Color(0xFF0B0F19)
val BgPrimary = Color(0xFF0B0F19)
val BgSurface = Color(0xFF111827)
val BgSurfaceElevated = Color(0xFF1E293B)
val BgSurfaceHover = Color(0xFF334155)
val BgCardGlass = Color(0xFF111827)

// Crisp Typography
val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted = Color(0xFF64748B)
val TextSubtle = Color(0xFF475569)

// Vibrant Luminous Accents
val StripeIris = Color(0xFF635BFF)
val StripeCyan = Color(0xFF00D4FF)
val StripeViolet = Color(0xFF818CF8)
val StripeEmerald = Color(0xFF10B981)
val StripeAmber = Color(0xFFF59E0B)
val StripeRose = Color(0xFFF43F5E)
val StripePink = Color(0xFFEC4899)

// Legacy alias mappings
val Accent = StripeIris
val AccentCyan = StripeCyan
val AccentIndigo = StripeIris
val AccentPurple = StripeViolet
val AccentDark = Color(0xFF4338CA)

val Success = StripeEmerald
val Warning = StripeAmber
val Danger = StripeRose

// Borders & Dividers
val Border = Color(0xFF1E293B)
val BorderSubtle = Color(0xFF334155)
val BorderMedium = Color(0xFF475569)
val BorderBright = Color(0xFF64748B)
val BorderGlowIris = StripeIris
val BorderGlowCyan = StripeCyan

// Gradient Brushes
val StripeGradientBrush = Brush.horizontalGradient(
    colors = listOf(StripeIris, StripeCyan)
)

val StripeSecondaryGradientBrush = Brush.horizontalGradient(
    colors = listOf(StripeIris, StripePink)
)

val StripeCardMeshBrush = Brush.verticalGradient(
    colors = listOf(Color(0xFF1E1B4B), Color(0xFF111827))
)
