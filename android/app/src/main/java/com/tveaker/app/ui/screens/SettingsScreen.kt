package com.tveaker.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tveaker.app.ui.theme.*
import com.tveaker.app.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val state by viewModel.uiState.collectAsState()
    var urlInput by remember { mutableStateOf(state.baseUrl) }
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    LaunchedEffect(state.baseUrl) {
        urlInput = state.baseUrl
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Settings & System", fontWeight = FontWeight.Black, fontSize = 20.sp, color = TextPrimary)
                        Text("OTA Updates & Host Configuration", fontSize = 11.sp, color = TextMuted)
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
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Stripe OTA Update Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = BgSurface,
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(StripeIris.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, tint = StripeCyan, modifier = Modifier.size(18.dp))
                            }
                            Column {
                                Text("OTA Updates", fontWeight = FontWeight.Black, color = TextPrimary, fontSize = 16.sp)
                                Text("Instant over-the-air distribution", color = TextMuted, fontSize = 11.sp)
                            }
                        }
                        Surface(
                            color = StripeIris.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, StripeIris.copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = "v${state.currentVersionName} (Build ${state.currentVersionCode})",
                                color = StripeCyan,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (state.serverVersionInfo != null) {
                        Surface(
                            color = if (state.isNewUpdateAvailable) StripeViolet.copy(alpha = 0.15f) else BgSurfaceElevated,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (state.isNewUpdateAvailable) StripeViolet.copy(alpha = 0.45f) else BorderSubtle
                            )
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = if (state.isNewUpdateAvailable) StripeCyan else StripeEmerald,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = if (state.isNewUpdateAvailable) "⚡ Update Ready: v${state.serverVersionInfo?.versionName} (Build ${state.serverVersionInfo?.versionCode})" else "✓ Server Build: v${state.serverVersionInfo?.versionName} (Build ${state.serverVersionInfo?.versionCode})",
                                        fontWeight = FontWeight.Bold,
                                        color = if (state.isNewUpdateAvailable) StripeCyan else TextPrimary,
                                        fontSize = 13.sp
                                    )
                                }
                                if (!state.serverVersionInfo?.changelog.isNullOrEmpty()) {
                                    Text(
                                        text = state.serverVersionInfo?.changelog ?: "",
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                }
                                if (state.serverVersionInfo?.apkSizeBytes != null) {
                                    val sizeMb = String.format("%.2f", (state.serverVersionInfo?.apkSizeBytes ?: 0) / 1048576f)
                                    Text(
                                        text = "Size: $sizeMb MB",
                                        color = TextMuted,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "Could not connect to update server. Verify connection to Wi-Fi LAN.",
                            color = TextMuted,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (state.isNewUpdateAvailable) {
                        if (state.downloadProgress != null) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                LinearProgressIndicator(
                                    progress = { state.downloadProgress ?: 0f },
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                    color = StripeCyan,
                                    trackColor = Border
                                )
                                Text(
                                    text = "Downloading APK: ${((state.downloadProgress ?: 0f) * 100).toInt()}%",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        } else {
                            Button(
                                onClick = { viewModel.startDownloadUpdate(context) },
                                colors = ButtonDefaults.buttonColors(containerColor = StripeIris),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().height(42.dp)
                            ) {
                                Text("Download & Install Update", color = TextPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Button(
                            onClick = { viewModel.checkForUpdates() },
                            colors = ButtonDefaults.buttonColors(containerColor = BgSurfaceElevated),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                            modifier = Modifier.fillMaxWidth().height(42.dp)
                        ) {
                            Text("Check for Updates", color = TextPrimary, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            // Stripe Connection Settings Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = BgSurface,
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(StripeEmerald.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Sensors, contentDescription = null, tint = StripeEmerald, modifier = Modifier.size(18.dp))
                        }
                        Column {
                            Text("Host Gateway", fontWeight = FontWeight.Black, color = TextPrimary, fontSize = 16.sp)
                            Text("Local network API endpoint", color = TextMuted, fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("Server Base URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = StripeCyan,
                            unfocusedBorderColor = BorderSubtle,
                            focusedContainerColor = BgSurfaceElevated,
                            unfocusedContainerColor = BgSurfaceElevated
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Preset connection shortcuts
                    Text("QUICK PRESETS", fontSize = 9.sp, color = StripeCyan, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = BgSurfaceElevated,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                            modifier = Modifier.weight(1f).clickable {
                                urlInput = "http://192.168.1.33:8000/"
                                viewModel.setBaseUrl("http://192.168.1.33:8000/")
                            }
                        ) {
                            Text(
                                text = "Wi-Fi (192.168.1.33)",
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 8.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }

                        Surface(
                            color = BgSurfaceElevated,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                            modifier = Modifier.weight(1f).clickable {
                                urlInput = "http://10.0.2.2:8000/"
                                viewModel.setBaseUrl("http://10.0.2.2:8000/")
                            }
                        ) {
                            Text(
                                text = "Emulator (10.0.2.2)",
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 8.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { viewModel.setBaseUrl(urlInput) },
                        colors = ButtonDefaults.buttonColors(containerColor = StripeIris),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        Text("Apply & Save Gateway", color = TextPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
