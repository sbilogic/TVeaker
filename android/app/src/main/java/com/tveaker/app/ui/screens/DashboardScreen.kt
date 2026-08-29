package com.tveaker.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tveaker.app.data.model.RecommendationItemDto
import com.tveaker.app.data.model.ShowEstimateDto
import com.tveaker.app.ui.theme.*
import com.tveaker.app.ui.viewmodel.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: DashboardViewModel) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TVeaker", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(
                        onClick = { viewModel.triggerSync("incremental") },
                        enabled = !state.isSyncing
                    ) {
                        if (state.isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Accent,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Sync", tint = Accent)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BgPrimary,
                    titleContentColor = TextPrimary
                )
            )
        },
        containerColor = BgPrimary
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header Section
            item {
                Text(
                    text = "Active Watching",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
            }

            if (state.activeShows.isEmpty() && !state.isLoading) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = BgSurface)
                    ) {
                        Text(
                            text = "No active shows tracked. Connect your account or mark a show as watching.",
                            color = TextSecondary,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                }
            } else {
                items(state.activeShows) { show ->
                    ActiveShowCard(show = show)
                }
            }

            // Recommendations Section
            item {
                Text(
                    text = "Recommended for You",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (state.recommendations.isNotEmpty()) {
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(state.recommendations) { rec ->
                            RecommendationMiniCard(
                                item = rec,
                                onAction = { action -> viewModel.submitFeedback(rec.candidateId, action) }
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun ActiveShowCard(show: ShowEstimateDto) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BgSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = show.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = "${show.watchedEpisodes}/${show.totalEpisodes} eps (${show.completionPercent}%)",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
                Surface(
                    color = if (show.isCaughtUp) Success.copy(alpha = 0.15f) else Accent.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (show.isCaughtUp) "CAUGHT UP" else show.status.uppercase(),
                        color = if (show.isCaughtUp) Success else Accent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Border)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(show.completionPercent / 100f)
                        .fillMaxHeight()
                        .background(Accent)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Finish date footer
            if (show.estimatedFinishDate != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Target Finish:", color = TextSecondary, fontSize = 12.sp)
                    Text(
                        text = show.estimatedFinishDate.substring(0, minOf(10, show.estimatedFinishDate.length)),
                        color = Accent,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${show.episodesPerWeek} eps/wk (${show.paceSource})",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "~${show.daysToFinish} days left",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            } else if (show.isCaughtUp) {
                Text(
                    text = "✓ Up to date with latest episodes",
                    color = Success,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun RecommendationMiniCard(
    item: RecommendationItemDto,
    onAction: (String) -> Unit
) {
    Card(
        modifier = Modifier.width(260.dp),
        colors = CardDefaults.cardColors(containerColor = BgSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = item.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = TextPrimary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${(item.score * 100).toInt()}%",
                    color = Accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
            Text(
                text = "${item.mediaType.uppercase()} • ${item.runtimeMinutes ?: 45}m",
                color = TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            Text(
                text = item.explanation,
                color = TextMuted,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                maxLines = 2,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = { onAction("accepted") },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    modifier = Modifier.weight(1f).height(32.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Watch", color = BgPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = { onAction("not_now") },
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Later", color = TextSecondary, fontSize = 11.sp)
                }
            }
        }
    }
}
