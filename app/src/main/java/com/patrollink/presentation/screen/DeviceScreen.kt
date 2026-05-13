package com.patrollink.presentation.screen

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrollink.domain.AppUiState
import com.patrollink.domain.DeviceStatus
import com.patrollink.domain.DeviceType
import com.patrollink.domain.ScannedDevice
import com.patrollink.domain.StreamRelayState
import com.patrollink.presentation.PatrolViewModel
import com.patrollink.presentation.component.ActionTile
import com.patrollink.presentation.component.DeviceStatPill
import com.patrollink.presentation.component.ForceTopBar
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

@Composable
fun DeviceScreen(uiState: AppUiState, viewModel: PatrolViewModel, onSos: () -> Unit, onAddDevice: () -> Unit) {
    val colors = PatrolDisplay.colors
    SystemBars(statusBarColor = colors.topBar, navigationBarColor = colors.bottomBar, lightStatusBar = !colors.dark, lightNavigationBar = !colors.dark)
    val device = uiState.device
    val connectedDevices = uiState.connectedDevices.ifEmpty { listOf(device) }
    Column(Modifier.fillMaxSize().background(colors.page)) {
        ForceTopBar(
            title = null,
            dark = colors.dark,
            onSos = onSos
        )
        OfflineBanner(uiState.networkOnline)
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                ConnectedDevicesPanel(
                    devices = connectedDevices,
                    selectedId = device.id,
                    onSelect = viewModel::selectConnectedDevice,
                    onAddDevice = onAddDevice
                )
            }
            when (device.type) {
                DeviceType.Recorder -> {
                    item { RecorderLiveFeed(uiState, viewModel, device) }
                    item { RecorderActions(device, viewModel) }
                    item { MetricTile("在线时长", device.onlineDuration, TechBlue, 0.65f) }
                    item {
                        MetricTile(
                            "存储空间",
                            "${device.storageUsedGb}GB / ${device.storageTotalGb.toInt()}GB",
                            Warning,
                            device.storageUsedGb / device.storageTotalGb
                        )
                    }
                }
                DeviceType.Headset -> {
                    item { RecorderLiveFeed(uiState, viewModel, device) }
                    item { HeadsetCapabilityCard(device) }
                    item { HeadsetActions(device, viewModel) }
                }
                DeviceType.Sensor -> {
                    item { SensorCapabilityCard(device) }
                    item { MetricTile("在线时长", device.onlineDuration, TechBlue, 0.65f) }
                    item { MetricTile("状态稳定度", "96%", Success, 0.96f) }
                }
                DeviceType.Glasses -> {
                    item { RecorderLiveFeed(uiState, viewModel, device) }
                    item { GlassesCapabilityCard(device) }
                    item { GlassesActions(device, viewModel) }
                    item { MetricTile("在线时长", device.onlineDuration, TechBlue, 0.65f) }
                    item { MetricTile("眼镜电量", "${device.battery}%", Success, device.battery / 100f) }
                }
            }
        }
    }
}

@Composable
private fun ConnectedDevicesPanel(devices: List<DeviceStatus>, selectedId: String, onSelect: (String) -> Unit, onAddDevice: () -> Unit) {
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
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black)
            .border(4.dp, Color(0xFF0F172A), RoundedCornerShape(18.dp))
            .clickable {
                if (uiState.streamState == StreamRelayState.Relaying) viewModel.stopStream() else viewModel.startLowLatencyStream()
            }
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(Color(0xFF111827), Color(0xFF1E3A8A), Color(0xFF0F172A))))
        )
        Text(
            when (uiState.streamState) {
                StreamRelayState.Connecting -> "CONNECTING"
                StreamRelayState.Relaying -> "LIVE FEED"
                StreamRelayState.Failed -> "FAILED"
                StreamRelayState.Idle -> "LIVE FEED"
            },
            color = Color.White.copy(alpha = 0.16f),
            fontSize = 44.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.align(Alignment.Center)
        )
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = if (uiState.streamState == StreamRelayState.Relaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(44.dp)
            )
            Text(streamHint(uiState.streamState), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
        }
        Row(Modifier.align(Alignment.TopStart).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            StatusTag(
                streamTag(uiState.streamState, device.isRecording),
                if (uiState.streamState == StreamRelayState.Failed) Danger else if (device.isRecording) Danger else Success,
                filled = true
            )
        }
        Text(
            "Recorder Feed · Encrypted",
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(14.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun RecorderActions(device: DeviceStatus, viewModel: PatrolViewModel) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) { ActionTile("拍照", "camera", onClick = viewModel::takePhoto) }
        Box(Modifier.weight(1f)) {
            ActionTile(
                if (device.isRecording) "停止录像" else "录制视频",
                if (device.isRecording) "stop" else "video",
                active = device.isRecording,
                danger = device.isRecording,
                onClick = viewModel::toggleRecord
            )
        }
        Box(Modifier.weight(1f)) { CapabilityTile("云端同步", "已启用", TechBlue) }
    }
}

