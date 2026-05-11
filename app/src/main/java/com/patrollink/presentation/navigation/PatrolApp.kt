package com.patrollink.presentation.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.patrollink.presentation.PatrolViewModel
import com.patrollink.presentation.screen.AlertDetailScreen
import com.patrollink.presentation.screen.AlertListScreen
import com.patrollink.presentation.screen.DeviceScreen
import com.patrollink.presentation.screen.LoginScreen
import com.patrollink.presentation.screen.MediaScreen
import com.patrollink.presentation.screen.ProfileScreen
import com.patrollink.presentation.screen.SosScreen
import com.patrollink.presentation.theme.Navy
import com.patrollink.presentation.theme.PageBg
import com.patrollink.presentation.theme.TechBlue

private enum class Route(val path: String, val label: String) {
    Device("device", "设备"),
    Alert("alert", "预警"),
    Media("media", "媒体"),
    Profile("profile", "我的"),
    Sos("sos", "SOS")
}

@Composable
fun PatrolApp(viewModel: PatrolViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val navController = rememberNavController()

    if (!uiState.isLoggedIn) {
        LoginScreen(uiState, onLogin = viewModel::login)
        return
    }

    Scaffold(
        bottomBar = { PatrolBottomBar(navController) },
        containerColor = PageBg
    ) { padding ->
        Box(Modifier.padding(padding).background(PageBg)) {
            NavHost(navController = navController, startDestination = Route.Device.path) {
                composable(Route.Device.path) {
                    DeviceScreen(uiState, viewModel, onSos = { navController.navigate(Route.Sos.path) })
                }
                composable(Route.Alert.path) {
                    AlertListScreen(
                        uiState = uiState,
                        viewModel = viewModel,
                        onSos = { navController.navigate(Route.Sos.path) },
                        onOpenDetail = { id -> navController.navigate("alertDetail/$id") }
                    )
                }
                composable("alertDetail/{id}") { entry ->
                    val id = entry.arguments?.getString("id").orEmpty()
                    AlertDetailScreen(
                        alertId = id,
                        uiState = uiState,
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onSos = { navController.navigate(Route.Sos.path) }
                    )
                }
                composable(Route.Media.path) {
                    MediaScreen(uiState, viewModel, onSos = { navController.navigate(Route.Sos.path) })
                }
                composable(Route.Profile.path) {
                    ProfileScreen(uiState, viewModel, onSos = { navController.navigate(Route.Sos.path) })
                }
                composable(Route.Sos.path) {
                    SosScreen(uiState, viewModel, onClose = { navController.popBackStack() })
                }
            }
        }
    }
}

@Composable
private fun PatrolBottomBar(navController: NavHostController) {
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route.orEmpty()
    val tabs = listOf(Route.Device, Route.Alert, Route.Media, Route.Profile)

    NavigationBar(containerColor = Navy, contentColor = Color.White) {
        tabs.forEach { tab ->
            val selected = current == tab.path || current.startsWith("alertDetail") && tab == Route.Alert
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(tab.path) {
                        popUpTo(Route.Device.path)
                        launchSingleTop = true
                    }
                },
                label = {
                    Text(
                        tab.label,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) TechBlue else Color(0xFF9AA3B2)
                    )
                },
                icon = {
                    Text(
                        when (tab) {
                            Route.Device -> "DEV"
                            Route.Alert -> "ALT"
                            Route.Media -> "MED"
                            Route.Profile -> "ME"
                            Route.Sos -> "SOS"
                        },
                        fontWeight = FontWeight.Black,
                        color = if (selected) TechBlue else Color(0xFF9AA3B2)
                    )
                }
            )
        }
    }
}
