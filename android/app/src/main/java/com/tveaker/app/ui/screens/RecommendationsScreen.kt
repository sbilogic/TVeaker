package com.tveaker.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tveaker.app.data.model.RecommendationItemDto
import com.tveaker.app.ui.theme.BorderSubtle
import com.tveaker.app.ui.theme.LocalCompactMode
import com.tveaker.app.ui.theme.StripeIris
import com.tveaker.app.ui.theme.StripeViolet
import com.tveaker.app.ui.theme.TextMuted
import com.tveaker.app.ui.theme.TextPrimary
import com.tveaker.app.ui.theme.TextSecondary
import com.tveaker.app.ui.viewmodel.RecommendationsViewModel

@Composable
fun RecommendationsScreen(viewModel: RecommendationsViewModel) {
    val state by viewModel.uiState.collectAsState()
    val compact = LocalCompactMode.current
    val budgets = listOf("Any" to null, "30m" to 30, "45m" to 45, "60m" to 60, "90m" to 90, "120m" to 120)
    val intents = listOf("auto" to "Balanced", "finish_show" to "Finish a show", "start_new" to "Start fresh", "movie" to "Movies", "show" to "Series")

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = if (compact) 4.dp else 10.dp, bottom = if (compact) 16.dp else 24.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 9.dp)
    ) {
        item {
            EditorialPageHeader(
                index = "03",
                title = "DISCOVER",
                subtitle = "A considered next watch, chosen from your taste and the time you actually have."
            )
        }
        if (state.isOffline) {
            item {
                EditorialOfflineBanner(
                    modifier = Modifier.padding(bottom = if (compact) 4.dp else 8.dp)
                )
            }
        }
        item {
            EditorialSectionLabel("TIME BUDGET")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 8.dp), modifier = Modifier.padding(top = if (compact) 5.dp else 9.dp)) {
                items(budgets) { (label, budget) ->
                    EditorialFilterChip(label, state.selectedBudget == budget) { viewModel.setBudget(budget) }
                }
            }
        }
        item {
            EditorialSectionLabel("VIEWING INTENT")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 8.dp), modifier = Modifier.padding(top = if (compact) 5.dp else 9.dp)) {
                items(intents) { (value, label) ->
                    EditorialFilterChip(label, state.selectedIntent == value) { viewModel.setIntent(value) }
                }
            }
        }
        item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
        if (state.isLoading && state.items.isEmpty()) {
            item { EditorialInlineLoading("BUILDING YOUR EDIT") }
        } else if (state.items.isEmpty()) {
            item {
                EditorialEmptyMessage(
                    title = "NOTHING MATCHES\nTHIS EDIT.",
                    body = state.errorMessage ?: "Try a wider time budget or a different viewing intent."
                )
            }
        } else {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    EditorialSectionLabel("YOUR NEXT WATCH")
                    Text("${state.items.size} PICKS", color = TextMuted, fontSize = 9.sp, letterSpacing = 1.sp)
                }
            }
            items(state.items, key = { it.candidateId }) { item ->
                CleanRecommendationCard(item) { action -> viewModel.submitFeedback(item.candidateId, action) }
            }
        }
    }
}

@Composable
fun CleanRecommendationCard(item: RecommendationItemDto, onAction: (String) -> Unit) {
    val compact = LocalCompactMode.current
    EditorialPageCard {
        Column(modifier = Modifier.padding(vertical = if (compact) 6.dp else 9.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 14.dp), verticalAlignment = Alignment.Top) {
                if (!item.posterUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(artworkUrl(item.posterUrl)).crossfade(true).build(),
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(width = if (compact) 50.dp else 58.dp, height = if (compact) 72.dp else 84.dp)
                    )
                } else {
                    Box(modifier = Modifier.size(width = if (compact) 50.dp else 58.dp, height = if (compact) 72.dp else 84.dp).background(StripeIris.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = StripeIris, modifier = Modifier.size(24.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(item.title, color = TextPrimary, fontFamily = EditorialSerif, fontSize = if (compact) 18.sp else 21.sp, lineHeight = if (compact) 20.sp else 23.sp, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = { onAction("not_interested") }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = TextMuted, modifier = Modifier.size(17.dp))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = if (compact) 5.dp else 9.dp)) {
                        Surface(color = StripeViolet.copy(alpha = .14f), shape = RectangleShape) {
                            Text("${(item.score * 100).toInt()}% MATCH", color = StripeIris, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .5.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
                        }
                        Text("${item.mediaType.uppercase()} · ${item.runtimeMinutes ?: 45}M", color = TextMuted, fontSize = 10.sp, letterSpacing = .4.sp)
                    }
                }
            }
            item.overview?.takeIf { it.isNotBlank() }?.let { overview -> Text(overview, color = TextSecondary, fontFamily = EditorialSerif, fontSize = if (compact) 13.sp else 14.sp, lineHeight = if (compact) 17.sp else 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = if (compact) 6.dp else 9.dp)) }
            Row(modifier = Modifier.fillMaxWidth().padding(top = if (compact) 6.dp else 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = StripeIris, modifier = Modifier.size(15.dp))
                Text(item.explanation, color = TextSecondary, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(start = 7.dp).weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = if (compact) 6.dp else 9.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onAction("accepted") },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shape = RectangleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = TextPrimary, contentColor = MaterialTheme.colorScheme.background),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("WATCH NOW", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = .6.sp)
                }
                OutlinedButton(onClick = { onAction("not_now") }, modifier = Modifier.heightIn(min = 48.dp), shape = RectangleShape, border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle), contentPadding = PaddingValues(horizontal = 15.dp)) {
                    Text("LATER", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = .6.sp)
                }
            }
            HorizontalDivider(modifier = Modifier.padding(top = if (compact) 6.dp else 9.dp), color = BorderSubtle)
        }
    }
}
