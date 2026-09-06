package com.tveaker.app.ui.theme

import android.graphics.Typeface
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val EditorialCondensed = FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD))
val EditorialSerif = FontFamily.Serif

object EditorialTypography {
    val HeadlineHeroCompact = TextStyle(
        fontFamily = EditorialCondensed,
        fontWeight = FontWeight.Black,
        fontSize = 82.sp,
        lineHeight = 84.sp,
        letterSpacing = (-3).sp
    )
    val HeadlineHeroStandard = TextStyle(
        fontFamily = EditorialCondensed,
        fontWeight = FontWeight.Black,
        fontSize = 118.sp,
        lineHeight = 120.sp,
        letterSpacing = (-3).sp
    )
    val SubheadSerif = TextStyle(
        fontFamily = EditorialSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 20.sp
    )
    val MetricForecastCompact = TextStyle(
        fontFamily = EditorialSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 48.sp,
        lineHeight = 48.sp
    )
    val MetricForecastStandard = TextStyle(
        fontFamily = EditorialSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 72.sp,
        lineHeight = 70.sp
    )
    val SectionEyebrow = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 9.sp,
        letterSpacing = 1.45.sp
    )
    val BadgePill = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 9.sp,
        letterSpacing = 0.6.sp
    )
    val ShowTitleSerif = TextStyle(
        fontFamily = EditorialSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 22.sp
    )
}

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        lineHeight = 14.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 9.sp,
        lineHeight = 12.sp
    )
)
