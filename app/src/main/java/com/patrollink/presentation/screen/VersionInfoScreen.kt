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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrollink.domain.AppUiState
import com.patrollink.domain.VersionUpdatePhase
import com.patrollink.domain.VersionUpdateUiState
import com.patrollink.presentation.PatrolViewModel
import com.patrollink.presentation.component.PrimaryAction
import com.patrollink.presentation.component.SystemBars
import com.patrollink.presentation.theme.PatrolDisplay
import com.patrollink.presentation.theme.PatrolTextSize
import com.patrollink.presentation.theme.TechBlue

@Composable
fun VersionInfoScreen(uiState: AppUiState, viewModel: PatrolViewModel, onBack: () -> Unit) {
    val colors = PatrolDisplay.colors
    SystemBars(statusBarColor = colors.topBar, navigationBarColor = colors.bottomBar, lightStatusBar = !colors.dark, lightNavigationBar = !colors.dark)
    var contentDialog by remember { mutableStateOf<VersionContent?>(null) }
    val updateState = uiState.versionUpdate

    Box(Modifier.fillMaxSize().background(colors.page)) {
        Column(Modifier.fillMaxSize()) {
            VersionTopBar(onBack = onBack)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.surfaceHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            VersionIcon("shield", TechBlue, Modifier.size(48.dp))
                        }
                        Spacer(Modifier.height(28.dp))
                        Text("执法链路", color = colors.text, fontSize = 34.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "版本 v${updateState.currentVersionName}",
                            color = colors.text,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(colors.control)
                                .padding(horizontal = 20.dp, vertical = 7.dp)
                        )
                    }
                }

                item {
                    PrimaryAction(
                        text = if (updateState.phase == VersionUpdatePhase.Checking) "检查中" else "检查更新",
                        onClick = { viewModel.checkVersionUpdate() },
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    )
                }

                item {
                    VersionRowCard(
                        icon = "logs",
                        title = "版本日志",
                        subtitle = "查看历史更新记录",
                        onClick = { contentDialog = VersionContent.Logs }
                    )
                }

                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        VersionTile("privacy", "隐私政策", Modifier.weight(1f), onClick = { contentDialog = VersionContent.Privacy })
                        VersionTile("agreement", "用户协议", Modifier.weight(1f), onClick = { contentDialog = VersionContent.Agreement })
                    }
                }

                item {
                    Column(
                        Modifier.padding(top = 16.dp, bottom = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("战术系统节点：${uiState.user.systemNode}", color = colors.textSubtle, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                        Text("© 2024 哨兵核心系统", color = colors.textMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            MiniSeal("认证")
                            MiniSeal("安全")
                        }
                    }
                }
            }
        }

        if (updateState.phase in setOf(VersionUpdatePhase.Available, VersionUpdatePhase.Downloading, VersionUpdatePhase.Ready, VersionUpdatePhase.UpToDate, VersionUpdatePhase.Failed)) {
            NewVersionDialog(
                uiState = uiState,
                onUpdate = viewModel::installVersionUpdate,
                onLater = viewModel::dismissVersionUpdate
            )
        }
        contentDialog?.let { dialog ->
            VersionContentDialog(content = dialog, update = updateState, onDismiss = { contentDialog = null })
        }
    }
}

@Composable
private fun VersionTopBar(onBack: () -> Unit) {
    val colors = PatrolDisplay.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(colors.topBar)
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawLine(
                    color = colors.border.copy(alpha = 0.45f),
                    start = Offset(0f, size.height - stroke / 2f),
                    end = Offset(size.width, size.height - stroke / 2f),
                    strokeWidth = stroke
                )
            }
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
            VersionIcon("back", TechBlue, Modifier.size(28.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text("版本信息", color = TechBlue, fontSize = 22.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun VersionRowCard(icon: String, title: String, subtitle: String, onClick: () -> Unit) {
    val colors = PatrolDisplay.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceHigh)
            .clickable(onClick = onClick)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(colors.control),
            contentAlignment = Alignment.Center
        ) {
            VersionIcon(icon, Color(0xFFC4D2FF), Modifier.size(30.dp))
        }
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = colors.text, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Text(subtitle, color = colors.textMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color(0xFFC4D2FF), modifier = Modifier.size(32.dp))
    }
}

