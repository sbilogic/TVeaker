package com.tveaker.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & Sync", fontWeight = FontWeight.Bold) },
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
                        Text("Incremental Sync (15m Polling)", color = TextPrimary)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { viewModel.triggerSync("full") },
                        colors = ButtonDefaults.buttonColors(containerColor = BgSurfaceHover),
                        enabled = !state.isSyncing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("7-Day Full Reconciliation", color = TextPrimary)
                    }

                    if (state.syncMessage != null) {
                        Text(
                            text = state.syncMessage ?: "",
                            color = Success,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                }
            }
        }
    }
}
