package com.patrollink.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.patrollink.domain.AppUiState
import com.patrollink.domain.DailyReport
import com.patrollink.domain.MediaFile
import com.patrollink.domain.MediaKind
import com.patrollink.presentation.PatrolViewModel
import com.patrollink.presentation.component.PatrolCard
import com.patrollink.presentation.component.StatusTag
import com.patrollink.presentation.component.SystemBars
import com.patrollink.presentation.theme.Danger
import com.patrollink.presentation.theme.PatrolDisplay
import com.patrollink.presentation.theme.PatrolTextStyle
import com.patrollink.presentation.theme.Success
import com.patrollink.presentation.theme.TechBlue
import com.patrollink.presentation.theme.Warning
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DailyReportScreen(uiState: AppUiState, viewModel: PatrolViewModel) {
    val colors = PatrolDisplay.colors
    val reportState = uiState.dailyReport
    val reportableMedia = uiState.mediaFiles.filter { it.kind == MediaKind.Video || it.kind == MediaKind.Audio }
    val videoCount = reportableMedia.count { it.kind == MediaKind.Video }
    val audioCount = reportableMedia.count { it.kind == MediaKind.Audio }
    val selectedCount = reportState.selectedMediaIds.takeIf { it.isNotEmpty() }?.size ?: reportableMedia.size
    val reportStatus = when {
        reportState.generating -> "生成中"
        reportState.report?.requiresHumanConfirmation == true -> "待复核"
        reportState.report != null -> "已生成"
        else -> "草稿未生成"
    }
    var mediaExpanded by rememberSaveable { mutableStateOf(false) }
    var settingsExpanded by rememberSaveable { mutableStateOf(false) }
    SystemBars(statusBarColor = colors.topBar, navigationBarColor = colors.bottomBar, lightStatusBar = !colors.dark, lightNavigationBar = !colors.dark)

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(colors.page),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("执勤日报", color = colors.text, style = PatrolTextStyle.PageTitle)
                    Text(todayText(), color = colors.textMuted, style = PatrolTextStyle.BodySmall.copy(fontWeight = FontWeight.Bold))
                }
                ReportStatusPill(reportStatus, if (reportState.lastError != null) Danger else if (reportState.report != null) Success else TechBlue)
            }
        }

        item {
            PatrolCard(radius = 12, padding = PaddingValues(15.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(TechBlue.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, tint = TechBlue, modifier = Modifier.size(24.dp))
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("今日执勤素材", color = colors.text, style = PatrolTextStyle.CardTitle)
                            Text(
                                if (reportState.selectedMediaIds.isEmpty()) "默认分析今日全部音视频" else "已手动选择 $selectedCount 个素材",
                                color = colors.textMuted,
                                style = PatrolTextStyle.BodySmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        TextButton(onClick = { mediaExpanded = !mediaExpanded }) {
                            Text(if (mediaExpanded) "收起" else "更改", color = TechBlue, fontWeight = FontWeight.Black)
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
                    ReportMetricRow(videoCount = videoCount, audioCount = audioCount, eventCount = uiState.deviceEvents.size)
                    if (mediaExpanded) {
                        ReportMediaSelector(
                            files = reportableMedia,
                            selectedIds = reportState.selectedMediaIds,
                            onToggle = viewModel::toggleDailyReportMedia,
                            onClear = viewModel::clearDailyReportMediaSelection
                        )
                    }
                }
            }
        }

        item {
            PatrolCard(radius = 12, padding = PaddingValues(15.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    ReportNoteField(
                        value = reportState.operatorNote,
                        placeholder = "例如：重点巡逻商业街、学校周边，发现两处占道经营已劝离。",
                        onValueChange = viewModel::updateDailyReportOperatorNote
                    )
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(colors.control.copy(alpha = 0.58f)).padding(horizontal = 11.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (reportState.missionId.isBlank()) "任务编号将自动生成" else "任务编号：${reportState.missionId}",
                            color = colors.textMuted,
                            style = PatrolTextStyle.BodySmall.copy(fontWeight = FontWeight.Black),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { settingsExpanded = !settingsExpanded }) {
                            Text(if (settingsExpanded) "收起" else "更多设置", color = TechBlue, fontWeight = FontWeight.Black)
                        }
                    }
                    if (settingsExpanded) {
                        ReportTextField(
                            label = "任务编号",
                            value = reportState.missionId,
                            placeholder = "留空时按警号和日期自动生成",
                            singleLine = true,
                            onValueChange = viewModel::updateDailyReportMissionId
                        )
                        Text("接口：POST /api/v1/llm/report", color = colors.textSubtle, style = PatrolTextStyle.Caption)
                    }
                    Button(
                        onClick = viewModel::generateDailyReport,
                        enabled = !reportState.generating,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TechBlue, disabledContainerColor = colors.control)
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(
                            if (reportState.generating) "生成中" else "生成日报",
                            modifier = Modifier.padding(start = 8.dp),
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp
                        )
                    }
                    if (reportState.generating) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(99.dp)),
                            color = TechBlue,
                            trackColor = colors.control
                        )
                    }
                    ReportReviewHint()
                }
            }
        }

        reportState.lastError?.let { error ->
            item { ReportStatusCard(error) }
        }

        reportState.report?.let { report ->
            item {
                ReportResultCard(
                    report = report,
                    contentSaving = reportState.contentSaving,
                    onContentChange = viewModel::updateDailyReportContent,
                    onSaveContent = viewModel::saveDailyReportContent
                )
            }
        } ?: item {
            ReportEmptyPreview()
        }
    }
}

