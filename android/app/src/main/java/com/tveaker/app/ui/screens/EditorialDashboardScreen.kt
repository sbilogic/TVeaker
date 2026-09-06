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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import android.graphics.Typeface
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tveaker.app.data.model.AppVersionDto
import com.tveaker.app.data.model.NowWatchingDto
import com.tveaker.app.data.model.RecommendationItemDto
import com.tveaker.app.data.model.ShowEstimateDto
import com.tveaker.app.data.model.UnwatchedEpisodesResponseDto
import com.tveaker.app.ui.viewmodel.DashboardViewModel
import com.tveaker.app.ui.theme.LocalCompactMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorialDashboardScreen(
    viewModel: DashboardViewModel,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onOpenUpdates: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val compact = LocalCompactMode.current
    val nowWatching = state.nowWatching
    val focusShow = state.activeShows
        .filter { it.remainingEpisodes > 0 }
        .minWithOrNull(compareBy<ShowEstimateDto> { it.remainingEpisodes }.thenByDescending { it.completionPercent })
        ?: state.activeShows.firstOrNull()
    val queue = state.activeShows
        .filter { it.showId != (nowWatching?.showId ?: focusShow?.showId) }
        .take(4)
    val recommendation = state.recommendations.firstOrNull()
    val availableUpdate = state.serverVersionInfo?.takeIf { state.isNewUpdateAvailable }
    when {
        state.errorMessage != null && nowWatching == null && focusShow == null && state.activeShows.isEmpty() && state.recommendations.isEmpty() -> EditorialErrorState(
            serverUrl = state.currentServerUrl,
            onRetry = viewModel::loadDashboardData,
            modifier = Modifier.fillMaxSize()
        )

        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = if (compact) 8.dp else 18.dp, bottom = if (compact) 20.dp else 32.dp)
        ) {
            item {
                EditorialHeader(
                    isSyncing = state.isSyncing,
                    isDarkTheme = isDarkTheme,
                    hasUpdate = state.isNewUpdateAvailable,
                    onSync = { viewModel.triggerSync("incremental") },
                    onToggleTheme = onToggleTheme,
                    onOpenUpdates = onOpenUpdates
                )
            }

            if (availableUpdate != null) {
                item {
                    EditorialUpdateNotice(
                        version = availableUpdate,
                        onOpenUpdates = onOpenUpdates,
                        modifier = Modifier.padding(top = if (compact) 12.dp else 24.dp)
                    )
                }
            }

            if (state.isOffline) {
                item {
                    EditorialOfflineBanner(
                        modifier = Modifier.padding(top = if (compact) 8.dp else 16.dp)
                    )
                }
            }

            if (nowWatching != null) {
                item {
                    EditorialNowWatchingHero(
                        nowWatching = nowWatching,
                        onChangeEpisode = { viewModel.loadUnwatchedEpisodes(nowWatching.showId) },
                        onMarkWatched = {
                            viewModel.markEpisodeWatched(nowWatching.showId, nowWatching.episodeId)
                        }
                    )
                }
            } else if (focusShow == null) {
                item {
                    if (state.isLoading) {
                        EditorialLoadingState()
                    } else {
                        EditorialEmptyState(onSync = { viewModel.triggerSync("incremental") })
                    }
                }
            } else {
                item {
                    EditorialHero(
                        show = focusShow,
                        onOpenEpisodes = { viewModel.loadUnwatchedEpisodes(focusShow.showId) },
                        onMarkWatched = { viewModel.quickScrobble(focusShow.showId) }
                    )
                }
            }

            recommendation?.let { item ->
                item {
                    EditorialRecommendation(
                        item = item,
                        onAdd = {
                            state.runId?.let { runId ->
                                viewModel.submitFeedback(runId, item.candidateId, "accepted")
                            }
                        },
                        modifier = Modifier.padding(top = if (compact) 18.dp else 34.dp)
                    )
                }
            }

            if (queue.isNotEmpty()) {
                item { EditorialSectionLabel("03 / YOUR QUEUE", Modifier.padding(top = if (compact) 18.dp else 34.dp, bottom = if (compact) 5.dp else 8.dp)) }
                items(queue) { show ->
                    EditorialQueueRow(show = show, onClick = { viewModel.loadUnwatchedEpisodes(show.showId) })
                }
            }
        }
    }

    state.selectedShowUnwatched?.let { data ->
        EditorialEpisodesSheet(
            data = data,
            onDismiss = viewModel::dismissEpisodesSheet,
            onWatchEpisode = { episodeId -> viewModel.markEpisodeWatched(data.showId, episodeId) },
            onSelectNowWatching = viewModel::selectNowWatching
        )
    }
}

