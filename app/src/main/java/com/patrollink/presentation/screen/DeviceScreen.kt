package com.patrollink.presentation.screen

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrollink.R
import com.patrollink.domain.AppUiState
import com.patrollink.domain.DeviceCapabilities
import com.patrollink.domain.DeviceEvent
import com.patrollink.domain.DeviceEventLevel
import com.patrollink.domain.DeviceFactoryResetTarget
import com.patrollink.domain.DeviceStatus
import com.patrollink.domain.DeviceType
import com.patrollink.domain.ScannedDevice
import com.patrollink.domain.StreamMode
import com.patrollink.domain.StreamRelayState
import com.patrollink.presentation.PatrolViewModel
import com.patrollink.presentation.component.ActionTile
import com.patrollink.presentation.component.DeviceStatPill
import com.patrollink.presentation.component.MetricTile
import com.patrollink.presentation.component.OfflineBanner
import com.patrollink.presentation.component.PatrolCard
import com.patrollink.presentation.component.StatusTag
import com.patrollink.presentation.component.SystemBars
import com.patrollink.presentation.theme.PatrolDisplay
import com.patrollink.presentation.theme.Danger
import com.patrollink.presentation.theme.Navy
import com.patrollink.presentation.theme.Success
import com.patrollink.presentation.theme.PatrolTextStyle
import com.patrollink.presentation.theme.TechBlue
import com.patrollink.presentation.theme.Warning
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun DeviceScreen(uiState: AppUiState, viewModel: PatrolViewModel, onAddDevice: () -> Unit) {
    val colors = PatrolDisplay.colors
    SystemBars(statusBarColor = colors.topBar, navigationBarColor = colors.bottomBar, lightStatusBar = !colors.dark, lightNavigationBar = !colors.dark)
    val device = uiState.device
    val connectedDevices = uiState.connectedDevices.filter { it.isControllableDevice() }
    val hasConnectedDevice = device.isControllableDevice()
    val confirmClearAccount = remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(colors.page)) {
        Column(Modifier.fillMaxSize()) {
            OfflineBanner(uiState.networkOnline)
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                contentPadding = PaddingValues(top = 6.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    DeviceConsoleHeader(
                        devices = connectedDevices,
                        selectedId = device.id,
                        onSelect = viewModel::selectConnectedDevice,
                        onAddDevice = onAddDevice
                    )
                }
                if (hasConnectedDevice) {
                    item { CurrentDevicePanel(device) }
                    item { DevicePrimaryPanel(uiState, viewModel, device) }
                    item {
                        DeviceControlConsole(
                            device = device,
                            capabilities = uiState.deviceCapabilities,
                            recording = uiState.realtimeAudioSyncing,
                            photoBusy = uiState.photoCaptureInProgress,
                            onPhoto = viewModel::takePhoto,
                            onToggleRecord = viewModel::toggleRecord,
                            onToggleTalk = viewModel::toggleTalk,
                            onSelfCheck = viewModel::runDeviceSelfCheck,
                            onMore = { confirmClearAccount.value = true }
                        )
                    }
                    item { DeviceStatusPanel(device, uiState.deviceCapabilities, uiState.realtimeAudioSyncing) }
                    item { DeviceEventsPanel(uiState.deviceEvents) }
                } else {
                    item { EmptyDeviceConsole(onAddDevice) }
                }
            }
        }
        if (uiState.deviceCommandInProgress) {
            DeviceCommandOverlay(uiState.deviceCommandMessage)
        }
    }
    if (confirmClearAccount.value) {
        ResetPairingDialog(
            onDismiss = { confirmClearAccount.value = false },
            onClearAccount = {
                confirmClearAccount.value = false
                viewModel.clearConnectedDeviceAccount()
            },
            onResetHeadset = {
                confirmClearAccount.value = false
                viewModel.factoryResetConnectedDevice(DeviceFactoryResetTarget.Headset)
            },
            onResetGlasses = {
                confirmClearAccount.value = false
                viewModel.factoryResetConnectedDevice(DeviceFactoryResetTarget.Glasses)
            }
        )
    }
}

