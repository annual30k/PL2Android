package com.patrollink.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Videocam
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
    var filter by remember { mutableStateOf(MediaFilter.All) }
    val files = uiState.mediaFiles
        .filter { it.local == uiState.selectedMediaLocal }
        .filter { filter.matches(it.kind) }
    val selected = files.firstOrNull { it.id == uiState.selectedMediaFileId } ?: files.firstOrNull()
    var pendingDelete by remember { mutableStateOf<MediaFile?>(null) }
    Column(Modifier.fillMaxSize().background(colors.page)) {
        ForceTopBar(title = null, dark = true, onSos = onSos)
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(top = 16.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                item {
                    MediaEndpointSwitch(
                        phoneSelected = uiState.selectedMediaLocal,
                        onPhone = { viewModel.setMediaLocal(true) },
                        onDevice = { viewModel.setMediaLocal(false) }
                    )
                }
                item {
                    StorageSummaryCard(
                        title = if (uiState.selectedMediaLocal) "手机存储" else "设备存储",
                        usedGb = uiState.device.storageUsedGb,
                        totalGb = uiState.device.storageTotalGb
                    )
                }
                item {
                    MediaFilterRow(selected = filter, onSelected = { filter = it })
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        files.chunked(2).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                row.forEach { file ->
                                    MediaGridCard(
                                        file = file,
                                        selected = selected?.id == file.id,
                                        onClick = { viewModel.selectMedia(file.id) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (row.size == 1) Box(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            MediaActionBar(
                onUpload = { selected?.let { viewModel.uploadMedia(it.id) } },
                onPlay = { selected?.let { viewModel.openMediaPreview(it.id) } },
                onDelete = { pendingDelete = selected }
            )
            Spacer(Modifier.height(12.dp))
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
private fun MediaEndpointSwitch(phoneSelected: Boolean, onPhone: () -> Unit, onDevice: () -> Unit) {
    val colors = PatrolDisplay.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.control.copy(alpha = 0.95f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        EndpointButton("手机端", phoneSelected, onPhone, Modifier.weight(1f))
        EndpointButton("设备端", !phoneSelected, onDevice, Modifier.weight(1f))
    }
}

@Composable
private fun EndpointButton(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = PatrolDisplay.colors
    Box(
        modifier
            .height(42.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) colors.surface else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) TechBlue else colors.textMuted,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun StorageSummaryCard(title: String, usedGb: Float, totalGb: Float) {
    val colors = PatrolDisplay.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .border(1.dp, colors.border.copy(alpha = 0.65f), RoundedCornerShape(18.dp))
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text("${usedGb}GB / ${totalGb.toInt()}GB", color = Color(0xFF91A1BA), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        LinearProgressIndicator(
            progress = { (usedGb / totalGb).coerceIn(0f, 1f) },
            color = TechBlue,
            trackColor = colors.control.copy(alpha = 0.55f),
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(99.dp))
        )
    }
}

@Composable
private fun MediaFilterRow(selected: MediaFilter, onSelected: (MediaFilter) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MediaFilter.entries.forEach { filter ->
            val weight = if (filter == MediaFilter.All) 0.9f else 1f
            FilterChip(filter.label, selected == filter, { onSelected(filter) }, Modifier.weight(weight))
        }
    }
}

@Composable
private fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = PatrolDisplay.colors
    Box(
        modifier
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (selected) TechBlue else colors.surface)
            .border(1.dp, if (selected) TechBlue else colors.border, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) Color.White else colors.text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun MediaGridCard(file: MediaFile, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = PatrolDisplay.colors
    Column(
        modifier
            .clickable(onClick = onClick)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(18.dp))
                .border(if (selected) 3.dp else 0.dp, if (selected) TechBlue else Color.Transparent, RoundedCornerShape(18.dp))
        ) {
            MediaArtwork(file = file, modifier = Modifier.fillMaxSize())
            MediaKindBadge(file)
            if (file.verified) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(TechBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Security, contentDescription = null, tint = Color.White, modifier = Modifier.size(21.dp))
                }
            }
            Text(
                file.size,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            )
            if (selected) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(TechBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
        }
        Column(Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(file.name, color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    file.time,
                    color = colors.textSubtle,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(6.dp))
                StatusTag(transferLabel(file.transferStatus), transferColor(file.transferStatus))
            }
        }
    }
}

@Composable
private fun BoxScope.MediaKindBadge(file: MediaFile) {
    val icon = when (file.kind) {
        MediaKind.Photo -> Icons.Filled.Image
        MediaKind.Audio -> Icons.Filled.Mic
        MediaKind.Video -> Icons.Filled.Videocam
    }
    val label = file.duration
    Row(
        Modifier
            .align(Alignment.TopStart)
            .padding(8.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(Color.Black.copy(alpha = 0.46f))
            .padding(horizontal = 7.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
        if (label != null) Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MediaArtwork(file: MediaFile, modifier: Modifier) {
    val brush = when (file.kind) {
        MediaKind.Video -> Brush.linearGradient(listOf(Color(0xFF17365D), Color(0xFF0C8DBC), Color(0xFF101827)))
        MediaKind.Photo -> Brush.linearGradient(listOf(Color(0xFF4FA7DF), Color(0xFF20314A), Color(0xFF07111F)))
        MediaKind.Audio -> Brush.linearGradient(listOf(Color(0xFFCBC8F7), Color(0xFF8F87A5)))
    }
    val icon = when (file.kind) {
        MediaKind.Video -> Icons.Filled.Videocam
        MediaKind.Photo -> Icons.Filled.Image
        MediaKind.Audio -> Icons.AutoMirrored.Filled.InsertDriveFile
    }
    Box(modifier.background(brush), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(76.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.86f), modifier = Modifier.size(42.dp))
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(64.dp)
                .background(Color.Black.copy(alpha = 0.18f))
        )
    }
}

@Composable
private fun MediaActionBar(onUpload: () -> Unit, onPlay: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(76.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF0C1427))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionBarItem("上传云端", Icons.Filled.CloudUpload, Color(0xFF63A8FF), onUpload, Modifier.weight(1f))
        ActionDivider()
        ActionBarItem("本地回放", Icons.Filled.PlayCircleFilled, Color.White, onPlay, Modifier.weight(1f))
        ActionDivider()
        ActionBarItem("删除", Icons.Filled.Delete, Color(0xFFFF5E7C), onDelete, Modifier.weight(1f))
    }
}

@Composable
private fun ActionDivider() {
    Box(
        Modifier
            .height(42.dp)
            .width(1.dp)
            .background(Color.White.copy(alpha = 0.20f))
    )
}

@Composable
private fun ActionBarItem(text: String, icon: ImageVector, color: Color, onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(6.dp))
        Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
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
                            MediaKind.Audio -> Icons.AutoMirrored.Filled.VolumeUp
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

private enum class MediaFilter(val label: String) {
    All("全部"),
    Video("视频"),
    Photo("图片"),
    Audio("音频");

    fun matches(kind: MediaKind) = when (this) {
        All -> true
        Video -> kind == MediaKind.Video
        Photo -> kind == MediaKind.Photo
        Audio -> kind == MediaKind.Audio
    }
}