@Composable
private fun EditorialNowWatchingHero(
    nowWatching: NowWatchingDto,
    onChangeEpisode: () -> Unit,
    onMarkWatched: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val compact = LocalCompactMode.current
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = if (compact) 14.dp else 24.dp),
        color = colors.surface,
        shape = RectangleShape,
        border = BorderStroke(1.dp, colors.primary)
    ) {
        Column(modifier = Modifier.padding(if (compact) 14.dp else 20.dp)) {
            EditorialSectionLabel("01 / NOW WATCHING")
            Text(
                nowWatching.showTitle,
                color = colors.onSurface,
                fontFamily = FontFamily.Serif,
                fontSize = if (compact) 29.sp else 34.sp,
                lineHeight = if (compact) 31.sp else 36.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
            Text(
                "S${nowWatching.seasonNumber.toString().padStart(2, '0')}E${nowWatching.episodeNumber.toString().padStart(2, '0')} · ${nowWatching.episodeTitle ?: "Untitled episode"}",
                color = colors.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 6.dp)
            )
            Text(
                "Chosen locally. It will only be marked watched when you say so.",
                color = colors.onSurfaceVariant,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = if (compact) 12.dp else 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onChangeEpisode,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shape = RectangleShape
                ) {
                    Text("CHANGE", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onMarkWatched,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shape = RectangleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.onBackground,
                        contentColor = colors.background
                    )
                ) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("MARK WATCHED", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun EditorialHeader(
    isSyncing: Boolean,
    isDarkTheme: Boolean,
    hasUpdate: Boolean,
    onSync: () -> Unit,
    onToggleTheme: () -> Unit,
    onOpenUpdates: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("TVeaker", color = colors.onBackground, fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.7).sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("SYNCED NOW", color = colors.outline, fontSize = 8.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.1.sp)
            IconButton(onClick = onOpenUpdates) {
                Icon(
                    Icons.Default.SystemUpdateAlt,
                    contentDescription = if (hasUpdate) "OTA update available" else "OTA updates",
                    tint = if (hasUpdate) colors.primary else colors.onSurfaceVariant
                )
            }
            IconButton(onClick = onSync) {
                if (isSyncing) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = colors.primary, strokeWidth = 2.dp)
                else Icon(Icons.Default.Sync, contentDescription = "Sync", tint = colors.onSurfaceVariant)
            }
            IconButton(onClick = onToggleTheme) {
                Icon(
                    imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = if (isDarkTheme) "Use light mode" else "Use dark mode",
                    tint = colors.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun EditorialUpdateNotice(
    version: AppVersionDto,
    onOpenUpdates: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val compact = LocalCompactMode.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.surface,
        shape = RectangleShape,
        border = BorderStroke(1.dp, colors.primary)
    ) {
        Row(
            modifier = Modifier.padding(start = if (compact) 10.dp else 14.dp, top = if (compact) 8.dp else 12.dp, end = 6.dp, bottom = if (compact) 8.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)
        ) {
            Box(Modifier.width(3.dp).height(if (compact) 30.dp else 42.dp).background(colors.primary))
            Column(modifier = Modifier.weight(1f)) {
                Text("UPDATE READY", color = colors.primary, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                Text(
                    "TVeaker ${version.versionName}",
                    color = colors.onSurface,
                    fontFamily = FontFamily.Serif,
                    fontSize = 19.sp,
                    lineHeight = 21.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
                version.changelog?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = colors.onSurfaceVariant, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            TextButton(
                onClick = onOpenUpdates,
                colors = ButtonDefaults.textButtonColors(contentColor = colors.onSurface)
            ) {
                Text("OPEN OTA", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(15.dp))
            }
        }
    }
}

@Composable
private fun EditorialHero(show: ShowEstimateDto, onOpenEpisodes: () -> Unit, onMarkWatched: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val compact = LocalCompactMode.current
    val condensed = FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD))
    Column(modifier = Modifier.padding(top = 2.dp)) {
        Box(modifier = Modifier.fillMaxWidth().height(if (compact) 84.dp else 120.dp)) {
            Text(
                text = "TONIGHT",
                color = colors.onBackground,
                fontSize = if (compact) 82.sp else 118.sp,
                lineHeight = if (compact) 84.sp else 120.sp,
                fontWeight = FontWeight.Black,
                fontFamily = condensed,
                letterSpacing = (-3).sp,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Text(
            text = if (show.remainingEpisodes == 1) "One episode. Then you’re done." else "Pick up where you left off.",
            color = colors.onBackground,
            fontFamily = FontFamily.Serif,
            fontSize = if (compact) 14.sp else 17.sp,
            lineHeight = if (compact) 17.sp else 20.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = if (compact) 6.dp else 12.dp)
        )
        Divider(color = colors.primary, thickness = 1.dp)

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = if (compact) 6.dp else 11.dp),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 22.dp),
            verticalAlignment = Alignment.Top
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(artworkUrl(show.backdropUrl ?: show.posterUrl)).crossfade(true).build(),
                contentDescription = "${show.title} poster",
                contentScale = ContentScale.Crop,
                modifier = Modifier.weight(1.04f).height(if (compact) 236.dp else 360.dp).clickable(onClick = onOpenEpisodes)
            )

            Column(modifier = Modifier.weight(.96f).height(if (compact) 236.dp else 360.dp).padding(top = if (compact) 20.dp else 50.dp)) {
                Text(
                    show.title.uppercase(),
                    color = colors.onBackground,
                    fontSize = if (show.title.length > 20) 20.sp else if (compact) 27.sp else 32.sp,
                    lineHeight = if (show.title.length > 20) 22.sp else if (compact) 28.sp else 33.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = condensed,
                    letterSpacing = (-1.5).sp,
                    maxLines = 2,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "${show.remainingEpisodes} EPISODE${if (show.remainingEpisodes == 1) "" else "S"} LEFT",
                    color = colors.outline,
                    fontSize = 9.sp,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(top = if (compact) 4.dp else 8.dp)
                )
                Divider(color = colors.outlineVariant, modifier = Modifier.padding(top = if (compact) 11.dp else 22.dp, bottom = if (compact) 12.dp else 24.dp))
                EditorialSectionLabel("FINISH FORECAST")
                Text(
                    show.remainingRuntimeDisplay ?: "${show.unwatchedMinutes / 60}h ${show.unwatchedMinutes % 60}m",
                    color = colors.onBackground,
                    fontFamily = FontFamily.Serif,
                    fontSize = if (compact) 48.sp else 72.sp,
                    lineHeight = if (compact) 48.sp else 70.sp,
                    modifier = Modifier.padding(top = 3.dp)
                )
                Text(
                    compactDate(show.estimatedFinishDate),
                    color = colors.outline,
                    fontSize = 9.sp,
                    letterSpacing = 1.4.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 3.dp)
                )
                Spacer(Modifier.weight(1f))
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onOpenEpisodes).padding(vertical = if (compact) 6.dp else 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("RESUME", color = colors.onBackground, fontSize = if (compact) 15.sp else 16.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    Icon(Icons.Default.ArrowForward, null, tint = colors.primary, modifier = Modifier.size(20.dp))
                }
                Divider(color = colors.onBackground)
            }
        }
    }
}

@Composable
private fun EditorialLoadingState() {
    val colors = MaterialTheme.colorScheme
    val compact = LocalCompactMode.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (compact) 36.dp else 72.dp, bottom = if (compact) 26.dp else 48.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp)
    ) {
        Text(
            text = "CURATING YOUR NEXT WATCH",
            color = colors.primary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.6.sp
        )
        Text(
            text = "Reading your watch history…",
            color = colors.onBackground,
            fontSize = if (compact) 23.sp else 25.sp,
            fontWeight = FontWeight.SemiBold
        )
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = colors.primary,
            trackColor = colors.outline.copy(alpha = 0.18f)
        )
    }
}