@Composable
private fun HeadsetCapabilityCard(device: DeviceStatus) {
    CapabilitySummaryCard(
        title = device.name,
        type = device.type,
        rows = listOf(
            "摄像头" to if (device.isRecording) "录像中" else "待机",
            "语音对讲" to if (device.isTalking) "通道占用中" else "待机",
            "在线时长" to device.onlineDuration,
            "电量" to "${device.battery}%",
            "本机存储" to "${device.storageUsedGb}GB / ${device.storageTotalGb.toInt()}GB"
        )
    )
}

@Composable
private fun HeadsetActions(device: DeviceStatus, viewModel: PatrolViewModel) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) {
            ActionTile(
                if (device.isTalking) "对讲中" else "语音对讲",
                "talk",
                active = device.isTalking,
                onClick = viewModel::toggleTalk
            )
        }
        Box(Modifier.weight(1f)) { ActionTile("拍照", "camera", onClick = viewModel::takePhoto) }
        Box(Modifier.weight(1f)) {
            ActionTile(
                if (device.isRecording) "停止录像" else "执法录像",
                if (device.isRecording) "stop" else "video",
                active = device.isRecording,
                danger = device.isRecording,
                onClick = viewModel::toggleRecord
            )
        }
    }
}

@Composable
private fun SensorCapabilityCard(device: DeviceStatus) {
    CapabilitySummaryCard(
        title = device.name,
        type = device.type,
        rows = listOf("环境状态" to "正常", "姿态监测" to "稳定", "电量" to "${device.battery}%")
    )
}

@Composable
private fun GlassesCapabilityCard(device: DeviceStatus) {
    CapabilitySummaryCard(
        title = device.name,
        type = device.type,
        rows = listOf("第一视角" to "低延迟预览", "AR 提示" to "待命", "电量" to "${device.battery}%")
    )
}

@Composable
private fun GlassesActions(device: DeviceStatus, viewModel: PatrolViewModel) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) { ActionTile("抓拍", "camera", onClick = viewModel::takePhoto) }
        Box(Modifier.weight(1f)) {
            ActionTile(
                if (device.isRecording) "停止录像" else "执法录像",
                if (device.isRecording) "stop" else "video",
                active = device.isRecording,
                danger = device.isRecording,
                onClick = viewModel::toggleRecord
            )
        }
        Box(Modifier.weight(1f)) { CapabilityTile("AR 取证", "已就绪", device.type.accent()) }
    }
}