@Composable
private fun DeviceCommandOverlay(message: String) {
    val colors = PatrolDisplay.colors
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.34f))
            .clickable(enabled = true, onClick = {}),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .width(220.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                .padding(horizontal = 18.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(color = TechBlue, strokeWidth = 4.dp, modifier = Modifier.size(38.dp))
            Text(message.ifBlank { "正在等待设备回复" }, color = colors.text, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Black)
            Text("请勿重复操作", color = colors.textMuted, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DeviceConsoleHeader(
    devices: List<DeviceStatus>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onAddDevice: () -> Unit
) {
    val colors = PatrolDisplay.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("设备", color = colors.text, fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Black)
            Text("${devices.size} 台设备在线", color = colors.textMuted, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (devices.isNotEmpty()) {
                items(devices.size) { index ->
                    val device = devices[index]
                    DeviceSwitchChip(
                        device = device,
                        selected = device.id == selectedId,
                        onClick = { onSelect(device.id) }
                    )
                }
            }
            item {
                Box(
                    Modifier
                        .size(width = 40.dp, height = 40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                        .clickable(onClick = onAddDevice),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "添加设备", tint = colors.textMuted, modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

@Composable
private fun DeviceSwitchChip(device: DeviceStatus, selected: Boolean, onClick: () -> Unit) {
    val colors = PatrolDisplay.colors
    val accent = device.type.accent()
    Row(
        Modifier
            .width(if (selected) 150.dp else 168.dp)
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) TechBlue else colors.surface)
            .border(1.dp, if (selected) TechBlue else colors.border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier
                .size(width = 42.dp, height = 32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (selected) Color.White.copy(alpha = 0.94f) else colors.control.copy(alpha = 0.72f)),
            contentAlignment = Alignment.Center
        ) {
            val imageModifier = when (device.type) {
                DeviceType.Headset -> Modifier.size(33.dp)
                DeviceType.Glasses -> Modifier.size(width = 38.dp, height = 24.dp)
                else -> Modifier.size(30.dp)
            }
            DeviceAssetImage(type = device.type, contentDescription = null, modifier = imageModifier)
        }
        Text(
            device.name.removePrefix("ForceLink-"),
            color = if (selected) Color.White else colors.textMuted,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Box(Modifier.size(6.dp).clip(RoundedCornerShape(99.dp)).background(if (device.online) Success else colors.textSubtle))
    }
}

@Composable
private fun DeviceAssetImage(type: DeviceType, contentDescription: String?, modifier: Modifier = Modifier) {
    val resId = when (type) {
        DeviceType.Headset -> R.drawable.device_headset_h7
        DeviceType.Glasses -> R.drawable.device_smart_glasses
        DeviceType.Recorder -> R.drawable.device_smart_glasses
        else -> R.drawable.device_smart_glasses
    }
    Image(
        painter = painterResource(resId),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}

@Composable
private fun CurrentDevicePanel(device: DeviceStatus) {
    val colors = PatrolDisplay.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(8.dp))
            .padding(start = 14.dp, end = 14.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DeviceAssetImage(type = device.type, contentDescription = null, modifier = Modifier.size(width = 78.dp, height = 78.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(device.name, color = colors.text, fontSize = 19.sp, lineHeight = 23.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(99.dp)).background(if (device.online) Success else Warning))
                    Text(if (device.online) "在线" else "离线", color = colors.textMuted, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Black)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border.copy(alpha = 0.72f)))
            Row(Modifier.fillMaxWidth().height(39.dp), verticalAlignment = Alignment.CenterVertically) {
                DeviceSummaryMetric("电量", device.batteryText(), device.batteryProgress(), Success, Modifier.weight(1f))
                DeviceMetricDivider()
                DeviceSummaryMetric("存储", device.storageTextCompact(), device.storageProgress(), TechBlue, Modifier.weight(1.45f), valueFontSize = 12)
                DeviceMetricDivider()
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("连接状态", color = colors.textMuted, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Black)
                    Text(if (device.online) "连接稳定" else "未连接", color = if (device.online) Success else Warning, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Icon(Icons.Filled.Router, contentDescription = null, tint = if (device.online) Success else Warning, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun DeviceSummaryMetric(label: String, value: String, progress: Float, accent: Color, modifier: Modifier = Modifier, valueFontSize: Int = 13) {
    val colors = PatrolDisplay.colors
    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(label, color = colors.textMuted, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Black)
        Text(value, color = accent, fontSize = valueFontSize.sp, lineHeight = 16.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(99.dp)).background(colors.control)) {
            Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(4.dp).clip(RoundedCornerShape(99.dp)).background(accent))
        }
    }
}

@Composable
private fun DeviceMetricDivider() {
    Box(Modifier.width(1.dp).height(38.dp).background(PatrolDisplay.colors.border.copy(alpha = 0.8f)))
    Spacer(Modifier.width(8.dp))
}

@Composable
private fun DevicePrimaryPanel(uiState: AppUiState, viewModel: PatrolViewModel, device: DeviceStatus) {
    when (device.type) {
        DeviceType.Sensor -> SensorStatusHero(device)
        else -> RecorderLiveFeed(uiState, viewModel, device)
    }
}

@Composable
private fun SensorStatusHero(device: DeviceStatus) {
    val colors = PatrolDisplay.colors
    PatrolCard(radius = 8, padding = PaddingValues(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("设备状态", color = colors.text, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Black)
                StatusTag("监测中", Success, filled = true)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SensorMetricTile("环境", "正常", Success, Modifier.weight(1f))
                SensorMetricTile("姿态", "稳定", TechBlue, Modifier.weight(1f))
                SensorMetricTile("电量", device.batteryText(), Warning, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SensorMetricTile(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    val colors = PatrolDisplay.colors
    Column(
        modifier
            .height(82.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = 0.10f))
            .padding(10.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(99.dp)).background(accent))
        Spacer(Modifier.height(8.dp))
        Text(label, color = colors.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text(value, color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

@Composable
private fun DeviceControlConsole(
    device: DeviceStatus,
    capabilities: DeviceCapabilities,
    recording: Boolean,
    photoBusy: Boolean,
    onPhoto: () -> Unit,
    onToggleRecord: () -> Unit,
    onToggleTalk: () -> Unit,
    onSelfCheck: () -> Unit,
    onMore: () -> Unit
) {
    val enabled = device.canUseSdkControls()
    val photoEnabled = enabled && device.type != DeviceType.Sensor && (device.type != DeviceType.Headset || capabilities.supportsPhoto) && !photoBusy
    val videoEnabled = enabled && device.type != DeviceType.Sensor && (device.type != DeviceType.Headset || capabilities.supportsVideo)
    val audioEnabled = enabled && device.type == DeviceType.Headset && capabilities.supportsAudioRecord
    val colors = PatrolDisplay.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text("常用操作", color = colors.text, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Black)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DeviceControlButton(
                    label = if (photoBusy) "拍照中" else "拍照",
                    icon = Icons.Filled.CameraAlt,
                    accent = TechBlue,
                    enabled = photoEnabled,
                    onClick = onPhoto,
                    modifier = Modifier.weight(1f)
                )
                DeviceControlButton(
                    label = if (device.isRecording) "停止录像" else "开始录像",
                    icon = if (device.isRecording) Icons.Filled.Stop else Icons.Filled.Videocam,
                    accent = if (device.isRecording) Danger else Color(0xFFDC2626),
                    enabled = videoEnabled,
                    active = device.isRecording,
                    onClick = onToggleRecord,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DeviceControlButton(
                    label = if (audioEnabled) {
                        if (recording || device.isTalking) "停止录音" else "录音"
                    } else {
                        "设备自检"
                    },
                    icon = if (audioEnabled) Icons.Filled.Mic else Icons.Filled.CheckCircle,
                    accent = if (audioEnabled && (recording || device.isTalking)) Danger else TechBlue,
                    enabled = if (audioEnabled) true else enabled,
                    active = audioEnabled && (recording || device.isTalking),
                    onClick = if (audioEnabled) onToggleTalk else onSelfCheck,
                    modifier = Modifier.weight(1f)
                )
                DeviceControlButton(
                    label = "更多",
                    icon = Icons.Filled.MoreHoriz,
                    accent = Color(0xFF64748B),
                    enabled = enabled,
                    onClick = onMore,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DeviceControlButton(
    label: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
    onClick: () -> Unit
) {
    val colors = PatrolDisplay.colors
    val bg = when {
        active -> accent
        else -> colors.surface
    }
    val content = when {
        active -> Color.White
        else -> colors.text
    }
    Box(
        modifier
            .height(42.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(bg)
            .border(1.dp, if (active) Color.Transparent else colors.border, RoundedCornerShape(7.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(icon, contentDescription = null, tint = if (active) Color.White else accent.copy(alpha = if (enabled) 1f else 0.68f), modifier = Modifier.size(24.dp))
            Text(label, color = content, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DeviceStatusPanel(device: DeviceStatus, capabilities: DeviceCapabilities, recording: Boolean) {
    val colors = PatrolDisplay.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("设备状态", color = colors.text, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Black)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            DeviceStatusTile("摄像头", cameraStatus(device, capabilities), Icons.Filled.CameraAlt, TechBlue, Modifier.weight(1f))
            DeviceStatusTile("录音", audioStatus(device, capabilities, recording), Icons.Filled.Mic, Success, Modifier.weight(1f))
            DeviceStatusTile("蓝牙", if (device.online) "已连接" else "未连接", Icons.Filled.Bluetooth, TechBlue, Modifier.weight(1f))
            DeviceStatusTile("存储", storageStatus(device), Icons.Filled.Storage, Success, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DeviceStatusTile(label: String, value: String, icon: ImageVector, accent: Color, modifier: Modifier = Modifier) {
    val colors = PatrolDisplay.colors
    Column(
        modifier
            .height(62.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(7.dp))
            .padding(horizontal = 4.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, color = colors.text, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, color = deviceInfoValueColor(label, value, accent), fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun EmptyDeviceConsole(onAddDevice: () -> Unit) {
    val colors = PatrolDisplay.colors
    PatrolCard(radius = 8, padding = PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("暂无在线设备", color = colors.text, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Black)
            Text("添加或连接设备后，可在这里进行拍照、录像、录音、自检和状态查看。", color = colors.textMuted, fontSize = 13.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold)
            Row(
                Modifier
                    .height(42.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(TechBlue)
                    .clickable(onClick = onAddDevice)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp))
                Text("添加设备", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun ResetPairingDialog(
    onDismiss: () -> Unit,
    onClearAccount: () -> Unit,
    onResetHeadset: () -> Unit,
    onResetGlasses: () -> Unit
) {
    val colors = PatrolDisplay.colors
    val pendingAction = remember { mutableStateOf<MoreConfirmAction?>(null) }
    val action = pendingAction.value
    AlertDialog(
        containerColor = colors.surface,
        tonalElevation = 0.dp,
        onDismissRequest = {
            if (action == null) {
                onDismiss()
            } else {
                pendingAction.value = null
            }
        },
        title = {
            if (action == null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(TechBlue.copy(alpha = 0.08f))
                        .border(1.dp, TechBlue.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(TechBlue.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.MoreHoriz, contentDescription = null, tint = TechBlue, modifier = Modifier.size(22.dp))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("更多设备操作", color = colors.text, fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.Black)
                        Text("用于处理配对异常或设备重置", color = TechBlue, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Black)
                    }
                }
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Danger.copy(alpha = 0.08f))
                        .border(1.dp, Danger.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Danger.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Info, contentDescription = null, tint = Danger, modifier = Modifier.size(21.dp))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(action.confirmTitle, color = colors.text, fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.Black)
                        Text("请再次确认后继续", color = Danger, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        },
        text = {
            if (action == null) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MoreActionRow(
                        title = "清除配对账号",
                        description = "解除当前账号与设备的绑定关系",
                        icon = Icons.Filled.Router,
                        accent = TechBlue,
                        danger = false,
                        onClick = { pendingAction.value = MoreConfirmAction.ClearAccount }
                    )
                    MoreActionRow(
                        title = "恢复耳机出厂",
                        description = "清除耳机端数据，完成后需要重新配对",
                        icon = Icons.Filled.Mic,
                        accent = Danger,
                        danger = true,
                        onClick = { pendingAction.value = MoreConfirmAction.ResetHeadset }
                    )
                    MoreActionRow(
                        title = "恢复眼镜出厂",
                        description = "清除眼镜端数据，完成后需要重新配对",
                        icon = Icons.Filled.CameraAlt,
                        accent = Danger,
                        danger = true,
                        onClick = { pendingAction.value = MoreConfirmAction.ResetGlasses }
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Warning.copy(alpha = 0.10f))
                            .border(1.dp, Warning.copy(alpha = 0.24f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text("恢复出厂会删除设备端数据，请确认设备在身边后再操作。", color = Warning, fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Black)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(action.confirmMessage, color = colors.textMuted, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Danger.copy(alpha = 0.08f))
                            .border(1.dp, Danger.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 9.dp)
                    ) {
                        Text(action.warningText, color = Danger, fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        },
        confirmButton = {
            if (action == null) {
                TextButton(onClick = onDismiss) {
                    Text("关闭", color = colors.textMuted, fontWeight = FontWeight.Black)
                }
            } else {
                TextButton(
                    onClick = {
                        when (action) {
                            MoreConfirmAction.ClearAccount -> onClearAccount()
                            MoreConfirmAction.ResetHeadset -> onResetHeadset()
                            MoreConfirmAction.ResetGlasses -> onResetGlasses()
                        }
                    }
                ) {
                    Text(action.confirmButton, color = Danger, fontWeight = FontWeight.Black)
                }
            }
        },
        dismissButton = {
            if (action != null) {
                TextButton(onClick = { pendingAction.value = null }) {
                    Text("取消", color = colors.textMuted, fontWeight = FontWeight.Black)
                }
            }
        }
    )
}

private enum class MoreConfirmAction(
    val confirmTitle: String,
    val confirmMessage: String,
    val warningText: String,
    val confirmButton: String
) {
    ClearAccount(
        confirmTitle = "确认清除配对账号？",
        confirmMessage = "清除后当前设备会解除账号绑定，后续需要重新搜索并完成配对。",
        warningText = "该操作会影响当前连接关系，请确认不是误触。",
        confirmButton = "确认清除"
    ),
    ResetHeadset(
        confirmTitle = "确认恢复耳机出厂？",
        confirmMessage = "恢复出厂会清除耳机端配置和数据，完成后需要重新配对。",
        warningText = "这是高风险操作，请确认耳机在身边且确实需要重置。",
        confirmButton = "确认恢复"
    ),
    ResetGlasses(
        confirmTitle = "确认恢复眼镜出厂？",
        confirmMessage = "恢复出厂会清除眼镜端配置和数据，完成后需要重新配对。",
        warningText = "这是高风险操作，请确认眼镜在身边且确实需要重置。",
        confirmButton = "确认恢复"
    )
}

@Composable
private fun MoreActionRow(
    title: String,
    description: String,
    icon: ImageVector,
    accent: Color,
    danger: Boolean,
    onClick: () -> Unit
) {
    val colors = PatrolDisplay.colors
    val rowBackground = if (danger) Danger.copy(alpha = 0.055f) else TechBlue.copy(alpha = 0.045f)
    val rowBorder = if (danger) Danger.copy(alpha = 0.26f) else TechBlue.copy(alpha = 0.18f)
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(rowBackground)
            .border(1.dp, rowBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(accent.copy(alpha = if (danger) 0.12f else 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = colors.text, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(description, color = colors.textMuted, fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = accent.copy(alpha = 0.82f), modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun ConnectedDevicesPanel(
    devices: List<DeviceStatus>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onAddDevice: () -> Unit
) {
    val colors = PatrolDisplay.colors
    PatrolCard(radius = 16, padding = PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "已连接设备",
                    color = colors.text,
                    style = PatrolTextStyle.CardTitle.copy(fontSize = 16.sp, lineHeight = 21.sp)
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${devices.size} 台在线", color = colors.textMuted, style = PatrolTextStyle.Caption)
                    Row(
                        Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(TechBlue.copy(alpha = 0.12f))
                            .clickable(onClick = onAddDevice)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("+", color = TechBlue, fontSize = 18.sp, lineHeight = 18.sp, fontWeight = FontWeight.Black)
                        Text("添加设备", color = TechBlue, style = PatrolTextStyle.BodySmall.copy(fontWeight = FontWeight.Black), maxLines = 1)
                    }
                }
            }
            if (devices.isEmpty()) {
                Text(
                    "当前没有设备在线，请添加或连接设备后再使用拍照、录像、对讲和实时画面。",
                    color = colors.textMuted,
                    style = PatrolTextStyle.BodySmall.copy(fontWeight = FontWeight.Bold)
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(devices.size) { index ->
                        val device = devices[index]
                        ConnectedDeviceChip(
                            device = device,
                            selected = device.id == selectedId,
                            onClick = { onSelect(device.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectedDeviceChip(device: DeviceStatus, selected: Boolean, onClick: () -> Unit) {
    val colors = PatrolDisplay.colors
    val accent = device.type.accent()
    Row(
        Modifier
            .width(178.dp)
            .height(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) accent.copy(alpha = 0.12f) else colors.control.copy(alpha = 0.52f))
            .border(1.dp, if (selected) accent.copy(alpha = 0.42f) else colors.border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            DeviceTypeIcon(type = device.type, tint = accent, modifier = Modifier.size(30.dp), fontSize = 23.sp)
        }
        Column(Modifier.weight(1f)) {
            Text(device.name, color = colors.text, style = PatrolTextStyle.BodyStrong.copy(fontSize = 14.sp, lineHeight = 18.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(device.type.label, color = colors.textMuted, style = PatrolTextStyle.BodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        }
        Box(Modifier.size(7.dp).clip(RoundedCornerShape(99.dp)).background(if (device.online) Success else colors.textSubtle))
    }
}

@Composable
private fun RecorderLiveFeed(uiState: AppUiState, viewModel: PatrolViewModel, device: DeviceStatus) {
    val enabled = device.isControllableDevice()
    val fullScreen = remember(device.id) { mutableStateOf(false) }
    val onToggleStream = {
        if (uiState.streamState == StreamRelayState.Relaying) {
            viewModel.stopStream()
        } else {
            viewModel.startStream(StreamMode.LowLatency)
        }
        Unit
    }
    StreamPlayerSurface(
        uiState = uiState,
        device = device,
        enabled = enabled,
        onToggleStream = onToggleStream,
        onFullscreen = { fullScreen.value = true },
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2.1f)
    )
    if (fullScreen.value) {
        StreamPlayerFullScreen(
            uiState = uiState,
            device = device,
            enabled = enabled,
            onToggleStream = onToggleStream,
            onDismiss = { fullScreen.value = false }
        )
    }
}

@Composable
private fun StreamPlayerSurface(
    uiState: AppUiState,
    device: DeviceStatus,
    enabled: Boolean,
    onToggleStream: () -> Unit,
    onFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isRelaying = uiState.streamState == StreamRelayState.Relaying
    val isConnecting = uiState.streamState == StreamRelayState.Connecting
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black)
            .border(1.dp, Color(0xFF0F172A), RoundedCornerShape(8.dp))
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF020617), Color(0xFF0B1220))))
        )
        Column(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(TechBlue.copy(alpha = if (isRelaying) 0.18f else 0.09f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Videocam,
                    contentDescription = null,
                    tint = if (isRelaying) TechBlue else Color.White.copy(alpha = 0.42f),
                    modifier = Modifier.size(38.dp)
                )
            }
            Text(
                if (enabled) streamHint(uiState.streamState) else "设备未连接",
                color = Color.White,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                device.name,
                color = Color.White.copy(alpha = 0.56f),
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(
            Modifier.align(Alignment.TopStart).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(Modifier.size(7.dp).clip(RoundedCornerShape(99.dp)).background(if (isRelaying) Danger else Warning))
            Text(streamTag(uiState.streamState, device.isRecording), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
        Box(
            Modifier.align(Alignment.BottomStart).padding(10.dp),
            contentAlignment = Alignment.Center
        ) {
            StreamControlButton(
                icon = if (isRelaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                label = when {
                    isConnecting -> "连接中"
                    isRelaying -> "停止"
                    else -> "播放"
                },
                enabled = enabled && !isConnecting,
                active = isRelaying,
                onClick = onToggleStream
            )
        }
        Box(
            Modifier.align(Alignment.BottomEnd).padding(10.dp),
            contentAlignment = Alignment.Center
        ) {
            StreamControlButton(
                icon = Icons.Filled.Fullscreen,
                label = "全屏",
                enabled = enabled,
                active = false,
                onClick = onFullscreen
            )
        }
    }
}

@Composable
private fun StreamControlButton(icon: ImageVector, label: String, enabled: Boolean, active: Boolean, onClick: () -> Unit) {
    val clickModifier = if (enabled) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(
                when {
                    active -> Danger
                    enabled -> Color.White.copy(alpha = 0.14f)
                    else -> Color.White.copy(alpha = 0.07f)
                }
            )
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(7.dp))
            .then(clickModifier)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(icon, contentDescription = label, tint = Color.White.copy(alpha = if (enabled) 1f else 0.38f), modifier = Modifier.size(16.dp))
        Text(label, color = Color.White.copy(alpha = if (enabled) 1f else 0.38f), fontSize = 12.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun StreamPlayerFullScreen(
    uiState: AppUiState,
    device: DeviceStatus,
    enabled: Boolean,
    onToggleStream: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).padding(10.dp)) {
            StreamPlayerSurface(
                uiState = uiState,
                device = device,
                enabled = enabled,
                onToggleStream = onToggleStream,
                onFullscreen = onDismiss,
                modifier = Modifier.fillMaxSize()
            )
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
            ) {
                Text("关闭", color = Color.White, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun RecorderActions(device: DeviceStatus, photoBusy: Boolean, viewModel: PatrolViewModel) {
    val enabled = device.isControllableDevice()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) { ActionTile(if (photoBusy) "拍照中" else "拍照", "camera", enabled = enabled && !photoBusy, onClick = viewModel::takePhoto) }
        Box(Modifier.weight(1f)) {
            ActionTile(
                if (device.isRecording) "停止录像" else "录制视频",
                if (device.isRecording) "stop" else "video",
                active = device.isRecording,
                danger = device.isRecording,
                enabled = enabled,
                onClick = viewModel::toggleRecord
            )
        }
        Box(Modifier.weight(1f)) { ActionTile("设备自检", "info", enabled = enabled, onClick = viewModel::runDeviceSelfCheck) }
    }
}

@Composable
private fun HeadsetCapabilityCard(device: DeviceStatus, capabilities: DeviceCapabilities, recording: Boolean) {
    CapabilitySummaryCard(
        title = device.name,
        type = device.type,
        rows = buildList {
            add(
                "摄像头" to when {
                    !capabilities.supportsPhoto && !capabilities.supportsVideo -> "等待控制通道"
                    device.isRecording -> "录像中"
                    else -> "待机"
                }
            )
            if (capabilities.supportsAudioRecord) {
                add(
                    "耳机录音" to when {
                        recording || device.isTalking -> "录制中"
                        else -> "待机"
                    }
                )
            }
            add("在线时长" to device.onlineDuration)
            add("电量" to device.batteryText())
            add("本机存储" to device.storageText())
        }
    )
}

@Composable
private fun HeadsetActions(device: DeviceStatus, capabilities: DeviceCapabilities, recording: Boolean, photoBusy: Boolean, viewModel: PatrolViewModel) {
    val confirmClearAccount = remember { mutableStateOf(false) }
    val enabled = device.canUseSdkControls()
    val recordEnabled = enabled && capabilities.supportsAudioRecord
    val photoEnabled = enabled && capabilities.supportsPhoto && !photoBusy
    val videoEnabled = enabled && capabilities.supportsVideo
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (capabilities.supportsAudioRecord) {
                Box(Modifier.weight(1f)) {
                    ActionTile(
                        if (recording || device.isTalking) "停止录音" else "耳机录音",
                        if (recording || device.isTalking) "stop" else "talk",
                        active = recording || device.isTalking,
                        danger = recording || device.isTalking,
                        enabled = recordEnabled,
                        onClick = viewModel::toggleTalk
                    )
                }
            }
            Box(Modifier.weight(1f)) { ActionTile(if (photoBusy) "拍照中" else "拍照", "camera", enabled = photoEnabled, onClick = viewModel::takePhoto) }
            Box(Modifier.weight(1f)) {
                ActionTile(
                    if (device.isRecording) "停止录像" else "执法录像",
                    if (device.isRecording) "stop" else "video",
                    active = device.isRecording,
                    danger = device.isRecording,
                    enabled = videoEnabled,
                    onClick = viewModel::toggleRecord
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                ActionTile("设备自检", "info", enabled = enabled, onClick = viewModel::runDeviceSelfCheck)
            }
            Box(Modifier.weight(1f)) {
                ActionTile("重置配对", "info", danger = true, enabled = enabled, onClick = { confirmClearAccount.value = true })
            }
        }
    }
    if (confirmClearAccount.value) {
        AlertDialog(
            onDismissRequest = { confirmClearAccount.value = false },
            title = { Text("重置设备配对") },
            text = { Text("先尝试清除设备账号；如果设备仍提示账号不一致，可恢复耳机或眼镜模块出厂设置。恢复出厂会删除设备端数据，完成后需要重新搜索并配对 PatrolLink。") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            confirmClearAccount.value = false
                            viewModel.clearConnectedDeviceAccount()
                        }
                    ) {
                        Text("清账号", color = Danger)
                    }
                    TextButton(
                        onClick = {
                            confirmClearAccount.value = false
                            viewModel.factoryResetConnectedDevice(DeviceFactoryResetTarget.Headset)
                        }
                    ) {
                        Text("恢复耳机", color = Danger)
                    }
                    TextButton(
                        onClick = {
                            confirmClearAccount.value = false
                            viewModel.factoryResetConnectedDevice(DeviceFactoryResetTarget.Glasses)
                        }
                    ) {
                        Text("恢复眼镜", color = Danger)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAccount.value = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun SensorCapabilityCard(device: DeviceStatus) {
    CapabilitySummaryCard(
        title = device.name,
        type = device.type,
        rows = listOf("环境状态" to "正常", "姿态监测" to "稳定", "电量" to device.batteryText())
    )
}

@Composable
private fun GlassesCapabilityCard(device: DeviceStatus) {
    CapabilitySummaryCard(
        title = device.name,
        type = device.type,
        rows = listOf("第一视角" to "低延迟预览", "AR 提示" to "待命", "电量" to device.batteryText())
    )
}

@Composable
private fun GlassesActions(device: DeviceStatus, photoBusy: Boolean, viewModel: PatrolViewModel) {
    val enabled = device.canUseSdkControls()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { ActionTile(if (photoBusy) "抓拍中" else "抓拍", "camera", enabled = enabled && !photoBusy, onClick = viewModel::takePhoto) }
            Box(Modifier.weight(1f)) {
                ActionTile(
                    if (device.isRecording) "停止录像" else "执法录像",
                    if (device.isRecording) "stop" else "video",
                    active = device.isRecording,
                    danger = device.isRecording,
                    enabled = enabled,
                    onClick = viewModel::toggleRecord
                )
            }
            Box(Modifier.weight(1f)) { ActionTile("设备自检", "info", enabled = enabled, onClick = viewModel::runDeviceSelfCheck) }
        }
    }
}

@Composable
private fun DeviceEventsPanel(events: List<DeviceEvent>) {
    val colors = PatrolDisplay.colors
    val selectedEvent = remember { mutableStateOf<DeviceEvent?>(null) }
    val rows = events.map { event ->
        PrototypeEventRow(
            title = event.title,
            time = event.timestamp.toEventTimeText(),
            icon = if (event.level == DeviceEventLevel.Info) Icons.Filled.CheckCircle else Icons.Filled.Info,
            color = event.level.accent(),
            event = event
        )
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(8.dp))
            .padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            "最近事件",
            color = colors.text,
            fontSize = 17.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 14.dp)
        )
        if (rows.isEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = colors.textSubtle, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Text("暂无真实设备事件", color = colors.textMuted, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((rows.size.coerceAtMost(4) * 41).dp)
            ) {
                items(rows.size) { index ->
                    val row = rows[index]
                    DeviceEventListRow(row, onClick = { selectedEvent.value = row.event })
                    if (index != rows.lastIndex) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border.copy(alpha = 0.55f)))
                    }
                }
            }
        }
    }
    selectedEvent.value?.let { event ->
        DeviceEventDetailDialog(
            event = event,
            onDismiss = { selectedEvent.value = null }
        )
    }
}

private data class PrototypeEventRow(
    val title: String,
    val time: String,
    val icon: ImageVector,
    val color: Color,
    val event: DeviceEvent
)

@Composable
private fun DeviceEventListRow(row: PrototypeEventRow, onClick: () -> Unit) {
    val colors = PatrolDisplay.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(row.icon, contentDescription = null, tint = row.color, modifier = Modifier.size(23.dp))
        Spacer(Modifier.width(12.dp))
        Text(row.title, color = colors.textMuted, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(row.time, color = colors.textSubtle, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colors.textSubtle, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun DeviceEventDetailDialog(event: DeviceEvent, onDismiss: () -> Unit) {
    val colors = PatrolDisplay.colors
    val accent = event.level.accent()
    AlertDialog(
        containerColor = colors.surface,
        tonalElevation = 0.dp,
        onDismissRequest = onDismiss,
        title = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(accent.copy(alpha = 0.08f))
                    .border(1.dp, accent.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(if (event.level == DeviceEventLevel.Info) Icons.Filled.CheckCircle else Icons.Filled.Info, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(event.title, color = colors.text, fontSize = 18.sp, lineHeight = 23.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(event.timestamp.toEventFullTimeText(), color = colors.textMuted, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DeviceEventDetailField("级别", event.level.labelText(), accent)
                DeviceEventDetailField("详情", event.detail.ifBlank { "无更多详情" }, colors.textMuted)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", color = colors.textMuted, fontWeight = FontWeight.Black)
            }
        }
    )
}

@Composable
private fun DeviceEventDetailField(label: String, value: String, valueColor: Color) {
    val colors = PatrolDisplay.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.control.copy(alpha = 0.55f))
            .border(1.dp, colors.border.copy(alpha = 0.72f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, color = colors.textSubtle, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Black)
        Text(value, color = valueColor, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CapabilitySummaryCard(title: String, type: DeviceType, rows: List<Pair<String, String>>) {
    val colors = PatrolDisplay.colors
    val accent = type.accent()
    PatrolCard(radius = 16, padding = PaddingValues(horizontal = 14.dp, vertical = 14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
                    DeviceTypeIcon(type = type, tint = accent, modifier = Modifier.size(32.dp), fontSize = 25.sp)
                }
                Column(Modifier.weight(1f)) {
                    Text(title, color = colors.text, style = PatrolTextStyle.CardTitle.copy(fontSize = 17.sp, lineHeight = 22.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(type.label, color = colors.textMuted, style = PatrolTextStyle.BodySmall.copy(fontWeight = FontWeight.Bold), maxLines = 1)
                }
                StatusTag(if (title.isNotBlank()) "在线" else "未连接", if (title.isNotBlank()) Success else Warning, filled = true)
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                rows.forEachIndexed { index, (label, value) ->
                    DeviceInfoRow(label = label, value = value, accent = deviceInfoAccent(index, label))
                }
            }
        }
    }
}

@Composable
private fun DeviceInfoRow(label: String, value: String, accent: Color) {
    val colors = PatrolDisplay.colors
    val valueColor = deviceInfoValueColor(label, value, accent)
    Row(
        Modifier.fillMaxWidth().height(29.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)).background(accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(deviceInfoIcon(label), contentDescription = null, tint = accent, modifier = Modifier.size(15.dp))
            }
            Text(
                label,
                color = colors.textMuted,
                style = PatrolTextStyle.BodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Black),
                maxLines = 1
            )
        }
        Text(
            value,
            color = valueColor,
            style = PatrolTextStyle.BodyStrong.copy(
                fontSize = if (label.contains("存储")) 13.sp else 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Black
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun deviceInfoIcon(label: String): ImageVector = when {
    label.contains("摄像") -> Icons.Filled.Videocam
    label.contains("语音") -> Icons.Filled.Mic
    label.contains("时长") -> Icons.Filled.AccessTime
    label.contains("电量") -> Icons.Filled.BatteryFull
    label.contains("存储") -> Icons.Filled.Storage
    label.contains("环境") || label.contains("姿态") -> Icons.Filled.Sensors
    label.contains("视角") || label.contains("AR") -> Icons.Filled.CameraAlt
    else -> Icons.Filled.Info
}

private fun cameraStatus(device: DeviceStatus, capabilities: DeviceCapabilities): String =
    when {
        device.type == DeviceType.Sensor -> "不适用"
        device.type == DeviceType.Headset && !capabilities.supportsPhoto && !capabilities.supportsVideo -> "等待控制通道"
        device.isRecording -> "录像中"
        else -> "待机"
    }

private fun audioStatus(device: DeviceStatus, capabilities: DeviceCapabilities, recording: Boolean): String =
    when {
        device.type == DeviceType.Headset && !capabilities.supportsAudioRecord -> "等待控制通道"
        recording || device.isTalking -> "录制中"
        device.type == DeviceType.Sensor -> "不适用"
        else -> "待机"
    }

private fun storageStatus(device: DeviceStatus): String =
    when {
        !device.storageKnown -> "读取失败"
        device.storageProgress() >= 0.92f -> "空间不足"
        device.storageProgress() >= 0.78f -> "占用较高"
        else -> "正常"
    }

private fun deviceInfoAccent(index: Int, label: String): Color = when {
    label.contains("电量") -> Success
    label.contains("存储") -> Warning
    label.contains("语音") -> TechBlue
    label.contains("摄像") || label.contains("视角") || label.contains("AR") -> Color(0xFF2563EB)
    label.contains("环境") || label.contains("姿态") -> Success
    index % 2 == 0 -> TechBlue
    else -> Color(0xFF14B8A6)
}

@Composable
private fun deviceInfoValueColor(label: String, value: String, accent: Color): Color {
    val colors = PatrolDisplay.colors
    return when {
        value.contains("待机") -> if (colors.dark) colors.textMuted else Color(0xFF334155)
        value.contains("录像") || value.contains("占用") -> Danger
        label.contains("电量") -> Success
        label.contains("存储") -> Warning
        label.contains("时长") -> if (colors.dark) Color(0xFF14B8A6) else Color(0xFF0F766E)
        else -> accent
    }
}

@Composable
private fun CapabilityTile(label: String, value: String, accent: Color) {
    val colors = PatrolDisplay.colors
    PatrolCard(modifier = Modifier.height(128.dp), padding = PaddingValues(12.dp)) {
        Column(Modifier.fillMaxWidth().height(104.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(99.dp)).background(accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Box(Modifier.size(12.dp).clip(RoundedCornerShape(99.dp)).background(accent))
            }
            Spacer(Modifier.height(12.dp))
            Text(label, color = colors.textMuted, style = PatrolTextStyle.BodySmall.copy(fontWeight = FontWeight.Black), maxLines = 1)
            Text(value, color = colors.text, style = PatrolTextStyle.Caption, maxLines = 1)
        }
    }
}

@Composable
fun AddDeviceScreen(
    uiState: AppUiState,
    viewModel: PatrolViewModel,
    bluetoothEnabled: Boolean,
    onToggleBluetooth: () -> Unit,
    onBack: () -> Unit
) {
    val colors = PatrolDisplay.colors
    val palette = addDevicePalette(colors.dark)
    val devices = remember(uiState.scannedDevices) { uiState.scannedDevices.distinctByDeviceIdentity() }
    val connectedDevices = (uiState.connectedDevices + uiState.device)
        .filter { it.isControllableDevice() }
        .distinctBy { it.id }
    LaunchedEffect(Unit) {
        viewModel.refreshScannedDevices(showFailureMessage = true)
    }
    SystemBars(
        statusBarColor = palette.topBar,
        navigationBarColor = colors.bottomBar,
        lightStatusBar = !palette.darkTopBar,
        lightNavigationBar = !colors.dark
    )
    Column(Modifier.fillMaxSize().background(palette.page)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(palette.page),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                ScanHeader(
                    palette = palette,
                    devices = devices,
                    connectedDevices = connectedDevices,
                    bluetoothEnabled = bluetoothEnabled,
                    onToggleBluetooth = onToggleBluetooth
                )
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("可用设备", color = palette.title, style = PatrolTextStyle.CardTitle.copy(fontSize = 16.sp, lineHeight = 21.sp))
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(palette.statusPill)
                            .padding(horizontal = 11.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(9.dp).clip(RoundedCornerShape(99.dp)).background(if (bluetoothEnabled) Success else Warning))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (bluetoothEnabled) "搜索中" else "蓝牙关闭",
                            color = palette.muted,
                            style = PatrolTextStyle.BodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Black)
                        )
                    }
                }
            }
            devices.forEach { device ->
                item {
                    val connected = device.isConnected(connectedDevices)
                    DiscoveredDeviceCard(
                        device = device,
                        palette = palette,
                        connected = connected,
                        onUnbind = {
                            viewModel.unbindDiscoveredDevice(
                                scannedId = device.id,
                                macAddress = device.macAddress,
                                scannedName = device.name,
                                scannedType = device.type
                            )
                        },
                        onConnect = {
                            viewModel.connectDiscoveredDevice(
                                id = device.id,
                                name = device.name,
                                mac = device.macAddress,
                                signalBars = device.signalBars.coerceAtLeast(1),
                                type = device.type
                            )
                            onBack()
                        }
                    )
                }
            }
            item { ScanTipsCard(palette) }
        }
    }
}

@Composable
private fun ScanHeader(
    palette: AddDevicePalette,
    devices: List<ScannedDevice>,
    connectedDevices: List<DeviceStatus>,
    bluetoothEnabled: Boolean,
    onToggleBluetooth: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "device-scan")
    val pulse = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar-pulse"
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(430.dp)
            .background(
                Brush.verticalGradient(
                    palette.scanGradient
                )
            )
    ) {
        Box(Modifier.size(12.dp).align(Alignment.TopStart).padding(start = 60.dp, top = 66.dp).background(Color.Transparent))
        Box(Modifier.size(10.dp).align(Alignment.TopStart).padding(start = 66.dp, top = 72.dp).clip(RoundedCornerShape(99.dp)).background(Color(0xFF60A5FA).copy(alpha = 0.55f)))
        Box(Modifier.size(9.dp).align(Alignment.CenterEnd).padding(end = 86.dp, top = 74.dp).clip(RoundedCornerShape(99.dp)).background(Color(0xFF93C5FD).copy(alpha = 0.62f)))
        Box(Modifier.align(Alignment.Center).padding(bottom = 28.dp), contentAlignment = Alignment.Center) {
            repeat(3) { index ->
                val progress = (pulse.value + index * 0.33f) % 1f
                Box(
                    Modifier
                        .size(210.dp)
                        .graphicsLayer {
                            scaleX = 0.46f + progress * 1.08f
                            scaleY = 0.46f + progress * 1.08f
                            alpha = 0.52f * (1f - progress)
                        }
                        .clip(RoundedCornerShape(999.dp))
                        .border(1.dp, Color(0xFF3B82F6), RoundedCornerShape(999.dp))
                )
            }
            Column(
                Modifier
                    .size(86.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (bluetoothEnabled) Color(0xFF3B82F6) else palette.iconMuted)
                    .clickable(onClick = onToggleBluetooth),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Filled.Bluetooth, contentDescription = "蓝牙开关", tint = Color.White, modifier = Modifier.size(36.dp))
                Text(
                    if (bluetoothEnabled) "已开启" else "点击开启",
                    color = Color.White,
                    style = PatrolTextStyle.Caption.copy(fontSize = 9.sp, lineHeight = 12.sp),
                    maxLines = 1
                )
            }
        }
        BoxWithConstraints(Modifier.align(Alignment.Center).fillMaxWidth().height(300.dp).padding(horizontal = 18.dp)) {
            val chipSize = 104.dp
            val placements = radarChipPlacements(devices)
            devices.take(4).forEachIndexed { index, device ->
                val placement = placements[device.id] ?: RadarChipPlacement(0.5f, 0.5f)
                HeaderDeviceChip(
                    device = device,
                    palette = palette,
                    connected = device.isConnected(connectedDevices),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(
                            x = (maxWidth - chipSize) * placement.x,
                            y = (300.dp - chipSize) * placement.y
                        )
                )
            }
        }
    }
}

@Composable
private fun HeaderDeviceChip(
    device: ScannedDevice,
    palette: AddDevicePalette,
    connected: Boolean,
    modifier: Modifier = Modifier
) {
    val ringColor = when {
        connected -> Success
        device.bonded -> TechBlue
        else -> palette.headerChipBorder
    }
    Box(
        modifier
            .size(if (connected) 104.dp else 96.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(ringColor.copy(alpha = if (connected) 0.18f else 0.12f))
            .border(1.dp, ringColor.copy(alpha = 0.46f), RoundedCornerShape(999.dp))
            .padding(7.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(999.dp))
                .background(if (connected) palette.headerChipActive else palette.headerChip)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            DeviceTypeIcon(
                type = device.type,
                tint = if (device.bonded || connected) Color(0xFF60A5FA) else palette.iconMuted,
                modifier = Modifier.size(34.dp),
                fontSize = 25.sp
            )
            Spacer(Modifier.height(1.dp))
            Text(
                device.name.removePrefix("ForceLink-"),
                color = palette.headerChipText,
                style = PatrolTextStyle.Caption.copy(fontSize = 10.sp, lineHeight = 15.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (connected) "已连" else device.type.shortLabel,
                color = if (connected) Success else palette.headerChipSubtext,
                style = PatrolTextStyle.Caption.copy(fontSize = 9.sp, lineHeight = 15.sp),
                maxLines = 1
            )
        }
    }
}

private data class RadarChipPlacement(val x: Float, val y: Float)

private fun radarChipPlacements(devices: List<ScannedDevice>): Map<String, RadarChipPlacement> {
    val candidates = listOf(
        RadarChipPlacement(0.05f, 0.18f),
        RadarChipPlacement(0.78f, 0.12f),
        RadarChipPlacement(0.10f, 0.72f),
        RadarChipPlacement(0.84f, 0.72f)
    )
    return devices.take(4).mapIndexed { index, device ->
        device.id to candidates[index % candidates.size]
    }.toMap()
}

private fun List<ScannedDevice>.distinctByDeviceIdentity(): List<ScannedDevice> =
    groupBy { device -> device.identityKey() }
        .values
        .map { group ->
            group.sortedWith(
                compareByDescending<ScannedDevice> { it.type == DeviceType.Headset }
                    .thenByDescending { it.serviceUuid == "system-bluetooth-audio-control-connected" }
                    .thenByDescending { it.serviceUuid == "ute-ble-control-scanned" }
                    .thenByDescending { it.serviceUuid.startsWith("system-bluetooth-audio") }
                    .thenByDescending { it.signalBars }
            ).first()
        }

private fun ScannedDevice.identityKey(): String =
    when {
        isKnownDualModeAudioDevice() -> "patrol-dual-mode-audio"
        macAddress.isNotBlank() -> macAddress.uppercase()
        id.isNotBlank() -> id
        else -> name
    }

@Composable
private fun DiscoveredDeviceCard(
    device: ScannedDevice,
    palette: AddDevicePalette,
    connected: Boolean,
    onUnbind: () -> Unit,
    onConnect: () -> Unit
) {
    val confirmUnbind = remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 7.dp)
            .height(92.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(palette.card)
            .border(1.dp, palette.border, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (device.bonded) palette.iconActiveBg else palette.iconBg),
            contentAlignment = Alignment.Center
        ) {
            DeviceTypeIcon(
                type = device.type,
                tint = if (device.bonded) TechBlue else palette.iconMuted,
                modifier = Modifier.size(38.dp),
                fontSize = 28.sp
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                device.name,
                color = palette.title,
                style = PatrolTextStyle.CardTitle.copy(fontSize = 15.sp, lineHeight = 19.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${device.type.label}  ·  MAC: ${device.macAddress.take(8)}...",
                color = palette.muted,
                style = PatrolTextStyle.BodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold),
                maxLines = 1
            )
        }
        SignalBars(device.signalBars, active = device.bonded, palette = palette)
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .width(72.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(
                    if (connected) Danger
                    else if (device.bonded) Color(0xFF2F7DF6)
                    else Color(0xFF4D8DF6)
                )
                .clickable(onClick = if (connected) { { confirmUnbind.value = true } } else onConnect),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (connected) "解绑" else "连接",
                color = Color.White,
                style = PatrolTextStyle.BodyStrong.copy(fontSize = 12.sp, lineHeight = 16.sp),
                maxLines = 1
            )
        }
    }
    if (confirmUnbind.value) {
        AlertDialog(
            onDismissRequest = { confirmUnbind.value = false },
            title = { Text("解除设备绑定") },
            text = { Text("将清除设备账号并从 PatrolLink 移除当前绑定。完成后需要重新搜索并配对设备。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmUnbind.value = false
                        onUnbind()
                    }
                ) {
                    Text("解除绑定", color = Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmUnbind.value = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun SignalBars(count: Int, active: Boolean, palette: AddDevicePalette) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        repeat(3) { index ->
            Box(
                Modifier
                    .width(4.dp)
                    .height((8 + index * 7).dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(if (index < count) (if (active) Color(0xFF60A5FA) else palette.signalInactive) else palette.signalTrack)
            )
        }
    }
}

@Composable
private fun ScanTipsCard(palette: AddDevicePalette) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 26.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(palette.tipBg)
            .border(1.dp, palette.border, RoundedCornerShape(14.dp))
            .padding(18.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(TechBlue),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("扫描说明", color = palette.title, style = PatrolTextStyle.CardTitle.copy(fontSize = 15.sp, lineHeight = 20.sp))
            Text(
                "请确保手机蓝牙已开启，且待连接设备处于配对模式并靠近手机（3米以内）。如未发现设备，请尝试重启设备蓝牙或刷新页面。",
                color = palette.body,
                style = PatrolTextStyle.BodySmall.copy(fontSize = 11.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold)
            )
        }
    }
}

private fun ScannedDevice.isConnected(connectedDevices: List<DeviceStatus>): Boolean {
    val scannedId = id.normalizedDeviceKey()
    val scannedMac = macAddress.normalizedDeviceKey()
    return connectedDevices.any { device ->
        val deviceId = device.id.normalizedDeviceKey()
        device.id.equals(id, ignoreCase = true) ||
            macAddress.isNotBlank() && device.id.equals(macAddress, ignoreCase = true) ||
            deviceId.isNotBlank() && (
                deviceId == scannedId ||
                    deviceId == scannedMac ||
                    scannedId.contains(deviceId) ||
                    scannedMac.contains(deviceId)
                ) ||
            isKnownDualModeAudioDevice() && device.type == DeviceType.Headset && hasSimilarAudioName(device.name, name)
    }
}

private fun String.normalizedDeviceKey(): String =
    uppercase().filter { it.isLetterOrDigit() }

private fun hasSimilarAudioName(left: String, right: String): Boolean {
    if (left.isBlank() || right.isBlank()) return false
    val leftNormalized = left.uppercase()
    val rightNormalized = right.uppercase()
    return listOf("E1-PRO", "FORCELINK", "HEADSET", "耳机").any { marker ->
        marker in leftNormalized && marker in rightNormalized
    } || leftNormalized == rightNormalized
}

private fun ScannedDevice.isKnownDualModeAudioDevice(): Boolean {
    val normalized = name.uppercase()
    return serviceUuid.startsWith("system-bluetooth-audio") ||
        "E1-PRO" in normalized
}

private fun DeviceStatus.isControllableDevice(): Boolean =
    id.isNotBlank() && online

private fun DeviceStatus.canUseSdkControls(): Boolean =
    isControllableDevice() && !onlineDuration.startsWith("系统蓝牙")

private fun DeviceStatus.batteryText(): String =
    if (batteryKnown) "${battery.coerceIn(0, 100)}%" else "读取失败"

private fun DeviceStatus.batteryProgress(): Float =
    if (batteryKnown) battery.coerceIn(0, 100) / 100f else 0f

private fun DeviceStatus.storageText(): String =
    if (storageKnown) {
        "${storageUsedGb.formatGb()}GB / ${storageTotalGb.formatGb()}GB"
    } else {
        "读取失败"
    }

private fun DeviceStatus.storageTextCompact(): String =
    if (storageKnown) {
        "${storageUsedGb.formatGb()}/${storageTotalGb.formatGb()}GB"
    } else {
        "读取失败"
    }

private fun DeviceStatus.storageProgress(): Float =
    if (storageKnown && storageTotalGb > 0f) {
        (storageUsedGb / storageTotalGb).coerceIn(0f, 1f)
    } else {
        0f
    }

private fun Float.formatGb(): String =
    if (this >= 10f || this % 1f == 0f) {
        toInt().toString()
    } else {
        String.format(java.util.Locale.US, "%.1f", this)
    }

@Composable
private fun DeviceTypeIcon(
    type: DeviceType,
    tint: Color,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 22.sp
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(type.emojiIcon, fontSize = fontSize, lineHeight = (fontSize.value * 1.25f).sp, maxLines = 1)
    }
}

private val DeviceType.label: String
    get() = when (this) {
        DeviceType.Headset -> "摄录耳机"
        DeviceType.Recorder -> "智能眼镜"
        DeviceType.Sensor -> "传感器"
        DeviceType.Glasses -> "智能眼镜"
    }

private val DeviceType.shortLabel: String
    get() = when (this) {
        DeviceType.Headset -> "摄录"
        DeviceType.Recorder -> "眼镜"
        DeviceType.Sensor -> "传感"
        DeviceType.Glasses -> "眼镜"
    }

private fun DeviceType.icon() = when (this) {
    DeviceType.Headset -> Icons.Filled.Videocam
    DeviceType.Recorder -> Icons.Filled.Videocam
    DeviceType.Sensor -> Icons.Filled.Sensors
    DeviceType.Glasses -> Icons.Filled.Videocam
}

private val DeviceType.emojiIcon: String
    get() = when (this) {
        DeviceType.Headset -> "🎧"
        DeviceType.Recorder -> "👓"
        DeviceType.Sensor -> "📟"
        DeviceType.Glasses -> "👓"
    }

private fun DeviceType.accent() = when (this) {
    DeviceType.Headset -> TechBlue
    DeviceType.Recorder -> Color(0xFF14B8A6)
    DeviceType.Sensor -> Success
    DeviceType.Glasses -> Color(0xFF14B8A6)
}

private fun DeviceEventLevel.accent() = when (this) {
    DeviceEventLevel.Info -> TechBlue
    DeviceEventLevel.Warning -> Warning
    DeviceEventLevel.Error -> Danger
}

private fun DeviceEventLevel.labelText() = when (this) {
    DeviceEventLevel.Info -> "正常"
    DeviceEventLevel.Warning -> "提醒"
    DeviceEventLevel.Error -> "异常"
}

private data class AddDevicePalette(
    val darkTopBar: Boolean,
    val topBar: Color,
    val topBarContent: Color,
    val page: Color,
    val scanGradient: List<Color>,
    val title: Color,
    val body: Color,
    val muted: Color,
    val statusPill: Color,
    val card: Color,
    val border: Color,
    val iconBg: Color,
    val iconActiveBg: Color,
    val iconMuted: Color,
    val disabledButton: Color,
    val signalInactive: Color,
    val signalTrack: Color,
    val tipBg: Color,
    val scanText: Color,
    val headerChip: Color,
    val headerChipActive: Color,
    val headerChipBorder: Color,
    val headerChipText: Color,
    val headerChipSubtext: Color
)

private fun addDevicePalette(dark: Boolean): AddDevicePalette = if (dark) {
    AddDevicePalette(
        darkTopBar = true,
        topBar = Navy,
        topBarContent = Color.White,
        page = Color(0xFF071120),
        scanGradient = listOf(Color(0xFF071120), Color(0xFF0F2D6F), Color(0xFF1A2F52)),
        title = Color(0xFFEAF0FF),
        body = Color(0xFFB4C0D6),
        muted = Color(0xFF8FA1BD),
        statusPill = Color(0xFF17243A),
        card = Color(0xFF101C2F),
        border = Color(0xFF2A3A55),
        iconBg = Color(0xFF17243A),
        iconActiveBg = Color(0xFF123B73),
        iconMuted = Color(0xFF8FA1BD),
        disabledButton = Color(0xFF26334D),
        signalInactive = Color(0xFF6B7D99),
        signalTrack = Color(0xFF2A3A55),
        tipBg = Color(0xFF0D1A2D),
        scanText = Color.White,
        headerChip = Color(0xFF0D1A2D).copy(alpha = 0.84f),
        headerChipActive = Color(0xFF0F2D57).copy(alpha = 0.92f),
        headerChipBorder = Color(0xFF2A4B76).copy(alpha = 0.88f),
        headerChipText = Color(0xFFEAF0FF),
        headerChipSubtext = Color(0xFF8FA1BD)
    )
} else {
    AddDevicePalette(
        darkTopBar = false,
        topBar = Color(0xFFF5F7FB),
        topBarContent = Color(0xFF0F172A),
        page = Color.White,
        scanGradient = listOf(Color(0xFFF5F7FB), Color(0xFFB8CBEE), Color(0xFFF7FAFF)),
        title = Color(0xFF0F172A),
        body = Color(0xFF64748B),
        muted = Color(0xFF94A3B8),
        statusPill = Color(0xFFF1F5F9),
        card = Color.White,
        border = Color(0xFFE2E8F0),
        iconBg = Color(0xFFF8FAFC),
        iconActiveBg = Color(0xFFEFF6FF),
        iconMuted = Color(0xFF94A3B8),
        disabledButton = Color(0xFFE2E8F0),
        signalInactive = Color(0xFFCBD5E1),
        signalTrack = Color(0xFFE2E8F0),
        tipBg = Color(0xFFF8FBFF),
        scanText = Color(0xFF0F172A),
        headerChip = Color.White.copy(alpha = 0.78f),
        headerChipActive = Color(0xFFEFF6FF).copy(alpha = 0.92f),
        headerChipBorder = Color(0xFFD8E2F0),
        headerChipText = Color(0xFF0F172A),
        headerChipSubtext = Color(0xFF64748B)
    )
}

private val eventTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val eventFullTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm:ss")

private fun Long.toEventTimeText(): String =
    runCatching {
        Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(eventTimeFormatter)
    }.getOrDefault("--:--")

private fun Long.toEventFullTimeText(): String =
    runCatching {
        Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(eventFullTimeFormatter)
    }.getOrDefault("时间未知")

private fun streamHint(state: StreamRelayState): String = when (state) {
    StreamRelayState.Idle -> "点击播放接入实时画面"
    StreamRelayState.Connecting -> "正在连接安全视频通道"
    StreamRelayState.Relaying -> "实时画面传输中"
    StreamRelayState.Failed -> "连接失败，可重新播放"
}

private fun streamTag(state: StreamRelayState, recording: Boolean): String = when (state) {
    StreamRelayState.Idle -> if (recording) "录像中" else "待接入"
    StreamRelayState.Connecting -> "连接中"
    StreamRelayState.Relaying -> "低延迟直播中"
    StreamRelayState.Failed -> "连接失败"
}
