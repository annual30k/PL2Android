package com.patrollink.presentation.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import com.patrollink.domain.OperationMessage
import com.patrollink.domain.OperationMessageType
import com.patrollink.presentation.PatrolViewModel
import com.patrollink.presentation.permission.PermissionGate
import com.patrollink.presentation.screen.AddDeviceScreen
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
import com.patrollink.presentation.theme.PatrolTextStyle
import com.patrollink.presentation.theme.Danger
import com.patrollink.presentation.theme.TechBlue

private enum class Route(val path: String, val label: String) {
    Device("device", "设备"),
    Alert("alert", "预警"),
    Media("media", "媒体"),
    Profile("profile", "我的"),
    VersionInfo("versionInfo", "版本"),
    AddDevice("addDevice", "添加设备"),
    Sos("sos", "SOS")
}

@Composable
fun PatrolApp(
    viewModel: PatrolViewModel,
    bluetoothEnabled: Boolean,
    onToggleBluetooth: () -> Unit
) {
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
            val lowBattery = uiState.device.battery < 15
            var dismissedLowBatteryReminder by rememberSaveable(uiState.device.id, lowBattery) { mutableStateOf(false) }

            Scaffold(
                bottomBar = { if (showBottomBar) PatrolBottomBar(navController) },
                containerColor = PatrolDisplay.colors.page
            ) { padding ->
                val contentPadding = when (currentRoute) {
                    Route.Sos.path -> PaddingValues(0.dp)
                    Route.AddDevice.path -> PaddingValues(bottom = padding.calculateBottomPadding())
                    else -> padding
                }
                Box(Modifier.fillMaxSize().background(PatrolDisplay.colors.page)) {
                    Box(Modifier.padding(contentPadding).background(PatrolDisplay.colors.page)) {
                        NavHost(navController = navController, startDestination = Route.Device.path) {
                            composable(Route.Device.path) {
                                DeviceScreen(
                                    uiState = uiState,
                                    viewModel = viewModel,
                                    onSos = { navController.navigateToSos() },
                                    onAddDevice = { navController.navigate(Route.AddDevice.path) }
                                )
                            }
                            composable(Route.AddDevice.path) {
                                AddDeviceScreen(
                                    uiState = uiState,
                                    viewModel = viewModel,
                                    bluetoothEnabled = bluetoothEnabled,
                                    onToggleBluetooth = onToggleBluetooth,
                                    onBack = { navController.popBackStack() },
                                    onSos = { navController.navigateToSos() }
                                )
                            }
                            composable(Route.Alert.path) {
                                AlertListScreen(
                                    uiState = uiState,
                                    viewModel = viewModel,
                                    onSos = { navController.navigateToSos() },
                                    onOpenDetail = { id -> navController.navigate("alertDetail/$id") }
                                )
                            }
                            composable(
                                "alertDetail/{id}",
                                enterTransition = { EnterTransition.None },
                                exitTransition = { ExitTransition.None },
                                popEnterTransition = { EnterTransition.None },
                                popExitTransition = { ExitTransition.None }
                            ) { entry ->
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
                    }
                    if (currentRoute.startsWith("alertDetail")) {
                        Spacer(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .windowInsetsBottomHeight(WindowInsets.navigationBars)
                                .background(PatrolDisplay.colors.bottomBar)
                        )
                    }
                    uiState.operationMessage?.let { message ->
                        AppMessage(message = message, onShown = viewModel::clearMessage)
                    }
                    if (lowBattery && !dismissedLowBatteryReminder && currentRoute != Route.Sos.path) {
                        EquipmentReminderDialog(
                            battery = uiState.device.battery,
                            onDismiss = { dismissedLowBatteryReminder = true }
                        )
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
private fun EquipmentReminderDialog(battery: Int, onDismiss: () -> Unit) {
    val colors = PatrolDisplay.colors
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = if (colors.dark) 0.46f else 0.28f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .padding(horizontal = 28.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(18.dp))
                .clickable(onClick = {})
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(99.dp)).background(Danger.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = Danger, modifier = Modifier.size(24.dp))
                }
                Column {
                    Text("系统提醒", color = colors.text, style = PatrolTextStyle.CardTitle.copy(fontSize = 17.sp, lineHeight = 22.sp))
                    Text("装备电量低", color = Danger, style = PatrolTextStyle.BodySmall.copy(fontWeight = FontWeight.Black))
                }
            }
            Text(
                "您的个人装备（单警执法记录仪）当前电量为 ${battery.coerceIn(0, 100)}%，请及时充电。",
                color = colors.textMuted,
                style = PatrolTextStyle.Body.copy(fontWeight = FontWeight.Bold)
            )
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Danger)
            ) {
                Text("知道了", color = Color.White, style = PatrolTextStyle.BodyStrong)
            }
        }
    }
}

