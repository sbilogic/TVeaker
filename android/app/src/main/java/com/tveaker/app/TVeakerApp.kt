package com.tveaker.app

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tveaker.app.ui.screens.*
import com.tveaker.app.ui.theme.*
import com.tveaker.app.ui.viewmodel.*

sealed class Screen(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Home)
    object Shows : Screen("shows", "Shows", Icons.Default.Tv)
    object Recommendations : Screen("recommendations", "Discover", Icons.Default.Star)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
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
                NavigationBar(containerColor = BgSurface) {
                    items.forEachIndexed { index, screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            selected = selectedItem == index,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = BgPrimary,
                                selectedTextColor = Accent,
                                indicatorColor = Accent,
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
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Dashboard.route,
                modifier = Modifier.padding(innerPadding)
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