private fun todayText(): String =
    SimpleDateFormat("yyyy-MM-dd EEEE", Locale.CHINA).format(Date())

@Composable
private fun ReportStatusPill(text: String, color: Color) {
    val colors = PatrolDisplay.colors
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = if (colors.dark) 0.16f else 0.08f))
            .border(1.dp, color.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Text(text, color = color, style = PatrolTextStyle.BodySmall.copy(fontWeight = FontWeight.Black), maxLines = 1)
    }
}

@Composable
private fun ReportMetricRow(videoCount: Int, audioCount: Int, eventCount: Int) {
    Row(
        Modifier.fillMaxWidth().padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ReportMetricItem("视频", videoCount.toString(), Icons.Filled.Videocam, TechBlue, Modifier.weight(1f))
        ReportMetricDivider()
        ReportMetricItem("录音", audioCount.toString(), Icons.Filled.Mic, Success, Modifier.weight(1f))
        ReportMetricDivider()
        ReportMetricItem("事件", eventCount.toString(), Icons.Filled.Error, Warning, Modifier.weight(1f))
    }
}

@Composable
private fun ReportMetricItem(label: String, value: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
    val colors = PatrolDisplay.colors
    Column(
        modifier
            .height(82.dp)
            .padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Text(label, color = colors.textMuted, style = PatrolTextStyle.BodySmall.copy(fontWeight = FontWeight.Black), maxLines = 1)
        }
        Text(value, color = colors.text, fontSize = 31.sp, lineHeight = 36.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun ReportMetricDivider() {
    Box(Modifier.width(1.dp).height(52.dp).background(PatrolDisplay.colors.border))
}

@Composable
private fun ReportReviewHint() {
    val colors = PatrolDisplay.colors
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = TechBlue, modifier = Modifier.size(18.dp))
        Text("AI 生成需人工复核后入库，生成后可编辑正文并保存", color = colors.textMuted, style = PatrolTextStyle.Caption)
    }
}

