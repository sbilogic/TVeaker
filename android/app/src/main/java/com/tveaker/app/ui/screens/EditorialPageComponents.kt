package com.tveaker.app.ui.screens

import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tveaker.app.ui.theme.BorderSubtle
import com.tveaker.app.ui.theme.LocalCompactMode
import com.tveaker.app.ui.theme.StripeAmber
import com.tveaker.app.ui.theme.StripeIris
import com.tveaker.app.ui.theme.TextMuted
import com.tveaker.app.ui.theme.TextPrimary
import com.tveaker.app.ui.theme.TextSecondary

internal val EditorialCondensed = FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD))
internal val EditorialSerif = FontFamily.Serif

@Composable
internal fun EditorialPageHeader(
    index: String,
    title: String,
    subtitle: String,
    trailing: (@Composable () -> Unit)? = null
) {
    val compact = LocalCompactMode.current
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = if (compact) 7.dp else 14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$index / TVEAKER",
                color = MaterialTheme.colorScheme.outline,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
            trailing?.invoke()
        }
        Text(
            text = title,
            color = TextPrimary,
            fontFamily = EditorialCondensed,
            fontSize = if (compact) 34.sp else 42.sp,
            lineHeight = if (compact) 32.sp else 39.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-1.7).sp,
            modifier = Modifier.padding(top = if (compact) 4.dp else 7.dp)
        )
        Text(
            text = subtitle,
            color = TextSecondary,
            fontFamily = EditorialSerif,
            fontSize = if (compact) 12.sp else 14.sp,
            lineHeight = if (compact) 15.sp else 18.sp,
            modifier = Modifier.padding(top = if (compact) 3.dp else 5.dp)
        )
        HorizontalDivider(
            modifier = Modifier.padding(top = if (compact) 7.dp else 11.dp),
            thickness = 2.dp,
            color = TextPrimary
        )
    }
}

@Composable
internal fun EditorialSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.outline,
        fontSize = 9.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.45.sp,
        modifier = modifier
    )
}

@Composable
internal fun EditorialFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val compact = LocalCompactMode.current
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = if (selected) colors.onBackground else Color.Transparent,
        shape = RectangleShape,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) colors.onBackground else BorderSubtle)
    ) {
            Box(
            modifier = Modifier.heightIn(min = 48.dp).padding(horizontal = if (compact) 8.dp else 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label.uppercase(),
                color = if (selected) colors.background else TextSecondary,
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                letterSpacing = .7.sp,
                modifier = Modifier.padding(vertical = if (compact) 5.dp else 8.dp)
            )
        }
    }
}

@Composable
internal fun EditorialPageCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.Transparent,
        shape = RectangleShape,
        content = content
    )
}

@Composable
internal fun EditorialPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null
) {
    val compact = LocalCompactMode.current
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RectangleShape,
        contentPadding = PaddingValues(horizontal = if (compact) 13.dp else 16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = TextPrimary,
            contentColor = MaterialTheme.colorScheme.background
        )
    ) {
        icon?.invoke()
        if (icon != null) Spacer(Modifier.width(8.dp))
        Text(text.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
    }
}

@Composable
internal fun EditorialInlineLoading(label: String = "LOADING") {
    val compact = LocalCompactMode.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = if (compact) 24.dp else 44.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)
    ) {
        EditorialSectionLabel(label)
        Text("Reading your library…", color = TextPrimary, fontFamily = EditorialSerif, fontSize = 23.sp)
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = StripeIris,
            trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f)
        )
    }
}

@Composable
internal fun EditorialEmptyMessage(title: String, body: String) {
    val compact = LocalCompactMode.current
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = if (compact) 30.dp else 54.dp)) {
        EditorialSectionLabel("EMPTY EDIT")
        Text(
            title,
            color = TextPrimary,
            fontFamily = EditorialCondensed,
            fontSize = 35.sp,
            lineHeight = 33.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-1).sp,
            modifier = Modifier.padding(top = if (compact) 6.dp else 10.dp)
        )
        Text(body, color = TextSecondary, fontFamily = EditorialSerif, fontSize = if (compact) 14.sp else 16.sp, lineHeight = if (compact) 19.sp else 21.sp, modifier = Modifier.padding(top = if (compact) 8.dp else 12.dp))
    }
}

@Composable
internal fun EditorialValueRow(label: String, value: String, valueColor: Color = TextPrimary) {
    val compact = LocalCompactMode.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = if (compact) 6.dp else 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label.uppercase(), color = TextMuted, fontSize = 9.sp, letterSpacing = 1.sp)
        Text(value, color = valueColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
internal fun EditorialOfflineBanner(
    modifier: Modifier = Modifier,
    message: String = "Offline Mode — Showing cached library"
) {
    val colors = MaterialTheme.colorScheme
    val compact = LocalCompactMode.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.surface,
        shape = RectangleShape,
        border = androidx.compose.foundation.BorderStroke(1.dp, StripeAmber.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier.padding(
                start = if (compact) 10.dp else 14.dp,
                top = if (compact) 8.dp else 10.dp,
                end = 10.dp,
                bottom = if (compact) 8.dp else 10.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(if (compact) 24.dp else 28.dp)
                    .background(StripeAmber)
            )
            Icon(
                imageVector = Icons.Default.CloudOff,
                contentDescription = "Offline",
                tint = StripeAmber,
                modifier = Modifier.size(16.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "OFFLINE MODE",
                    color = StripeAmber,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp
                )
                Text(
                    text = message,
                    color = colors.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

