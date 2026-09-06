package com.tveaker.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tveaker.app.data.model.RecommendationItemDto
import com.tveaker.app.data.model.ShowEstimateDto
import com.tveaker.app.data.model.UnwatchedEpisodesResponseDto
import com.tveaker.app.ui.viewmodel.DashboardViewModel

/*
 * PROTOTYPE ONLY: one real-data premium home direction, enabled only in debug builds.
 * Question: can a decision-first, entertainment-native surface feel premium without
 * turning into either a streaming poster wall or a developer analytics dashboard?
 */

private val PrototypeInk = Color(0xFF07080A)
private val PrototypeSurface = Color(0xFF111318)
private val PrototypeSurfaceRaised = Color(0xFF171A20)
private val PrototypeText = Color(0xFFF7F7F5)
private val PrototypeMuted = Color(0xFF989DA8)
private val PrototypeFaint = Color(0xFF5E6470)
private val PrototypeLine = Color(0xFF23262D)
private val PrototypeAccent = Color(0xFF7C6CFF)
private val PrototypeSuccess = Color(0xFF58D6A7)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumDashboardPrototype(viewModel: DashboardViewModel) {
    val state by viewModel.uiState.collectAsState()
    val focusShow = state.activeShows
        .filter { it.remainingEpisodes > 0 }
        .minWithOrNull(compareBy<ShowEstimateDto> { it.remainingEpisodes }.thenByDescending { it.completionPercent })
        ?: state.activeShows.firstOrNull()
    val nextShows = state.activeShows.filter { it.showId != focusShow?.showId }.take(3)
    val recommendation = state.recommendations.firstOrNull()

    Scaffold(containerColor = PrototypeInk) { scaffoldPadding ->
        when {
            state.isLoading && focusShow == null -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(scaffoldPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = PrototypeAccent, strokeWidth = 2.dp)
                }
            }

            state.errorMessage != null && focusShow == null -> {
                PrototypeErrorState(
                    serverUrl = state.currentServerUrl,
                    onRetry = viewModel::loadDashboardData,
                    modifier = Modifier.fillMaxSize().padding(scaffoldPadding)
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(scaffoldPadding),
                    contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    item {
                        PrototypeHeader(
                            isSyncing = state.isSyncing,
                            onSync = { viewModel.triggerSync("incremental") }
                        )
                    }

                    if (state.isNewUpdateAvailable && state.serverVersionInfo != null) {
                        item {
                            PrototypeUpdateNotice(
                                version = state.serverVersionInfo?.versionName.orEmpty(),
                                modifier = Modifier.padding(top = 20.dp)
                            )
                        }
                    }

                    if (focusShow != null) {
                        item {
                            Column(modifier = Modifier.padding(top = 34.dp)) {
                                Text(
                                    text = "TONIGHT",
                                    color = PrototypeAccent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 1.8.sp
                                )
                                Text(
                                    text = if (focusShow.remainingEpisodes == 1) {
                                        "One episode.\nThen you're done."
                                    } else {
                                        "Keep the story\nmoving."
                                    },
                                    color = PrototypeText,
                                    fontSize = 34.sp,
                                    lineHeight = 38.sp,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = (-0.8).sp,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 22.dp)
                                )
                                PrototypeFocusShow(
                                    show = focusShow,
                                    onOpenEpisodes = { viewModel.loadUnwatchedEpisodes(focusShow.showId) },
                                    onMarkWatched = { viewModel.quickScrobble(focusShow.showId) }
                                )
                            }
                        }
                    }

                    if (nextShows.isNotEmpty()) {
                        item {
                            PrototypeSectionHeader(
                                eyebrow = "YOUR QUEUE",
                                title = "Next in line",
                                modifier = Modifier.padding(top = 34.dp, bottom = 8.dp)
                            )
                        }
                        items(nextShows) { show ->
                            PrototypeQueueRow(
                                show = show,
                                onClick = { viewModel.loadUnwatchedEpisodes(show.showId) }
                            )
                        }
                    }

                    if (recommendation != null) {
                        item {
                            PrototypeSectionHeader(
                                eyebrow = "AFTER THAT",
                                title = "A considered next pick",
                                modifier = Modifier.padding(top = 34.dp, bottom = 12.dp)
                            )
                            PrototypeRecommendation(recommendation)
                        }
                    }

                    item {
                        val totalMinutes = state.activeShows.sumOf { it.unwatchedMinutes }
                        PrototypeLibrarySummary(
                            activeShows = state.activeShows.size,
                            remainingHours = totalMinutes / 60,
                            modifier = Modifier.padding(top = 34.dp)
                        )
                    }
                }
            }
        }

        state.selectedShowUnwatched?.let { data ->
            PremiumEpisodesSheet(
                data = data,
                onDismiss = viewModel::dismissEpisodesSheet,
                onWatchEpisode = { episodeId -> viewModel.markEpisodeWatched(data.showId, episodeId) }
            )
        }
    }
}

