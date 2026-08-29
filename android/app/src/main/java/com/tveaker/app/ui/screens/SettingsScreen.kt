package com.tveaker.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & Updates", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BgPrimary,
                    titleContentColor = TextPrimary
                )
            )
        },
        containerColor = BgPrimary
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // OTA Update Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = BgSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("App Updates (OTA)", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text(
                            text = "v${state.currentVersionName} (${state.currentVersionCode})",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (state.availableUpdate != null) {
                        Surface(
                            color = Accent.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "⚡ New Version: v${state.availableUpdate?.versionName}",
                                    fontWeight = FontWeight.Bold,
                                    color = Accent,
                                    fontSize = 14.sp
                                )
                                if (!state.availableUpdate?.changelog.isNullOrEmpty()) {
                                    Text(
                                        text = state.availableUpdate?.changelog ?: "",
                                        color = TextSecondary,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }

                        if (state.downloadProgress != null) {
                            LinearProgressIndicator(
                                progress = { state.downloadProgress ?: 0f },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                color = Accent,
                                trackColor = Border
                            )
                        } else if (state.readyToInstallApk != null) {
                            Button(
                                onClick = { viewModel.installUpdate(context) },
                                colors = ButtonDefaults.buttonColors(containerColor = Success),
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                            ) {
                                Text("Install Update Now", color = BgPrimary, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = { viewModel.startDownloadUpdate(context) },
                                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                            ) {
                                Text("Download & Install Update", color = BgPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Text(
                            text = state.updateMessage ?: "App is up to date",
                            color = Success,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        OutlinedButton(
                            onClick = { viewModel.checkForUpdates() },
                            enabled = !state.isCheckingUpdate,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        ) {
                            if (state.isCheckingUpdate) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Accent)
                            } else {
                                Text("Check for Updates", color = TextPrimary)
                            }
                        }
                    }
                }
            }

            // Connection Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = BgSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Backend Server URL", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = Accent,
                            unfocusedBorderColor = Border
                        )
                    )
                    Button(
                        onClick = { viewModel.setBaseUrl(urlInput) },
                        colors = ButtonDefaults.buttonColors(containerColor = Accent),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Update Server URL", color = BgPrimary, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (state.health != null) {
                        Text(
                            text = "Connected • User: ${state.health?.username ?: "Local"}",
                            color = Success,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
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
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Sync Triggers", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = { viewModel.triggerSync("incremental") },
                        colors = ButtonDefaults.buttonColors(containerColor = BgSurfaceHover),
                        enabled = !state.isSyncing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("⚡ 15-Minute Incremental Polling", color = TextPrimary)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { viewModel.triggerSync("full") },
                        colors = ButtonDefaults.buttonColors(containerColor = BgSurfaceHover),
                        enabled = !state.isSyncing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🔄 7-Day Full Reconciliation", color = TextPrimary)
                    }

                    if (state.syncMessage != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(state.syncMessage ?: "", color = Success, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