@Composable
private fun CapabilitySummaryCard(title: String, type: DeviceType, rows: List<Pair<String, String>>) {
    val colors = PatrolDisplay.colors
    val accent = type.accent()
    PatrolCard(radius = 16) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
                    DeviceTypeIcon(type = type, tint = accent, modifier = Modifier.size(32.dp), fontSize = 25.sp)
                }
                Column {
                    Text(title, color = colors.text, style = PatrolTextStyle.CardTitle.copy(fontSize = 17.sp, lineHeight = 22.sp))
                    Text(type.label, color = colors.textMuted, style = PatrolTextStyle.BodySmall.copy(fontWeight = FontWeight.Bold))
                }
            }
            rows.forEach { (label, value) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, color = colors.textMuted, style = PatrolTextStyle.BodySmall.copy(fontWeight = FontWeight.Bold))
                    Text(value, color = colors.text, style = PatrolTextStyle.BodyStrong.copy(fontSize = 13.sp, lineHeight = 18.sp))
                }
            }
        }
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
    onBack: () -> Unit,
    onSos: () -> Unit
) {
    val colors = PatrolDisplay.colors
    val palette = addDevicePalette(colors.dark)
    val devices = remember(uiState.scannedDevices) { uiState.scannedDevices.distinctByDeviceIdentity() }
    val connectedKeys = uiState.connectedDevices
        .ifEmpty { listOf(uiState.device) }
        .flatMap { listOf(it.id, it.name) }
        .toSet()
    SystemBars(
        statusBarColor = palette.topBar,
        navigationBarColor = colors.bottomBar,
        lightStatusBar = !palette.darkTopBar,
        lightNavigationBar = !colors.dark
    )
    Column(Modifier.fillMaxSize().background(palette.page)) {
        Box(Modifier.fillMaxWidth().background(palette.topBar).statusBarsPadding()) {
            ForceTopBar(
                title = null,
                dark = palette.darkTopBar,
                onSos = onSos,
                leading = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = palette.topBarContent,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onBack)
                            .padding(4.dp)
                    )
                }
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(palette.page),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                ScanHeader(
                    palette = palette,
                    devices = devices,
                    connectedKeys = connectedKeys,
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
                    val connected = device.isConnected(connectedKeys)
                    DiscoveredDeviceCard(
                        device = device,
                        palette = palette,
                        connected = connected,
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
    connectedKeys: Set<String>,
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
                    connected = device.isConnected(connectedKeys),
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
    distinctBy { device ->
        when {
            device.macAddress.isNotBlank() -> device.macAddress.uppercase()
            device.id.isNotBlank() -> device.id
            else -> device.name
        }
    }

@Composable
private fun DiscoveredDeviceCard(
    device: ScannedDevice,
    palette: AddDevicePalette,
    connected: Boolean,
    onConnect: () -> Unit
) {
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
                    if (connected) palette.disabledButton
                    else if (device.bonded) Color(0xFF2F7DF6)
                    else Color(0xFF4D8DF6)
                )
                .then(if (connected) Modifier else Modifier.clickable(onClick = onConnect)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (connected) "已连接" else "连接",
                color = Color.White,
                style = PatrolTextStyle.BodyStrong.copy(fontSize = 12.sp, lineHeight = 16.sp),
                maxLines = 1
            )
        }
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

private fun ScannedDevice.isConnected(connectedKeys: Set<String>) =
    id in connectedKeys || macAddress in connectedKeys || name in connectedKeys

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
        DeviceType.Recorder -> "记录仪"
        DeviceType.Sensor -> "传感器"
        DeviceType.Glasses -> "智能眼镜"
    }

private val DeviceType.shortLabel: String
    get() = when (this) {
        DeviceType.Headset -> "摄录"
        DeviceType.Recorder -> "记录"
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
        DeviceType.Recorder -> "📹"
        DeviceType.Sensor -> "📟"
        DeviceType.Glasses -> "👓"
    }

private fun DeviceType.accent() = when (this) {
    DeviceType.Headset -> TechBlue
    DeviceType.Recorder -> Color(0xFF8B5CF6)
    DeviceType.Sensor -> Success
    DeviceType.Glasses -> Color(0xFF14B8A6)
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

private fun streamHint(state: StreamRelayState): String = when (state) {
    StreamRelayState.Idle -> "点击查看实时画面"
    StreamRelayState.Connecting -> "正在连接安全视频通道"
    StreamRelayState.Relaying -> "点击关闭实时画面"
    StreamRelayState.Failed -> "连接失败，点击重试"
}

private fun streamTag(state: StreamRelayState, recording: Boolean): String = when (state) {
    StreamRelayState.Idle -> if (recording) "REC 00:12:45" else "LIVE"
    StreamRelayState.Connecting -> "连接中"
    StreamRelayState.Relaying -> "低延迟直播中"
    StreamRelayState.Failed -> "连接失败"
}
