package com.patrollink.presentation.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import com.patrollink.presentation.PatrolViewModel
import com.patrollink.presentation.permission.PermissionGate
import com.patrollink.presentation.screen.AlertDetailScreen
import com.patrollink.presentation.screen.AlertListScreen
import com.patrollink.presentation.screen.DeviceScreen
import com.patrollink.presentation.screen.LoginScreen
import com.patrollink.presentation.screen.MediaScreen
import com.patrollink.presentation.screen.ProfileScreen
import com.patrollink.presentation.screen.SosScreen
import com.patrollink.presentation.screen.VersionInfoScreen
import com.patrollink.presentation.theme.PatrolDisplay
import com.patrollink.presentation.theme.PatrolTheme
import com.patrollink.presentation.theme.TechBlue

private enum class Route(val path: String, val label: String) {
    Device("device", "设备"),
    Alert("alert", "预警"),
    Media("media", "媒体"),
    Profile("profile", "我的"),
    VersionInfo("versionInfo", "版本"),
    Sos("sos", "SOS")
}

@Composable
fun PatrolApp(viewModel: PatrolViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val navController = rememberNavController()

    PatrolTheme(
        fontSizeMode = uiState.fontSizeMode,
        displayThemeMode = uiState.displayThemeMode
    ) {
        PermissionGate {
            if (!uiState.isLoggedIn) {
                LoginScreen(uiState, onLogin = viewModel::login)
                return@PermissionGate
            }

            val backStack by navController.currentBackStackEntryAsState()
            val currentRoute = backStack?.destination?.route.orEmpty()
            val showBottomBar = currentRoute !in setOf(Route.Sos.path, Route.VersionInfo.path) && !currentRoute.startsWith("alertDetail")

            Scaffold(
                bottomBar = { if (showBottomBar) PatrolBottomBar(navController) },
                containerColor = PatrolDisplay.colors.page
            ) { padding ->
                val contentPadding = if (currentRoute == Route.Sos.path) PaddingValues(0.dp) else padding
                Box(Modifier.padding(contentPadding).background(PatrolDisplay.colors.page)) {
                    NavHost(navController = navController, startDestination = Route.Device.path) {
                        composable(Route.Device.path) {
                            DeviceScreen(uiState, viewModel, onSos = { navController.navigateToSos() })
                        }
                        composable(Route.Alert.path) {
                            AlertListScreen(
                                uiState = uiState,
                                viewModel = viewModel,
                                onSos = { navController.navigateToSos() },
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
                                onSos = { navController.navigateToSos() }
                            )
                        }
                        composable(Route.Media.path) {
                            MediaScreen(uiState, viewModel, onSos = { navController.navigateToSos() })
                        }
                        composable(Route.Profile.path) {
                            ProfileScreen(
                                uiState = uiState,
                                viewModel = viewModel,
                                onSos = { navController.navigateToSos() },
                                onOpenVersionInfo = { navController.navigate(Route.VersionInfo.path) }
                            )
                        }
                        composable(Route.VersionInfo.path) {
                            VersionInfoScreen(uiState = uiState, viewModel = viewModel, onBack = { navController.popBackStack() })
                        }
                        composable(
                            Route.Sos.path,
                            enterTransition = { fadeIn(animationSpec = tween(durationMillis = 160)) },
                            exitTransition = { fadeOut(animationSpec = tween(durationMillis = 160)) },
                            popEnterTransition = { fadeIn(animationSpec = tween(durationMillis = 120)) },
                            popExitTransition = { fadeOut(animationSpec = tween(durationMillis = 180)) }
                        ) {
                            SosScreen(uiState, viewModel, onClose = { navController.popBackStack() })
                        }
                    }
                    uiState.operationMessage?.let { message ->
                        AppMessage(message = message, onShown = viewModel::clearMessage)
                    }
                }
            }
        }
    }
}

private fun NavHostController.navigateToSos() {
    navigate(Route.Sos.path) {
        launchSingleTop = true
    }
}

@Composable
private fun AppMessage(message: String, onShown: () -> Unit) {
    val colors = PatrolDisplay.colors
    LaunchedEffect(message) {
        delay(1800)
        onShown()
    }
    Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.TopCenter) {
        Text(
            text = message,
            color = colors.text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier
                .clip(RoundedCornerShape(99.dp))
                .background(colors.surfaceHigh)
                .border(1.dp, colors.border, RoundedCornerShape(99.dp))
                .padding(horizontal = 16.dp, vertical = 9.dp)
        )
    }
}

@Composable
private fun PatrolBottomBar(navController: NavHostController) {
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route.orEmpty()
    val tabs = listOf(Route.Device, Route.Alert, Route.Media, Route.Profile)
    val colors = PatrolDisplay.colors
    val barBg = colors.bottomBar
    val inactive = colors.textSubtle

    Row(
        Modifier
            .fillMaxWidth()
            .background(barBg)
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawLine(
                    color = colors.border.copy(alpha = 0.45f),
                    start = Offset(0f, stroke / 2f),
                    end = Offset(size.width, stroke / 2f),
                    strokeWidth = stroke
                )
            }
            .navigationBarsPadding()
            .height(72.dp)
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 0.dp),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        tabs.forEach { tab ->
            val selected = current == tab.path || current.startsWith("alertDetail") && tab == Route.Alert
            val color = if (selected) TechBlue else inactive
            val bg = if (selected) TechBlue.copy(alpha = 0.12f) else Color.Transparent
            Box(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(bg)
                    .clickable {
                        navController.navigate(tab.path) {
                            popUpTo(Route.Device.path)
                            launchSingleTop = true
                        }
                    }
                    .padding(horizontal = 17.dp, vertical = 6.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        when (tab) {
                            Route.Device -> Icons.Filled.Devices
                            Route.Alert -> Icons.Filled.Warning
                            Route.Media -> Icons.Filled.Folder
                            Route.Profile -> Icons.Filled.Person
                            Route.VersionInfo -> Icons.Filled.Folder
                            Route.Sos -> Icons.Filled.Warning
                        },
                        contentDescription = tab.label,
                        tint = color
                    )
                    Text(tab.label, fontWeight = FontWeight.Bold, color = color, fontSize = 10.sp)
                }
            }
        }
    }
}