@Composable
private fun AppMessage(message: OperationMessage, onShown: () -> Unit) {
    val displayColors = PatrolDisplay.colors
    val style = messageStyleFor(message.type, displayColors.dark)
    LaunchedEffect(message) {
        delay(style.durationMillis)
        onShown()
    }
    Box(Modifier.fillMaxSize().padding(horizontal = 32.dp), contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(style.container)
                .border(1.dp, style.border, RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(style.accent.copy(alpha = if (displayColors.dark) 0.22f else 0.13f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(style.icon, contentDescription = null, tint = style.accent, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = style.title,
                    color = style.accent,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    letterSpacing = 0.sp
                )
                Text(
                    text = message.text,
                    color = style.text,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private data class MessageVisualStyle(
    val title: String,
    val icon: ImageVector,
    val accent: Color,
    val container: Color,
    val border: Color,
    val text: Color,
    val durationMillis: Long
)

@Composable
private fun messageStyleFor(type: OperationMessageType, dark: Boolean): MessageVisualStyle {
    val accent = when (type) {
        OperationMessageType.Info -> Color(0xFF2563EB)
        OperationMessageType.Success -> Color(0xFF16A34A)
        OperationMessageType.Warning -> Color(0xFFF59E0B)
        OperationMessageType.Error -> Color(0xFFDC2626)
    }
    val title = when (type) {
        OperationMessageType.Info -> "提示"
        OperationMessageType.Success -> "完成"
        OperationMessageType.Warning -> "警告"
        OperationMessageType.Error -> "错误"
    }
    val icon = when (type) {
        OperationMessageType.Info -> Icons.Filled.Info
        OperationMessageType.Success -> Icons.Filled.CheckCircle
        OperationMessageType.Warning -> Icons.Filled.Warning
        OperationMessageType.Error -> Icons.Filled.Error
    }
    val container = if (dark) {
        when (type) {
            OperationMessageType.Info -> Color(0xFF0F2547)
            OperationMessageType.Success -> Color(0xFF0D2F22)
            OperationMessageType.Warning -> Color(0xFF332711)
            OperationMessageType.Error -> Color(0xFF3A1518)
        }
    } else {
        when (type) {
            OperationMessageType.Info -> Color(0xFFEFF6FF)
            OperationMessageType.Success -> Color(0xFFF0FDF4)
            OperationMessageType.Warning -> Color(0xFFFFF7ED)
            OperationMessageType.Error -> Color(0xFFFEF2F2)
        }
    }
    val text = if (dark) Color(0xFFF8FAFC) else Color(0xFF111827)
    return MessageVisualStyle(
        title = title,
        icon = icon,
        accent = accent,
        container = container,
        border = accent.copy(alpha = if (dark) 0.42f else 0.26f),
        text = text,
        durationMillis = if (type == OperationMessageType.Error || type == OperationMessageType.Warning) 2400 else 1800
    )
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
            .height(76.dp)
            .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 0.dp),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        tabs.forEach { tab ->
            val selected = current == tab.path ||
                current == Route.AddDevice.path && tab == Route.Device ||
                current.startsWith("alertDetail") && tab == Route.Alert
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
                    .padding(horizontal = 15.dp, vertical = 7.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        when (tab) {
                            Route.Device -> Icons.Filled.Devices
                            Route.Alert -> Icons.Filled.Warning
                            Route.Media -> Icons.Filled.Folder
                            Route.Profile -> Icons.Filled.Person
                            Route.VersionInfo -> Icons.Filled.Folder
                            Route.AddDevice -> Icons.Filled.Devices
                            Route.Sos -> Icons.Filled.Warning
                        },
                        contentDescription = tab.label,
                        tint = color,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(tab.label, fontWeight = FontWeight.Bold, color = color, fontSize = 12.sp, lineHeight = 16.sp)
                }
            }
        }
    }
}