@Composable
private fun ReportEmptyPreview() {
    val colors = PatrolDisplay.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(colors.control.copy(alpha = 0.64f)), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, tint = colors.textSubtle, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("生成后将在这里显示正文预览", color = colors.text, style = PatrolTextStyle.BodyStrong, maxLines = 1)
            Text("日报正文支持继续编辑并保存", color = colors.textMuted, style = PatrolTextStyle.Caption, maxLines = 1)
        }
    }
}

@Composable
private fun ReportMediaSelector(
    files: List<MediaFile>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    onClear: () -> Unit
) {
    val colors = PatrolDisplay.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                Text("素材明细", color = colors.text, style = PatrolTextStyle.BodyStrong)
                Text(
                    if (selectedIds.isEmpty()) "不勾选时默认分析今日全部音视频" else "已选择 ${selectedIds.size} 个素材",
                    color = colors.textSubtle,
                    style = PatrolTextStyle.Caption
                )
            }
            if (selectedIds.isNotEmpty()) {
                Button(
                    onClick = onClear,
                    shape = RoundedCornerShape(9.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.control)
                ) {
                    Text("清空", color = colors.text, style = PatrolTextStyle.BodyStrong)
                }
            }
        }
        if (files.isEmpty()) {
            Text("暂无可选视频或录音", color = colors.textMuted, style = PatrolTextStyle.BodySmall)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                files.take(8).forEach { file ->
                    ReportMediaOption(
                        file = file,
                        selected = file.id in selectedIds,
                        onClick = { onToggle(file.id) }
                    )
                }
                if (files.size > 8) {
                    Text("还有 ${files.size - 8} 个素材将按默认规则参与分析", color = colors.textSubtle, style = PatrolTextStyle.Caption)
                }
            }
        }
    }
}

@Composable
private fun ReportMediaOption(file: MediaFile, selected: Boolean, onClick: () -> Unit) {
    val colors = PatrolDisplay.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) TechBlue.copy(alpha = if (colors.dark) 0.18f else 0.08f) else colors.control.copy(alpha = 0.55f))
            .border(1.dp, if (selected) TechBlue.copy(alpha = 0.55f) else colors.border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = { onClick() },
            colors = CheckboxDefaults.colors(checkedColor = TechBlue)
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(file.name, color = colors.text, style = PatrolTextStyle.BodyStrong)
            Text("${mediaKindLabel(file.kind)} · ${file.time} · ${file.size}", color = colors.textSubtle, style = PatrolTextStyle.Caption)
        }
        StatusTag(if (file.local) "手机" else "设备", if (file.local) Success else TechBlue)
    }
}

private fun mediaKindLabel(kind: MediaKind): String = when (kind) {
    MediaKind.Video -> "视频"
    MediaKind.Photo -> "图片"
    MediaKind.Audio -> "录音"
}

@Composable
private fun ReportNoteField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit
) {
    val colors = PatrolDisplay.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("人工补充说明", color = colors.text, style = PatrolTextStyle.CardTitle)
        Box {
            OutlinedTextField(
                value = value,
                onValueChange = { onValueChange(it.take(300)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(placeholder, color = colors.textSubtle, lineHeight = 24.sp) },
                minLines = 4,
                maxLines = 5,
                shape = RoundedCornerShape(10.dp),
                textStyle = PatrolTextStyle.Body.copy(color = colors.text, lineHeight = 24.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TechBlue,
                    unfocusedBorderColor = colors.border,
                    focusedContainerColor = colors.surface,
                    unfocusedContainerColor = colors.surface,
                    cursorColor = TechBlue
                )
            )
            Text(
                "${value.length.coerceAtMost(300)} / 300",
                color = colors.textSubtle,
                style = PatrolTextStyle.BodySmall.copy(fontWeight = FontWeight.Black),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 12.dp)
            )
        }
    }
}

