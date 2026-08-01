package com.quocdat.lingolens.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.quocdat.lingolens.LingoLensApplication
import com.quocdat.lingolens.ui.auth.*
import com.quocdat.lingolens.ui.screens.*

sealed class BottomNavItem(val screen: Screen, val title: String, val icon: ImageVector) {
    data object Home : BottomNavItem(Screen.Home, "Trang chủ", Icons.Default.Home)
    data object Camera : BottomNavItem(Screen.Camera, "Camera", Icons.Default.Search)
    data object MyWords : BottomNavItem(Screen.MyWords, "Sổ tay", Icons.Default.List)
    data object Stats : BottomNavItem(Screen.Stats, "Thống kê", Icons.Default.Star)
    data object Settings : BottomNavItem(Screen.Settings, "Cài đặt", Icons.Default.Settings)
}

@Composable
fun AppNavigation() {
    val application = LocalContext.current.applicationContext as LingoLensApplication
    val authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory(application.container.authRepository))
    when (val session = authViewModel.session.collectAsState().value) {
        SessionState.Loading -> SplashScreen()
        SessionState.SignedOut -> AuthNavigation(authViewModel)
        is SessionState.SignedIn -> MainNavigation(authViewModel, session.profile)
    }
}

@Composable
private fun AuthNavigation(viewModel: AuthViewModel) {
    val navController = rememberNavController()
    NavHost(navController, startDestination = Screen.Login.route) {
        composable(Screen.Login.route) { LoginScreen(viewModel) { navController.navigate(Screen.Register.route) } }
        composable(Screen.Register.route) { RegisterScreen(viewModel) { navController.popBackStack() } }
    }
}

@Composable
private fun MainNavigation(viewModel: AuthViewModel, profile: com.quocdat.lingolens.data.remote.dto.UserProfileDto) {
    val navController = rememberNavController()
    val currentDestination = navController.currentBackStackEntryAsState().value?.destination
    val items = listOf(BottomNavItem.Home, BottomNavItem.Camera, BottomNavItem.MyWords, BottomNavItem.Stats, BottomNavItem.Settings)
    val showBottomBar = currentDestination?.route != Screen.Result.route && currentDestination?.route != Screen.Camera.route
    Scaffold(bottomBar = {
        if (showBottomBar) NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 8.dp) {
            items.forEach { item ->
                NavigationBarItem(
                    selected = currentDestination?.hierarchy?.any { it.route == item.screen.route } == true,
                    onClick = { navController.navigate(item.screen.route) { popUpTo(navController.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } },
                    icon = { Icon(item.icon, item.title) }, label = { Text(item.title) }
                )
            }
        }
    }) { padding ->
        NavHost(navController, Screen.Home.route, Modifier.padding(padding)) {
            composable(Screen.Home.route) { HomeScreen(navController) }
            composable(Screen.Camera.route) { CameraScreen(navController) }
            composable(Screen.Result.route, arguments = listOf(navArgument("word") { type = NavType.StringType })) {
                ResultScreen(navController, it.arguments?.getString("word") ?: "cat")
            }
            composable(Screen.MyWords.route) { MyWordsScreen(navController) }
            composable(Screen.Stats.route) { StatsScreen(navController) }
            composable(Screen.Settings.route) { SettingsScreen(navController, profile, viewModel) }
        }
    }
}
