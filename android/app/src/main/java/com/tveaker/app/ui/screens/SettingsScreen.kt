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
                                        lineHeight = 14.sp,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }

                        if (state.downloadProgress != null) {
                            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                Text(
                                    text = "Downloading APK update... ${( (state.downloadProgress ?: 0f) * 100).toInt()}%",
                                    color = AccentCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                LinearProgressIndicator(
                                    progress = { state.downloadProgress ?: 0f },
                                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                    color = AccentCyan,
                                    trackColor = Border
                                )
                            }
                        } else if (state.readyToInstallApk != null) {
                            Button(
                                onClick = { viewModel.installUpdate(context) },
                                colors = ButtonDefaults.buttonColors(containerColor = Success),
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                            ) {
                                Text("Install Downloaded Update", color = BgBase, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.startDownloadUpdate(context) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (state.isNewUpdateAvailable) AccentCyan else BgSurfaceHover
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = if (state.isNewUpdateAvailable) "Download & Install" else "Re-download APK",
                                        color = if (state.isNewUpdateAvailable) BgBase else TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                                OutlinedButton(
                                    onClick = { viewModel.checkForUpdates() },
                                    modifier = Modifier.height(40.dp)
                                ) {
                                    Text("Check Again", color = TextSecondary, fontSize = 12.sp)
                                }
                            }
                        }
                    } else {
                        Text(
                            text = state.updateMessage ?: "Checking for updates...",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        OutlinedButton(
                            onClick = { viewModel.checkForUpdates() },
                            enabled = !state.isCheckingUpdate,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        ) {
                            if (state.isCheckingUpdate) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = AccentCyan)
                            } else {
                                Text("Check for Updates", color = TextPrimary)
                            }
                        }
                    }

                    if (state.errorMessage != null && state.errorMessage?.contains("update", ignoreCase = true) == true) {
                        Text(
                            text = state.errorMessage ?: "",
                            color = Danger,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }

            // Connection & Server URL Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = BgSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Backend Server Connection", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
                    Text(
                        text = "Enter your PC's IP or emulator URL to connect to the TVeaker server.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                    )

                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = BorderSubtle,
                            focusedContainerColor = BgSurfaceElevated,
                            unfocusedContainerColor = BgSurfaceElevated
                        )
                    )

                    // Quick URL Presets
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                urlInput = "http://10.0.2.2:8000/"
                                viewModel.setBaseUrl(urlInput)
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("Emulator (10.0.2.2)", fontSize = 10.sp, color = TextSecondary)
                        }
                        OutlinedButton(
                            onClick = {
                                urlInput = "http://192.168.1.33:8000/"
                                viewModel.setBaseUrl(urlInput)
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("Wi-Fi (192.168.1.33)", fontSize = 10.sp, color = AccentCyan)
                        }
                    }

                    Button(
                        onClick = { viewModel.setBaseUrl(urlInput) },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Connect & Save URL", color = BgBase, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (state.health != null) {
                        Text(
                            text = "✓ Connected to TVeaker (${state.health?.username ?: "Local Database"})",
                            color = Success,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else if (state.errorMessage != null) {
                        Text(
                            text = "Connection error: ${state.errorMessage}",
                            color = Danger,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Sync Triggers Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = BgSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Sync Triggers", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = { viewModel.triggerSync("incremental") },
                        colors = ButtonDefaults.buttonColors(containerColor = BgSurfaceElevated),
                        enabled = !state.isSyncing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("⚡ 15-Minute Incremental Sync", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { viewModel.triggerSync("full") },
                        colors = ButtonDefaults.buttonColors(containerColor = BgSurfaceElevated),
                        enabled = !state.isSyncing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🔄 7-Day Full Reconciliation", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    }

                    if (state.syncMessage != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(state.syncMessage ?: "", color = Success, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
