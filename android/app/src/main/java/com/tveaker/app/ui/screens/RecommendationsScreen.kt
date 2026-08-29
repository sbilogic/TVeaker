package com.tveaker.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tveaker.app.data.model.RecommendationItemDto
import com.tveaker.app.ui.theme.*
import com.tveaker.app.ui.viewmodel.RecommendationsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecommendationsScreen(viewModel: RecommendationsViewModel) {
    val state by viewModel.uiState.collectAsState()
    val budgets = listOf(
        "Any" to null,
        "30m" to 30,
        "45m" to 45,
        "60m" to 60,
        "90m" to 90,
        "120m" to 120
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("What to Watch", fontWeight = FontWeight.Bold) },
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
                .padding(horizontal = 16.dp)
        ) {
            // Time Budget Chips
            Text("Time Budget", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                items(budgets) { (label, budgetVal) ->
                    FilterChip(
                        selected = state.selectedBudget == budgetVal,
                        onClick = { viewModel.setBudget(budgetVal) },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = BgSurface,
                            labelColor = TextSecondary,
                            selectedContainerColor = Accent,
                            selectedLabelColor = BgPrimary
                        )
                    )
                }
            }

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Accent)
                }
            } else if (state.items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No recommendations match this filter.", color = TextSecondary)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.items) { item ->
                        RecommendationDetailCard(
                            item = item,
                            onAction = { action -> viewModel.submitFeedback(item.candidateId, action) }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
fun RecommendationDetailCard(
    item: RecommendationItemDto,
    onAction: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BgSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = "${item.mediaType.uppercase()} • ${item.runtimeMinutes ?: 45} min • ${(item.score * 100).toInt()}% match",
                        color = Accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                IconButton(
                    onClick = { onAction("not_interested") },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = TextMuted)
                }
            }

            if (item.overview != null) {
                Text(
                    text = item.overview,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            // Explanation box
            Surface(
                color = BgPrimary.copy(alpha = 0.6f),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
            ) {
                Text(
                    text = "💡 ${item.explanation}",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(8.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onAction("accepted") },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    modifier = Modifier.weight(1f).height(36.dp)
                ) {
                    Text("Watch Now", color = BgPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { onAction("not_now") },
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("Later", color = TextSecondary, fontSize = 12.sp)
                }
            }
        }
    }
}
