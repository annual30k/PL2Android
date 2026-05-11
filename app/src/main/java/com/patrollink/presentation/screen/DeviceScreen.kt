package com.patrollink.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.patrollink.presentation.PatrolViewModel
import com.patrollink.presentation.component.MetricTile
import com.patrollink.presentation.component.OfflineBanner
import com.patrollink.presentation.component.PatrolCard
import com.patrollink.presentation.component.PatrolTopBar
import com.patrollink.presentation.component.PrimaryAction
import com.patrollink.presentation.component.StatusTag
import com.patrollink.presentation.theme.Danger
import com.patrollink.presentation.theme.Muted
import com.patrollink.presentation.theme.Success
import com.patrollink.presentation.theme.TechBlue
import com.patrollink.presentation.theme.Warning

@Composable
fun DeviceScreen(uiState: AppUiState, viewModel: PatrolViewModel, onSos: () -> Unit) {
    val device = uiState.device
    Column(Modifier.fillMaxSize()) {
        PatrolTopBar("设备工作台", onSos)
        OfflineBanner(uiState.networkOnline)
        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                PatrolCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("已连接设备", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Black)
                            Text(device.name, fontSize = 22.sp, fontWeight = FontWeight.Black)
                            Text("固件 ${device.firmware} · 信号 ${device.signalBars} 格", color = Muted, fontSize = 12.sp)
                        }
                        StatusTag(if (device.online) "在线" else "离线", if (device.online) Success else Danger)
                    }
                }
            }
            item {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(16 / 9f).clip(RoundedCornerShape(16.dp)).background(
                        Brush.linearGradient(listOf(Color(0xFF15233E), Color(0xFF040912)))
                    ).clickable { }
                ) {
                    Text("LIVE FEED", color = Color.White.copy(alpha = 0.18f), fontSize = 44.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.Center))
                    Row(Modifier.align(Alignment.TopStart).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusTag(if (device.isRecording) "REC 00:12:45" else "LIVE", if (device.isRecording) Danger else Success)
                        StatusTag("Encrypted", TechBlue)
                    }
                    Text("点击查看实时画面", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp))
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryAction("拍照", onClick = viewModel::takePhoto, modifier = Modifier.weight(1f))
                    PrimaryAction(if (device.isRecording) "停止录像" else "录像", onClick = viewModel::toggleRecord, modifier = Modifier.weight(1f), danger = device.isRecording)
                    PrimaryAction(if (device.isTalking) "对讲中" else "对讲", onClick = viewModel::toggleTalk, modifier = Modifier.weight(1f))
                }
            }
            item {
                MetricTile("在线时长", device.onlineDuration, TechBlue, 0.65f)
            }
            item {
                MetricTile("存储空间", "${device.storageUsedGb}GB / ${device.storageTotalGb.toInt()}GB", Warning, device.storageUsedGb / device.storageTotalGb)
            }
            item {
                MetricTile("电量", "${device.battery}%", if (device.battery > 20) Success else Danger, device.battery / 100f)
            }
            item {
                PatrolCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("设备接入能力", fontWeight = FontWeight.Black)
                        Text("BLE 长连接 · Wi-Fi 文件服务 · WebRTC 对讲 · 云端心跳同步", color = Muted, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