@Composable
private fun PrototypeHeader(isSyncing: Boolean, onSync: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "TVEAKER",
            color = PrototypeText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.4.sp
        )
        Surface(
            onClick = onSync,
            color = Color.Transparent,
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(13.dp),
                        color = PrototypeAccent,
                        strokeWidth = 1.5.dp
                    )
                    Text("Syncing", color = PrototypeMuted, fontSize = 12.sp)
                } else {
                    Box(
                        modifier = Modifier.size(7.dp).clip(CircleShape).background(PrototypeSuccess)
                    )
                    Text("Up to date", color = PrototypeMuted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun PrototypeFocusShow(
    show: ShowEstimateDto,
    onOpenEpisodes: () -> Unit,
    onMarkWatched: () -> Unit
) {
    val progress by animateFloatAsState(
        targetValue = (show.completionPercent / 100f).coerceIn(0f, 1f),
        animationSpec = tween(700),
        label = "showProgress"
    )

    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenEpisodes),
        color = PrototypeSurface,
        shape = RoundedCornerShape(20.dp)
    ) {
        Column {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(1.72f)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(show.backdropUrl ?: show.posterUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = show.title,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.08f),
                            0.58f to Color.Black.copy(alpha = 0.18f),
                            1f to PrototypeSurface
                        )
                    )
                )
                Surface(
                    color = Color.Black.copy(alpha = 0.64f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.align(Alignment.TopStart).padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = PrototypeText, modifier = Modifier.size(13.dp))
                        Text(
                            text = show.remainingRuntimeDisplay ?: "${show.unwatchedMinutes} min left",
                            color = PrototypeText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = show.title,
                            color = PrototypeText,
                            fontSize = 24.sp,
                            lineHeight = 28.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.35).sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${show.remainingEpisodes} ${if (show.remainingEpisodes == 1) "episode" else "episodes"} remaining",
                            color = PrototypeMuted,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Text(
                        text = "${show.completionPercent.toInt()}%",
                        color = PrototypeFaint,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                LinearProgressIndicator(
                    progress = { progress },
                    color = PrototypeAccent,
                    trackColor = PrototypeLine,
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(2.dp).clip(CircleShape)
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onMarkWatched,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrototypeText,
                            contentColor = PrototypeInk
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                        modifier = Modifier.height(48.dp).weight(1f)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Mark next watched", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    IconButton(
                        onClick = onOpenEpisodes,
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(PrototypeSurfaceRaised)
                    ) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "View episodes", tint = PrototypeText)
                    }
                }
            }
        }
    }
}

@Composable
private fun PrototypeSectionHeader(eyebrow: String, title: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = eyebrow,
            color = PrototypeFaint,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.6.sp
        )
        Text(
            text = title,
            color = PrototypeText,
            fontSize = 20.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.25).sp,
            modifier = Modifier.padding(top = 5.dp)
        )
    }
}

@Composable
private fun PrototypeQueueRow(show: ShowEstimateDto, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(show.posterUrl).crossfade(true).build(),
            contentDescription = show.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(width = 42.dp, height = 58.dp).clip(RoundedCornerShape(8.dp))
        )
        Column(modifier = Modifier.weight(1f).padding(horizontal = 13.dp)) {
            Text(
                text = show.title,
                color = PrototypeText,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${show.remainingEpisodes} left  ·  ${show.remainingRuntimeDisplay ?: "${show.unwatchedMinutes} min"}",
                color = PrototypeMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Icon(Icons.Default.ArrowForward, contentDescription = null, tint = PrototypeFaint, modifier = Modifier.size(17.dp))
    }
    Divider(color = PrototypeLine, thickness = 1.dp)
}

@Composable
private fun PrototypeRecommendation(item: RecommendationItemDto) {
    Surface(color = PrototypeSurface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(item.posterUrl).crossfade(true).build(),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 48.dp, height = 68.dp).clip(RoundedCornerShape(8.dp))
            )
            Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                Text(item.title, color = PrototypeText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = item.explanation,
                    color = PrototypeMuted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Icon(Icons.Default.ArrowForward, contentDescription = null, tint = PrototypeFaint, modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
private fun PrototypeLibrarySummary(activeShows: Int, remainingHours: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Divider(color = PrototypeLine)
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("$activeShows active shows", color = PrototypeMuted, fontSize = 12.sp)
            Text("$remainingHours hours remaining", color = PrototypeMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun PrototypeUpdateNotice(version: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = PrototypeSurfaceRaised,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, PrototypeLine)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(PrototypeAccent))
            Text("Update $version ready", color = PrototypeText, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp).weight(1f))
            Text("Open settings", color = PrototypeAccent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PrototypeErrorState(serverUrl: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start
    ) {
        Text("TVEAKER", color = PrototypeFaint, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Text("Can't reach your library.", color = PrototypeText, fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 12.dp))
        Text(serverUrl, color = PrototypeMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp, bottom = 18.dp))
        Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = PrototypeText, contentColor = PrototypeInk)) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Try again")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PremiumEpisodesSheet(
    data: UnwatchedEpisodesResponseDto,
    onDismiss: () -> Unit,
    onWatchEpisode: (Int) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = PrototypeSurface,
        contentColor = PrototypeText,
        dragHandle = { BottomSheetDefaults.DragHandle(color = PrototypeFaint) }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 28.dp)) {
            Text(data.title, color = PrototypeText, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "${data.remainingEpisodes} ${if (data.remainingEpisodes == 1) "episode" else "episodes"} · ${data.unwatchedMinutes / 60}h ${data.unwatchedMinutes % 60}m",
                color = PrototypeMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
            )
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                items(data.unwatchedEpisodes) { episode ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "S${episode.seasonNumber} E${episode.episodeNumber}",
                                color = PrototypeAccent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                episode.title ?: "Untitled episode",
                                color = PrototypeText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                            episode.runtimeMinutes?.let {
                                Text("$it min", color = PrototypeMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
                            }
                        }
                        IconButton(onClick = { onWatchEpisode(episode.id) }) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Mark watched", tint = PrototypeMuted)
                        }
                    }
                    Divider(color = PrototypeLine)
                }
            }
        }
    }
}