private fun compactDate(value: String?): String {
    if (value.isNullOrBlank()) return "AT YOUR PACE"
    val date = value.substringBefore('T').split('-')
    if (date.size != 3) return value.uppercase()
    val month = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
        .getOrNull(date[1].toIntOrNull()?.minus(1) ?: -1) ?: return value.uppercase()
    return "$month ${date[2]}"
}

@Composable
private fun EditorialDatum(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val compact = LocalCompactMode.current
    Column(modifier) {
        Text(label, color = colors.outline, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp)
        Text(value, color = colors.onBackground, fontFamily = FontFamily.Serif, fontSize = if (compact) 20.sp else 22.sp, lineHeight = if (compact) 22.sp else 24.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun EditorialRecommendation(
    item: RecommendationItemDto,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val compact = LocalCompactMode.current
    Column(modifier) {
        Divider(color = colors.outlineVariant)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = if (compact) 6.dp else 12.dp, bottom = if (compact) 5.dp else 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            EditorialSectionLabel("NEXT RECOMMENDATION")
            Text("WHY THIS?  →", color = colors.primary, fontFamily = FontFamily.Serif, fontSize = 11.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 18.dp), verticalAlignment = Alignment.Top) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(artworkUrl(item.backdropUrl ?: item.posterUrl)).crossfade(true).build(),
                contentDescription = "${item.title} poster",
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(if (compact) 58.dp else 80.dp).height(if (compact) 58.dp else 80.dp)
            )
            Column(modifier = Modifier.weight(1f).height(if (compact) 58.dp else 80.dp)) {
                Text(item.title, color = colors.onBackground, fontFamily = FontFamily.Serif, fontSize = if (compact) 20.sp else 28.sp, lineHeight = if (compact) 21.sp else 29.sp)
                Spacer(Modifier.weight(1f))
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(onClick = onAdd).padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("ADD TO WATCHLIST", color = colors.onBackground, fontSize = 9.sp, letterSpacing = 1.sp)
                    Icon(Icons.Default.Add, null, tint = colors.onBackground, modifier = Modifier.size(17.dp))
                }
                Divider(color = colors.onBackground)
            }
        }
    }
}

