package com.tveaker.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tveaker.app.data.model.ShowEstimateDto
import com.tveaker.app.ui.theme.*
import com.tveaker.app.ui.viewmodel.ShowsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowsScreen(viewModel: ShowsViewModel) {
    val state by viewModel.uiState.collectAsState()
    val filterOptions = listOf(
        "All" to null,
        "Watching" to "watching",
        "Planned" to "planned",
        "Paused" to "paused",
        "Completed" to "completed",
        "Dropped" to "dropped"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tracked Shows", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BgPrimary,
                    titleContentColor = TextPrimary
                )
            )
        },
        containerColor = BgPrimary
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Filters row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 12.dp)
            ) {
                items(filterOptions) { (label, statusValue) ->
                    val isSelected = state.selectedStatus == statusValue
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setStatusFilter(statusValue) },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = BgSurface,
                            labelColor = TextSecondary,
                            selectedContainerColor = Accent,
                            selectedLabelColor = BgPrimary
                        )
                    )
                }
            }

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Accent)
                }
            } else if (state.shows.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No shows match this filter.", color = TextSecondary)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.shows) { show ->
                        ShowDetailCard(
                            show = show,
                            onStatusChange = { newStatus -> viewModel.updateShowStatus(show.showId, newStatus) },
                            onToggleSpecials = { viewModel.toggleSpecials(show.showId, false) }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
fun ShowDetailCard(
    show: ShowEstimateDto,
    onStatusChange: (String) -> Unit,
    onToggleSpecials: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

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
                        text = "${show.watchedEpisodes}/${show.totalEpisodes} episodes • ${show.episodesPerWeek} eps/wk",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                Box {
                    Button(
                        onClick = { expanded = true },
                        colors = ButtonDefaults.buttonColors(containerColor = BgSurfaceHover),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text(show.status.capitalize(), color = Accent, fontSize = 12.sp)
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(BgSurface)
                    ) {
                        listOf("watching", "planned", "paused", "completed", "dropped").forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s.capitalize(), color = TextPrimary) },
                                onClick = {
                                    expanded = false
                                    onStatusChange(s)
                                }
                            )
                        }
                    }
                }
            }

            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .padding(top = 10.dp)
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

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (show.estimatedFinishDate != null) "Finish: ${show.estimatedFinishDate.substring(0, minOf(10, show.estimatedFinishDate.length))}" else "Status: ${show.status}",
                    color = TextMuted,
                    fontSize = 12.sp
                )
                Text(
                    text = "${show.completionPercent}% done",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }
        }
    }
}
