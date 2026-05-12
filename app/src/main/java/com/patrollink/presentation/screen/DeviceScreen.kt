package com.patrollink.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrollink.domain.AppUiState
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
import com.patrollink.presentation.theme.Success
import com.patrollink.presentation.theme.TechBlue
import com.patrollink.presentation.theme.Warning

@Composable
fun DeviceScreen(uiState: AppUiState, viewModel: PatrolViewModel, onSos: () -> Unit) {
    val colors = PatrolDisplay.colors
    SystemBars(statusBarColor = colors.topBar, navigationBarColor = colors.bottomBar, lightStatusBar = !colors.dark, lightNavigationBar = !colors.dark)
    val device = uiState.device
    Column(Modifier.fillMaxSize().background(colors.page)) {
        ForceTopBar(title = null, dark = true, onSos = onSos)
        OfflineBanner(uiState.networkOnline)
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                PatrolCard(radius = 16) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("已连接设备", color = colors.textSubtle, fontSize = 10.sp, fontWeight = FontWeight.Black)
                            Text(device.name, color = colors.text, fontSize = 19.sp, fontWeight = FontWeight.Black)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            DeviceStatPill("良好", TechBlue)
                            DeviceStatPill("${device.battery}%", Success)
                        }
                    }
                }
            }
            item {
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
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF111827), Color(0xFF1E3A8A), Color(0xFF0F172A))
                                )
                            )
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
                        "Live Feed · Encrypted",
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
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        ActionTile("拍照", "camera", onClick = viewModel::takePhoto)
                    }
                    Box(Modifier.weight(1f)) {
                        ActionTile(
                            if (device.isRecording) "停止录像" else "录制视频",
                            if (device.isRecording) "stop" else "video",
                            active = device.isRecording,
                            danger = device.isRecording,
                            onClick = viewModel::toggleRecord
                        )
                    }
                    Box(Modifier.weight(1f)) {
                        ActionTile(
                            if (device.isTalking) "对讲中" else "语音对讲",
                            "talk",
                            active = device.isTalking,
                            onClick = viewModel::toggleTalk
                        )
                    }
                }
            }
            item { MetricTile("在线时长", device.onlineDuration, TechBlue, 0.65f) }
            item {
                MetricTile(
                    "存储空间",
                    "${device.storageUsedGb}GB / ${device.storageTotalGb.toInt()}GB",
                    Warning,
                    device.storageUsedGb / device.storageTotalGb
                )
            }
            item {
                PatrolCard {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("信号强度", color = colors.textSubtle, fontSize = 10.sp, fontWeight = FontWeight.Black)
                        Text("Excellent", color = colors.text, fontSize = 24.sp, fontWeight = FontWeight.Black)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            repeat(5) { index ->
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(99.dp))
                                        .background(if (index < device.signalBars) TechBlue else colors.control)
                                        .padding(vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
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
