package com.patrollink.presentation.screen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.patrollink.domain.AppUiState
import com.patrollink.domain.MediaFile
import com.patrollink.domain.MediaKind
import com.patrollink.domain.OperationMessageType
import com.patrollink.domain.TransferStatus
import com.patrollink.domain.TransferTarget
import com.patrollink.presentation.PatrolViewModel
import com.patrollink.presentation.component.MediaThumbBackground
import com.patrollink.presentation.component.StatusTag
import com.patrollink.presentation.component.SystemBars
import com.patrollink.presentation.theme.Muted
import com.patrollink.presentation.theme.PatrolDisplay
import com.patrollink.presentation.theme.Success
import com.patrollink.presentation.theme.TechBlue
import com.patrollink.presentation.theme.Warning
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaScreen(uiState: AppUiState, viewModel: PatrolViewModel) {
    val colors = PatrolDisplay.colors
    SystemBars(statusBarColor = colors.topBar, navigationBarColor = colors.bottomBar, lightStatusBar = !colors.dark, lightNavigationBar = !colors.dark)
    var filter by remember { mutableStateOf(MediaFilter.All) }
    var timeFilter by remember { mutableStateOf(MediaTimeFilter.All) }
    var batchMode by remember { mutableStateOf(false) }
    var batchSelection by remember { mutableStateOf<Set<String>>(emptySet()) }
    val files = uiState.mediaFiles
        .filter { it.local == uiState.selectedMediaLocal }
        .filter { filter.matches(it.kind) }
        .filter { timeFilter.matches(it.time) }
    val selected = files.firstOrNull { it.id == uiState.selectedMediaFileId } ?: files.firstOrNull()
    val primaryAction = selected?.mediaPrimaryAction(uiState.selectedMediaLocal)
    var pendingDelete by remember { mutableStateOf<MediaFile?>(null) }
    var pendingBatchDelete by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showIntegrityHelp by remember { mutableStateOf(false) }
    var dismissedTransferFileId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(uiState.selectedMediaLocal) {
        batchSelection = emptySet()
        batchMode = false
    }
    LaunchedEffect(uiState.selectedMediaLocal, filter, timeFilter, files) {
        val visibleIds = files.map { it.id }.toSet()
        batchSelection = batchSelection.intersect(visibleIds)
        if (batchSelection.isEmpty()) batchMode = false
    }
    Box(Modifier.fillMaxSize().background(colors.page)) {
        Column(Modifier.fillMaxSize()) {
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
                            totalGb = uiState.device.storageTotalGb,
                            onHelp = { showIntegrityHelp = true }
                        )
                    }
                    item {
                        MediaFilterRow(selected = filter, onSelected = { filter = it })
                    }
                    item {
                        MediaTimeFilterRow(selected = timeFilter, onSelected = { timeFilter = it })
                    }
                    if (files.isNotEmpty()) {
                        item {
                            MediaBatchToolbar(
                                batchMode = batchMode,
                                selectedCount = batchSelection.size,
                                totalCount = files.size,
                                onToggleBatch = {
                                    batchMode = !batchMode
                                    if (!batchMode) batchSelection = emptySet()
                                },
                                onSelectAll = {
                                    batchMode = true
                                    batchSelection = files.map { it.id }.toSet()
                                },
                                onDelete = {
                                    if (batchSelection.isEmpty()) {
                                        viewModel.showOperationMessage("请选择要删除的媒体文件", OperationMessageType.Warning)
                                    } else {
                                        pendingBatchDelete = batchSelection
                                    }
                                }
                            )
                        }
                    }
                    item {
                    if (files.isEmpty()) {
                        MediaEmptyState(filter = filter, timeFilter = timeFilter, phoneSelected = uiState.selectedMediaLocal)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            files.chunked(2).forEach { row ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    row.forEach { file ->
                                        MediaGridCard(
                                            file = file,
                                            selected = selected?.id == file.id,
                                            checked = file.id in batchSelection,
                                            batchMode = batchMode,
                                            onClick = {
                                                if (batchMode) {
                                                    batchSelection = if (file.id in batchSelection) batchSelection - file.id else batchSelection + file.id
                                                } else {
                                                    viewModel.selectMedia(file.id)
                                                }
                                            },
                                            onLongClick = {
                                                batchMode = true
                                                batchSelection = batchSelection + file.id
                                            },
                                            onPlay = { viewModel.openMediaPreview(file.id, file.local) },
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
                MediaActionBar(
                    primaryText = primaryAction?.label ?: if (uiState.selectedMediaLocal) "上传云端" else "上传手机",
                    primaryIcon = primaryAction?.icon ?: if (uiState.selectedMediaLocal) Icons.Filled.UploadFile else Icons.Filled.PhoneAndroid,
                    onPrimary = {
                        selected?.let {
                            when (it.mediaPrimaryAction(uiState.selectedMediaLocal)) {
                                MediaPrimaryAction.UploadCloud -> {
                                    dismissedTransferFileId = null
                                    viewModel.uploadMedia(it.id, it.local)
                                }
                                MediaPrimaryAction.UploadPhone -> {
                                    dismissedTransferFileId = null
                                    viewModel.downloadMedia(it.id)
                                }
                                MediaPrimaryAction.UploadedCloud -> viewModel.showOperationMessage("${it.name} 已上传", OperationMessageType.Success)
                                MediaPrimaryAction.UploadedPhone -> viewModel.showOperationMessage("${it.name} 已上传到手机", OperationMessageType.Success)
                                MediaPrimaryAction.Busy -> viewModel.showOperationMessage("${it.name} ${transferLabel(it.transferStatus)}，请稍候", OperationMessageType.Warning)
                            }
                        }
                    },
                    onPlay = {
                        selected?.let {
                            viewModel.openMediaPreview(it.id, it.local)
                        }
                    },
                    onVerify = {
                        selected?.let {
                            if (it.verified) viewModel.showOperationMessage("${it.name} 已完成证据校验", OperationMessageType.Success) else viewModel.verifyMedia(it.id, it.local)
                        }
                    },
                    onDelete = {
                        selected?.let {
                            if (it.transferStatus.inProgress) viewModel.showOperationMessage("${it.name} 正在处理，完成后再删除", OperationMessageType.Warning) else pendingDelete = it
                        }
                    }
                )
                Spacer(Modifier.height(12.dp))
            }
        }
        selected?.takeIf { it.transferStatus.inProgress && it.id != dismissedTransferFileId }?.let { file ->
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (colors.dark) 0.18f else 0.10f))
                    .clickable { dismissedTransferFileId = file.id },
                contentAlignment = Alignment.Center
            ) {
                FloatingTransferProgress(
                    file = file,
                    modifier = Modifier.clickable {}
                )
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
            text = { Text(if (file.local) "删除 ${file.name} 后，手机端列表将不再显示该文件。" else "删除 ${file.name} 后，将向设备发送删除文件指令，并从设备端列表移除。") },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteMedia(file.id, file.local)
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
    pendingBatchDelete.takeIf { it.isNotEmpty() }?.let { ids ->
        AlertDialog(
            onDismissRequest = { pendingBatchDelete = emptySet() },
            title = { Text("批量删除") },
            text = { Text("确认删除已选 ${ids.size} 个媒体文件？正在传输的文件会自动跳过。") },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteMediaBatch(ids, uiState.selectedMediaLocal)
                    batchSelection = emptySet()
                    batchMode = false
                    pendingBatchDelete = emptySet()
                }) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingBatchDelete = emptySet() }) {
                    Text("取消")
                }
            }
        )
    }
    if (showIntegrityHelp) {
        IntegrityHelpDialog(onDismiss = { showIntegrityHelp = false })
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StorageSummaryCard(title: String, usedGb: Float, totalGb: Float, onHelp: () -> Unit) {
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(colors.control)
                        .combinedClickable(onClick = {}, onLongClick = onHelp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(15.dp))
                }
            }
            Text("${usedGb}GB / ${totalGb.toInt()}GB", color = colors.textMuted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        LinearProgressIndicator(
            progress = { storageProgress(usedGb, totalGb) },
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
private fun MediaTimeFilterRow(selected: MediaTimeFilter, onSelected: (MediaTimeFilter) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MediaTimeFilter.entries.forEach { filter ->
            FilterChip(filter.label, selected == filter, { onSelected(filter) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MediaBatchToolbar(
    batchMode: Boolean,
    selectedCount: Int,
    totalCount: Int,
    onToggleBatch: () -> Unit,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = PatrolDisplay.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .border(1.dp, colors.border.copy(alpha = 0.72f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (batchMode) "已选 $selectedCount / $totalCount" else "批量操作",
            color = colors.text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.weight(1f)
        )
        ToolbarCommand(
            text = if (batchMode) "退出" else "批量选择",
            icon = if (batchMode) Icons.Filled.Close else Icons.Filled.CheckCircle,
            tint = if (batchMode) colors.textMuted else TechBlue,
            onClick = onToggleBatch
        )
        ToolbarCommand("全选", Icons.Filled.SelectAll, TechBlue, onSelectAll)
        ToolbarCommand("删除", Icons.Filled.DeleteSweep, Color(0xFFFF4F73), onDelete)
    }
}

@Composable
private fun ToolbarCommand(text: String, icon: ImageVector, tint: Color, onClick: () -> Unit) {
    val colors = PatrolDisplay.colors
    Row(
        Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.control.copy(alpha = 0.72f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(text, color = colors.text, fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

@Composable
private fun MediaEmptyState(filter: MediaFilter, timeFilter: MediaTimeFilter, phoneSelected: Boolean) {
    val colors = PatrolDisplay.colors
    Column(
        Modifier
            .fillMaxWidth()
            .height(230.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .border(1.dp, colors.border.copy(alpha = 0.65f), RoundedCornerShape(18.dp))
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(54.dp).clip(CircleShape).background(colors.control.copy(alpha = 0.78f)), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text("暂无${timeFilter.emptyPrefix}${filter.label}文件", color = colors.text, fontSize = 17.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(6.dp))
        Text(
            if (phoneSelected) "可从设备端上传到手机，或等待现场采集生成。" else "设备端当前没有匹配类型的媒体。",
            color = colors.textMuted,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaGridCard(
    file: MediaFile,
    selected: Boolean,
    checked: Boolean,
    batchMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier
) {
    val colors = PatrolDisplay.colors
    Column(
        modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
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
            MediaSyncBadge(file = file, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.46f))
                    .border(1.dp, Color.White.copy(alpha = 0.62f), CircleShape)
                    .clickable(onClick = onPlay),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
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
            if (batchMode) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (checked) TechBlue else Color.Black.copy(alpha = 0.50f))
                        .border(1.dp, Color.White.copy(alpha = 0.72f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (checked) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
            } else if (selected) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(TechBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
            if (file.transferStatus.inProgress) {
                LinearProgressIndicator(
                    progress = { file.progress.safeProgress() },
                    color = TechBlue,
                    trackColor = Color.White.copy(alpha = 0.22f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(5.dp)
                )
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
                StatusTag(mediaStatusLabel(file), transferColor(file.transferStatus))
            }
        }
    }
}

@Composable
private fun BoxScope.MediaSyncBadge(file: MediaFile, modifier: Modifier = Modifier) {
    val (icon, background, tint) = when (file.transferStatus) {
        TransferStatus.Done -> Triple(if (file.local) Icons.Filled.CloudDone else Icons.Filled.PhoneAndroid, Success, Color.White)
        TransferStatus.Uploading, TransferStatus.Hashing, TransferStatus.Verifying -> Triple(Icons.Filled.CloudUpload, TechBlue, Color.White)
        TransferStatus.Failed -> Triple(Icons.Filled.CloudUpload, Color(0xFFFF4F73), Color.White)
        TransferStatus.Idle -> Triple(if (file.local) Icons.Filled.CloudUpload else Icons.Filled.UploadFile, Color.White.copy(alpha = 0.88f), TechBlue)
    }
    Box(
        modifier
            .size(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(21.dp))
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
    val context = LocalContext.current
    var thumbnail by remember(file.contentUri, file.kind) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(file.contentUri, file.kind) {
        thumbnail = withContext(Dispatchers.IO) { loadMediaThumbnail(context, file) }
    }
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
        if (thumbnail != null && file.kind != MediaKind.Audio) {
            Image(
                bitmap = thumbnail!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.86f), modifier = Modifier.size(42.dp))
            }
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
private fun FloatingTransferProgress(file: MediaFile, modifier: Modifier = Modifier) {
    val colors = PatrolDisplay.colors
    val percent = (file.progress.safeProgress() * 100).toInt()
    Column(
        modifier
            .widthIn(max = 310.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(colors.surface)
            .border(1.dp, colors.border.copy(alpha = 0.85f), RoundedCornerShape(22.dp))
            .padding(horizontal = 18.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(TechBlue.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.UploadFile, contentDescription = null, tint = TechBlue, modifier = Modifier.size(27.dp))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(file.name, color = colors.text, fontSize = 16.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${mediaStatusLabel(file)} · $percent%", color = colors.textMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        LinearProgressIndicator(
            progress = { file.progress.safeProgress() },
            color = TechBlue,
            trackColor = colors.control.copy(alpha = 0.65f),
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(99.dp))
        )
    }
}

@Composable
private fun MediaActionBar(primaryText: String, primaryIcon: ImageVector, onPrimary: () -> Unit, onPlay: () -> Unit, onVerify: () -> Unit, onDelete: () -> Unit) {
    val colors = PatrolDisplay.colors
    val barColor = if (colors.dark) Color(0xFF0C1427) else colors.surface
    val borderColor = if (colors.dark) Color.Transparent else colors.border.copy(alpha = 0.9f)
    val dividerColor = if (colors.dark) Color.White.copy(alpha = 0.20f) else colors.border
    val textColor = if (colors.dark) Color.White else colors.text
    Row(
        Modifier
            .fillMaxWidth()
            .height(76.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(barColor)
            .border(1.dp, borderColor, RoundedCornerShape(18.dp))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionBarItem(primaryText, primaryIcon, Color(0xFF2F80ED), textColor, onPrimary, Modifier.weight(1f))
        ActionDivider(dividerColor)
        ActionBarItem("本地回放", Icons.Filled.PlayCircleFilled, if (colors.dark) Color.White else colors.text, textColor, onPlay, Modifier.weight(1f))
        ActionDivider(dividerColor)
        ActionBarItem("证据校验", Icons.Filled.Security, Success, textColor, onVerify, Modifier.weight(1f))
        ActionDivider(dividerColor)
        ActionBarItem("删除", Icons.Filled.Delete, Color(0xFFFF4F73), textColor, onDelete, Modifier.weight(1f))
    }
}

@Composable
private fun ActionDivider(color: Color) {
    Box(
        Modifier
            .height(42.dp)
            .width(1.dp)
            .background(color)
    )
}

@Composable
private fun ActionBarItem(text: String, icon: ImageVector, iconColor: Color, textColor: Color, onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(6.dp))
        Text(text, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun MediaPreviewDialog(file: MediaFile, onDismiss: () -> Unit) {
    val colors = PatrolDisplay.colors
    var fullScreen by remember(file.id) { mutableStateOf(false) }
    val canFullscreen = file.kind == MediaKind.Video || file.kind == MediaKind.Photo
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        if (fullScreen) {
            FullscreenMediaPlayer(
                file = file,
                onExitFullscreen = { fullScreen = false },
                onDismiss = onDismiss
            )
            return@Dialog
        }
        Column(
            Modifier
                .fillMaxWidth(0.78f)
                .clip(RoundedCornerShape(26.dp))
                .background(colors.surface)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(file.name, color = colors.text, fontSize = 22.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                if (canFullscreen) {
                    IconButton(
                        onClick = { fullScreen = true },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Fullscreen,
                            contentDescription = null,
                            tint = colors.textMuted,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(30.dp))
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(18.dp))
            ) {
                EmbeddedMediaPreview(file = file, modifier = Modifier.fillMaxSize())
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("时间：${file.time}", color = colors.textMuted, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("大小：${file.size}", color = colors.textMuted, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("同步状态：${mediaStatusLabel(file)}", color = colors.textMuted, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("完整性：${if (file.verified) "SHA-256 已校验 / 水印令牌已登记" else "待校验"}", color = colors.textMuted, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("存储：${if (file.local) "App 私有沙盒，可本地播放并上传小脑" else "设备端，播放/日报前会先同步到手机"}", color = colors.textMuted, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                if (file.progress > 0f && file.progress < 1f) {
                    LinearProgressIndicator(
                        progress = { file.progress.safeProgress() },
                        color = TechBlue,
                        trackColor = colors.control,
                        modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(99.dp))
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text("关闭", fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun IntegrityHelpDialog(onDismiss: () -> Unit) {
    val colors = PatrolDisplay.colors
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(colors.surface)
                .border(1.dp, colors.border.copy(alpha = 0.75f), RoundedCornerShape(22.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Success.copy(alpha = if (colors.dark) 0.22f else 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Security, contentDescription = null, tint = Success, modifier = Modifier.size(28.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("证据校验", color = colors.text, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Black)
                    Text("上传前确认文件可信", color = colors.textMuted, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(
                "对选中的媒体生成完整性记录，用来确认同步、保存、上传前后没有被篡改。",
                color = colors.textMuted,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Bold
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                IntegrityFeatureRow(Icons.Filled.Check, "SHA-256 指纹", "生成唯一文件摘要，云端可复核。", TechBlue)
                IntegrityFeatureRow(Icons.Filled.Security, "水印令牌", "登记人员与时间线索，便于追溯。", Success)
                IntegrityFeatureRow(Icons.Filled.Inventory2, "沙盒存储", "文件保存在 App 私有目录，减少外部访问。", Warning)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    "知道了",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(TechBlue)
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 22.dp, vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun IntegrityFeatureRow(icon: ImageVector, title: String, body: String, color: Color) {
    val colors = PatrolDisplay.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (colors.dark) colors.surfaceHigh.copy(alpha = 0.72f) else colors.control.copy(alpha = 0.55f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(color.copy(alpha = if (colors.dark) 0.24f else 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = colors.text, fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Black)
            Text(body, color = colors.textMuted, fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EmbeddedMediaPreview(file: MediaFile, modifier: Modifier = Modifier) {
    when (file.kind) {
        MediaKind.Photo -> PhotoPreview(file = file, modifier = modifier)
        MediaKind.Video, MediaKind.Audio -> PlayableMediaPreview(file = file, modifier = modifier)
    }
}

@Composable
private fun PhotoPreview(file: MediaFile, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var image by remember(file.contentUri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(file.contentUri) {
        image = withContext(Dispatchers.IO) { loadImageBitmap(context, file.contentUri) }
    }
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        if (image != null) {
            Image(
                bitmap = image!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            MediaThumbBackground(kind = file.kind.toKindCode(), Modifier.fillMaxSize())
            Icon(Icons.Filled.Image, contentDescription = null, tint = Color.White, modifier = Modifier.size(72.dp))
        }
    }
}

@Composable
private fun PlayableMediaPreview(file: MediaFile, modifier: Modifier = Modifier) {
    val uri = remember(file.contentUri) { file.contentUri?.let(Uri::parse) }
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        if (uri == null) {
            MediaThumbBackground(kind = file.kind.toKindCode(), Modifier.fillMaxSize())
            Icon(
                if (file.kind == MediaKind.Audio) Icons.AutoMirrored.Filled.VolumeUp else Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(72.dp)
            )
        } else {
            AndroidView(
                factory = { viewContext ->
                    VideoView(viewContext).apply {
                        setMediaController(MediaController(viewContext).also { it.setAnchorView(this) })
                        setOnPreparedListener { player ->
                            player.isLooping = false
                            start()
                        }
                    }
                },
                update = { player ->
                    if (player.tag != uri) {
                        player.tag = uri
                        player.setVideoURI(uri)
                        player.requestFocus()
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            if (file.kind == MediaKind.Audio) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.52f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(76.dp))
                }
            }
        }
    }
}

@Composable
private fun FullscreenMediaPlayer(file: MediaFile, onExitFullscreen: () -> Unit, onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        EmbeddedMediaPreview(file = file, modifier = Modifier.fillMaxSize())
        Row(
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Color.Black.copy(alpha = 0.42f))
                .padding(horizontal = 18.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(file.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            IconButton(onClick = onExitFullscreen, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.FullscreenExit, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
            }
        }
    }
}

private fun MediaKind.toKindCode() = when (this) {
    MediaKind.Video -> "VIDEO"
    MediaKind.Photo -> "PHOTO"
    MediaKind.Audio -> "AUDIO"
}

private fun transferLabel(status: TransferStatus) = when (status) {
    TransferStatus.Done -> "已完成"
    TransferStatus.Hashing -> "校验中"
    TransferStatus.Uploading -> "上传中"
    TransferStatus.Verifying -> "确认中"
    TransferStatus.Failed -> "失败"
    TransferStatus.Idle -> "待上传"
}

private fun transferColor(status: TransferStatus) = when (status) {
    TransferStatus.Done -> Success
    TransferStatus.Hashing, TransferStatus.Uploading, TransferStatus.Verifying -> TechBlue
    TransferStatus.Failed -> Color(0xFFFF4F73)
    else -> Muted
}

private val TransferStatus.inProgress: Boolean
    get() = this == TransferStatus.Hashing || this == TransferStatus.Uploading || this == TransferStatus.Verifying

private fun storageProgress(usedGb: Float, totalGb: Float): Float =
    if (usedGb.isFinite() && totalGb.isFinite() && totalGb > 0f) {
        (usedGb / totalGb).safeProgress()
    } else {
        0f
    }

private fun Float.safeProgress(): Float =
    if (isFinite()) coerceIn(0f, 1f) else 0f

private fun mediaStatusLabel(file: MediaFile): String = when (file.transferStatus) {
    TransferStatus.Done -> if (file.local) "已上传" else "已上传手机"
    TransferStatus.Idle -> if (file.local) "待上传" else "待上传手机"
    else -> transferLabel(file.transferStatus)
}

private enum class MediaPrimaryAction(val label: String, val icon: ImageVector) {
    UploadCloud("上传云端", Icons.Filled.UploadFile),
    UploadedCloud("已上传", Icons.Filled.CloudDone),
    UploadPhone("上传手机", Icons.Filled.PhoneAndroid),
    UploadedPhone("已传手机", Icons.Filled.PhoneAndroid),
    Busy("处理中", Icons.Filled.CloudUpload)
}

private fun MediaFile.mediaPrimaryAction(phoneSelected: Boolean): MediaPrimaryAction = when {
    transferStatus.inProgress -> MediaPrimaryAction.Busy
    phoneSelected && transferStatus == TransferStatus.Done && lastTransferTarget == TransferTarget.Cloud -> MediaPrimaryAction.UploadedCloud
    phoneSelected -> MediaPrimaryAction.UploadCloud
    transferStatus == TransferStatus.Done -> MediaPrimaryAction.UploadedPhone
    else -> MediaPrimaryAction.UploadPhone
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

private enum class MediaTimeFilter(val label: String, val emptyPrefix: String) {
    All("全部时间", ""),
    Today("今天", "今天的"),
    SevenDays("近7天", "近7天的"),
    ThirtyDays("近30天", "近30天的");

    fun matches(time: String): Boolean {
        if (this == All) return true
        val date = parseMediaDate(time) ?: return true
        val today = LocalDate.now()
        return when (this) {
            All -> true
            Today -> date == today
            SevenDays -> !date.isBefore(today.minusDays(6))
            ThirtyDays -> !date.isBefore(today.minusDays(29))
        }
    }
}

private fun parseMediaDate(value: String): LocalDate? {
    val raw = value.trim()
    if (raw.isBlank()) return null
    raw.toLongOrNull()?.let { epoch ->
        val millis = if (epoch < 10_000_000_000L) epoch * 1000 else epoch
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    }
    val formatters = listOf(
        DateTimeFormatter.ISO_OFFSET_DATE_TIME,
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ISO_LOCAL_DATE
    )
    formatters.forEach { formatter ->
        runCatching {
            if (formatter == DateTimeFormatter.ISO_OFFSET_DATE_TIME) {
                return Instant.from(formatter.parse(raw)).atZone(ZoneId.systemDefault()).toLocalDate()
            }
            return LocalDate.from(formatter.parse(raw))
        }.getOrNull()
    }
    return try {
        DateTimeFormatter.ofPattern("HH:mm:ss").parse(raw)
        LocalDate.now()
    } catch (_: DateTimeParseException) {
        null
    }
}

private fun loadMediaThumbnail(context: Context, file: MediaFile): Bitmap? =
    when (file.kind) {
        MediaKind.Photo -> loadImageBitmap(context, file.contentUri)
        MediaKind.Video -> loadVideoFrame(context, file.contentUri)
        MediaKind.Audio -> null
    }

private fun loadImageBitmap(context: Context, value: String?): Bitmap? {
    val uri = value?.takeIf { it.isNotBlank() }?.let(Uri::parse) ?: return null
    return runCatching {
        when {
            uri.scheme == "content" || uri.scheme == "file" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
                } else {
                    context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
                }
            }
            value.startsWith("http://") || value.startsWith("https://") -> {
                val connection = (URL(value).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 4_000
                    readTimeout = 6_000
                }
                try {
                    connection.inputStream.use(BitmapFactory::decodeStream)
                } finally {
                    connection.disconnect()
                }
            }
            value.startsWith("/") -> BitmapFactory.decodeFile(value)
            else -> null
        }
    }.getOrNull()
}

private fun loadVideoFrame(context: Context, value: String?): Bitmap? {
    val raw = value?.takeIf { it.isNotBlank() } ?: return null
    return runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            val uri = Uri.parse(raw)
            when {
                raw.startsWith("http://") || raw.startsWith("https://") -> retriever.setDataSource(raw, emptyMap())
                uri.scheme == "content" || uri.scheme == "file" -> retriever.setDataSource(context, uri)
                raw.startsWith("/") -> retriever.setDataSource(raw)
                else -> return@runCatching null
            }
            retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } finally {
            retriever.release()
        }
    }.getOrNull()
}
