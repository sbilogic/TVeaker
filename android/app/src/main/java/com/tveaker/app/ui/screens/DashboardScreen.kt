package com.tveaker.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
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

    val totalActive = state.activeShows.size
    val totalRemainingEps = state.activeShows.sumOf { it.remainingEpisodes }
    val totalMins = state.activeShows.sumOf { it.unwatchedMinutes }
    val totalHours = totalMins / 60
    val totalDays = totalMins / 1440

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "TVeaker",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = AccentCyan.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AccentCyan.copy(alpha = 0.3f))
                        ) {
                            Text(
                                "PRO",
                                color = AccentCyan,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.triggerSync("incremental") },
                        enabled = !state.isSyncing
                    ) {
                        if (state.isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = AccentCyan,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Sync", tint = AccentCyan)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BgBase,
                    titleContentColor = TextPrimary
                )
            )
        },
        containerColor = BgBase
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Metrics Ribbon
            if (state.activeShows.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricCard(
                            label = "ACTIVE SHOWS",
                            value = "$totalActive",
                            subText = "$totalRemainingEps eps left",
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            label = "REMAINING TIME",
                            value = if (totalDays > 0) "${totalDays}d ${totalHours % 24}h" else "${totalHours}h",
                            subText = "$totalMins total mins",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Header Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Active In-Progress",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "${state.activeShows.size} shows",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }

            if (state.activeShows.isEmpty() && !state.isLoading) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = BgSurface),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = "No active shows tracked. Connect your account or import watch data in Settings.",
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
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (state.recommendations.isEmpty() && !state.isLoading) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = BgSurface),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = "Sync your watch history to see recommendations.",
                            color = TextSecondary,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                }
            } else {
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.recommendations) { rec ->
                            RecommendationMiniCard(
                                item = rec,
                                onAction = { action ->
                                    viewModel.submitFeedback(rec.candidateId, action)
                                }
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
fun MetricCard(label: String, value: String, subText: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = BgSurface),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                letterSpacing = 0.5.sp
            )
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            Text(
                text = subText,
                fontSize = 11.sp,
                color = TextSecondary
            )
        }
    }
}

@Composable
fun ActiveShowCard(show: ShowEstimateDto) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BgSurface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(AccentCyan.copy(alpha = 0.12f))
                            .border(1.dp, AccentCyan.copy(alpha = 0.25f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = show.title.take(1),
                            fontWeight = FontWeight.ExtraBold,
                            color = AccentCyan,
                            fontSize = 16.sp
                        )
                    }

                    Column {
                        Text(
                            text = show.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = "${show.watchedEpisodes}/${show.totalEpisodes} eps • ${show.avgRuntimeMinutes ?: 42}m avg/ep",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                Surface(
                    color = if (show.isCaughtUp) Success.copy(alpha = 0.15f) else AccentCyan.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (show.isCaughtUp) Success.copy(alpha = 0.3f) else AccentCyan.copy(alpha = 0.3f)
                    )
                ) {
                    Text(
                        text = if (show.isCaughtUp) "CAUGHT UP" else show.status.uppercase(),
                        color = if (show.isCaughtUp) Success else AccentCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
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
                        .background(
                            Brush.horizontalGradient(
                                listOf(AccentCyan, AccentIndigo)
                            )
                        )
                )
            }

            // Progress details
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${show.completionPercent}% completed",
                    fontSize = 11.sp,
                    color = TextMuted
                )
                Text(
                    text = if (show.remainingEpisodes > 0) {
                        "${show.remainingEpisodes} left (${show.remainingRuntimeDisplay ?: "${show.unwatchedMinutes}m"})"
                    } else "0 eps left",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentCyan
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Finish date footer
            if (show.estimatedFinishDate != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Target Finish:", color = TextSecondary, fontSize = 11.sp)
                    Text(
                        text = show.estimatedFinishDate.substring(0, minOf(10, show.estimatedFinishDate.length)),
                        color = AccentCyan,
                        fontWeight = FontWeight.Bold,
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
                        fontSize = 10.sp
                    )
                    Text(
                        text = "~${show.daysToFinish} days left",
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }
            } else if (show.isCaughtUp) {
                Text(
                    text = "✓ Up to date with latest episodes",
                    color = Success,
                    fontSize = 11.sp,
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
        modifier = Modifier
            .width(230.dp)
            .height(175.dp),
        colors = CardDefaults.cardColors(containerColor = BgSurface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = item.mediaType.uppercase(),
                        color = AccentCyan,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "${(item.score * 100).toInt()}% MATCH",
                        color = AccentPurple,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = item.title,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = item.explanation,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 2,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = { onAction("accepted") },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.weight(1f).height(28.dp)
                ) {
                    Text("Watch", color = BgBase, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = { onAction("not_now") },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("Later", color = TextSecondary, fontSize = 11.sp)
                }
            }
        }
    }
}
