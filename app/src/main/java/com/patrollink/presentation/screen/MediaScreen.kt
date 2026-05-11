package com.patrollink.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrollink.domain.AppUiState
import com.patrollink.domain.MediaKind
import com.patrollink.domain.TransferStatus
import com.patrollink.presentation.PatrolViewModel
import com.patrollink.presentation.component.MetricTile
import com.patrollink.presentation.component.PatrolCard
import com.patrollink.presentation.component.PatrolTopBar
import com.patrollink.presentation.component.PrimaryAction
import com.patrollink.presentation.component.StatusTag
import com.patrollink.presentation.theme.Border
import com.patrollink.presentation.theme.Danger
import com.patrollink.presentation.theme.Muted
import com.patrollink.presentation.theme.Success
import com.patrollink.presentation.theme.TechBlue
import com.patrollink.presentation.theme.Warning

@Composable
fun MediaScreen(uiState: AppUiState, viewModel: PatrolViewModel, onSos: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PatrolTopBar("媒体证据", onSos)
        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                MetricTile("设备存储", "${uiState.device.storageUsedGb} / ${uiState.device.storageTotalGb.toInt()} GB", TechBlue, uiState.device.storageUsedGb / uiState.device.storageTotalGb)
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MediaTab("设备文件", !uiState.selectedMediaLocal, Modifier.weight(1f)) { viewModel.setMediaLocal(false) }
                    MediaTab("本地文件", uiState.selectedMediaLocal, Modifier.weight(1f)) { viewModel.setMediaLocal(true) }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val visible = uiState.mediaFiles.firstOrNull { it.local == uiState.selectedMediaLocal }
                    PrimaryAction("下载", onClick = { visible?.let { viewModel.downloadMedia(it.id) } }, modifier = Modifier.weight(1f))
                    PrimaryAction("上传", onClick = { visible?.let { viewModel.uploadMedia(it.id) } }, modifier = Modifier.weight(1f))
                    PrimaryAction("删除", onClick = { visible?.let { viewModel.deleteMedia(it.id) } }, modifier = Modifier.weight(1f), danger = true)
                }
            }
            items(uiState.mediaFiles.filter { it.local == uiState.selectedMediaLocal }) { file ->
                PatrolCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(file.name, fontSize = 18.sp, fontWeight = FontWeight.Black)
                                Text("${file.time} · ${file.size}${file.duration?.let { " · $it" } ?: ""}", color = Muted, fontSize = 12.sp)
                            }
                            StatusTag(kindLabel(file.kind), kindColor(file.kind))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            StatusTag(if (file.verified) "SHA-256 已校验" else "待校验", if (file.verified) Success else Warning)
                            StatusTag(transferLabel(file.transferStatus), transferColor(file.transferStatus))
                        }
                        if (file.transferStatus == TransferStatus.Uploading || file.transferStatus == TransferStatus.Hashing) {
                            LinearProgressIndicator(progress = { file.progress }, modifier = Modifier.fillMaxWidth().height(6.dp), color = TechBlue, trackColor = Border)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaTab(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(42.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (selected) TechBlue else Color.White, contentColor = if (selected) Color.White else Muted)
    ) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}

private fun kindLabel(kind: MediaKind) = when (kind) {
    MediaKind.Video -> "视频"
    MediaKind.Photo -> "照片"
    MediaKind.Audio -> "音频"
}

private fun kindColor(kind: MediaKind) = when (kind) {
    MediaKind.Video -> TechBlue
    MediaKind.Photo -> Success
    MediaKind.Audio -> Warning
}

private fun transferLabel(status: TransferStatus) = when (status) {
    TransferStatus.Idle -> "待处理"
    TransferStatus.Hashing -> "计算哈希"
    TransferStatus.Uploading -> "上传中"
    TransferStatus.Verifying -> "校验中"
    TransferStatus.Done -> "已同步"
    TransferStatus.Failed -> "失败"
}

private fun transferColor(status: TransferStatus) = when (status) {
    TransferStatus.Done -> Success
    TransferStatus.Failed -> Danger
    TransferStatus.Idle -> Muted
    else -> TechBlue
}
