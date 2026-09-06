package com.tveaker.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tveaker.app.data.model.ShowEstimateDto
import com.tveaker.app.ui.theme.BorderSubtle
import com.tveaker.app.ui.theme.LocalCompactMode
import com.tveaker.app.ui.theme.StripeEmerald
import com.tveaker.app.ui.theme.StripeIris
import com.tveaker.app.ui.theme.StripeViolet
import com.tveaker.app.ui.theme.TextMuted
import com.tveaker.app.ui.theme.TextPrimary
import com.tveaker.app.ui.theme.TextSecondary
import com.tveaker.app.ui.viewmodel.ShowsViewModel

@Composable
fun ShowsScreen(viewModel: ShowsViewModel) {
    val state by viewModel.uiState.collectAsState()
    val compact = LocalCompactMode.current
    var searchQuery by remember { mutableStateOf("") }
    val statuses = listOf(null to "All", "watching" to "Watching", "planned" to "Planned", "paused" to "Paused", "completed" to "Completed", "dropped" to "Dropped")
    val filteredShows = remember(state.shows, searchQuery) {
        if (searchQuery.isBlank()) state.shows else state.shows.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = if (compact) 4.dp else 10.dp, bottom = if (compact) 16.dp else 24.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 9.dp)
    ) {
        item {
            EditorialPageHeader(
                index = "02",
                title = "LIBRARY",
                subtitle = "Everything you are watching, paused, planned, or nearly finished."
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
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search your library", color = TextMuted, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = StripeIris) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RectangleShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = StripeIris,
                    unfocusedBorderColor = BorderSubtle,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)) {
                items(statuses) { (status, label) ->
                    EditorialFilterChip(label, state.selectedStatus == status) { viewModel.setStatusFilter(status) }
                }
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                EditorialSectionLabel("TRACKED SHOWS")
                Text("${filteredShows.size} TITLES", color = TextMuted, fontSize = 9.sp, letterSpacing = 1.sp)
            }
        }
        item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }

        if (state.isLoading && state.shows.isEmpty()) {
            item { EditorialInlineLoading("READING YOUR LIBRARY") }
        } else if (state.errorMessage != null && state.shows.isEmpty()) {
            item {
                EditorialEmptyMessage(
                    title = "LIBRARY\nOFFLINE.",
                    body = "Cannot reach ${state.currentServerUrl}. Check the gateway in Settings and try again."
                )
            }
        } else if (filteredShows.isEmpty()) {
            item { EditorialEmptyMessage("NO SHOWS\nHERE YET.", "Try another status or search term.") }
        } else {
            items(filteredShows, key = { it.showId }) { show ->
                CleanShowDetailCard(
                    show = show,
                    onStatusChange = { viewModel.updateShowStatus(show.showId, it) },
                    onToggleSpecials = { viewModel.toggleSpecials(show.showId, show.includeSpecials) },
                    onOpenEpisodes = { viewModel.loadUnwatchedEpisodes(show.showId) },
                    onQuickScrobble = { viewModel.quickScrobble(show.showId) }
                )
            }
        }
    }

    state.selectedShowUnwatched?.let { data ->
        EditorialEpisodesSheet(
            data = data,
            nowWatchingEpisodeId = null,
            onDismiss = viewModel::dismissEpisodesSheet,
            onWatchEpisode = { viewModel.markEpisodeWatched(data.showId, it) },
            onSelectNowWatching = viewModel::selectNowWatching
        )
    }
}