@Composable
private fun EditorialQueueRow(show: ShowEstimateDto, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val compact = LocalCompactMode.current
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick).padding(vertical = if (compact) 6.dp else 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("${show.completionPercent.toInt()}%", color = colors.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(42.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(show.title, color = colors.onBackground, fontFamily = FontFamily.Serif, fontSize = if (compact) 17.sp else 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${show.remainingEpisodes} left · ${show.remainingRuntimeDisplay ?: "${show.unwatchedMinutes}m"}", color = colors.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Icon(Icons.Default.ArrowForward, null, tint = colors.outline, modifier = Modifier.size(18.dp))
    }
    Divider(color = colors.outlineVariant)
}

@Composable
private fun EditorialEmptyState(onSync: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val compact = LocalCompactMode.current
    Column(modifier = Modifier.padding(top = if (compact) 56.dp else 76.dp)) {
        EditorialSectionLabel("YOUR LIBRARY")
        Text("NOTHING\nQUEUED.", color = colors.onBackground, fontSize = if (compact) 56.sp else 62.sp, lineHeight = if (compact) 50.sp else 55.sp, fontWeight = FontWeight.Black, letterSpacing = (-3).sp, modifier = Modifier.padding(top = 8.dp))
        Text("Sync your Trakt history or start tracking a show to build tonight’s edit.", color = colors.onSurfaceVariant, fontFamily = FontFamily.Serif, fontSize = if (compact) 16.sp else 18.sp, lineHeight = if (compact) 22.sp else 24.sp, modifier = Modifier.padding(top = if (compact) 18.dp else 24.dp))
        Button(onClick = onSync, shape = RectangleShape, colors = ButtonDefaults.buttonColors(containerColor = colors.onBackground, contentColor = colors.background), modifier = Modifier.padding(top = if (compact) 20.dp else 26.dp)) {
            Icon(Icons.Default.Sync, null); Spacer(Modifier.width(8.dp)); Text("SYNC TRAKT", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EditorialErrorState(serverUrl: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val compact = LocalCompactMode.current
    Column(modifier.padding(if (compact) 22.dp else 26.dp), verticalArrangement = Arrangement.Center) {
        EditorialSectionLabel("CONNECTION")
        Text("CAN’T REACH\nYOUR LIBRARY.", color = colors.onBackground, fontSize = 43.sp, lineHeight = 40.sp, fontWeight = FontWeight.Black, letterSpacing = (-2).sp, modifier = Modifier.padding(top = 10.dp))
        Text(serverUrl, color = colors.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(top = if (compact) 11.dp else 14.dp, bottom = if (compact) 20.dp else 24.dp))
        Button(onClick = onRetry, shape = RectangleShape, colors = ButtonDefaults.buttonColors(containerColor = colors.onBackground, contentColor = colors.background)) {
            Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("TRY AGAIN")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorialEpisodesSheet(
    data: UnwatchedEpisodesResponseDto,
    onDismiss: () -> Unit,
    onWatchEpisode: (Int) -> Unit,
    onSelectNowWatching: (Int) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val compact = LocalCompactMode.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        contentColor = colors.onSurface,
        shape = RectangleShape,
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.outline) }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = if (compact) 16.dp else 20.dp).padding(bottom = if (compact) 20.dp else 28.dp)) {
            EditorialSectionLabel("REMAINING EPISODES")
            Text(data.title, color = colors.onSurface, fontFamily = FontFamily.Serif, fontSize = if (compact) 32.sp else 34.sp, lineHeight = if (compact) 34.sp else 36.sp, modifier = Modifier.padding(top = 4.dp))
            Text("${data.remainingEpisodes} remaining · ${data.unwatchedMinutes / 60}h ${data.unwatchedMinutes % 60}m", color = colors.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = if (compact) 12.dp else 16.dp))
            Divider(color = colors.outlineVariant)
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = if (compact) 400.dp else 440.dp)) {
                items(data.unwatchedEpisodes) { episode ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = if (compact) 10.dp else 13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("S${episode.seasonNumber.toString().padStart(2, '0')}E${episode.episodeNumber.toString().padStart(2, '0')}", color = colors.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(58.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(episode.title ?: "Untitled episode", color = colors.onSurface, fontFamily = FontFamily.Serif, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            episode.runtimeMinutes?.let { Text("$it min", color = colors.outline, fontSize = 10.sp) }
                        }
                        Row {
                            IconButton(onClick = { onSelectNowWatching(episode.id) }) {
                                Icon(Icons.Default.PlayArrow, "Watch now", tint = colors.primary)
                            }
                            IconButton(onClick = { onWatchEpisode(episode.id) }) {
                                Icon(Icons.Default.CheckCircle, "Mark watched", tint = colors.onSurfaceVariant)
                            }
                        }
                    }
                    Divider(color = colors.outlineVariant)
                }
            }
        }
    }
}
