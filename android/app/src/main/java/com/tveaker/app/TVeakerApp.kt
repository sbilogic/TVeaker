package com.tveaker.app

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tveaker.app.ui.screens.*
import com.tveaker.app.ui.theme.*
import com.tveaker.app.ui.viewmodel.*

sealed class Screen(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Dashboard)
    object Shows : Screen("shows", "Shows", Icons.Default.Tv)
    object Recommendations : Screen("recommendations", "Discover", Icons.Default.AutoAwesome)
    object Settings : Screen("settings", "Settings", Icons.Default.Tune)
}

@Composable
fun TVeakerApp() {
    val navController = rememberNavController()
    val items = listOf(
        Screen.Dashboard,
        Screen.Shows,
        Screen.Recommendations,
        Screen.Settings
    )
    var selectedItem by remember { mutableStateOf(0) }

    val dashboardViewModel: DashboardViewModel = viewModel()
    val showsViewModel: ShowsViewModel = viewModel()
    val recViewModel: RecommendationsViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()

    TVeakerTheme {
        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = BgSurface,
                    tonalElevation = 8.dp
                ) {
                    items.forEachIndexed { index, screen ->
                        val isSelected = selectedItem == index
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = screen.icon,
                                    contentDescription = screen.title,
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = screen.title,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            selected = isSelected,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = StripeCyan,
                                selectedTextColor = StripeCyan,
                                indicatorColor = StripeIris.copy(alpha = 0.25f),
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted
                            ),
                            onClick = {
                                selectedItem = index
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            },
            containerColor = BgBase
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Dashboard.route,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                composable(Screen.Dashboard.route) {
                    DashboardScreen(viewModel = dashboardViewModel)
                }
                composable(Screen.Shows.route) {
                    ShowsScreen(viewModel = showsViewModel)
                }
                composable(Screen.Recommendations.route) {
                    RecommendationsScreen(viewModel = recViewModel)
                }
                composable(Screen.Settings.route) {
                    SettingsScreen(viewModel = settingsViewModel)
                }
            }
        }
    }
}
