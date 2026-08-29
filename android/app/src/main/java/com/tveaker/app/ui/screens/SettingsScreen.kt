package com.tveaker.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

    // Sync input when state baseUrl changes externally
    LaunchedEffect(state.baseUrl) {
        urlInput = state.baseUrl
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & Updates", fontWeight = FontWeight.Bold) },
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

            // OTA Update Card
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
                        Text("App Updates (OTA)", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
                        Surface(
                            color = AccentCyan.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Installed: v${state.currentVersionName} (${state.currentVersionCode})",
                                color = AccentCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (state.serverVersionInfo != null) {
                        Surface(
                            color = if (state.isNewUpdateAvailable) AccentPurple.copy(alpha = 0.12f) else BgSurfaceElevated,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (state.isNewUpdateAvailable) AccentPurple.copy(alpha = 0.3f) else BorderSubtle
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = if (state.isNewUpdateAvailable) "⚡ New Version: v${state.serverVersionInfo?.versionName} (Build ${state.serverVersionInfo?.versionCode})" else "✓ Server Build: v${state.serverVersionInfo?.versionName} (Build ${state.serverVersionInfo?.versionCode})",
                                    fontWeight = FontWeight.Bold,
                                    color = if (state.isNewUpdateAvailable) AccentPurple else AccentCyan,
                                    fontSize = 13.sp
                                )
                                if (!state.serverVersionInfo?.changelog.isNullOrEmpty()) {
                                    Text(
                                        text = state.serverVersionInfo?.changelog ?: "",
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                                if (state.serverVersionInfo?.apkSizeBytes != null) {
                                    val sizeMb = String.format("%.2f", (state.serverVersionInfo?.apkSizeBytes ?: 0) / 1048576f)
                                    Text(
                                        text = "Size: $sizeMb MB",
                                        color = TextMuted,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "Could not reach the update server. Connect to Wi-Fi LAN to check for updates.",
                            color = TextMuted,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (state.isNewUpdateAvailable) {
                        if (state.downloadProgress != null) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                LinearProgressIndicator(
                                    progress = { state.downloadProgress ?: 0f },
                                    modifier = Modifier.fillMaxWidth().height(6.dp),
                                    color = AccentCyan,
                                    trackColor = Border
                                )
                                Text(
                                    text = "Downloading: ${((state.downloadProgress ?: 0f) * 100).toInt()}%",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        } else {
                            Button(
                                onClick = { viewModel.startDownloadUpdate(context) },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentPurple),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().height(42.dp)
                            ) {
                                Text("Download & Install Update", color = TextPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Button(
                            onClick = { viewModel.checkForUpdates() },
                            colors = ButtonDefaults.buttonColors(containerColor = BgSurfaceElevated),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                            modifier = Modifier.fillMaxWidth().height(42.dp)
                        ) {
                            Text("Check for Updates", color = TextPrimary)
                        }
                    }
                }
            }

            // Connection Settings Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = BgSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Connection Settings", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
                    Text("Point the Android companion app to your TVeaker server host address.", color = TextSecondary, fontSize = 11.sp)

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("Server Base URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = BorderSubtle,
                            focusedContainerColor = BgBase,
                            unfocusedContainerColor = BgBase
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Preset connection shortcuts
                    Text("Presets", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                urlInput = "http://192.168.1.33:8000/"
                                viewModel.setBaseUrl("http://192.168.1.33:8000/")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BgSurfaceElevated),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            modifier = Modifier.weight(1f).height(32.dp),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("Wi-Fi (192.168.1.33)", color = TextPrimary, fontSize = 11.sp)
                        }

                        Button(
                            onClick = {
                                urlInput = "http://10.0.2.2:8000/"
                                viewModel.setBaseUrl("http://10.0.2.2:8000/")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BgSurfaceElevated),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            modifier = Modifier.weight(1f).height(32.dp),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("Emulator (10.0.2.2)", color = TextPrimary, fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { viewModel.setBaseUrl(urlInput) },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        Text("Save Base URL", color = BgBase, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
