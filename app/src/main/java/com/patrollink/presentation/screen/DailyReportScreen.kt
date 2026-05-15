package com.patrollink.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@Composable
fun DailyReportScreen(uiState: AppUiState, viewModel: PatrolViewModel) {
    val colors = PatrolDisplay.colors
    val reportState = uiState.dailyReport
    SystemBars(statusBarColor = colors.topBar, navigationBarColor = colors.bottomBar, lightStatusBar = !colors.dark, lightNavigationBar = !colors.dark)

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(colors.page),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("执勤日报", color = colors.text, style = PatrolTextStyle.PageTitle)
                    Text("小脑本地报告草稿", color = colors.textMuted, style = PatrolTextStyle.BodySmall)
                }
                Box(
                    Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(TechBlue.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, tint = TechBlue, modifier = Modifier.size(27.dp))
                }
            }
        }

        item {
            PatrolCard(radius = 12) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("生成参数", color = colors.text, style = PatrolTextStyle.CardTitle)
                            Text("接口：POST /api/v1/llm/report", color = colors.textSubtle, style = PatrolTextStyle.Caption)
                        }
                        StatusTag("DAILY", TechBlue)
                    }
                    ReportTextField(
                        label = "任务编号",
                        value = reportState.missionId,
                        placeholder = "留空时按警号和日期自动生成",
                        singleLine = true,
                        onValueChange = viewModel::updateDailyReportMissionId
                    )
                    ReportTextField(
                        label = "人工补充说明",
                        value = reportState.operatorNote,
                        placeholder = "例如：今日重点巡逻商业街和学校周边",
                        singleLine = false,
                        onValueChange = viewModel::updateDailyReportOperatorNote
                    )
                    ReportMediaSelector(
                        files = uiState.mediaFiles.filter { it.kind == MediaKind.Video || it.kind == MediaKind.Audio },
                        selectedIds = reportState.selectedMediaIds,
                        onToggle = viewModel::toggleDailyReportMedia,
                        onClear = viewModel::clearDailyReportMediaSelection
                    )
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
                Text("分析媒体", color = colors.text, style = PatrolTextStyle.BodyStrong)
                Text(
                    if (selectedIds.isEmpty()) "未选择时默认分析今天全部视频音轨和录音" else "已选择 ${selectedIds.size} 个音频来源",
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
                files.take(12).forEach { file ->
                    ReportMediaOption(
                        file = file,
                        selected = file.id in selectedIds,
                        onClick = { onToggle(file.id) }
                    )
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
    PatrolCard(radius = 12, padding = PaddingValues(0.dp)) {
        Column {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                        Text(report.missionId, color = colors.text, style = PatrolTextStyle.CardTitle)
                        Text("${report.backend} · ${report.model}", color = colors.textMuted, style = PatrolTextStyle.BodySmall)
                    }
                    StatusTag("已生成", Success, filled = true)
                }
                Text("生成时间：${report.generatedAt}", color = colors.textSubtle, style = PatrolTextStyle.Caption)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
            OutlinedTextField(
                value = report.content,
                onValueChange = onContentChange,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                label = { Text("日报正文（可编辑）", fontWeight = FontWeight.Bold) },
                minLines = 16,
                maxLines = 32,
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
            Button(
                onClick = onSaveContent,
                enabled = !contentSaving,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TechBlue, disabledContainerColor = colors.control)
            ) {
                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(19.dp))
                Text(
                    if (contentSaving) "保存中" else "保存正文",
                    modifier = Modifier.padding(start = 8.dp),
                    fontWeight = FontWeight.Black
                )
            }
            if (report.requiresHumanConfirmation) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(TechBlue.copy(alpha = if (colors.dark) 0.18f else 0.08f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = TechBlue, modifier = Modifier.size(21.dp))
                    Text("AI 生成日报需执勤人员复核后入库", color = colors.text, style = PatrolTextStyle.BodyStrong)
                }
            }
        }
    }
}
