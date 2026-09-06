package com.tveaker.app.ui.screens

import android.graphics.Typeface
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
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
import com.tveaker.app.ui.theme.HeroArtworkScrim
import com.tveaker.app.ui.theme.LocalCompactMode
import com.tveaker.app.ui.theme.StripeCyan
import com.tveaker.app.ui.theme.StripeEmerald
import com.tveaker.app.ui.theme.StripeIris
import com.tveaker.app.ui.theme.StripeViolet
import com.tveaker.app.ui.viewmodel.DashboardViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    var showPickerVisible by remember { mutableStateOf(false) }
    val focusShow = state.activeShows.find { it.showId == state.selectedHeroShowId && it.remainingEpisodes > 0 }
        ?: state.activeShows.find { it.showId == nowWatching?.showId && it.remainingEpisodes > 0 }
        ?: state.activeShows.filter { it.remainingEpisodes > 0 }.minWithOrNull(
            compareBy<ShowEstimateDto> { it.remainingEpisodes }.thenByDescending { it.completionPercent }
        )
        ?: state.activeShows.firstOrNull()
    val queue = state.activeShows
        .filter { it.showId != focusShow?.showId }
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

            if (focusShow != null) {
                item {
                    AnimatedContent(
                        targetState = focusShow,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(280)) togetherWith fadeOut(animationSpec = tween(180))
                        },
                        label = "HeroTransition"
                    ) { currentShow ->
                        EditorialHero(
                            show = currentShow,
                            onOpenEpisodes = { viewModel.loadUnwatchedEpisodes(currentShow.showId) },
                            onSwitchShow = { showPickerVisible = true },
                            onMarkWatched = { viewModel.quickScrobble(currentShow.showId) }
                        )
                    }
                }
            } else {
                item {
                    if (state.isLoading) {
                        EditorialLoadingState()
                    } else {
                        EditorialEmptyState(onSync = { viewModel.triggerSync("incremental") })
                    }
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
                items(queue, key = { it.showId }) { show ->
                    Box(modifier = Modifier.animateItem()) {
                        EditorialQueueRow(
                            show = show,
                            onClick = { viewModel.loadUnwatchedEpisodes(show.showId) },
                            onSetHero = { viewModel.setHeroShow(show.showId) },
                            onQuickScrobble = { viewModel.quickScrobble(show.showId) }
                        )
                    }
                }
            }
        }
    }

    if (showPickerVisible) {
        EditorialShowPickerSheet(
            shows = state.activeShows,
            selectedShowId = focusShow?.showId,
            onSelect = { showId ->
                viewModel.setHeroShow(showId)
                showPickerVisible = false
            },
            onDismiss = { showPickerVisible = false }
        )
    }

    state.selectedShowUnwatched?.let { data ->
        EditorialEpisodesSheet(
            data = data,
            nowWatchingEpisodeId = nowWatching?.episodeId,
            onDismiss = viewModel::dismissEpisodesSheet,
            onWatchEpisode = { episodeId -> viewModel.markEpisodeWatched(data.showId, episodeId) },
            onSelectNowWatching = viewModel::selectNowWatching
        )
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
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(15.dp))
            }
        }
    }
}

