package com.patrollink.presentation.screen

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.patrollink.data.remote.UploadAttachmentDto
import com.patrollink.domain.AlertLevel
import com.patrollink.domain.AlertResult
import com.patrollink.domain.AlertStatus
import com.patrollink.domain.AppUiState
import com.patrollink.presentation.PatrolViewModel
import com.patrollink.presentation.component.AlertLevelTag
import com.patrollink.presentation.component.ForceTopBar
import com.patrollink.presentation.component.FileUploadGrid
import com.patrollink.presentation.component.OfflineBanner
import com.patrollink.presentation.component.PatrolCard
import com.patrollink.presentation.component.PrimaryAction
import com.patrollink.presentation.component.SectionTitle
import com.patrollink.presentation.component.SegmentedTabs
import com.patrollink.presentation.component.SmallInfo
import com.patrollink.presentation.component.StatusTag
import com.patrollink.presentation.component.SystemBars
import com.patrollink.presentation.component.UploadFileItem
import com.patrollink.presentation.component.UploadFileState
import com.patrollink.presentation.theme.Danger
import com.patrollink.presentation.theme.Muted
import com.patrollink.presentation.theme.PatrolDisplay
import com.patrollink.presentation.theme.Success
import com.patrollink.presentation.theme.TechBlue
import com.patrollink.presentation.theme.Warning
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AlertListScreen(
    uiState: AppUiState,
    viewModel: PatrolViewModel,
    onSos: () -> Unit,
    onOpenDetail: (String) -> Unit
) {
    val colors = PatrolDisplay.colors
    SystemBars(statusBarColor = colors.topBar, navigationBarColor = colors.surface, lightStatusBar = !colors.dark, lightNavigationBar = !colors.dark)
    Column(Modifier.fillMaxSize().background(colors.page)) {
        ForceTopBar(dark = false, onSos = onSos)
        OfflineBanner(uiState.networkOnline)
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            SegmentedTabs(
                left = "待处理",
                right = "已处理",
                leftSelected = uiState.selectedAlertTab == AlertStatus.Pending,
                onLeft = { viewModel.setAlertTab(AlertStatus.Pending) },
                onRight = { viewModel.setAlertTab(AlertStatus.Closed) }
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val alerts = uiState.alerts.filter { it.status == uiState.selectedAlertTab }
            if (alerts.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(top = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.NotificationsOff, contentDescription = null, tint = colors.textSubtle, modifier = Modifier.size(52.dp))
                        Text("暂无预警记录", color = colors.textSubtle, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
            items(alerts) { alert ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(13.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.border, RoundedCornerShape(13.dp))
                        .clickable { onOpenDetail(alert.id) }
                ) {
                    Box(
                        Modifier
                            .width(6.dp)
                            .height(158.dp)
                            .background(alertLevelColor(alert.level))
                    )
                    Column(Modifier.padding(16.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                            Column {
                                Text(alert.title.uppercase(), color = colors.text, fontSize = 18.sp, fontWeight = FontWeight.Black)
                                Spacer(Modifier.height(4.dp))
                                AlertLevelTag(alert.level)
                            }
                            Text(alert.time, color = colors.textSubtle, fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text("位置 ${alert.location}", color = colors.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text("来源 ${alert.source}", color = colors.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        PrimaryAction("处置", onClick = { onOpenDetail(alert.id) }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
fun AlertDetailScreen(
    alertId: String,
    uiState: AppUiState,
    viewModel: PatrolViewModel,
    onBack: () -> Unit,
    onSos: () -> Unit
) {
    val colors = PatrolDisplay.colors
    SystemBars(statusBarColor = colors.topBar, navigationBarColor = colors.bottomBar, lightStatusBar = !colors.dark, lightNavigationBar = !colors.dark)
    val alert = uiState.alerts.firstOrNull { it.id == alertId } ?: uiState.alerts.first()
    val context = LocalContext.current
    var selectedResult by remember { mutableStateOf("已处置") }
    var note by remember { mutableStateOf("") }
    var evidenceFiles by remember { mutableStateOf<List<UploadFileItem>>(emptyList()) }
    var leaving by remember { mutableStateOf(false) }
    var showEvidenceSourceDialog by remember { mutableStateOf(false) }
    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }
    val scope = rememberCoroutineScope()
    val leaveDetail = {
        if (!leaving) {
            leaving = true
            scope.launch {
                delay(32)
                onBack()
            }
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            pendingCaptureUri?.let { uri ->
                evidenceFiles = evidenceFiles + newEvidenceFile(context, uri, source = "CAMERA")
            }
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) evidenceFiles = evidenceFiles + newEvidenceFile(context, uri, source = "GALLERY")
    }
    val draftButtonBg = if (colors.dark) colors.surfaceHigh else colors.control
    val draftButtonContent = if (colors.dark) colors.text else colors.textMuted
    val draftButtonBorder = if (colors.dark) colors.border.copy(alpha = 0.9f) else Color.Transparent

    BackHandler(enabled = !showEvidenceSourceDialog, onBack = leaveDetail)

    Column(Modifier.fillMaxSize().background(colors.page)) {
        AlertDetailTopBar(onBack = leaveDetail)
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                CriticalAlertCard(alert)
            }
            item {
                EvidenceSection(
                    files = evidenceFiles,
                    onAdd = { showEvidenceSourceDialog = true },
                    onDelete = { fileId -> evidenceFiles = evidenceFiles.filterNot { it.id == fileId } }
                )
            }
            item {
                ProcessingCard(
                    selectedResult = selectedResult,
                    onSelectResult = { selectedResult = it },
                    note = note,
                    onNoteChange = { note = it }
                )
            }
            item {
                Spacer(Modifier.height(2.dp))
            }
        }
        if (!leaving) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(colors.bottomBar)
                    .drawBehind {
                        val stroke = 1.dp.toPx()
                        drawLine(
                            color = colors.border,
                            start = Offset(0f, stroke / 2f),
                            end = Offset(size.width, stroke / 2f),
                            strokeWidth = stroke
                        )
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(draftButtonBg)
                        .border(1.dp, draftButtonBorder, RoundedCornerShape(12.dp))
                        .clickable { viewModel.saveAlertDraft(alert.id, alertResultFromLabel(selectedResult), note, evidenceFiles.toUploadAttachments()) },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SaveIcon(draftButtonContent)
                        Text("保存草稿", color = draftButtonContent, fontSize = 15.sp, fontWeight = FontWeight.Black)
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(2f)
                        .height(48.dp)
                        .shadow(8.dp, RoundedCornerShape(12.dp), ambientColor = TechBlue.copy(alpha = 0.18f), spotColor = TechBlue.copy(alpha = 0.24f))
                        .clip(RoundedCornerShape(12.dp))
                        .background(TechBlue)
                        .clickable { viewModel.closeAlert(alert.id, alertResultFromLabel(selectedResult), note, evidenceFiles.toUploadAttachments()); leaveDetail() },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        UploadIcon(Color.White)
                        Text("确认上传", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
        if (showEvidenceSourceDialog) {
            EvidenceSourceDialog(
                onDismiss = { showEvidenceSourceDialog = false },
                onCamera = {
                    showEvidenceSourceDialog = false
                    val uri = createEvidenceImageUri(context)
                    pendingCaptureUri = uri
                    cameraLauncher.launch(uri)
                },
                onGallery = {
                    showEvidenceSourceDialog = false
                    galleryLauncher.launch("image/*")
                }
            )
        }
    }
}

@Composable
private fun EvidenceSourceDialog(onDismiss: () -> Unit, onCamera: () -> Unit, onGallery: () -> Unit) {
    val colors = PatrolDisplay.colors
    val dialogBorder = if (colors.dark) colors.border.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.9f)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .fillMaxWidth(0.84f)
                    .shadow(24.dp, RoundedCornerShape(26.dp), ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.22f))
                    .clip(RoundedCornerShape(26.dp))
                    .background(colors.surface)
                    .border(1.dp, dialogBorder, RoundedCornerShape(26.dp))
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(TechBlue.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.AddAPhoto, contentDescription = null, tint = TechBlue, modifier = Modifier.size(27.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("添加现场证据", color = colors.text, fontSize = 21.sp, fontWeight = FontWeight.Black)
                        Text("照片将加入本次处置材料", color = colors.textMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    EvidenceSourceAction(
                        title = "现场拍照",
                        subtitle = "调用相机记录当前画面",
                        icon = Icons.Filled.AddAPhoto,
                        onClick = onCamera
                    )
                    EvidenceSourceAction(
                        title = "本地相册",
                        subtitle = "从手机选择已有图片",
                        icon = Icons.Filled.Image,
                        onClick = onGallery
                    )
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Text("取消", color = colors.textMuted, fontSize = 14.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun EvidenceSourceAction(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val colors = PatrolDisplay.colors
    val actionBg = if (colors.dark) colors.surfaceHigh else Color.White
    Row(
        Modifier
            .fillMaxWidth()
            .height(76.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(actionBg)
            .border(1.dp, colors.border.copy(alpha = 0.9f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(Brush.linearGradient(listOf(TechBlue.copy(alpha = 0.16f), TechBlue.copy(alpha = 0.08f)))),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = TechBlue, modifier = Modifier.size(26.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = colors.text, fontSize = 16.sp, fontWeight = FontWeight.Black)
            Text(subtitle, color = colors.textMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colors.textSubtle, modifier = Modifier.size(22.dp))
    }
}

private fun newEvidenceFile(context: Context, uri: Uri, source: String): UploadFileItem {
    val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
    val extension = when (mimeType) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        else -> "jpg"
    }
    val now = System.currentTimeMillis()
    return UploadFileItem(
        id = "local-$now",
        name = "现场证据_$now.$extension",
        uri = uri,
        mimeType = mimeType,
        sizeBytes = null,
        source = source,
        state = UploadFileState.Pending,
        deletable = true
    )
}

private fun List<UploadFileItem>.toUploadAttachments(): List<UploadAttachmentDto> =
    filter { it.state == UploadFileState.Pending }.map { file ->
        UploadAttachmentDto(
            clientFileId = file.id,
            fileName = file.name,
            mimeType = file.mimeType,
            sizeBytes = file.sizeBytes,
            source = file.source,
            localUri = file.uri?.toString()
        )
    }

private fun createEvidenceImageUri(context: Context): Uri {
    val dir = File(context.cacheDir, "evidence").apply { mkdirs() }
    val file = File(dir, "evidence-${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

@Composable
private fun AlertDetailTopBar(onBack: () -> Unit) {
    val colors = PatrolDisplay.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.topBar)
            .height(56.dp)
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawLine(
                    color = colors.border.copy(alpha = 0.45f),
                    start = Offset(0f, size.height - stroke / 2f),
                    end = Offset(size.width, size.height - stroke / 2f),
                    strokeWidth = stroke
                )
            }
            .padding(start = 18.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = TechBlue, modifier = Modifier.clickable(onClick = onBack))
        Spacer(Modifier.width(12.dp))
        Text("预警详情与处置", color = TechBlue, fontSize = 19.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun CriticalAlertCard(alert: com.patrollink.domain.AlertItem) {
    val colors = PatrolDisplay.colors
    val levelColor = alertLevelColor(alert.level)
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(16.dp), ambientColor = Color.Black.copy(alpha = 0.06f), spotColor = Color.Black.copy(alpha = 0.08f))
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .background(levelColor)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(levelLabel(alert.level), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
            Text("编号：${alert.id.takeLast(6)}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(Color.White.copy(alpha = 0.2f)).padding(horizontal = 10.dp, vertical = 4.dp))
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 19.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(alert.title, color = colors.text, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Text("位置  ${alert.location}", color = colors.textMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.size(58.dp).clip(RoundedCornerShape(15.dp)).background(levelColor.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                    GroupIcon(levelColor)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AlertMetricBox("触发时间", detailTime(alert.time), Modifier.weight(1f))
                AlertMetricBox("置信度评分", "${alert.confidence} 匹配", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AlertMetricBox(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = PatrolDisplay.colors
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceHigh)
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .height(78.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(label, color = colors.textSubtle, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(value, color = colors.text, fontSize = 12.sp, fontWeight = FontWeight.Black, lineHeight = 16.sp)
    }
}

@Composable
private fun EvidenceSection(files: List<UploadFileItem>, onAdd: () -> Unit, onDelete: (String) -> Unit) {
    val colors = PatrolDisplay.colors
    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                VideoCameraIcon(TechBlue)
                Text("实时证据", color = colors.text, fontSize = 17.sp, fontWeight = FontWeight.Black)
            }
            Row(
                modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(Color(0xFFFFE3E5)).padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Filled.FiberManualRecord, contentDescription = null, tint = Color(0xFFE11D2E), modifier = Modifier.size(8.dp))
                Text("录制中", color = Color(0xFFE11D2E), fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.8f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0F172A))
                .border(1.dp, Color(0xFF111827), RoundedCornerShape(16.dp))
        ) {
            SurveillanceFrame(Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC0F172A)))))
            Text("通道04", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.BottomStart).padding(14.dp).clip(RoundedCornerShape(5.dp)).background(Color.Black.copy(alpha = 0.72f)).padding(horizontal = 10.dp, vertical = 6.dp))
            Text("14:32:44", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.BottomStart).padding(start = 78.dp, bottom = 14.dp).clip(RoundedCornerShape(5.dp)).background(Color.Black.copy(alpha = 0.72f)).padding(horizontal = 10.dp, vertical = 6.dp))
            Box(Modifier.align(Alignment.CenterEnd).padding(end = 14.dp).size(44.dp).clip(RoundedCornerShape(99.dp)).background(Color.White.copy(alpha = 0.86f)), contentAlignment = Alignment.Center) {
                PlayIcon(Color(0xFF111827))
            }
        }
        FileUploadGrid(files = files, onAdd = onAdd, onDelete = onDelete)
    }
}

@Composable
private fun ProcessingCard(
    selectedResult: String,
    onSelectResult: (String) -> Unit,
    note: String,
    onNoteChange: (String) -> Unit
) {
    val colors = PatrolDisplay.colors
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(16.dp), ambientColor = Color.Black.copy(alpha = 0.04f), spotColor = Color.Black.copy(alpha = 0.06f))
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Text("处置结果", color = colors.textSubtle, fontSize = 13.sp, fontWeight = FontWeight.Black)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ResultChip("已处置", selectedResult == "已处置", onSelectResult, Modifier.weight(1f))
            ResultChip("误报", selectedResult == "误报", onSelectResult, Modifier.weight(1f))
            ResultChip("请求增援", selectedResult == "请求增援", onSelectResult, Modifier.weight(1f))
        }
        Text("处置备注", color = colors.textSubtle, fontSize = 13.sp, fontWeight = FontWeight.Black)
        Box(
            Modifier
                .fillMaxWidth()
                .height(104.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surfaceHigh)
                .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            if (note.isEmpty()) {
                Text("请输入核实情况及处置措施...", color = colors.textSubtle, fontSize = 13.sp)
            }
            BasicTextField(
                value = note,
                onValueChange = onNoteChange,
                textStyle = TextStyle(color = colors.text, fontSize = 13.sp, lineHeight = 21.sp),
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun ResultChip(text: String, selected: Boolean, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = PatrolDisplay.colors
    Box(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) TechBlue.copy(alpha = 0.12f) else colors.surfaceHigh)
            .border(1.dp, if (selected) TechBlue else colors.border, RoundedCornerShape(12.dp))
            .clickable { onSelect(text) },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) TechBlue else colors.textMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )
    }
}

private fun levelLabel(level: AlertLevel): String = when (level) {
    AlertLevel.Critical -> "严重预警"
    AlertLevel.Warning -> "风险预警"
    AlertLevel.Info -> "信息预警"
}

private fun alertLevelColor(level: AlertLevel): Color = when (level) {
    AlertLevel.Critical -> Color(0xFFDC2626)
    AlertLevel.Warning -> Color(0xFFF97316)
    AlertLevel.Info -> TechBlue
}

private fun alertResultFromLabel(label: String): AlertResult = when (label) {
    "误报" -> AlertResult.FalseAlarm
    "请求增援" -> AlertResult.RequestBackup
    else -> AlertResult.Resolved
}

private fun detailTime(time: String): String =
    if (time.length <= 5) "2023-10-24 $time:05" else time

@Composable
private fun GroupIcon(color: Color) {
    Icon(Icons.Filled.Groups, contentDescription = null, tint = color, modifier = Modifier.size(32.dp))
}

@Composable
private fun VideoCameraIcon(color: Color) {
    Icon(Icons.Filled.Videocam, contentDescription = null, tint = color, modifier = Modifier.size(26.dp))
}

@Composable
private fun PlayIcon(color: Color) {
    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
}

@Composable
private fun CameraPlusIcon(color: Color) {
    Icon(Icons.Filled.AddAPhoto, contentDescription = null, tint = color, modifier = Modifier.size(34.dp))
}

@Composable
private fun SaveIcon(color: Color) {
    Icon(Icons.Filled.Save, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
}

@Composable
private fun UploadIcon(color: Color) {
    Icon(Icons.Filled.CloudUpload, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
}

@Composable
private fun SurveillanceFrame(modifier: Modifier = Modifier) {
    Box(
        modifier.background(
            Brush.linearGradient(
                listOf(
                    Color(0xFF0B1728),
                    Color(0xFF12395C),
                    Color(0xFF0B1220)
                )
            )
        )
    ) {
        Row(Modifier.fillMaxSize()) {
            repeat(6) { index ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .background(if (index % 2 == 0) Color.White.copy(alpha = 0.04f) else Color.Transparent)
                )
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(46.dp)
                .align(Alignment.TopCenter)
                .background(Color.White.copy(alpha = 0.05f))
        ) {
            repeat(7) {
                Box(Modifier.weight(1f).height(46.dp).border(0.5.dp, Color.White.copy(alpha = 0.04f)))
            }
        }
        Row(Modifier.fillMaxWidth().align(Alignment.Center), horizontalArrangement = Arrangement.SpaceEvenly) {
            repeat(5) {
                Box(Modifier.width(12.dp).height(128.dp).background(Color(0xFF155B91).copy(alpha = 0.72f)))
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(58.dp)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD07101F))))
        )
    }
}

@Composable
private fun EvidenceThumbBackground(variant: Int, modifier: Modifier = Modifier) {
    val colors = if (variant == 0) {
        listOf(Color(0xFFFF7A18), Color(0xFF7A1FB8), Color(0xFF051225))
    } else {
        listOf(Color(0xFF4B5563), Color(0xFF111827), Color(0xFF020617))
    }
    Box(modifier.background(Brush.linearGradient(colors))) {
        if (variant == 0) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .align(Alignment.BottomCenter)
                    .background(Color(0xFFFFD36A).copy(alpha = 0.75f))
            )
        } else {
            Box(
                Modifier
                    .size(44.dp)
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Text("核", color = Color.White.copy(alpha = 0.86f), fontSize = 18.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}
