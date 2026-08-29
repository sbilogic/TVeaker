package com.tveaker.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tveaker.app.data.model.ShowEstimateDto
import com.tveaker.app.ui.theme.*
import com.tveaker.app.ui.viewmodel.ShowsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowsScreen(viewModel: ShowsViewModel) {
    val state by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    val statuses = listOf(
        null to "All",
        "watching" to "Watching",
        "planned" to "Planned",
        "paused" to "Paused",
        "completed" to "Completed",
        "dropped" to "Dropped"
    )

    val filteredShows = remember(state.shows, searchQuery) {
        if (searchQuery.isBlank()) state.shows
        else state.shows.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Tracked Shows", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
                        Text("${filteredShows.size} shows cataloged", fontSize = 11.sp, color = TextMuted)
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search catalog by title...", color = TextMuted, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = StripeCyan) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = StripeCyan,
                    unfocusedBorderColor = BorderSubtle,
                    focusedContainerColor = BgSurfaceElevated,
                    unfocusedContainerColor = BgSurface
                )
            )

            // Status Filter Pills
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 10.dp)
            ) {
                items(statuses) { (statusValue, label) ->
                    val isSelected = state.selectedStatus == statusValue
                    Surface(
                        color = if (isSelected) StripeIris else BgSurface,
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) StripeCyan else BorderSubtle
                        ),
                        modifier = Modifier.clickable { viewModel.setStatusFilter(statusValue) }
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) TextPrimary else TextSecondary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            if (state.isLoading && state.shows.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = StripeIris)
                }
            } else if (state.errorMessage != null && state.shows.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = BgSurface),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("📡 Connection Failed", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                            Text(
                                text = "Cannot reach server at ${state.currentServerUrl}",
                                fontSize = 12.sp,
                                color = TextMuted,
                                modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )

                            Button(
                                onClick = { viewModel.setServerUrl("http://192.168.1.33:8000/") },
                                colors = ButtonDefaults.buttonColors(containerColor = StripeIris),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("📱 Wi-Fi LAN (192.168.1.33:8000)", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedButton(
                                onClick = { viewModel.setServerUrl("http://10.0.2.2:8000/") },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("💻 Emulator (10.0.2.2:8000)", color = TextPrimary, fontSize = 12.sp)
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            TextButton(onClick = { viewModel.loadShows() }) {
                                Text("🔄 Retry Connection", color = StripeCyan, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else if (filteredShows.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No matching shows found.", color = TextSecondary)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredShows) { show ->
                        CleanShowDetailCard(
                            show = show,
                            onStatusChange = { newStatus -> viewModel.updateShowStatus(show.showId, newStatus) },
                            onOpenEpisodes = { viewModel.loadUnwatchedEpisodes(show.showId) },
                            onQuickScrobble = { viewModel.quickScrobble(show.showId) }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }

        // Unwatched Episodes Bottom Sheet
        val unwatchedData = state.selectedShowUnwatched
        if (unwatchedData != null) {
            UnwatchedEpisodesBottomSheet(
                data = unwatchedData,
                onDismiss = { viewModel.dismissEpisodesSheet() },
                onWatchEpisode = { epId ->
                    viewModel.markEpisodeWatched(unwatchedData.showId, epId)
                }
            )
        }
    }
}

@Composable
fun CleanShowDetailCard(
    show: ShowEstimateDto,
    onStatusChange: (String) -> Unit,
    onOpenEpisodes: () -> Unit,
    onQuickScrobble: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenEpisodes() },
        color = BgSurface,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
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
                    if (!show.posterUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(show.posterUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = show.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(width = 44.dp, height = 64.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(width = 44.dp, height = 64.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(StripeIris.copy(alpha = 0.15f))
                                .border(1.dp, StripeIris.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = show.title.take(1),
                                fontWeight = FontWeight.Bold,
                                color = StripeCyan,
                                fontSize = 16.sp
                            )
                        }
                    }

                    Column {
                        Text(
                            text = show.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = "${show.watchedEpisodes}/${show.totalEpisodes} eps • ${show.avgRuntimeMinutes ?: 42}m avg • ${show.episodesPerWeek} eps/wk",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                // Status Tag Dropdown
                Box {
                    val statusDotColor = when (show.status) {
                        "watching" -> StripeEmerald
                        "planned" -> StripeCyan
                        "paused" -> StripeAmber
                        "completed" -> StripeViolet
                        else -> TextMuted
                    }

                    Surface(
                        color = BgSurfaceElevated,
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                        modifier = Modifier.clickable { expanded = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(statusDotColor)
                            )
                            Text(
                                text = show.status.replaceFirstChar { it.uppercase() },
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(BgSurfaceElevated)
                    ) {
                        val statusList = listOf("watching", "planned", "paused", "completed", "dropped")
                        statusList.forEach { status ->
                            DropdownMenuItem(
                                text = { Text(status.replaceFirstChar { it.uppercase() }, color = TextPrimary) },
                                onClick = {
                                    onStatusChange(status)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Progress Bar
            LinearProgressIndicator(
                progress = { (show.completionPercent / 100f).coerceIn(0.01f, 1f) },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = StripeCyan,
                trackColor = Border
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (show.remainingEpisodes > 0) "${show.remainingEpisodes} episodes left (${show.remainingRuntimeDisplay ?: "${show.unwatchedMinutes}m"})" else "✓ Complete",
                    color = StripeCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
                Text(
                    text = "${show.completionPercent.toInt()}% Done",
                    color = TextMuted,
                    fontSize = 10.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (show.estimatedFinishDate != null) "Finish: ${show.estimatedFinishDate.take(10)}" else if (show.isCaughtUp) "✓ Caught up" else "In Progress",
                    color = if (show.isCaughtUp) StripeEmerald else TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (show.remainingEpisodes > 0) {
                        Button(
                            onClick = onQuickScrobble,
                            colors = ButtonDefaults.buttonColors(containerColor = StripeIris),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(26.dp),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("+1 Ep", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                    Button(
                        onClick = onOpenEpisodes,
                        colors = ButtonDefaults.buttonColors(containerColor = BgSurfaceElevated),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(26.dp),
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                    ) {
                        Text("Episodes", color = TextPrimary, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
