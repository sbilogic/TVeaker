package com.tveaker.app

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tveaker.app.ui.screens.*
import com.tveaker.app.ui.theme.*
import com.tveaker.app.ui.update.AutoUpdateEffect
import com.tveaker.app.ui.viewmodel.*

sealed class Screen(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Dashboard : Screen("dashboard", "Home", Icons.Default.Home)
    object Shows : Screen("shows", "Shows", Icons.Default.Tv)
    object Recommendations : Screen("recommendations", "Discover", Icons.Default.AutoAwesome)
    object Settings : Screen("settings", "Settings", Icons.Default.Tune)
}

@Composable
fun TVeakerApp() {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val themePrefs = remember { context.getSharedPreferences("tveaker_prefs", Context.MODE_PRIVATE) }
    var darkTheme by remember {
        mutableStateOf(
            if (themePrefs.contains("dark_theme")) themePrefs.getBoolean("dark_theme", systemDark)
            else systemDark
        )
    }
    var compactMode by remember { mutableStateOf(themePrefs.getBoolean("compact_mode", false)) }
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route ?: Screen.Dashboard.route
    val items = listOf(
        Screen.Dashboard,
        Screen.Shows,
        Screen.Recommendations,
        Screen.Settings
    )
    val navigateTo = { screen: Screen ->
        navController.navigate(screen.route) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val onCompactModeChange: (Boolean) -> Unit = { enabled ->
        compactMode = enabled
        themePrefs.edit().putBoolean("compact_mode", enabled).apply()
    }

    TVeakerTheme(
        darkTheme = darkTheme,
        compactMode = compactMode,
        onCompactModeChange = onCompactModeChange
    ) {
        AutoUpdateEffect()
        val view = LocalView.current
        val colors = MaterialTheme.colorScheme
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
            window.statusBarColor = colors.background.toArgb()
            window.navigationBarColor = colors.background.toArgb()
        }
        Scaffold(
            bottomBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    items.forEach { screen ->
                        val isSelected = screen.route == currentRoute
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { navigateTo(screen) }
                                .padding(vertical = 3.dp),
                            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = screen.title,
                                modifier = Modifier.size(22.dp),
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = screen.title,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Dashboard.route,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                composable(Screen.Dashboard.route) {
                    EditorialDashboardScreen(
                        viewModel = viewModel(),
                        isDarkTheme = darkTheme,
                        onToggleTheme = {
                            darkTheme = !darkTheme
                            themePrefs.edit().putBoolean("dark_theme", darkTheme).apply()
                        },
                        onOpenUpdates = { navigateTo(Screen.Settings) }
                    )
                }
                composable(Screen.Shows.route) {
                    ShowsScreen(viewModel = viewModel())
                }
                composable(Screen.Recommendations.route) {
                    RecommendationsScreen(viewModel = viewModel())
                }
                composable(Screen.Settings.route) {
                    SettingsScreen(viewModel = viewModel())
                }
            }
        }
    }
}
