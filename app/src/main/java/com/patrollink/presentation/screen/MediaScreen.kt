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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrollink.domain.AppUiState
import com.patrollink.domain.MediaFile
import com.patrollink.domain.MediaKind
import com.patrollink.domain.TransferStatus
import com.patrollink.presentation.PatrolViewModel
import com.patrollink.presentation.component.ForceTopBar
import com.patrollink.presentation.component.MediaThumbBackground
import com.patrollink.presentation.component.PatrolCard
import com.patrollink.presentation.component.PrimaryAction
import com.patrollink.presentation.component.SegmentedTabs
import com.patrollink.presentation.component.StatusTag
import com.patrollink.presentation.component.SystemBars
import com.patrollink.presentation.theme.Danger
import com.patrollink.presentation.theme.Muted
import com.patrollink.presentation.theme.PatrolDisplay
import com.patrollink.presentation.theme.Success
import com.patrollink.presentation.theme.TechBlue
import com.patrollink.presentation.theme.Warning

@Composable
fun MediaScreen(uiState: AppUiState, viewModel: PatrolViewModel, onSos: () -> Unit) {
    val colors = PatrolDisplay.colors
    SystemBars(statusBarColor = colors.topBar, navigationBarColor = colors.bottomBar, lightStatusBar = !colors.dark, lightNavigationBar = !colors.dark)
    val files = uiState.mediaFiles.filter { it.local == uiState.selectedMediaLocal }
    val selected = files.firstOrNull { it.id == uiState.selectedMediaFileId } ?: files.firstOrNull()
    var pendingDelete by remember { mutableStateOf<MediaFile?>(null) }
    Column(Modifier.fillMaxSize().background(colors.page)) {
        ForceTopBar(title = null, dark = true, onSos = onSos)
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                PatrolCard(radius = 16) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("设备存储", color = colors.textSubtle, fontSize = 11.sp, fontWeight = FontWeight.Black)
                        Text("${uiState.device.storageUsedGb} / ${uiState.device.storageTotalGb.toInt()} GB", color = colors.text, fontSize = 30.sp, fontWeight = FontWeight.Black)
                        LinearProgressIndicator(
                            progress = { uiState.device.storageUsedGb / uiState.device.storageTotalGb },
                            color = TechBlue,
                            trackColor = colors.control,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(99.dp))
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            LegendDot("媒体文件", TechBlue)
                            LegendDot("剩余空间", colors.textMuted)
                        }
                    }
                }
            }
            item {
                SegmentedTabs(
                    left = "设备端",
                    right = "手机端",
                    leftSelected = !uiState.selectedMediaLocal,
                    onLeft = { viewModel.setMediaLocal(false) },
                    onRight = { viewModel.setMediaLocal(true) }
                )
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryAction("下载", onClick = { selected?.let { viewModel.downloadMedia(it.id) } }, modifier = Modifier.weight(1f))
                    PrimaryAction("上传", onClick = { selected?.let { viewModel.uploadMedia(it.id) } }, modifier = Modifier.weight(1f))
                    PrimaryAction("删除", onClick = { pendingDelete = selected }, modifier = Modifier.weight(1f), danger = true)
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    files.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { file ->
                                MediaGridCard(
                                    file = file,
                                    selected = selected?.id == file.id,
                                    onClick = {
                                        viewModel.selectMedia(file.id)
                                        viewModel.openMediaPreview(file.id)
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (row.size == 1) Box(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
    uiState.previewMediaFile?.let { file ->
        MediaPreviewDialog(file = file, onDismiss = viewModel::closeMediaPreview)
    }
    pendingDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("确认删除") },
            text = { Text("删除 ${file.name} 后，本地列表将不再显示该文件。") },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteMedia(file.id)
                    pendingDelete = null
                }) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun LegendDot(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MediaGridCard(file: MediaFile, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = PatrolDisplay.colors
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .border(2.dp, if (selected) TechBlue else Color.Transparent, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
        ) {
            MediaThumbBackground(kind = file.kind.toKindCode(), Modifier.fillMaxSize())
            Row(Modifier.align(Alignment.TopEnd).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (file.verified) StatusTag("✓", TechBlue, filled = true)
            }
            Text(
                file.duration ?: file.size,
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            )
            Box(
                Modifier
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(10.dp)
            ) {
                Icon(
                    imageVector = when (file.kind) {
                        MediaKind.Photo -> Icons.Filled.Image
                        MediaKind.Audio -> Icons.Filled.VolumeUp
                        MediaKind.Video -> Icons.Filled.PlayArrow
                    },
                    contentDescription = null,
                    tint = colors.text
                )
            }
        }
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(file.time, color = colors.textSubtle, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(file.name, color = colors.text, fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                StatusTag(transferLabel(file.transferStatus), transferColor(file.transferStatus))
                Text(file.size, color = colors.textSubtle, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MediaPreviewDialog(file: MediaFile, onDismiss: () -> Unit) {
    val colors = PatrolDisplay.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(file.name, color = colors.text, fontWeight = FontWeight.Black)
                Icon(Icons.Filled.Close, contentDescription = null, tint = colors.textMuted, modifier = Modifier.clickable(onClick = onDismiss))
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    MediaThumbBackground(kind = file.kind.toKindCode(), Modifier.fillMaxSize())
                    Icon(
                        imageVector = when (file.kind) {
                            MediaKind.Photo -> Icons.Filled.Image
                            MediaKind.Audio -> Icons.Filled.VolumeUp
                            MediaKind.Video -> Icons.Filled.PlayArrow
                        },
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                Text("时间：${file.time}", color = colors.textMuted)
                Text("大小：${file.size}", color = colors.textMuted)
                Text("同步状态：${transferLabel(file.transferStatus)}", color = colors.textMuted)
                if (file.progress > 0f && file.progress < 1f) {
                    LinearProgressIndicator(progress = { file.progress }, color = TechBlue, trackColor = colors.control, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

private fun MediaKind.toKindCode() = when (this) {
    MediaKind.Video -> "VIDEO"
    MediaKind.Photo -> "PHOTO"
    MediaKind.Audio -> "AUDIO"
}

private fun transferLabel(status: TransferStatus) = when (status) {
    TransferStatus.Idle -> "待同步"
    TransferStatus.Hashing -> "校验中"
    TransferStatus.Uploading -> "上传中"
    TransferStatus.Verifying -> "验证中"
    TransferStatus.Done -> "已同步"
    TransferStatus.Failed -> "失败"
}

private fun transferColor(status: TransferStatus) = when (status) {
    TransferStatus.Done -> Success
    TransferStatus.Failed -> Danger
    TransferStatus.Idle -> Muted
    TransferStatus.Hashing -> Warning
    else -> TechBlue
}
