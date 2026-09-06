package com.tveaker.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.tveaker.app.ui.theme.BorderSubtle
import com.tveaker.app.ui.theme.LocalCompactMode
import com.tveaker.app.ui.theme.LocalOnCompactModeChange
import com.tveaker.app.ui.theme.StripeEmerald
import com.tveaker.app.ui.theme.StripeIris
import com.tveaker.app.ui.theme.StripeViolet
import com.tveaker.app.ui.theme.TextMuted
import com.tveaker.app.ui.theme.TextPrimary
import com.tveaker.app.ui.theme.TextSecondary
import com.tveaker.app.data.api.GatewayUrl
import com.tveaker.app.ui.viewmodel.SettingsViewModel
import java.util.Locale

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val state by viewModel.uiState.collectAsState()
    var urlInput by remember {
        mutableStateOf(if (state.baseUrl == GatewayUrl.UNCONFIGURED_BASE_URL) "" else state.baseUrl)
    }
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val compactMode = LocalCompactMode.current
    val onCompactModeChange = LocalOnCompactModeChange.current

    LaunchedEffect(state.baseUrl) {
        urlInput = if (state.baseUrl == GatewayUrl.UNCONFIGURED_BASE_URL) "" else state.baseUrl
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(start = 16.dp, end = 16.dp, top = if (compactMode) 4.dp else 10.dp, bottom = if (compactMode) 16.dp else 24.dp),
        verticalArrangement = Arrangement.spacedBy(if (compactMode) 5.dp else 9.dp)
    ) {
        EditorialPageHeader(
            index = "04",
            title = "SETTINGS",
            subtitle = "Keep the companion connected, current, and pointed at the right online gateway."
        )

        EditorialPageCard {
            Column(modifier = Modifier.padding(if (compactMode) 9.dp else 15.dp)) {
                SettingsSectionHeading(Icons.Default.Tune, "DISPLAY DENSITY", "Choose how much fits on screen.")
                HorizontalDivider(modifier = Modifier.padding(vertical = if (compactMode) 9.dp else 15.dp), color = BorderSubtle)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(if (compactMode) "Compact mode on" else "Comfortable mode on", color = TextPrimary, fontFamily = EditorialSerif, fontSize = if (compactMode) 17.sp else 18.sp)
                        Text("Tighten layout rhythm without reducing touch targets.", color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                    Switch(
                        checked = compactMode,
                        onCheckedChange = onCompactModeChange,
                        colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.background, checkedTrackColor = StripeIris, uncheckedThumbColor = TextMuted, uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
            }
        }

        EditorialPageCard {
            Column(modifier = Modifier.padding(if (compactMode) 9.dp else 15.dp)) {
                SettingsSectionHeading(Icons.Default.CloudDownload, "OTA UPDATES", "Keep the Android companion current.")
                HorizontalDivider(modifier = Modifier.padding(vertical = if (compactMode) 9.dp else 15.dp), color = BorderSubtle)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        EditorialSectionLabel("INSTALLED VERSION")
                        Text("v${state.currentVersionName} · BUILD ${state.currentVersionCode}", color = TextPrimary, fontFamily = EditorialSerif, fontSize = 20.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                    if (state.isCheckingUpdate) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = StripeIris, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Update status", tint = StripeEmerald, modifier = Modifier.size(21.dp))
                    }
                }
                Text(
                    text = state.serverVersionInfo?.let { "SERVER v${it.versionName} · BUILD ${it.versionCode}" } ?: "UPDATE SERVER NOT CHECKED",
                    color = if (state.isNewUpdateAvailable) StripeIris else TextMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = .9.sp,
                    modifier = Modifier.padding(top = if (compactMode) 8.dp else 15.dp)
                )
                state.updateMessage?.let { Text(it, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
                if (state.isNewUpdateAvailable) {
                    state.serverVersionInfo?.changelog?.takeIf { it.isNotBlank() }?.let { changelog ->
                        Text(changelog, color = TextSecondary, fontFamily = EditorialSerif, fontSize = 15.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 9.dp))
                    }
                }
                state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp)) }

                if (state.downloadProgress != null) {
                    Column(modifier = Modifier.padding(top = if (compactMode) 9.dp else 16.dp)) {
                        LinearProgressIndicator(progress = { state.downloadProgress ?: 0f }, modifier = Modifier.fillMaxWidth().height(2.dp), color = StripeIris, trackColor = BorderSubtle)
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("DOWNLOADING · ${((state.downloadProgress ?: 0f) * 100).toInt()}%", color = StripeIris, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .6.sp)
                            Text(state.downloadBytesProgress ?: "", color = TextMuted, fontSize = 9.sp)
                        }
                    }
                } else {
                    Row(modifier = Modifier.fillMaxWidth().padding(top = if (compactMode) 9.dp else 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.isNewUpdateAvailable) {
                            EditorialPrimaryButton("DOWNLOAD & INSTALL", { viewModel.startDownloadUpdate(context) }, modifier = Modifier.weight(1f), icon = { Icon(Icons.Default.CloudDownload, null, modifier = Modifier.size(15.dp)) })
                        } else {
                            OutlinedButton(onClick = viewModel::checkForUpdates, enabled = !state.isCheckingUpdate, modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = RectangleShape, border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)) {
                                Icon(Icons.Default.Refresh, null, tint = StripeIris, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(7.dp)); Text("CHECK FOR UPDATES", color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = .5.sp)
                            }
                        }
                        if (state.serverVersionInfo != null && !state.isNewUpdateAvailable) {
                            OutlinedButton(onClick = { viewModel.startDownloadUpdate(context) }, modifier = Modifier.heightIn(min = 48.dp), shape = RectangleShape, border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)) {
                                Text("REINSTALL", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        EditorialPageCard {
            Column(modifier = Modifier.padding(if (compactMode) 9.dp else 15.dp)) {
                SettingsSectionHeading(Icons.Default.Sensors, "ONLINE PHONE GATEWAY", "A public HTTPS route to your TVeaker library.")
                HorizontalDivider(modifier = Modifier.padding(vertical = if (compactMode) 9.dp else 15.dp), color = BorderSubtle)
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("Online HTTPS URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RectangleShape,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, focusedBorderColor = StripeIris, unfocusedBorderColor = BorderSubtle, focusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedContainerColor = MaterialTheme.colorScheme.surface)
                )
                Text("On the PC, run `tveaker phone-gateway`, then paste its HTTPS URL here. Keep that PC terminal open while you use the phone.", color = TextMuted, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = if (compactMode) 9.dp else 14.dp))
                EditorialSectionLabel("GATEWAY PRESETS", Modifier.padding(top = if (compactMode) 9.dp else 16.dp))
                Row(modifier = Modifier.fillMaxWidth().padding(top = if (compactMode) 5.dp else 9.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GatewayPreset("RENDER CLOUD", "tveaker.onrender.com", Modifier.weight(1f)) {
                        urlInput = GatewayUrl.CLOUD_RENDER_URL
                        viewModel.setBaseUrl(urlInput)
                    }
                    GatewayPreset("EMULATOR", "10.0.2.2", Modifier.weight(1f)) {
                        urlInput = "http://10.0.2.2:8000/"
                        viewModel.setBaseUrl(urlInput)
                    }
                }
                EditorialPrimaryButton("APPLY & SAVE GATEWAY", { viewModel.setBaseUrl(urlInput) }, modifier = Modifier.fillMaxWidth().padding(top = if (compactMode) 8.dp else 14.dp))
            }
        }

        EditorialPageCard {
            Column(modifier = Modifier.padding(if (compactMode) 9.dp else 15.dp)) {
                SettingsSectionHeading(Icons.Default.CheckCircle, "SYSTEM STATUS", "A quick read on the local connection.")
                HorizontalDivider(modifier = Modifier.padding(vertical = 5.dp), color = BorderSubtle)
                val health = state.health
                EditorialValueRow("DATABASE", if (health?.databaseConnected == true) "CONNECTED" else "NOT CONFIRMED", if (health?.databaseConnected == true) StripeEmerald else TextMuted)
                EditorialValueRow("TRAKT ACCOUNT", health?.username ?: if (health?.traktAuthenticated == true) "CONNECTED" else "NOT CONNECTED", if (health?.traktAuthenticated == true) StripeEmerald else TextMuted)
                EditorialValueRow("LAST SYNC", health?.lastSyncAt?.replace('T', ' ') ?: "NEVER", TextSecondary)
                    Row(modifier = Modifier.fillMaxWidth().padding(top = if (compactMode) 7.dp else 12.dp), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = viewModel::checkHealth, enabled = !state.isLoading, modifier = Modifier.heightIn(min = 48.dp), shape = RectangleShape, border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)) {
                        Icon(Icons.Default.Refresh, null, tint = StripeIris, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(6.dp)); Text("REFRESH STATUS", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        EditorialPageCard {
            Column(modifier = Modifier.padding(if (compactMode) 9.dp else 15.dp)) {
                SettingsSectionHeading(Icons.Default.Sync, "SYNC ENGINE", "Pull the latest history from Trakt.")
                Row(modifier = Modifier.fillMaxWidth().padding(top = if (compactMode) 9.dp else 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EditorialPrimaryButton("INCREMENTAL SYNC", { viewModel.triggerSync("incremental") }, modifier = Modifier.weight(1f), icon = { Icon(Icons.Default.Sync, null, modifier = Modifier.size(15.dp)) })
                    OutlinedButton(onClick = { viewModel.triggerSync("full") }, enabled = !state.isSyncing, modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = RectangleShape, border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)) {
                        Text("FULL RECONCILE", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = .5.sp)
                    }
                }
                state.syncMessage?.let { Text(it, color = StripeEmerald, fontSize = 11.sp, modifier = Modifier.padding(top = if (compactMode) 7.dp else 12.dp)) }
            }
        }

        EditorialPageCard {
            Column(modifier = Modifier.padding(if (compactMode) 9.dp else 15.dp)) {
                SettingsSectionHeading(Icons.Default.AutoAwesome, "METADATA", "Keep artwork, runtimes, and release schedules complete.")
                HorizontalDivider(modifier = Modifier.padding(vertical = if (compactMode) 9.dp else 15.dp), color = BorderSubtle)
                Text("Proactive daily refresh", color = TextPrimary, fontFamily = EditorialSerif, fontSize = 18.sp)
                Text("TVMaze runs without a key. Add a TMDB token on the server for richer movie and series metadata.", color = TextMuted, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 4.dp))
                OutlinedButton(
                    onClick = viewModel::hydrateMissingMetadata,
                    enabled = !state.isHydratingMetadata,
                    modifier = Modifier.fillMaxWidth().padding(top = if (compactMode) 9.dp else 14.dp).heightIn(min = 48.dp),
                    shape = RectangleShape,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                ) {
                    if (state.isHydratingMetadata) {
                        CircularProgressIndicator(modifier = Modifier.size(15.dp), color = StripeIris, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.AutoAwesome, null, tint = StripeIris, modifier = Modifier.size(15.dp))
                    }
                    Spacer(Modifier.width(7.dp))
                    Text(if (state.isHydratingMetadata) "QUEUING REFRESH" else "FETCH MISSING NOW", color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = .5.sp)
                }
                state.metadataMessage?.let { Text(it, color = StripeEmerald, fontSize = 11.sp, modifier = Modifier.padding(top = if (compactMode) 7.dp else 12.dp)) }
            }
        }
    }
}

@Composable
private fun SettingsSectionHeading(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    val compact = LocalCompactMode.current
    Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = StripeIris, modifier = Modifier.size(if (compact) 18.dp else 19.dp))
        Column {
            Text(title, color = TextPrimary, fontSize = if (compact) 12.sp else 13.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
            Text(subtitle, color = TextMuted, fontSize = if (compact) 10.sp else 11.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun GatewayPreset(label: String, host: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val compact = LocalCompactMode.current
    Surface(modifier = modifier.heightIn(min = 48.dp).clickable(onClick = onClick), color = MaterialTheme.colorScheme.surfaceVariant, shape = RectangleShape, border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)) {
        Column(modifier = Modifier.padding(horizontal = if (compact) 8.dp else 10.dp, vertical = if (compact) 8.dp else 10.dp)) {
            Text(label, color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = .5.sp)
            Text(host, color = TextMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
        }
    }
}