@Composable
private fun EditorialHero(
    show: ShowEstimateDto,
    onOpenEpisodes: () -> Unit,
    onSwitchShow: () -> Unit,
    onMarkWatched: () -> Unit
) {
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
        HorizontalDivider(color = colors.primary, thickness = 1.dp)

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = if (compact) 8.dp else 14.dp),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 20.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Artwork container with multi-stop scrim gradient protecting text & overlay badges
            Box(
                modifier = Modifier
                    .weight(1.02f)
                    .height(if (compact) 260.dp else 370.dp)
                    .border(1.dp, colors.outlineVariant, RectangleShape)
                    .clickable(onClick = onOpenEpisodes)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(artworkUrl(show.posterUrl ?: show.backdropUrl))
                        .crossfade(true)
                        .build(),
                    contentDescription = "${show.title} poster",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Multi-stop vertical scrim ensures overlay text and badges remain 100% legible
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(HeroArtworkScrim)
                )

                // Top badges over artwork
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (show.isCaughtUp) {
                        EditorialBadge(
                            text = "CAUGHT UP",
                            tint = StripeEmerald,
                            containerColor = Color.Black.copy(alpha = 0.75f),
                            borderColor = StripeEmerald.copy(alpha = 0.6f)
                        )
                    } else if (show.completionPercent > 0f) {
                        EditorialBadge(
                            text = "${show.completionPercent.toInt()}% COMPLETE",
                            tint = StripeCyan,
                            containerColor = Color.Black.copy(alpha = 0.75f),
                            borderColor = StripeCyan.copy(alpha = 0.6f)
                        )
                    }
                    if (show.genres.isNotEmpty()) {
                        EditorialBadge(
                            text = show.genres.first(),
                            tint = Color.White,
                            containerColor = Color.Black.copy(alpha = 0.65f),
                            borderColor = Color.White.copy(alpha = 0.35f)
                        )
                    }
                }

                // Bottom progress indicator over artwork
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { (show.watchedEpisodes.toFloat() / show.totalEpisodes.coerceAtLeast(1)).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = StripeIris,
                        trackColor = Color.White.copy(alpha = 0.3f)
                    )
                }
            }

            // Right Column: Show title, pace/forecast badges, finish forecast, and action buttons
            Column(
                modifier = Modifier
                    .weight(0.98f)
                    .heightIn(min = if (compact) 260.dp else 370.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        show.title.uppercase(),
                        color = colors.onBackground,
                        fontSize = if (show.title.length > 20) 20.sp else if (compact) 25.sp else 30.sp,
                        lineHeight = if (show.title.length > 20) 22.sp else if (compact) 26.sp else 31.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = condensed,
                        letterSpacing = (-1.5).sp,
                        maxLines = 2,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Pace badges and episode count
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EditorialBadge(
                            text = "${show.remainingEpisodes} EP LEFT",
                            tint = colors.primary
                        )
                        if (show.episodesPerWeek > 0f) {
                            EditorialBadge(
                                text = "${show.episodesPerWeek} EPS/WK",
                                tint = StripeCyan
                            )
                        }
                    }

                    if (show.daysToFinish != null && show.daysToFinish > 0) {
                        Text(
                            "${show.daysToFinish} DAYS TO FINISH",
                            color = colors.outline,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 1.1.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    HorizontalDivider(
                        color = colors.outlineVariant,
                        modifier = Modifier.padding(top = if (compact) 8.dp else 14.dp, bottom = if (compact) 8.dp else 14.dp)
                    )

                    EditorialSectionLabel("FINISH FORECAST")
                    Text(
                        show.remainingRuntimeDisplay ?: "${show.unwatchedMinutes / 60}h ${show.unwatchedMinutes % 60}m",
                        color = colors.onBackground,
                        fontFamily = FontFamily.Serif,
                        fontSize = if (compact) 40.sp else 58.sp,
                        lineHeight = if (compact) 42.sp else 58.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Text(
                        compactDate(show.estimatedFinishDate),
                        color = colors.outline,
                        fontSize = 9.sp,
                        letterSpacing = 1.4.sp,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                // Smooth action affordances (all minimum height >= 48dp)
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = onOpenEpisodes,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            shape = RectangleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = colors.onBackground, contentColor = colors.background),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("RESUME", fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.6.sp)
                        }

                        if (show.remainingEpisodes > 0) {
                            Button(
                                onClick = onMarkWatched,
                                modifier = Modifier.heightIn(min = 48.dp),
                                shape = RectangleShape,
                                colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = Color.White),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(2.dp))
                                Text("+1 EP", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = onSwitchShow,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = RectangleShape,
                        border = BorderStroke(1.dp, colors.outline),
                        contentPadding = PaddingValues(horizontal = 6.dp)
                    ) {
                        Text("SWITCH SHOW", fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = colors.onSurface)
                    }
                }
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

    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(color = colors.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (compact) 8.dp else 12.dp, bottom = if (compact) 6.dp else 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            EditorialSectionLabel("NEXT RECOMMENDATION")
            EditorialBadge(
                text = "${(item.score * 100).toInt()}% MATCH",
                tint = colors.primary,
                containerColor = colors.primary.copy(alpha = 0.14f),
                borderColor = colors.primary.copy(alpha = 0.35f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 16.dp),
            verticalAlignment = Alignment.Top
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(artworkUrl(item.posterUrl ?: item.backdropUrl))
                    .crossfade(true)
                    .build(),
                contentDescription = "${item.title} poster",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = if (compact) 54.dp else 68.dp, height = if (compact) 78.dp else 98.dp)
                    .border(1.dp, colors.outlineVariant, RectangleShape)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = colors.onBackground,
                    fontFamily = FontFamily.Serif,
                    fontSize = if (compact) 19.sp else 23.sp,
                    lineHeight = if (compact) 21.sp else 26.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Badges: Media Type, Runtime, Genres
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${item.mediaType.uppercase()} · ${item.runtimeMinutes ?: 45} MIN",
                        color = colors.onSurfaceVariant,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (item.genres.isNotEmpty()) {
                        Text(
                            text = "· ${item.genres.take(2).joinToString(", ")}",
                            color = colors.outline,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Explanation snippet
                if (item.explanation.isNotBlank()) {
                    Text(
                        text = item.explanation,
                        color = colors.onSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Accessible Add to Watchlist Button
                Button(
                    onClick = onAdd,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = RectangleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.onBackground,
                        contentColor = colors.background
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "ADD TO WATCHLIST",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(top = 12.dp), color = colors.outlineVariant)
    }
}

@Composable
private fun EditorialQueueRow(
    show: ShowEstimateDto,
    onClick: () -> Unit,
    onSetHero: () -> Unit,
    onQuickScrobble: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val compact = LocalCompactMode.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .padding(vertical = if (compact) 8.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail Artwork with subtle border
        if (!show.posterUrl.isNullOrEmpty()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(artworkUrl(show.posterUrl))
                    .crossfade(true)
                    .build(),
                contentDescription = "${show.title} poster",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = if (compact) 44.dp else 48.dp, height = if (compact) 62.dp else 68.dp)
                    .border(1.dp, colors.outlineVariant, RectangleShape)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(width = if (compact) 44.dp else 48.dp, height = if (compact) 62.dp else 68.dp)
                    .background(colors.surfaceContainerHigh)
                    .border(1.dp, colors.outlineVariant, RectangleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${show.completionPercent.toInt()}%",
                    color = colors.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        // Center Content: Title, Badges, Finish Forecast
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = show.title,
                color = colors.onBackground,
                fontFamily = FontFamily.Serif,
                fontSize = if (compact) 16.sp else 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Metadata Badges Row: Genres, Remaining, Runtime
            Row(
                modifier = Modifier.padding(top = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (show.genres.isNotEmpty()) {
                    EditorialBadge(
                        text = show.genres.first(),
                        tint = colors.primary
                    )
                }
                Text(
                    text = "${show.remainingEpisodes} left · ${show.remainingRuntimeDisplay ?: "${show.unwatchedMinutes}m"}",
                    color = colors.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }

            // Finish Estimate Badge
            val finishText = show.estimatedFinishDate?.let { compactDate(it) }
                ?: if (show.isCaughtUp) "UP TO DATE" else "AT YOUR PACE"
            Text(
                text = "EST. FINISH: $finishText",
                color = colors.outline,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.6.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        Spacer(Modifier.width(8.dp))

        // Accessible quick actions >= 48dp
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onSetHero,
                shape = RectangleShape,
                modifier = Modifier.heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text("SET HERO", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = colors.primary)
            }

            if (show.remainingEpisodes > 0) {
                Button(
                    onClick = onQuickScrobble,
                    shape = RectangleShape,
                    modifier = Modifier.heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.onBackground,
                        contentColor = colors.background
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("+1 EP", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    HorizontalDivider(color = colors.outlineVariant)
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
internal fun EditorialEpisodesSheet(
    data: UnwatchedEpisodesResponseDto,
    nowWatchingEpisodeId: Int?,
    onDismiss: () -> Unit,
    onWatchEpisode: (Int) -> Unit,
    onSelectNowWatching: (Int) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val compact = LocalCompactMode.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var resumingEpisodeId by remember { mutableStateOf<Int?>(null) }

    val episodesBySeason = remember(data.unwatchedEpisodes) {
        data.unwatchedEpisodes.groupBy { it.seasonNumber }
    }

    ModalBottomSheet(
        onDismissRequest = {
            scope.launch {
                sheetState.hide()
                onDismiss()
            }
        },
        sheetState = sheetState,
        containerColor = colors.surface,
        contentColor = colors.onSurface,
        shape = RectangleShape,
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.outline) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (compact) 16.dp else 20.dp)
                .padding(bottom = if (compact) 20.dp else 28.dp)
        ) {
            EditorialSectionLabel("REMAINING EPISODES")
            Text(
                data.title,
                color = colors.onSurface,
                fontFamily = FontFamily.Serif,
                fontSize = if (compact) 30.sp else 34.sp,
                lineHeight = if (compact) 32.sp else 36.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                "${data.remainingEpisodes} remaining · ${data.unwatchedMinutes / 60}h ${data.unwatchedMinutes % 60}m",
                color = colors.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = if (compact) 10.dp else 14.dp)
            )
            HorizontalDivider(color = colors.outlineVariant)

            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = if (compact) 400.dp else 460.dp)) {
                episodesBySeason.forEach { (seasonNumber, episodes) ->
                    item(key = "season_$seasonNumber") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            EditorialSectionLabel("SEASON ${seasonNumber.toString().padStart(2, '0')}")
                            Spacer(Modifier.width(8.dp))
                            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.5f), modifier = Modifier.weight(1f))
                        }
                    }

                    items(episodes, key = { it.id }) { episode ->
                        val isNowWatching = episode.id == nowWatchingEpisodeId
                        val isResumingThis = resumingEpisodeId == episode.id

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isNowWatching) colors.surfaceContainerHigh else Color.Transparent)
                                .padding(vertical = if (compact) 8.dp else 10.dp, horizontal = if (isNowWatching) 6.dp else 0.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "S${episode.seasonNumber.toString().padStart(2, '0')}E${episode.episodeNumber.toString().padStart(2, '0')}",
                                color = if (isNowWatching) colors.primary else colors.onSurfaceVariant,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(54.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        episode.title ?: "Untitled episode",
                                        color = colors.onSurface,
                                        fontFamily = FontFamily.Serif,
                                        fontSize = 16.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (isNowWatching) {
                                        Spacer(Modifier.width(6.dp))
                                        EditorialBadge(
                                            text = "ACTIVE",
                                            tint = colors.primary,
                                            containerColor = colors.primary.copy(alpha = 0.15f),
                                            borderColor = colors.primary.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                                episode.runtimeMinutes?.let {
                                    Text("$it min", color = colors.outline, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
                                }
                            }

                            Spacer(Modifier.width(6.dp))

                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        resumingEpisodeId = episode.id
                                        scope.launch {
                                            delay(150)
                                            sheetState.hide()
                                            onSelectNowWatching(episode.id)
                                        }
                                    },
                                    shape = RectangleShape,
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    if (isResumingThis) {
                                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = colors.primary)
                                        Spacer(Modifier.width(4.dp))
                                        Text("RESUMING...", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                                    } else {
                                        Icon(Icons.Default.PlayArrow, null, tint = colors.primary, modifier = Modifier.size(13.dp))
                                        Spacer(Modifier.width(2.dp))
                                        Text("RESUME HERE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                                    }
                                }

                                IconButton(
                                    onClick = { onWatchEpisode(episode.id) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, "Mark watched", tint = colors.onSurfaceVariant)
                                }
                            }
                        }
                        HorizontalDivider(color = colors.outlineVariant)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorialShowPickerSheet(
    shows: List<ShowEstimateDto>,
    selectedShowId: Int?,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val compact = LocalCompactMode.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        contentColor = colors.onSurface,
        shape = RectangleShape,
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.outline) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (compact) 16.dp else 20.dp)
                .padding(bottom = if (compact) 20.dp else 28.dp)
        ) {
            EditorialSectionLabel("CHOOSE MAIN WATCHING SHOW")
            Text(
                "Select Hero Show",
                color = colors.onSurface,
                fontFamily = FontFamily.Serif,
                fontSize = if (compact) 30.sp else 34.sp,
                lineHeight = if (compact) 32.sp else 36.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                "Sets the featured show in TONIGHT with finish forecast and quick resume.",
                color = colors.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = if (compact) 12.dp else 16.dp)
            )
            HorizontalDivider(color = colors.outlineVariant)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = if (compact) 420.dp else 480.dp)
            ) {
                items(shows, key = { it.showId }) { show ->
                    val isSelected = show.showId == selectedShowId
                    val progress = (show.watchedEpisodes.toFloat() / show.totalEpisodes.coerceAtLeast(1)).coerceIn(0f, 1f)
                    val animatedProgress by animateFloatAsState(targetValue = progress, animationSpec = tween(400), label = "pickerProgress")

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch {
                                    sheetState.hide()
                                    onSelect(show.showId)
                                }
                            }
                            .padding(vertical = if (compact) 8.dp else 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Rich 2:3 poster thumbnail
                        if (!show.posterUrl.isNullOrEmpty()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(artworkUrl(show.posterUrl))
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "${show.title} poster",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(width = 44.dp, height = 64.dp)
                                    .border(1.dp, colors.outlineVariant, RectangleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(width = 44.dp, height = 64.dp)
                                    .background(colors.surfaceContainerHigh)
                                    .border(1.dp, colors.outlineVariant, RectangleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "${show.completionPercent.toInt()}%",
                                    color = colors.primary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                show.title,
                                color = if (isSelected) colors.primary else colors.onSurface,
                                fontFamily = FontFamily.Serif,
                                fontSize = 17.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            // Clear progress: watched vs total episodes
                            Text(
                                "${show.watchedEpisodes}/${show.totalEpisodes} eps · ${show.completionPercent.toInt()}% · ${show.remainingEpisodes} left",
                                color = colors.onSurfaceVariant,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )

                            // Sleek progress bar
                            LinearProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp)
                                    .height(3.dp),
                                color = if (isSelected) colors.primary else StripeCyan,
                                trackColor = colors.outlineVariant.copy(alpha = 0.35f)
                            )
                        }

                        Spacer(Modifier.width(10.dp))

                        if (isSelected) {
                            EditorialBadge(
                                text = "ACTIVE",
                                tint = colors.primary,
                                containerColor = colors.primary.copy(alpha = 0.15f),
                                borderColor = colors.primary.copy(alpha = 0.5f)
                            )
                        } else {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        sheetState.hide()
                                        onSelect(show.showId)
                                    }
                                },
                                shape = RectangleShape,
                                modifier = Modifier.heightIn(min = 48.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp)
                            ) {
                                Text("SELECT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                            }
                        }
                    }
                    HorizontalDivider(color = colors.outlineVariant)
                }
            }
        }
    }
}
