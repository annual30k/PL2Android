package com.patrollink.presentation.screen

import android.content.Context
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.LruCache
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.core.content.FileProvider
import com.patrollink.domain.AppUiState
import com.patrollink.domain.DeviceCapabilities
import com.patrollink.domain.DeviceMediaSyncUiState
import com.patrollink.domain.MediaFile
import com.patrollink.domain.MediaKind
import com.patrollink.domain.OperationMessageType
import com.patrollink.domain.TransferStatus
import com.patrollink.domain.TransferTarget
import com.patrollink.presentation.MediaContentRequest
import com.patrollink.presentation.PatrolViewModel
import com.patrollink.presentation.component.MediaThumbBackground
import com.patrollink.presentation.component.PatrolConfirmDialog
import com.patrollink.presentation.component.PatrolConfirmStyle
import com.patrollink.presentation.component.StatusTag
import com.patrollink.presentation.component.SystemBars
import com.patrollink.presentation.theme.Muted
import com.patrollink.presentation.theme.PatrolDisplay
import com.patrollink.presentation.theme.Success
import com.patrollink.presentation.theme.TechBlue
import com.patrollink.presentation.theme.Warning
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

private const val MediaThumbnailMaxPx = 512
private const val MediaGridThumbnailMaxPx = 256
private const val RemoteImageThumbnailMaxBytes = 6L * 1024L * 1024L
private val DevicePhotoThumbnailLimiter = Semaphore(2)
private val DeviceVideoThumbnailLimiter = Semaphore(1)
private val MediaThumbnailMemoryCache = object : LruCache<String, Bitmap>(
    ((Runtime.getRuntime().maxMemory() / 1024L) / 8L)
        .coerceAtMost(48L * 1024L)
        .toInt()
) {
    override fun sizeOf(key: String, value: Bitmap): Int =
        (value.allocationByteCount / 1024).coerceAtLeast(1)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaScreen(uiState: AppUiState, viewModel: PatrolViewModel) {
    val colors = PatrolDisplay.colors
    val context = LocalContext.current
    SystemBars(statusBarColor = colors.topBar, navigationBarColor = colors.bottomBar, lightStatusBar = !colors.dark, lightNavigationBar = !colors.dark)
    var filter by remember { mutableStateOf(MediaFilter.All) }
    var timeFilter by remember { mutableStateOf(MediaTimeFilter.All) }
    var showTimeFilters by remember { mutableStateOf(false) }
    var batchMode by remember { mutableStateOf(false) }
    var batchSelection by remember { mutableStateOf<Set<String>>(emptySet()) }
    val files = uiState.mediaFiles
        .filter { it.local == uiState.selectedMediaLocal }
        .filter { filter.matches(it.kind) }
        .filter { timeFilter.matches(it.time) }
    val mediaGridState = rememberLazyGridState()
    val activeMediaKeys by remember(files, mediaGridState) {
        derivedStateOf {
            val visibleItems = mediaGridState.layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) {
                emptySet()
            } else {
                val firstIndex = (visibleItems.minOf { it.index } - 9).coerceAtLeast(0)
                val lastIndex = (visibleItems.maxOf { it.index } + 9).coerceAtMost(files.lastIndex)
                (firstIndex..lastIndex).map { index ->
                    val file = files[index]
                    "${file.local}-${file.id}"
                }.toSet()
            }
        }
    }
    val pendingDeviceSyncIds = remember(files, uiState.mediaFiles, uiState.selectedMediaLocal) {
        if (uiState.selectedMediaLocal) {
            emptySet()
        } else {
            files
                .filter { !it.transferStatus.inProgress }
                .filterNot { it.transferStatus == TransferStatus.Done && it.lastTransferTarget == TransferTarget.PhoneSandbox }
                .filter { deviceFile ->
                    uiState.mediaFiles.none { it.id == deviceFile.id && it.local && !it.contentUri.isNullOrBlank() }
                }
                .map { it.id }
                .toSet()
        }
    }
    val selected = files.firstOrNull { it.id == uiState.selectedMediaFileId } ?: files.firstOrNull()
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
                MediaLibraryHeader(
                    phoneSelected = uiState.selectedMediaLocal,
                    totalCount = files.size,
                    pendingDeviceCount = pendingDeviceSyncIds.size,
                    batchMode = batchMode,
                    selectedCount = batchSelection.size,
                    onPhone = { viewModel.setMediaLocal(true) },
                    onDevice = { viewModel.setMediaLocal(false) },
                    onToggleBatch = {
                        batchMode = !batchMode
                        if (!batchMode) batchSelection = emptySet()
                    }
                )
                Spacer(Modifier.height(12.dp))
                MediaCompactFilterBar(
                    selectedType = filter,
                    selectedTime = timeFilter,
                    showTimeFilters = showTimeFilters,
                    onTypeSelected = { filter = it },
                    onTimeToggle = { showTimeFilters = !showTimeFilters },
                    onTimeDismiss = { showTimeFilters = false },
                    onTimeSelected = {
                        timeFilter = it
                        showTimeFilters = false
                    }
                )
                Spacer(Modifier.height(12.dp))
                if (!uiState.selectedMediaLocal) {
                    DeviceMediaSyncBar(
                        totalCount = files.size,
                        pendingCount = pendingDeviceSyncIds.size,
                        loading = uiState.mediaLoading,
                        onWifiSettings = {
                            runCatching { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
                        },
                        onRefresh = { viewModel.refreshMediaFiles(showFailureMessage = true) },
                        onSync = { viewModel.syncDeviceMediaToPhone(pendingDeviceSyncIds, refreshFirst = true) }
                    )
                    Spacer(Modifier.height(12.dp))
                }
                if (files.isEmpty()) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        MediaEmptyState(
                            filter = filter,
                            timeFilter = timeFilter,
                            phoneSelected = uiState.selectedMediaLocal,
                            loading = uiState.mediaLoading,
                            capabilities = uiState.deviceCapabilities
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = mediaGridState,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(files, key = { "${it.local}-${it.id}" }) { file ->
                            val itemKey = "${file.local}-${file.id}"
                            MediaLibraryTile(
                                file = file,
                                loadThumbnail = itemKey in activeMediaKeys,
                                selected = selected?.id == file.id,
                                checked = file.id in batchSelection,
                                batchMode = batchMode,
                                onClick = {
                                    if (batchMode) {
                                        batchSelection = if (file.id in batchSelection) batchSelection - file.id else batchSelection + file.id
                                    } else {
                                        viewModel.selectMedia(file.id)
                                        viewModel.openMediaPreview(file.id, file.local)
                                    }
                                },
                                onLongClick = {
                                    batchMode = true
                                    batchSelection = batchSelection + file.id
                                },
                                thumbnailRequest = viewModel::mediaContentRequest
                            )
                        }
                    }
                }
                if (batchMode) {
                    MediaActionBar(
                        primaryText = if (uiState.selectedMediaLocal) "全选" else "同步",
                        primaryIcon = if (uiState.selectedMediaLocal) Icons.Filled.SelectAll else Icons.Filled.PhoneAndroid,
                        onPrimary = {
                            if (uiState.selectedMediaLocal) {
                                batchSelection = files.map { it.id }.toSet()
                            } else {
                                val ids = batchSelection.ifEmpty { pendingDeviceSyncIds }
                                if (ids.isEmpty()) {
                                    viewModel.showOperationMessage("没有待同步的设备文件", OperationMessageType.Warning)
                                } else {
                                    viewModel.syncDeviceMediaToPhone(ids, refreshFirst = true)
                                    batchSelection = emptySet()
                                    batchMode = false
                                }
                            }
                        },
                        onVerify = { viewModel.showOperationMessage("已选择 ${batchSelection.size} 个媒体文件", OperationMessageType.Info) },
                        onDelete = {
                            if (batchSelection.isEmpty()) {
                                viewModel.showOperationMessage("请选择要删除的媒体文件", OperationMessageType.Warning)
                            } else {
                                pendingBatchDelete = batchSelection
                            }
                        }
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
        val selectedTransfer = selected?.takeIf { it.transferStatus.inProgress && it.id != dismissedTransferFileId }
        val deviceSync = uiState.deviceMediaSync.takeIf { it.active }
        if (deviceSync != null) {
            DeviceSyncProgressDialog(sync = deviceSync)
        } else if (selectedTransfer != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (colors.dark) 0.18f else 0.10f))
                    .clickable {
                        selectedTransfer?.let { dismissedTransferFileId = it.id }
                    },
                contentAlignment = Alignment.Center
            ) {
                FloatingTransferProgress(file = selectedTransfer, modifier = Modifier.clickable {})
            }
        }
        if (deviceSync == null && !uiState.selectedMediaLocal && uiState.mediaLoading) {
            MediaBlockingLoading(message = "正在读取设备文件")
        }
    }
    uiState.previewMediaFile?.let { file ->
        val previewAction = file.mediaPrimaryAction()
        MediaPreviewDialog(
            file = file,
            contentRequest = viewModel::mediaContentRequest,
            primaryText = previewAction.label,
            primaryIcon = previewAction.icon,
            onPrimary = {
                when (previewAction) {
                    MediaPrimaryAction.UploadCloud -> {
                        dismissedTransferFileId = null
                        viewModel.uploadMedia(file.id, local = true)
                    }
                    MediaPrimaryAction.UploadedCloud -> viewModel.showOperationMessage("${file.name} 已上传云端", OperationMessageType.Success)
                    MediaPrimaryAction.SyncPhone -> {
                        dismissedTransferFileId = null
                        viewModel.downloadMedia(file.id)
                    }
                    MediaPrimaryAction.SyncedPhone -> viewModel.showOperationMessage("${file.name} 已同步到手机", OperationMessageType.Success)
                    MediaPrimaryAction.Busy -> viewModel.showOperationMessage("${file.name} ${mediaStatusLabel(file)}，请稍候", OperationMessageType.Warning)
                }
            },
            onVerify = {
                if (file.verified) {
                    viewModel.showOperationMessage("${file.name} 已完成证据校验", OperationMessageType.Success)
                } else {
                    viewModel.showOperationMessage("正在校验证据完整性...", OperationMessageType.Info)
                    viewModel.verifyMedia(file.id, file.local)
                }
            },
            onDelete = {
                if (file.transferStatus.inProgress) viewModel.showOperationMessage("${file.name} 正在处理，完成后再删除", OperationMessageType.Warning) else pendingDelete = file
            },
            onDismiss = viewModel::closeMediaPreview
        )
    }
    pendingDelete?.let { file ->
        PatrolConfirmDialog(
            title = "确认删除",
            message = if (file.local) {
                "删除 ${file.name} 后，手机端列表将不再显示该文件。"
            } else {
                "删除 ${file.name} 后，将向设备发送删除文件指令，并从设备端列表移除。"
            },
            warningText = "删除后无法从当前列表直接恢复，请确认文件已经不再需要。",
            confirmText = "删除",
            style = PatrolConfirmStyle.Danger,
            onDismiss = { pendingDelete = null },
            onConfirm = {
                viewModel.deleteMedia(file.id, file.local)
                pendingDelete = null
            }
        )
    }
    pendingBatchDelete.takeIf { it.isNotEmpty() }?.let { ids ->
        PatrolConfirmDialog(
            title = "批量删除",
            message = "确认删除已选 ${ids.size} 个媒体文件？正在传输的文件会自动跳过。",
            warningText = "批量删除会一次性移除多个文件，请确认选择范围正确。",
            confirmText = "删除",
            style = PatrolConfirmStyle.Danger,
            onDismiss = { pendingBatchDelete = emptySet() },
            onConfirm = {
                viewModel.deleteMediaBatch(ids, uiState.selectedMediaLocal)
                batchSelection = emptySet()
                batchMode = false
                pendingBatchDelete = emptySet()
            }
        )
    }
    if (showIntegrityHelp) {
        IntegrityHelpDialog(onDismiss = { showIntegrityHelp = false })
    }
}