@Composable
private fun ReportTextField(
    label: String,
    value: String,
    placeholder: String,
    singleLine: Boolean,
    onValueChange: (String) -> Unit
) {
    val colors = PatrolDisplay.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label, fontWeight = FontWeight.Bold) },
        placeholder = { Text(placeholder, color = colors.textSubtle) },
        minLines = if (singleLine) 1 else 4,
        maxLines = if (singleLine) 1 else 6,
        singleLine = singleLine,
        shape = RoundedCornerShape(10.dp),
        textStyle = PatrolTextStyle.Body.copy(color = colors.text),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = TechBlue,
            unfocusedBorderColor = colors.border,
            focusedContainerColor = colors.surface,
            unfocusedContainerColor = colors.surface,
            cursorColor = TechBlue,
            focusedLabelColor = TechBlue,
            unfocusedLabelColor = colors.textMuted
        )
    )
}

@Composable
private fun ReportStatusCard(error: String) {
    val colors = PatrolDisplay.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Danger.copy(alpha = if (colors.dark) 0.18f else 0.09f))
            .border(1.dp, Danger.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Filled.Error, contentDescription = null, tint = Danger, modifier = Modifier.size(22.dp))
        Text(error, color = colors.text, style = PatrolTextStyle.BodyStrong, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ReportResultCard(
    report: DailyReport,
    contentSaving: Boolean,
    onContentChange: (String) -> Unit,
    onSaveContent: () -> Unit
) {
    val colors = PatrolDisplay.colors
    var editing by rememberSaveable(report.reportId, report.missionId) { mutableStateOf(false) }
    PatrolCard(radius = 12, padding = PaddingValues(15.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Success.copy(alpha = 0.11f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, tint = Success, modifier = Modifier.size(22.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("日报正文预览", color = colors.text, style = PatrolTextStyle.CardTitle)
                    Text("生成时间：${report.generatedAt}", color = colors.textMuted, style = PatrolTextStyle.Caption, maxLines = 1)
                }
                StatusTag(if (report.requiresHumanConfirmation) "待复核" else "已生成", if (report.requiresHumanConfirmation) TechBlue else Success, filled = !report.requiresHumanConfirmation)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
            if (editing) {
                OutlinedTextField(
                    value = report.content,
                    onValueChange = onContentChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("日报正文（可编辑）", fontWeight = FontWeight.Bold) },
                    minLines = 8,
                    maxLines = 18,
                    shape = RoundedCornerShape(10.dp),
                    textStyle = PatrolTextStyle.Body.copy(color = colors.text, lineHeight = 23.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TechBlue,
                        unfocusedBorderColor = colors.border,
                        focusedContainerColor = colors.surface,
                        unfocusedContainerColor = colors.surface,
                        cursorColor = TechBlue,
                        focusedLabelColor = TechBlue,
                        unfocusedLabelColor = colors.textMuted
                    )
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { editing = false },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.control)
                    ) {
                        Text("收起预览", color = colors.textMuted, fontWeight = FontWeight.Black)
                    }
                    Button(
                        onClick = onSaveContent,
                        enabled = !contentSaving,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TechBlue, disabledContainerColor = colors.control)
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(if (contentSaving) "保存中" else "保存正文", modifier = Modifier.padding(start = 7.dp), fontWeight = FontWeight.Black)
                    }
                }
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surfaceHigh.copy(alpha = if (colors.dark) 0.62f else 1f))
                        .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    Text(
                        report.content.ifBlank { "暂无正文内容" },
                        color = colors.text,
                        style = PatrolTextStyle.Body.copy(lineHeight = 22.sp),
                        maxLines = 7,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = TechBlue, modifier = Modifier.size(17.dp))
                        Text(
                            if (report.requiresHumanConfirmation) "请复核后入库" else "正文已生成，可继续编辑",
                            color = colors.textMuted,
                            style = PatrolTextStyle.Caption,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Button(
                        onClick = { editing = true },
                        modifier = Modifier.height(40.dp),
                        shape = RoundedCornerShape(9.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.control)
                    ) {
                        Text("编辑正文", color = colors.text, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}