@Composable
private fun VersionTile(icon: String, title: String, modifier: Modifier, onClick: () -> Unit) {
    val colors = PatrolDisplay.colors
    Column(
        modifier
            .height(156.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceHigh)
            .clickable(onClick = onClick)
            .padding(22.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        VersionIcon(icon, Color(0xFFC4D2FF), Modifier.size(34.dp))
        Text(title, color = colors.text, fontSize = 17.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun MiniSeal(text: String) {
    val colors = PatrolDisplay.colors
    Box(
        Modifier
            .size(40.dp, 32.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(colors.control),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = colors.textSubtle, fontSize = 8.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun NewVersionDialog(uiState: AppUiState, onUpdate: () -> Unit, onLater: () -> Unit) {
    val colors = PatrolDisplay.colors
    val update = uiState.versionUpdate
    val isDownloading = update.phase == VersionUpdatePhase.Downloading
    val isReady = update.phase == VersionUpdatePhase.Ready
    val hasUpdate = update.phase in setOf(VersionUpdatePhase.Available, VersionUpdatePhase.Downloading, VersionUpdatePhase.Ready)
    val overlay = if (colors.dark) Color(0xCC020817) else Color.Black.copy(alpha = 0.30f)
    val footer = if (colors.dark) colors.control else colors.surfaceHigh
    val secondaryButton = if (colors.dark) colors.controlSelected else colors.control
    Box(
        Modifier
            .fillMaxSize()
            .background(overlay)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(14.dp))
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(136.dp)
                    .background(Brush.linearGradient(listOf(Color(0xFF0084FF), Color(0xFF0057FF)))),
                contentAlignment = Alignment.Center
            ) {
                CircuitPattern()
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        VersionIcon("upload", Color.White, Modifier.size(34.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("战术升级", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
                }
            }
            Column(
                Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(dialogTitle(update.phase, update.latestVersionName), color = colors.text, fontSize = PatrolTextSize.SectionTitle, fontWeight = FontWeight.Black)
                Text(
                    update.message ?: "任务稳定性与现场报告能力已完成增强，现在可以更新。",
                    color = colors.textMuted,
                    fontSize = PatrolTextSize.Body,
                    lineHeight = 21.sp
                )
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    update.changelog.forEachIndexed { index, item ->
                        ChangeLog((index + 1).toString().padStart(2, '0'), item)
                    }
                }
                if (isDownloading || isReady) {
                    LinearProgressIndicator(
                        progress = { update.progress },
                        color = TechBlue,
                        trackColor = colors.control,
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(99.dp))
                    )
                }
                Spacer(Modifier.height(2.dp))
                if (hasUpdate) Button(
                    onClick = onUpdate,
                    enabled = !isDownloading && !isReady,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TechBlue)
                ) {
                    Text(if (isReady) "已准备安装" else "立即更新", fontSize = PatrolTextSize.CardTitle, fontWeight = FontWeight.Black)
                }
                Button(
                    onClick = onLater,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = secondaryButton)
                ) {
                    Text(if (hasUpdate && !isReady) "稍后再说" else "关闭", color = colors.text, fontSize = PatrolTextSize.CardTitle, fontWeight = FontWeight.Black)
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(footer)
                    .border(0.5.dp, colors.border)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    VersionIcon("smallShield", TechBlue, Modifier.size(18.dp))
                    Text("签名已验证", color = colors.text, fontSize = PatrolTextSize.BodySmall, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                }
                Text("校验码：0x82A1C", color = colors.textMuted, fontSize = PatrolTextSize.BodySmall)
            }
        }
    }
}

private fun dialogTitle(phase: VersionUpdatePhase, latest: String?): String = when (phase) {
    VersionUpdatePhase.UpToDate -> "当前已是最新版本"
    VersionUpdatePhase.Failed -> "检查更新失败"
    VersionUpdatePhase.Ready -> "更新包已准备完成"
    else -> latest?.let { "发现新版本 v$it" } ?: "发现新版本"
}

private enum class VersionContent { Logs, Privacy, Agreement }

@Composable
private fun VersionContentDialog(content: VersionContent, update: VersionUpdateUiState, onDismiss: () -> Unit) {
    val colors = PatrolDisplay.colors
    Box(
        Modifier.fillMaxSize().background(Color(0x99020817)).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(content.title(), color = colors.text, fontSize = 20.sp, fontWeight = FontWeight.Black)
            content.lines(update).forEach { line ->
                Text(line, color = colors.textMuted, fontSize = 14.sp, lineHeight = 22.sp)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text("关闭")
                }
            }
        }
    }
}

private fun VersionContent.title(): String = when (this) {
    VersionContent.Logs -> "版本日志"
    VersionContent.Privacy -> "隐私政策"
    VersionContent.Agreement -> "用户协议"
}

private fun VersionContent.lines(update: VersionUpdateUiState): List<String> = when (this) {
    VersionContent.Logs -> listOf("当前版本：v${update.currentVersionName}") +
        update.latestVersionName?.let { listOf("可用版本：v$it") }.orEmpty() +
        update.changelog.ifEmpty { listOf(update.message ?: "暂无新的版本日志") }
    VersionContent.Privacy -> listOf(
        "系统仅在执勤、取证、紧急上报等必要场景采集位置、音频、图片和设备状态。",
        "采集数据用于执法协同、证据校验和安全审计，不用于无关用途。",
        "本机展示设置和登录会话会保存在设备安全存储中。"
    )
    VersionContent.Agreement -> listOf(
        "使用本系统需遵守单位执法记录和证据管理规范。",
        "用户应保证账号仅由本人使用，并在任务结束后按要求同步或归档数据。",
        "异常上报、误报处置和请求增援会生成审计记录。"
    )
}

@Composable
private fun ChangeLog(number: String, text: String) {
    val colors = PatrolDisplay.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(number, color = colors.textSubtle, fontSize = PatrolTextSize.BodySmall, fontWeight = FontWeight.Black)
        Spacer(Modifier.width(18.dp))
        Text(text, color = colors.text, fontSize = PatrolTextSize.Body, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CircuitPattern() {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
        repeat(7) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.07f))
            )
        }
    }
}

@Composable
private fun VersionIcon(name: String, color: Color, modifier: Modifier = Modifier) {
    val imageVector: ImageVector = when (name) {
        "back" -> Icons.AutoMirrored.Filled.ArrowBack
        "logs" -> Icons.AutoMirrored.Filled.ReceiptLong
        "privacy" -> Icons.Filled.Policy
        "agreement" -> Icons.Filled.Gavel
        "upload" -> Icons.Filled.Upload
        "smallShield" -> Icons.Filled.VerifiedUser
        "shield" -> Icons.Filled.Security
        else -> Icons.Filled.SystemUpdateAlt
    }
    Icon(imageVector = imageVector, contentDescription = null, tint = color, modifier = modifier)
}