@Composable
private fun MediaLibraryHeader(
    phoneSelected: Boolean,
    totalCount: Int,
    pendingDeviceCount: Int,
    batchMode: Boolean,
    selectedCount: Int,
    onPhone: () -> Unit,
    onDevice: () -> Unit,
    onToggleBatch: () -> Unit
) {
    val colors = PatrolDisplay.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                if (batchMode) "已选择 $selectedCount 项" else "图库",
                color = colors.text,
                fontSize = 32.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                if (phoneSelected) {
                    "手机端 · $totalCount 个文件"
                } else {
                    "设备端 · $totalCount 个文件 · $pendingDeviceCount 个待同步"
                },
                color = colors.textMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (batchMode) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(colors.control.copy(alpha = 0.86f))
                        .clickable(onClick = onToggleBatch),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "取消多选", tint = colors.text, modifier = Modifier.size(20.dp))
                }
            }
            MediaEndpointSwitch(
                phoneSelected = phoneSelected,
                onPhone = onPhone,
                onDevice = onDevice,
                modifier = Modifier.width(126.dp)
            )
        }
    }
}

@Composable
private fun MediaEndpointSwitch(
    phoneSelected: Boolean,
    onPhone: () -> Unit,
    onDevice: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    val colors = PatrolDisplay.colors
    Row(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(colors.control.copy(alpha = 0.95f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
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
            .height(34.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(if (selected) colors.surface else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) TechBlue else colors.textMuted,
            fontSize = 14.sp,
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
        MediaProgressTrack(
            progress = storageProgress(usedGb, totalGb),
            height = 8,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun MediaCompactFilterBar(
    selectedType: MediaFilter,
    selectedTime: MediaTimeFilter,
    showTimeFilters: Boolean,
    onTypeSelected: (MediaFilter) -> Unit,
    onTimeToggle: () -> Unit,
    onTimeDismiss: () -> Unit,
    onTimeSelected: (MediaTimeFilter) -> Unit
) {
    val colors = PatrolDisplay.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MediaFilter.entries.forEach { filter ->
            FilterChip(
                text = filter.label,
                selected = selectedType == filter,
                onClick = { onTypeSelected(filter) },
                modifier = Modifier.weight(1f)
            )
        }
        Box(Modifier.weight(1f)) {
            FilterChip(
                text = "筛选",
                icon = Icons.Filled.FilterList,
                selected = showTimeFilters,
                onClick = onTimeToggle,
                modifier = Modifier.fillMaxWidth()
            )
            DropdownMenu(
                expanded = showTimeFilters,
                onDismissRequest = onTimeDismiss,
                shape = RoundedCornerShape(22.dp),
                containerColor = Color.Transparent,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                modifier = Modifier
                    .width(132.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.border, RoundedCornerShape(22.dp))
            ) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    MediaTimeFilter.entries.forEach { filter ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .clickable { onTimeSelected(filter) }
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (selectedTime == filter) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = TechBlue, modifier = Modifier.size(18.dp))
                            } else {
                                Spacer(Modifier.size(18.dp))
                            }
                            Text(
                                text = filter.label,
                                color = if (selectedTime == filter) TechBlue else colors.text,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceMediaSyncBar(
    totalCount: Int,
    pendingCount: Int,
    loading: Boolean,
    onWifiSettings: () -> Unit,
    onRefresh: () -> Unit,
    onSync: () -> Unit
) {
    val colors = PatrolDisplay.colors
    val hasDeviceFiles = totalCount > 0
    val primaryText = if (hasDeviceFiles) "同步到手机" else "查看设备文件"
    val primaryIcon = if (hasDeviceFiles) Icons.Filled.PhoneAndroid else Icons.Filled.UploadFile
    val primaryAction = if (hasDeviceFiles) onSync else onRefresh
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .border(1.dp, colors.border.copy(alpha = 0.72f), RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("设备文件", color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1, modifier = Modifier.weight(1f))
        IconButton(
            onClick = onWifiSettings,
            enabled = !loading,
            modifier = Modifier.size(42.dp)
        ) {
            Icon(Icons.Filled.PhoneAndroid, contentDescription = "设备 Wi-Fi", modifier = Modifier.size(20.dp), tint = colors.text)
        }
        Button(
            onClick = primaryAction,
            enabled = !loading,
            modifier = Modifier.height(44.dp).widthIn(min = 142.dp)
        ) {
            Icon(primaryIcon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(primaryText, fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
    }
}

@Composable
private fun MediaBlockingLoading(message: String) {
    val colors = PatrolDisplay.colors
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = if (colors.dark) 0.32f else 0.18f))
            .clickable(enabled = true, onClick = {}),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .widthIn(min = 220.dp, max = 300.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(colors.surface)
                .border(1.dp, colors.border.copy(alpha = 0.78f), RoundedCornerShape(22.dp))
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CircularProgressIndicator(color = TechBlue, strokeWidth = 4.dp, modifier = Modifier.size(42.dp))
            Text(message, color = colors.text, fontSize = 16.sp, fontWeight = FontWeight.Black)
            Text("请稍候", color = colors.textMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun FilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    icon: ImageVector? = null
) {
    val colors = PatrolDisplay.colors
    Box(
        modifier
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(if (selected) TechBlue else colors.surface)
            .border(1.dp, if (selected) TechBlue else colors.border, RoundedCornerShape(17.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) Color.White else colors.text,
                    modifier = Modifier.size(15.dp)
                )
            }
            Text(
                text = text,
                color = if (selected) Color.White else colors.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaLibraryTile(
    file: MediaFile,
    loadThumbnail: Boolean,
    selected: Boolean,
    checked: Boolean,
    batchMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    thumbnailRequest: suspend (MediaFile) -> MediaContentRequest?
) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        MediaArtwork(file = file, loadThumbnail = loadThumbnail, thumbnailRequest = thumbnailRequest, modifier = Modifier.fillMaxSize())
        CompactKindBadge(file, modifier = Modifier.align(Alignment.TopStart).padding(5.dp))
        CompactCloudBadge(file, modifier = Modifier.align(Alignment.TopEnd).padding(5.dp))
        if (file.kind != MediaKind.Photo) {
            val icon = if (file.kind == MediaKind.Audio) Icons.AutoMirrored.Filled.VolumeUp else Icons.Filled.PlayArrow
            Icon(
                icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(if (file.kind == MediaKind.Audio) 38.dp else 34.dp)
            )
        }
        if (batchMode) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(5.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(if (checked) TechBlue else Color.Black.copy(alpha = 0.42f))
                    .border(1.dp, Color.White.copy(alpha = 0.82f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (checked) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
            }
        }
        if (file.transferStatus.inProgress) {
            CompactTileProgress(
                progress = file.progress.safeProgress(),
                label = "${(file.progress.safeProgress() * 100).roundToInt()}%",
                modifier = Modifier.align(Alignment.BottomStart).padding(5.dp)
            )
        }
    }
}

@Composable
private fun CompactKindBadge(file: MediaFile, modifier: Modifier = Modifier) {
    val icon = when (file.kind) {
        MediaKind.Photo -> Icons.Filled.Image
        MediaKind.Audio -> Icons.Filled.Mic
        MediaKind.Video -> Icons.Filled.Videocam
    }
    Row(
        modifier
            .clip(RoundedCornerShape(5.dp))
            .background(Color.Black.copy(alpha = 0.46f))
            .padding(horizontal = 5.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
        file.duration?.let { Text(it, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black) }
    }
}

@Composable
private fun CompactCloudBadge(file: MediaFile, modifier: Modifier = Modifier) {
    val uploaded = file.transferStatus == TransferStatus.Done && file.lastTransferTarget == TransferTarget.Cloud
    val background = when {
        uploaded -> Success
        file.transferStatus.inProgress -> TechBlue
        file.transferStatus == TransferStatus.Failed -> Color(0xFFFF4F73)
        else -> Color.Black.copy(alpha = 0.32f)
    }
    val icon = when {
        uploaded -> Icons.Filled.CloudDone
        file.transferStatus.inProgress -> Icons.Filled.CloudUpload
        else -> if (file.local) Icons.Filled.UploadFile else Icons.Filled.PhoneAndroid
    }
    Box(
        modifier
            .size(24.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
    }
}

@Composable
private fun MediaEmptyState(
    filter: MediaFilter,
    timeFilter: MediaTimeFilter,
    phoneSelected: Boolean,
    loading: Boolean,
    capabilities: DeviceCapabilities
) {
    val colors = PatrolDisplay.colors
    val description = when {
        loading && phoneSelected -> "正在读取手机沙盒中的媒体文件。"
        loading -> "正在读取设备端媒体文件。"
        phoneSelected -> "可从设备端上传到手机，或等待现场采集生成。"
        filter == MediaFilter.Video -> "连接设备 Wi-Fi 后刷新设备文件，识别到视频即可同步到手机端。"
        filter == MediaFilter.Audio && capabilities.supportsAudioRecord ->
            "连接设备 Wi-Fi 后刷新设备文件，识别到录音即可同步到手机端。"
        filter == MediaFilter.Audio -> "当前设备未声明可同步录音能力。"
        filter == MediaFilter.Photo -> "连接设备 Wi-Fi 后刷新设备文件，识别到照片即可同步到手机端。"
        else -> "连接设备 Wi-Fi 后刷新设备文件，识别到媒体即可同步到手机端。"
    }
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
        Text(
            if (loading) "正在加载${if (phoneSelected) "手机端" else "设备端"}文件" else "暂无${timeFilter.emptyPrefix}${filter.label}文件",
            color = colors.text,
            fontSize = 17.sp,
            fontWeight = FontWeight.Black
        )
        Spacer(Modifier.height(6.dp))
        Text(
            description,
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
    thumbnailRequest: suspend (MediaFile) -> MediaContentRequest?,
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
            MediaArtwork(file = file, thumbnailRequest = thumbnailRequest, modifier = Modifier.fillMaxSize())
            MediaKindBadge(file)
            MediaSyncBadge(file = file, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
            if (file.kind != MediaKind.Photo) {
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
                    val playIcon = if (file.kind == MediaKind.Audio) Icons.AutoMirrored.Filled.VolumeUp else Icons.Filled.PlayArrow
                    Icon(playIcon, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
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
                CompactTileProgress(
                    progress = file.progress.safeProgress(),
                    label = mediaStatusLabel(file),
                    modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                )
            }
        }
        Column(Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(file.name, color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "ID: ${file.id}",
                color = colors.textSubtle,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    mediaDateTimeLabel(file.time),
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
        TransferStatus.Done -> Triple(
            if (file.local && file.lastTransferTarget == TransferTarget.Cloud) Icons.Filled.CloudDone else Icons.Filled.PhoneAndroid,
            Success,
            Color.White
        )
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
private fun MediaArtwork(file: MediaFile, loadThumbnail: Boolean = true, thumbnailRequest: suspend (MediaFile) -> MediaContentRequest?, modifier: Modifier) {
    val context = LocalContext.current
    var thumbnail by remember(file.id, file.contentUri, file.kind, file.local) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(file.id, file.contentUri, file.kind, file.local, file.transferStatus, loadThumbnail) {
        thumbnail = null
        if (!loadThumbnail || file.kind == MediaKind.Audio || file.transferStatus.inProgress) {
            return@LaunchedEffect
        }
        val request = thumbnailRequest(file)
        val cacheKey = mediaThumbnailMemoryKey(file, request, MediaGridThumbnailMaxPx)
        getMediaThumbnailFromMemory(cacheKey)?.let { cached ->
            thumbnail = cached
            return@LaunchedEffect
        }
        val loaded = if (file.local) {
            withContext(Dispatchers.IO) { loadMediaThumbnail(context, file.kind, request, MediaGridThumbnailMaxPx) }
        } else {
            loadDeviceMediaThumbnail(context, file.kind, request, MediaGridThumbnailMaxPx)
        }
        if (loaded != null) {
            putMediaThumbnailInMemory(cacheKey, loaded)
        }
        thumbnail = loaded
    }
    val brush = when (file.kind) {
        MediaKind.Video -> Brush.linearGradient(listOf(Color(0xFF17365D), Color(0xFF0C8DBC), Color(0xFF101827)))
        MediaKind.Photo -> Brush.linearGradient(listOf(Color(0xFF4FA7DF), Color(0xFF20314A), Color(0xFF07111F)))
        MediaKind.Audio -> Brush.linearGradient(listOf(Color(0xFFCBC8F7), Color(0xFF8F87A5)))
    }
    val icon = when (file.kind) {
        MediaKind.Video -> Icons.Filled.Videocam
        MediaKind.Photo -> Icons.Filled.Image
        MediaKind.Audio -> Icons.AutoMirrored.Filled.VolumeUp
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
private fun MediaTransferProgress(label: String, progress: Float, modifier: Modifier = Modifier) {
    val colors = PatrolDisplay.colors
    val safe = progress.safeProgress()
    Row(
        modifier
            .height(34.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.control.copy(alpha = if (colors.dark) 0.52f else 0.72f))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            label,
            color = colors.text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        MediaProgressTrack(
            progress = safe,
            height = 7,
            modifier = Modifier.weight(1.25f)
        )
        Text(
            "${(safe * 100).roundToInt()}%",
            color = colors.textMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
    }
}

@Composable
private fun CompactTileProgress(progress: Float, label: String, modifier: Modifier = Modifier) {
    val safe = progress.safeProgress()
    Column(
        modifier
            .widthIn(min = 76.dp, max = 118.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.62f))
            .padding(horizontal = 7.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            label,
            color = Color.White,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        MediaProgressTrack(
            progress = safe,
            height = 4,
            trackColor = Color.White.copy(alpha = 0.24f),
            fillColor = TechBlue,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun MediaProgressTrack(
    progress: Float,
    height: Int,
    modifier: Modifier = Modifier,
    trackColor: Color = PatrolDisplay.colors.control.copy(alpha = 0.62f),
    fillColor: Color = TechBlue
) {
    val safe = progress.safeProgress()
    Box(
        modifier
            .height(height.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(trackColor)
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(safe)
                .clip(RoundedCornerShape(99.dp))
                .background(fillColor)
        )
    }
}

@Composable
private fun FloatingTransferProgress(file: MediaFile, modifier: Modifier = Modifier) {
    val colors = PatrolDisplay.colors
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
            Text(mediaStatusLabel(file), color = colors.textMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        MediaTransferProgress(
            label = "处理进度",
            progress = file.progress.safeProgress(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun FloatingDeviceSyncProgress(sync: DeviceMediaSyncUiState, modifier: Modifier = Modifier) {
    val colors = PatrolDisplay.colors
    val countText = if (sync.totalCount > 0) {
        " · ${sync.completedCount.coerceAtMost(sync.totalCount)}/${sync.totalCount}"
    } else {
        ""
    }
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
            Icon(Icons.Filled.PhoneAndroid, contentDescription = null, tint = TechBlue, modifier = Modifier.size(27.dp))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(sync.fileName.ifBlank { "设备文件同步" }, color = colors.text, fontSize = 16.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${transferLabel(sync.status)}$countText", color = colors.textMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        MediaTransferProgress(
            label = "同步进度",
            progress = sync.progress.safeProgress(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun DeviceSyncProgressDialog(sync: DeviceMediaSyncUiState) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (PatrolDisplay.colors.dark) 0.32f else 0.18f))
                .clickable(enabled = true, onClick = {}),
            contentAlignment = Alignment.Center
        ) {
            FloatingDeviceSyncProgress(sync = sync)
        }
    }
}

@Composable
private fun MediaActionBar(
    primaryText: String,
    primaryIcon: ImageVector,
    onPrimary: () -> Unit,
    onVerify: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = PatrolDisplay.colors
    val barColor = if (colors.dark) Color(0xFF0C1427) else colors.surface
    val borderColor = if (colors.dark) Color.Transparent else colors.border.copy(alpha = 0.9f)
    val dividerColor = if (colors.dark) Color.White.copy(alpha = 0.20f) else colors.border
    val textColor = if (colors.dark) Color.White else colors.text
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(barColor)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionBarItem(primaryText, primaryIcon, Color(0xFF2F80ED), textColor, onPrimary, Modifier.weight(1f))
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
        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(23.dp))
        Spacer(Modifier.height(4.dp))
        Text(text, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun MediaPreviewDialog(
    file: MediaFile,
    contentRequest: suspend (MediaFile) -> MediaContentRequest?,
    primaryText: String,
    primaryIcon: ImageVector,
    onPrimary: () -> Unit,
    onVerify: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
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
                .fillMaxWidth(0.94f)
                .clip(RoundedCornerShape(26.dp))
                .background(colors.surface)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(file.name, color = colors.text, fontSize = 17.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
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
                    .aspectRatio(if (file.kind == MediaKind.Audio) 1.15f else 1f)
                    .clip(RoundedCornerShape(18.dp))
            ) {
                EmbeddedMediaPreview(file = file, contentRequest = contentRequest, modifier = Modifier.fillMaxSize())
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                PreviewMetaPill("${mediaDateTimeLabel(file.time)} · ${file.size}", Modifier.weight(1f))
                PreviewMetaPill(mediaStatusLabel(file), Modifier.weight(0.78f))
            }
            if (file.progress > 0f && file.progress < 1f) {
                MediaTransferProgress(
                    label = mediaStatusLabel(file),
                    progress = file.progress.safeProgress(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            MediaActionBar(
                primaryText = primaryText,
                primaryIcon = primaryIcon,
                onPrimary = onPrimary,
                onVerify = onVerify,
                onDelete = onDelete
            )
        }
    }
}

@Composable
private fun PreviewMetaPill(text: String, modifier: Modifier = Modifier) {
    val colors = PatrolDisplay.colors
    Box(
        modifier
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.control.copy(alpha = 0.78f))
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = colors.textMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
private fun EmbeddedMediaPreview(
    file: MediaFile,
    contentRequest: suspend (MediaFile) -> MediaContentRequest?,
    modifier: Modifier = Modifier
) {
    when (file.kind) {
        MediaKind.Photo -> PhotoPreview(file = file, contentRequest = contentRequest, modifier = modifier)
        MediaKind.Video -> NativeVideoPreview(file = file, contentRequest = contentRequest, modifier = modifier)
        MediaKind.Audio -> PlayableMediaPreview(file = file, contentRequest = contentRequest, modifier = modifier)
    }
}

@Composable
private fun PhotoPreview(
    file: MediaFile,
    contentRequest: suspend (MediaFile) -> MediaContentRequest?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var image by remember(file.id, file.contentUri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(file.id, file.contentUri) {
        val request = contentRequest(file)
        image = withContext(Dispatchers.IO) { loadImageBitmap(context, request?.value ?: file.contentUri, request?.authorization) }
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
private fun NativeVideoPreview(
    file: MediaFile,
    contentRequest: suspend (MediaFile) -> MediaContentRequest?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var thumbnail by remember(file.id, file.contentUri) { mutableStateOf<Bitmap?>(null) }
    var opening by remember(file.id, file.contentUri) { mutableStateOf(false) }
    LaunchedEffect(file.id, file.contentUri) {
        val request = contentRequest(file)
        thumbnail = withContext(Dispatchers.IO) {
            loadVideoCoverFrame(
                context,
                request?.value ?: file.contentUri,
                request?.authorization
            )
        }
    }
    Box(
        modifier
            .background(Color.Black)
            .clickable {
                if (opening) return@clickable
                opening = true
                scope.launch {
                    val request = contentRequest(file)
                    val target = request?.value ?: file.contentUri
                    openNativeMediaViewer(context, file, target)
                    opening = false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(Modifier.fillMaxSize().background(Color.Black))
        }
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.50f))
                .border(1.dp, Color.White.copy(alpha = 0.66f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(44.dp))
        }
    }
}

@Composable
private fun PlayableMediaPreview(
    file: MediaFile,
    contentRequest: suspend (MediaFile) -> MediaContentRequest?,
    modifier: Modifier = Modifier
) {
    var resolvedUri by remember(file.id, file.contentUri) { mutableStateOf(file.contentUri) }
    LaunchedEffect(file.id, file.contentUri) {
        resolvedUri = contentRequest(file)?.value ?: file.contentUri
    }
    val uri = remember(resolvedUri) { resolvedUri?.let(Uri::parse) }
    var playerView by remember(file.id, file.contentUri) { mutableStateOf<TextureMediaPlayerView?>(null) }
    var buffering by remember(file.id, file.contentUri) { mutableStateOf(uri != null) }
    var playing by remember(file.id, file.contentUri) { mutableStateOf(false) }
    var playbackError by remember(file.id, file.contentUri) { mutableStateOf<String?>(null) }
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
                    TextureMediaPlayerView(viewContext).apply {
                        playerView = this
                    }
                },
                update = { player ->
                    playerView = player
                    player.onBuffering = {
                        buffering = true
                        playbackError = null
                    }
                    player.onReady = {
                        buffering = false
                        playing = true
                        playbackError = null
                    }
                    player.onPaused = {
                        playing = false
                    }
                    player.onCompletion = {
                        playing = false
                    }
                    player.onPlaybackError = { message ->
                        buffering = false
                        playing = false
                        playbackError = message
                    }
                    if (player.tag != uri) {
                        player.tag = uri
                        buffering = true
                        playing = false
                        playbackError = null
                        player.setMedia(uri, autoPlay = true)
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
            when {
                playbackError != null -> {
                    PlaybackCenterOverlay(
                        message = playbackError ?: "",
                        onClick = {
                            playbackError = null
                            buffering = true
                            playerView?.play()
                        }
                    )
                }
                buffering -> {
                    PlaybackCenterOverlay(message = "加载中", onClick = {})
                }
                !playing -> {
                    PlaybackCenterOverlay(
                        message = null,
                        onClick = {
                            playbackError = null
                            buffering = true
                            playerView?.play()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxScope.PlaybackCenterOverlay(message: String?, onClick: () -> Unit) {
    Column(
        Modifier
            .align(Alignment.Center)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.46f))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(54.dp))
        message?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = Color.White, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun openNativeMediaViewer(context: Context, file: MediaFile, value: String?): Boolean {
    val shareable = value?.takeIf { it.isNotBlank() }?.toShareableMedia(context, file) ?: return false
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(shareable.uri, shareable.mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (shareable.uri.scheme == "content") {
            clipData = ClipData.newUri(context.contentResolver, file.name, shareable.uri)
        }
    }
    return runCatching {
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}

private data class ShareableMedia(val uri: Uri, val mimeType: String)

private fun String.toShareableMedia(context: Context, file: MediaFile): ShareableMedia? {
    val uri = runCatching { Uri.parse(this) }.getOrNull() ?: return null
    val localFile = when {
        uri.scheme == "file" -> uri.path?.takeIf { it.isNotBlank() }?.let(::File)
        uri.scheme == null && startsWith("/") -> File(this)
        else -> null
    }?.takeIf { it.exists() && it.isFile }
    return if (localFile != null) {
        val mimeType = localFile.detectMediaMimeType() ?: file.viewerMimeType(this)
        val fileForShare = if (file.kind == MediaKind.Photo) {
            localFile.normalizedImageShareFile(context, file, mimeType)
        } else {
            localFile
        }
        ShareableMedia(
            uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", fileForShare),
            mimeType = mimeType
        )
    } else {
        ShareableMedia(uri = uri, mimeType = file.viewerMimeType(this))
    }
}

private fun File.normalizedImageShareFile(context: Context, file: MediaFile, mimeType: String): File {
    val extension = mimeType.toImageExtension()
        ?: extension.takeIf { it.isNotBlank() }?.lowercase(Locale.ROOT)
        ?: "jpg"
    if (extension.equals(this.extension, ignoreCase = true)) return this
    val targetDir = File(context.cacheDir, "media_preview_cache/share").apply { mkdirs() }
    val safeName = file.name
        .substringBeforeLast('.', file.name)
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .ifBlank { file.id }
    val target = File(targetDir, "$safeName-${file.id.sha256Key().take(12)}.$extension")
    if (!target.exists() || target.length() != length()) {
        inputStream().use { input -> target.outputStream().use { output -> input.copyTo(output) } }
    }
    return target
}

private fun String.toImageExtension(): String? = when (this) {
    "image/jpeg" -> "jpg"
    "image/png" -> "png"
    "image/webp" -> "webp"
    "image/gif" -> "gif"
    "image/bmp" -> "bmp"
    else -> null
}

private fun File.detectMediaMimeType(): String? {
    val header = ByteArray(12)
    val read = inputStream().use { it.read(header) }
    if (read < 4) return null
    return when {
        header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte() -> "image/jpeg"
        header[0] == 0x89.toByte() && header[1] == 0x50.toByte() && header[2] == 0x4E.toByte() && header[3] == 0x47.toByte() -> "image/png"
        header[0] == 0x47.toByte() && header[1] == 0x49.toByte() && header[2] == 0x46.toByte() -> "image/gif"
        header[0] == 0x42.toByte() && header[1] == 0x4D.toByte() -> "image/bmp"
        read >= 12 &&
            header[0] == 0x52.toByte() && header[1] == 0x49.toByte() && header[2] == 0x46.toByte() && header[3] == 0x46.toByte() &&
            header[8] == 0x57.toByte() && header[9] == 0x45.toByte() && header[10] == 0x42.toByte() && header[11] == 0x50.toByte() -> "image/webp"
        else -> null
    }
}

private fun MediaFile.viewerMimeType(value: String?): String =
    when (kind) {
        MediaKind.Photo -> value?.extensionMimeType() ?: "image/*"
        MediaKind.Video -> value?.extensionMimeType() ?: "video/*"
        MediaKind.Audio -> value?.extensionMimeType() ?: "audio/*"
    }

private fun String.extensionMimeType(): String? =
    substringBefore('?')
        .substringAfterLast('/', "")
        .substringAfterLast('.', "")
        .lowercase()
        .let { extension ->
            when (extension) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "webp" -> "image/webp"
                "bmp" -> "image/bmp"
                "gif" -> "image/gif"
                "mp4" -> "video/mp4"
                "mov" -> "video/quicktime"
                "m4v" -> "video/mp4"
                "3gp" -> "video/3gpp"
                "opus" -> "audio/ogg"
                "wav" -> "audio/wav"
                "amr" -> "audio/amr"
                "aac" -> "audio/aac"
                else -> null
            }
        }

@Composable
private fun FullscreenMediaPlayer(file: MediaFile, onExitFullscreen: () -> Unit, onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        EmbeddedMediaPreview(file = file, contentRequest = { null }, modifier = Modifier.fillMaxSize())
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

private fun mediaStatusLabel(file: MediaFile): String =
    if (file.local) {
        when (file.transferStatus) {
            TransferStatus.Done -> when (file.lastTransferTarget) {
                TransferTarget.Cloud -> "已上传"
                TransferTarget.PhoneSandbox -> "已同步手机"
                null -> "已保存手机"
            }
            TransferStatus.Idle -> "待上传"
            else -> transferLabel(file.transferStatus)
        }
    } else {
        when (file.transferStatus) {
            TransferStatus.Done -> "已同步手机"
            TransferStatus.Idle -> "待同步手机"
            TransferStatus.Uploading -> "同步中"
            TransferStatus.Hashing -> "校验中"
            TransferStatus.Verifying -> "确认中"
            TransferStatus.Failed -> "同步失败"
        }
    }

private enum class MediaPrimaryAction(val label: String, val icon: ImageVector) {
    UploadCloud("上传云端", Icons.Filled.CloudUpload),
    UploadedCloud("已上传", Icons.Filled.CloudDone),
    SyncPhone("同步到手机", Icons.Filled.PhoneAndroid),
    SyncedPhone("已同步手机", Icons.Filled.PhoneAndroid),
    Busy("处理中", Icons.Filled.CloudUpload)
}

private fun MediaFile.mediaPrimaryAction(): MediaPrimaryAction = when {
    transferStatus.inProgress -> MediaPrimaryAction.Busy
    local && transferStatus == TransferStatus.Done && lastTransferTarget == TransferTarget.Cloud -> MediaPrimaryAction.UploadedCloud
    local -> MediaPrimaryAction.UploadCloud
    transferStatus == TransferStatus.Done && lastTransferTarget == TransferTarget.PhoneSandbox -> MediaPrimaryAction.SyncedPhone
    else -> MediaPrimaryAction.SyncPhone
}

private enum class MediaPreviewAction(val label: String, val icon: ImageVector) {
    Video("本地回放", Icons.Filled.PlayArrow),
    Photo("查看图片", Icons.Filled.Image),
    Audio("播放音频", Icons.AutoMirrored.Filled.VolumeUp)
}

private fun MediaFile.previewAction(): MediaPreviewAction = when (kind) {
    MediaKind.Video -> MediaPreviewAction.Video
    MediaKind.Photo -> MediaPreviewAction.Photo
    MediaKind.Audio -> MediaPreviewAction.Audio
}

private fun mediaDateTimeLabel(value: String): String {
    val raw = value.trim()
    if (raw.isBlank()) return value
    raw.toLongOrNull()?.let { epoch ->
        val millis = if (epoch < 10_000_000_000L) epoch * 1000 else epoch
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(ChineseMediaDateTimeFormatter)
    }
    parseMediaInstant(raw)?.let { instant ->
        return instant.atZone(ZoneId.systemDefault()).format(ChineseMediaDateTimeFormatter)
    }
    return raw
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
    parseMediaInstant(raw)?.let { instant ->
        return instant.atZone(ZoneId.systemDefault()).toLocalDate()
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

private fun parseMediaInstant(raw: String): Instant? {
    val formatters = listOf(
        DateTimeFormatter.ISO_OFFSET_DATE_TIME,
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss zzz yyyy", Locale.US)
    )
    formatters.forEach { formatter ->
        runCatching {
            return when (formatter) {
                DateTimeFormatter.ISO_OFFSET_DATE_TIME -> Instant.from(formatter.parse(raw))
                else -> {
                    val parsed = formatter.parse(raw)
                    runCatching { ZonedDateTime.from(parsed).toInstant() }.getOrElse {
                        java.time.LocalDateTime.from(parsed).atZone(ZoneId.systemDefault()).toInstant()
                    }
                }
            }
        }
    }
    return null
}

private val ChineseMediaDateTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm", Locale.CHINA)

private fun loadMediaThumbnail(context: Context, kind: MediaKind, request: MediaContentRequest?, maxPx: Int = MediaThumbnailMaxPx): Bitmap? =
    when (kind) {
        MediaKind.Photo -> loadImageBitmap(context, request?.value, request?.authorization, maxPx)
        MediaKind.Video -> loadVideoFrame(context, request?.value, request?.authorization, maxPx)
        MediaKind.Audio -> null
    }

private fun mediaThumbnailMemoryKey(file: MediaFile, request: MediaContentRequest?, maxPx: Int): String =
    buildString {
        append(maxPx)
        append(':')
        append(file.kind.name)
        append(':')
        append(if (file.local) "local" else "device")
        append(':')
        append(file.id)
        append(':')
        append(file.contentUri.orEmpty())
        append(':')
        append(request?.value.orEmpty())
    }

private fun getMediaThumbnailFromMemory(cacheKey: String): Bitmap? =
    synchronized(MediaThumbnailMemoryCache) { MediaThumbnailMemoryCache.get(cacheKey) }

private fun putMediaThumbnailInMemory(cacheKey: String, bitmap: Bitmap) {
    synchronized(MediaThumbnailMemoryCache) { MediaThumbnailMemoryCache.put(cacheKey, bitmap) }
}

private suspend fun loadDeviceMediaThumbnail(context: Context, kind: MediaKind, request: MediaContentRequest?, targetSize: Int = MediaThumbnailMaxPx): Bitmap? {
    val value = request?.value?.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: return null
    val cacheKey = "${kind.name}:$targetSize:$value"
    getMediaThumbnailFromMemory(cacheKey)?.let { return it }
    return withContext(Dispatchers.IO) {
        val cachedFile = deviceMediaThumbnailCacheFile(context, cacheKey)
        decodeFileBitmap(cachedFile.absolutePath, targetSize)?.let { cached ->
            cachedFile.setLastModified(System.currentTimeMillis())
            putMediaThumbnailInMemory(cacheKey, cached)
            return@withContext cached
        }
        val limiter = if (kind == MediaKind.Video) DeviceVideoThumbnailLimiter else DevicePhotoThumbnailLimiter
        limiter.withPermit {
            getMediaThumbnailFromMemory(cacheKey)?.let { return@withPermit it }
            decodeFileBitmap(cachedFile.absolutePath, targetSize)?.let { cached ->
                cachedFile.setLastModified(System.currentTimeMillis())
                putMediaThumbnailInMemory(cacheKey, cached)
                return@withPermit cached
            }
            loadRemoteDeviceThumbnail(context, kind, value, request.authorization, targetSize)?.also { loaded ->
                putMediaThumbnailInMemory(cacheKey, loaded)
                runCatching {
                    cachedFile.parentFile?.mkdirs()
                    cachedFile.outputStream().use { output ->
                        loaded.compress(Bitmap.CompressFormat.JPEG, 82, output)
                    }
                }
            }
        }
    }
}

private fun loadRemoteDeviceThumbnail(context: Context, kind: MediaKind, value: String, authorization: String?, maxPx: Int = MediaThumbnailMaxPx): Bitmap? =
    when (kind) {
        MediaKind.Photo -> loadImageBitmap(context, value, authorization, maxPx)
        MediaKind.Video -> loadVideoCoverFrame(context, value, authorization, maxPx)
        MediaKind.Audio -> null
    }

private fun deviceMediaThumbnailCacheFile(context: Context, cacheKey: String): File =
    File(File(context.filesDir, "patrol_media_cache/device_media_thumbnails"), "${cacheKey.sha256Key()}.jpg")

private fun String.sha256Key(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}

internal fun loadImageBitmap(context: Context, value: String?, authorization: String? = null, maxPx: Int = MediaThumbnailMaxPx): Bitmap? {
    val uri = value?.takeIf { it.isNotBlank() }?.let(Uri::parse) ?: return null
    return runCatching {
        when {
            uri.scheme == "content" -> decodeContentBitmap(context, uri, maxPx)
            uri.scheme == "file" -> uri.path?.let { decodeFileBitmap(it, maxPx) }
            value.startsWith("http://") || value.startsWith("https://") -> {
                val connection = (URL(value).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 4_000
                    readTimeout = 6_000
                    authorization?.let { setRequestProperty("Authorization", it) }
                }
                try {
                    val bytes = connection.inputStream.use { input ->
                        input.readBytes(limit = RemoteImageThumbnailMaxBytes)
                    }
                    decodeByteArrayBitmap(bytes, maxPx)
                } finally {
                    connection.disconnect()
                }
            }
            value.startsWith("/") -> decodeFileBitmap(value, maxPx)
            else -> null
        }
    }.getOrNull()
}

private fun decodeContentBitmap(context: Context, uri: Uri, maxPx: Int = MediaThumbnailMaxPx): Bitmap? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        return ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val target = info.size.thumbnailTargetSize(maxPx) ?: return@decodeBitmap
            decoder.setTargetSize(target.first, target.second)
        }
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    val options = BitmapFactory.Options().apply { inSampleSize = bounds.thumbnailSampleSize(maxPx) }
    return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
}

private fun decodeFileBitmap(path: String, maxPx: Int = MediaThumbnailMaxPx): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    val options = BitmapFactory.Options().apply { inSampleSize = bounds.thumbnailSampleSize(maxPx) }
    return BitmapFactory.decodeFile(path, options)
}

private fun decodeByteArrayBitmap(bytes: ByteArray, maxPx: Int = MediaThumbnailMaxPx): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val options = BitmapFactory.Options().apply { inSampleSize = bounds.thumbnailSampleSize(maxPx) }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}

private fun Bitmap.isMostlyBlackFrame(): Boolean {
    if (width <= 0 || height <= 0) return true
    val sampleSize = 12
    var darkPixels = 0
    var sampled = 0
    val stepX = (width / sampleSize).coerceAtLeast(1)
    val stepY = (height / sampleSize).coerceAtLeast(1)
    var y = stepY / 2
    while (y < height) {
        var x = stepX / 2
        while (x < width) {
            val pixel = getPixel(x, y)
            val red = android.graphics.Color.red(pixel)
            val green = android.graphics.Color.green(pixel)
            val blue = android.graphics.Color.blue(pixel)
            if ((red + green + blue) / 3 < 18) darkPixels += 1
            sampled += 1
            x += stepX
        }
        y += stepY
    }
    return sampled > 0 && darkPixels.toFloat() / sampled.toFloat() > 0.92f
}

private fun java.io.InputStream.readBytes(limit: Long): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        total += read
        if (total > limit) error("remote image too large for thumbnail")
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}

private fun android.util.Size.thumbnailTargetSize(maxPx: Int = MediaThumbnailMaxPx): Pair<Int, Int>? {
    val maxDim = maxOf(width, height)
    if (maxDim <= maxPx || width <= 0 || height <= 0 || maxPx <= 0) return null
    val scale = maxPx.toFloat() / maxDim.toFloat()
    return (width * scale).roundToInt().coerceAtLeast(1) to (height * scale).roundToInt().coerceAtLeast(1)
}

private fun BitmapFactory.Options.thumbnailSampleSize(maxPx: Int = MediaThumbnailMaxPx): Int {
    val maxDim = maxOf(outWidth, outHeight)
    if (maxDim <= maxPx || outWidth <= 0 || outHeight <= 0 || maxPx <= 0) return 1
    var sampleSize = 1
    while (maxDim / (sampleSize * 2) >= maxPx) {
        sampleSize *= 2
    }
    return sampleSize
}

private fun loadVideoFrame(context: Context, value: String?, authorization: String? = null, maxPx: Int = MediaThumbnailMaxPx): Bitmap? =
    loadVideoCoverFrame(context, value, authorization, maxPx)

private fun loadVideoCoverFrame(
    context: Context,
    value: String?,
    authorization: String? = null,
    maxPx: Int = MediaThumbnailMaxPx
): Bitmap? {
    val raw = value?.takeIf { it.isNotBlank() } ?: return null
    return runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            val uri = Uri.parse(raw)
            when {
                uri.scheme == "content" || uri.scheme == "file" -> retriever.setDataSource(context, uri)
                raw.startsWith("http://") || raw.startsWith("https://") -> {
                    val headers = authorization?.let { mapOf("Authorization" to it) }.orEmpty()
                    retriever.setDataSource(raw, headers)
                }
                raw.startsWith("/") -> retriever.setDataSource(raw)
                else -> return@runCatching null
            }
            val candidatesUs = longArrayOf(0L, 100_000L, 300_000L, 1_000_000L)
            var cover: Bitmap? = null
            for (timeUs in candidatesUs) {
                cover = retriever.extractCoverFrameAt(timeUs, maxPx)
                    ?.takeUnless { it.isMostlyBlackFrame() }
                if (cover != null) break
            }
            cover
        } finally {
            retriever.release()
        }
    }.getOrNull()
}

private fun MediaMetadataRetriever.extractCoverFrameAt(timeUs: Long, maxPx: Int): Bitmap? {
    val exact = getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
        ?.trimBlackFrameBorder()
        ?.scaledToBounds(maxPx)
    if (exact != null) return exact
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        getScaledFrameAtTime(
            timeUs,
            MediaMetadataRetriever.OPTION_CLOSEST,
            maxPx,
            maxPx
        )?.trimBlackFrameBorder()
    } else {
        getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ?.trimBlackFrameBorder()
            ?.scaledToBounds(maxPx)
    }
}

private fun Bitmap.trimBlackFrameBorder(): Bitmap? {
    if (width <= 0 || height <= 0) return null
    val rowStep = (height / 96).coerceAtLeast(1)
    val colStep = (width / 96).coerceAtLeast(1)
    var left = width
    var top = height
    var right = -1
    var bottom = -1
    var y = 0
    while (y < height) {
        var x = 0
        while (x < width) {
            if (!isDarkPixel(getPixel(x, y))) {
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
            x += colStep
        }
        y += rowStep
    }
    if (right < left || bottom < top) return null

    left = (left - colStep).coerceAtLeast(0)
    top = (top - rowStep).coerceAtLeast(0)
    right = (right + colStep).coerceAtMost(width - 1)
    bottom = (bottom + rowStep).coerceAtMost(height - 1)

    val cropWidth = right - left + 1
    val cropHeight = bottom - top + 1
    val cropAreaRatio = cropWidth.toFloat() * cropHeight.toFloat() / (width.toFloat() * height.toFloat())
    if (cropWidth < width * 0.12f || cropHeight < height * 0.12f || cropAreaRatio < 0.03f) return null
    if (cropWidth == width && cropHeight == height) return this
    return Bitmap.createBitmap(this, left, top, cropWidth, cropHeight)
}

private fun isDarkPixel(pixel: Int): Boolean {
    val red = android.graphics.Color.red(pixel)
    val green = android.graphics.Color.green(pixel)
    val blue = android.graphics.Color.blue(pixel)
    return maxOf(red, green, blue) < 34 && (red + green + blue) / 3 < 24
}

private fun Bitmap.scaledToThumbnailBounds(): Bitmap {
    return scaledToBounds(MediaThumbnailMaxPx)
}

private fun Bitmap.scaledToBounds(maxPx: Int): Bitmap {
    val maxDim = maxOf(width, height)
    if (maxDim <= maxPx || width <= 0 || height <= 0 || maxPx <= 0) return this
    val scale = maxPx.toFloat() / maxDim.toFloat()
    val targetWidth = (width * scale).roundToInt().coerceAtLeast(1)
    val targetHeight = (height * scale).roundToInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
}