@Composable
fun CleanShowDetailCard(
    show: ShowEstimateDto,
    onStatusChange: (String) -> Unit,
    onToggleSpecials: () -> Unit,
    onOpenEpisodes: () -> Unit,
    onQuickScrobble: () -> Unit
) {
    val compact = LocalCompactMode.current
    var statusMenuOpen by remember { mutableStateOf(false) }
    val statusColor = when (show.status) {
        "watching" -> StripeEmerald
        "completed" -> StripeViolet
        else -> StripeIris
    }

    EditorialPageCard(modifier = Modifier.clickable(onClick = onOpenEpisodes)) {
        Column(modifier = Modifier.padding(vertical = if (compact) 5.dp else 8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 14.dp), verticalAlignment = Alignment.Top) {
                if (!show.posterUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(artworkUrl(show.posterUrl)).crossfade(true).build(),
                        contentDescription = show.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(width = if (compact) 46.dp else 54.dp, height = if (compact) 66.dp else 78.dp)
                    )
                } else {
                    Box(modifier = Modifier.size(width = if (compact) 46.dp else 54.dp, height = if (compact) 66.dp else 78.dp).background(StripeIris.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = StripeIris, modifier = Modifier.size(22.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(show.title, color = TextPrimary, fontFamily = EditorialSerif, fontSize = if (compact) 18.sp else 21.sp, lineHeight = if (compact) 20.sp else 23.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${show.watchedEpisodes}/${show.totalEpisodes} EPISODES · ${show.avgRuntimeMinutes ?: 42}M AVG", color = TextMuted, fontSize = 9.sp, letterSpacing = .8.sp, modifier = Modifier.padding(top = if (compact) 4.dp else 8.dp))
                    Box {
                        Surface(modifier = Modifier.padding(top = if (compact) 3.dp else 6.dp).clickable { statusMenuOpen = true }, color = Color.Transparent, shape = RectangleShape) {
                            Text(show.status.uppercase(), color = statusColor, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .7.sp, modifier = Modifier.padding(vertical = 4.dp))
                        }
                        DropdownMenu(expanded = statusMenuOpen, onDismissRequest = { statusMenuOpen = false }) {
                            listOf("watching", "planned", "paused", "completed", "dropped").forEach { status ->
                                DropdownMenuItem(text = { Text(status.replaceFirstChar { it.uppercase() }) }, onClick = { statusMenuOpen = false; onStatusChange(status) })
                            }
                        }
                    }
                    TextButton(
                        onClick = onToggleSpecials,
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                    ) {
                        Text(
                            if (show.includeSpecials) "SPECIALS: ON" else "SPECIALS: OFF",
                            color = if (show.includeSpecials) StripeEmerald else TextMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = .7.sp,
                        )
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = if (compact) 6.dp else 9.dp), color = BorderSubtle)
            LinearProgressBar(show.completionPercent / 100f)
            Row(modifier = Modifier.fillMaxWidth().padding(top = if (compact) 4.dp else 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (show.remainingEpisodes > 0) "${show.remainingEpisodes} EPISODES LEFT · ${show.remainingRuntimeDisplay ?: "${show.unwatchedMinutes}M"}" else "CAUGHT UP", color = if (show.remainingEpisodes > 0) StripeIris else StripeEmerald, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .6.sp)
                Text("${show.completionPercent.toInt()}%", color = TextMuted, fontSize = 9.sp, letterSpacing = .5.sp)
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = if (compact) 5.dp else 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    EditorialSectionLabel("FINISH FORECAST")
                    Text(show.estimatedFinishDate?.take(10) ?: if (show.isCaughtUp) "UP TO DATE" else "AT YOUR PACE", color = TextPrimary, fontFamily = EditorialSerif, fontSize = 15.sp, modifier = Modifier.padding(top = 2.dp))
                }
                if (show.remainingEpisodes > 0) {
                    Button(onClick = onQuickScrobble, shape = RectangleShape, colors = ButtonDefaults.buttonColors(containerColor = TextPrimary, contentColor = MaterialTheme.colorScheme.background), modifier = Modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(4.dp)); Text("+1 EP", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(top = if (compact) 6.dp else 9.dp), color = BorderSubtle)
        }
    }
}

@Composable
private fun LinearProgressBar(progress: Float) {
    androidx.compose.material3.LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth().height(2.dp),
        color = StripeIris,
        trackColor = BorderSubtle
    )
}
