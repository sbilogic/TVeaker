package com.tveaker.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tveaker.app.data.model.ShowEstimateDto
import com.tveaker.app.data.model.UnwatchedEpisodesResponseDto
import com.tveaker.app.ui.theme.*
import com.tveaker.app.ui.viewmodel.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: DashboardViewModel) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(StripeGradientBrush),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Tv, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                        }
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("TVeaker", fontWeight = FontWeight.Black, fontSize = 18.sp, color = TextPrimary)
                                Surface(
                                    color = StripeIris.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, StripeIris.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = "PRO",
                                        color = StripeCyan,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text("Real-time Watch Analytics", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Medium)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.triggerSync("incremental") }) {
                        if (state.isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = StripeCyan, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Sync", tint = StripeCyan)
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
        if (state.isLoading && state.activeShows.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = StripeIris)
            }
        } else if (state.errorMessage != null && state.activeShows.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = BgSurface),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("📡 Connection Diagnostic", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
                        Text(
                            text = "Cannot connect to TVeaker backend at ${state.currentServerUrl}",
                            fontSize = 12.sp,
                            color = TextMuted,
                            modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )

                        Button(
                            onClick = { viewModel.setServerUrl("http://192.168.1.33:8000/") },
                            colors = ButtonDefaults.buttonColors(containerColor = StripeIris),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("📱 Wi-Fi LAN (192.168.1.33:8000)", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = { viewModel.setServerUrl("http://10.0.2.2:8000/") },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("💻 Emulator (10.0.2.2:8000)", color = TextPrimary, fontSize = 12.sp)
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        TextButton(
                            onClick = { viewModel.loadDashboardData() }
                        ) {
                            Text("🔄 Retry Connection", color = StripeCyan, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // OTA Update Available Banner
                if (state.isNewUpdateAvailable && state.serverVersionInfo != null) {
                    item {
                        Surface(
                            color = BgSurfaceElevated,
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, StripeViolet.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(StripeViolet.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = StripeCyan, modifier = Modifier.size(18.dp))
                                    }
                                    Column {
                                        Text("⚡ Update Available: v${state.serverVersionInfo?.versionName}", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 13.sp)
                                        Text(state.serverVersionInfo?.changelog ?: "New enhancements ready to install", color = TextSecondary, fontSize = 11.sp, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }

                // Stripe Aurora Spotlight Hero Card
                if (state.activeShows.isNotEmpty()) {
                    val heroShow = state.activeShows[0]
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(24.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderMedium),
                            color = BgSurfaceElevated
                        ) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                // Multi-stop Aurora Glow
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .background(StripeCardMeshBrush)
                                )

                                Row(
                                    modifier = Modifier
                                        .padding(18.dp)
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Poster artwork with hairline specular border
                                    if (!heroShow.posterUrl.isNullOrEmpty()) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(heroShow.posterUrl)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = heroShow.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(width = 78.dp, height = 116.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .border(1.dp, BorderMedium, RoundedCornerShape(12.dp))
                                                .clickable { viewModel.loadUnwatchedEpisodes(heroShow.showId) }
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Surface(
                                            color = StripeIris.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(6.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, StripeIris.copy(alpha = 0.4f)),
                                            modifier = Modifier.padding(bottom = 6.dp)
                                        ) {
                                            Text(
                                                text = "⚡ ACTIVE SPOTLIGHT",
                                                color = StripeCyan,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Black,
                                                letterSpacing = 1.sp,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                            )
                                        }

                                        Text(
                                            text = heroShow.title,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 18.sp,
                                            letterSpacing = (-0.4).sp,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            modifier = Modifier.clickable { viewModel.loadUnwatchedEpisodes(heroShow.showId) }
                                        )

                                        Text(
                                            text = "${heroShow.watchedEpisodes}/${heroShow.totalEpisodes} eps • ${heroShow.remainingRuntimeDisplay ?: "${heroShow.unwatchedMinutes}m remaining"}",
                                            color = TextSecondary,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )

                                        Spacer(modifier = Modifier.height(10.dp))

                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(
                                                onClick = { viewModel.quickScrobble(heroShow.showId) },
                                                colors = ButtonDefaults.buttonColors(containerColor = StripeIris),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                                modifier = Modifier.height(30.dp),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp), tint = TextPrimary)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("+1 Ep", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            }

                                            OutlinedButton(
                                                onClick = { viewModel.loadUnwatchedEpisodes(heroShow.showId) },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                modifier = Modifier.height(30.dp),
                                                shape = RoundedCornerShape(8.dp),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                                            ) {
                                                Text("Episodes 📋", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                            }
                                        }
                                    }

                                    // Tabular Circular Progress Ring
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.size(64.dp)
                                    ) {
                                        androidx.compose.foundation.Canvas(modifier = Modifier.size(58.dp)) {
                                            drawArc(
                                                color = Border,
                                                startAngle = 0f,
                                                sweepAngle = 360f,
                                                useCenter = false,
                                                style = Stroke(width = 5.dp.toPx())
                                            )
                                            drawArc(
                                                brush = Brush.sweepGradient(listOf(StripeIris, StripeViolet, StripeCyan, StripeIris)),
                                                startAngle = -90f,
                                                sweepAngle = (heroShow.completionPercent / 100f) * 360f,
                                                useCenter = false,
                                                style = Stroke(
                                                    width = 5.dp.toPx(),
                                                    cap = StrokeCap.Round
                                                )
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = "${heroShow.completionPercent.toInt()}%",
                                                fontWeight = FontWeight.Black,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 12.sp,
                                                color = TextPrimary
                                            )
                                            Text("DONE", fontSize = 7.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 0.5.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Stripe Metric Bento Grid
                item {
                    val totalEpsLeft = state.activeShows.sumOf { it.remainingEpisodes }
                    val totalMins = state.activeShows.sumOf { it.unwatchedMinutes }
                    val totalHours = totalMins / 60
                    val totalDays = totalMins / 1440

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            color = BgSurface,
                            shape = RoundedCornerShape(18.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("ACTIVE PIPELINE", fontSize = 9.sp, color = StripeCyan, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = StripeCyan, modifier = Modifier.size(14.dp))
                                }
                                Text(
                                    text = "${state.activeShows.size}",
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    color = TextPrimary,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                                Text("$totalEpsLeft unwatched episodes", fontSize = 11.sp, color = TextMuted)
                            }
                        }

                        Surface(
                            modifier = Modifier.weight(1f),
                            color = BgSurface,
                            shape = RoundedCornerShape(18.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("CLEARANCE TIME", fontSize = 9.sp, color = StripeViolet, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                                    Icon(Icons.Default.Schedule, contentDescription = null, tint = StripeViolet, modifier = Modifier.size(14.dp))
                                }
                                Text(
                                    text = if (totalDays > 0) "${totalDays}d ${totalHours % 24}h" else "${totalHours}h",
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    color = TextPrimary,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                                Text("$totalMins total minutes", fontSize = 11.sp, color = TextMuted)
                            }
                        }
                    }
                }

                // In-Progress Section Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("In-Progress Shows", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = TextPrimary)
                        Text("${state.activeShows.size} tracked", fontSize = 11.sp, color = StripeCyan, fontWeight = FontWeight.Bold)
                    }
                }

                items(state.activeShows) { show ->
                    StripeShowCard(
                        show = show,
                        onClick = { viewModel.loadUnwatchedEpisodes(show.showId) },
                        onQuickScrobble = { viewModel.quickScrobble(show.showId) }
                    )
                }

                item { Spacer(modifier = Modifier.height(32.dp)) }
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
fun StripeShowCard(
    show: ShowEstimateDto,
    onClick: () -> Unit,
    onQuickScrobble: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        color = BgSurface,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // High-res Poster Artwork
                if (!show.posterUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(show.posterUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = show.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 46.dp, height = 68.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(width = 46.dp, height = 68.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(StripeIris.copy(alpha = 0.15f))
                            .border(1.dp, StripeIris.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = show.title.take(1),
                            fontWeight = FontWeight.Black,
                            color = StripeCyan,
                            fontSize = 18.sp
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = show.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextPrimary,
                        maxLines = 1
                    )

                    Text(
                        text = "${show.watchedEpisodes}/${show.totalEpisodes} eps • ${show.avgRuntimeMinutes ?: 42}m avg • ${show.episodesPerWeek} eps/wk",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )

                    // Dual-Tone Gradient Progress Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Border)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth((show.completionPercent / 100f).coerceIn(0.01f, 1f))
                                .fillMaxHeight()
                                .background(StripeGradientBrush)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${show.completionPercent.toInt()}%", color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Text(
                            text = if (show.remainingEpisodes > 0) "${show.remainingEpisodes} left (${show.remainingRuntimeDisplay ?: "${show.unwatchedMinutes}m"})" else "✓ Complete",
                            color = StripeCyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Footer with Target Finish & Quick Scrobble CTA
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (show.estimatedFinishDate != null) "Target: ${show.estimatedFinishDate.substring(0, minOf(10, show.estimatedFinishDate.length))}" else if (show.isCaughtUp) "✓ Caught up" else "In Progress",
                    color = if (show.isCaughtUp) StripeEmerald else TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (show.remainingEpisodes > 0) {
                        Button(
                            onClick = onQuickScrobble,
                            colors = ButtonDefaults.buttonColors(containerColor = StripeIris),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("+1 Ep", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                    Button(
                        onClick = onClick,
                        colors = ButtonDefaults.buttonColors(containerColor = BgSurfaceElevated),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                    ) {
                        Text("Episodes 📋", color = TextPrimary, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnwatchedEpisodesBottomSheet(
    data: UnwatchedEpisodesResponseDto,
    onDismiss: () -> Unit,
    onWatchEpisode: (Int) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BgSurfaceElevated,
        dragHandle = { BottomSheetDefaults.DragHandle(color = BorderMedium) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!data.posterUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(data.posterUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = data.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 48.dp, height = 72.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(data.title, fontWeight = FontWeight.Black, fontSize = 18.sp, color = TextPrimary)
                    Text(
                        "${data.remainingEpisodes} episodes left • ~${data.unwatchedMinutes / 60}h total",
                        color = StripeCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (data.unwatchedEpisodes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🎉 Completely caught up with all episodes!", color = StripeEmerald, fontWeight = FontWeight.Bold)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(data.unwatchedEpisodes) { ep ->
                        Surface(
                            color = BgSurface,
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            color = StripeIris.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(4.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, StripeIris.copy(alpha = 0.4f))
                                        ) {
                                            Text(
                                                text = "S${ep.seasonNumber.toString().padStart(2, '0')}E${ep.episodeNumber.toString().padStart(2, '0')}",
                                                color = StripeCyan,
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Black,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Text(
                                            text = ep.title ?: "Untitled Episode",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = TextPrimary,
                                            maxLines = 1
                                        )
                                    }
                                    if (ep.runtimeMinutes != null) {
                                        Text(
                                            text = "${ep.runtimeMinutes}m ${if (!ep.firstAired.isNullOrEmpty()) "• " + ep.firstAired.take(10) else ""}",
                                            color = TextMuted,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }

                                Button(
                                    onClick = { onWatchEpisode(ep.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = StripeIris),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("✓ Watched", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
