package com.quocdat.lingolens.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.quocdat.lingolens.ui.screens.*

sealed class BottomNavItem(val screen: Screen, val title: String, val icon: ImageVector) {
    object Home : BottomNavItem(Screen.Home, "Trang chủ", Icons.Default.Home)
    object Camera : BottomNavItem(Screen.Camera, "Camera", Icons.Default.Search)
    object MyWords : BottomNavItem(Screen.MyWords, "Sổ tay", Icons.Default.List)
    object Stats : BottomNavItem(Screen.Stats, "Thống kê", Icons.Default.Star)
    object Settings : BottomNavItem(Screen.Settings, "Cài đặt", Icons.Default.Settings)
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val bottomNavItems = listOf(
        BottomNavItem.Home,
        BottomNavItem.Camera,
        BottomNavItem.MyWords,
        BottomNavItem.Stats,
        BottomNavItem.Settings
    )

    // Hide bottom bar on Camera and Result screen for fullscreen look
    val showBottomBar = currentDestination?.route != Screen.Result.route &&
            currentDestination?.route != Screen.Camera.route

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any { it.route == item.screen.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(imageVector = item.icon, contentDescription = item.title) },
                            label = { Text(item.title) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(navController = navController)
            }
            composable(Screen.Camera.route) {
                CameraScreen(navController = navController)
            }
            composable(
                route = Screen.Result.route,
                arguments = listOf(navArgument("word") { type = NavType.StringType })
            ) { backStackEntry ->
                val word = backStackEntry.arguments?.getString("word") ?: "cat"
                ResultScreen(navController = navController, word = word)
            }
            composable(Screen.MyWords.route) {
                MyWordsScreen(navController = navController)
            }
            composable(Screen.Stats.route) {
                StatsScreen(navController = navController)
            }
            composable(Screen.Settings.route) {
                SettingsScreen(navController = navController)
            }
        }
    }
}
