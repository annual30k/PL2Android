package com.patrollink.presentation.screen

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrollink.domain.AppUiState
import com.patrollink.domain.FirmwareUpdatePhase
import com.patrollink.domain.FirmwareUpdateUiState
import com.patrollink.domain.VersionUpdatePhase
import com.patrollink.domain.VersionUpdateUiState
import com.patrollink.presentation.PatrolViewModel
import com.patrollink.presentation.component.StatusTag
import com.patrollink.presentation.component.SystemBars
import com.patrollink.presentation.theme.PatrolDisplay
import com.patrollink.presentation.theme.PatrolTextSize
import com.patrollink.presentation.theme.TechBlue
import com.patrollink.presentation.theme.Warning

@Composable
fun VersionInfoScreen(uiState: AppUiState, viewModel: PatrolViewModel, onBack: () -> Unit) {
    val colors = PatrolDisplay.colors
    SystemBars(statusBarColor = colors.topBar, navigationBarColor = colors.bottomBar, lightStatusBar = !colors.dark, lightNavigationBar = !colors.dark)
    var contentDialog by remember { mutableStateOf<VersionContent?>(null) }
    var activePage by remember { mutableStateOf<VersionInfoPage?>(null) }
    val page = activePage
    val updateState = uiState.versionUpdate

    BackHandler(enabled = page != null) {
        activePage = null
    }
    Box(Modifier.fillMaxSize().background(colors.page)) {
        Column(Modifier.fillMaxSize()) {
            VersionTopBar(
                title = page?.title ?: "版本信息",
                onBack = {
                    if (page == null) onBack() else activePage = null
                }
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (page) {
                    null -> versionMenuContent(
                        uiState = uiState,
                        onOpenAppUpgrade = { activePage = VersionInfoPage.AppUpgrade },
                        onOpenFirmwareUpgrade = { activePage = VersionInfoPage.FirmwareUpgrade }
                    )
                    VersionInfoPage.AppUpgrade -> appUpgradeContent(
                        uiState = uiState,
                        viewModel = viewModel,
                        onOpenContent = { contentDialog = it }
                    )
                    VersionInfoPage.FirmwareUpgrade -> firmwareUpgradeContent(uiState = uiState, viewModel = viewModel)
                }
            }
        }

        contentDialog?.let { dialog ->
            VersionContentDialog(content = dialog, update = updateState, onDismiss = { contentDialog = null })
        }
    }
}

private enum class VersionInfoPage(val title: String) {
    AppUpgrade("App版本升级"),
    FirmwareUpgrade("耳机固件升级")
}

private fun androidx.compose.foundation.lazy.LazyListScope.versionMenuContent(
    uiState: AppUiState,
    onOpenAppUpgrade: () -> Unit,
    onOpenFirmwareUpgrade: () -> Unit
) {
    item {
        VersionRowCard(
            icon = "appUpgrade",
            title = "App版本升级",
            subtitle = "当前版本 v${uiState.versionUpdate.currentVersionName}",
            onClick = onOpenAppUpgrade
        )
    }
    item {
        VersionRowCard(
            icon = "firmwareUpgrade",
            title = "耳机固件升级",
            subtitle = headsetFirmwareSubtitle(uiState),
            onClick = onOpenFirmwareUpgrade
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.appUpgradeContent(
    uiState: AppUiState,
    viewModel: PatrolViewModel,
    onOpenContent: (VersionContent) -> Unit
) {
    item {
        VersionProductHeader(uiState = uiState)
    }
    item {
        AppUpgradeCard(updateState = uiState.versionUpdate, viewModel = viewModel)
    }
    item {
        VersionRowCard(
            icon = "logs",
            title = "版本日志",
            subtitle = "查看历史更新记录",
            onClick = { onOpenContent(VersionContent.Logs) }
        )
    }
    item {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            VersionTile("privacy", "隐私政策", Modifier.weight(1f), onClick = { onOpenContent(VersionContent.Privacy) })
            VersionTile("agreement", "用户协议", Modifier.weight(1f), onClick = { onOpenContent(VersionContent.Agreement) })
        }
    }
    item {
        VersionFooter(uiState = uiState)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.firmwareUpgradeContent(uiState: AppUiState, viewModel: PatrolViewModel) {
    item {
        FirmwareUpgradeCard(uiState = uiState, viewModel = viewModel)
    }
}

@Composable
private fun VersionProductHeader(uiState: AppUiState) {
    val colors = PatrolDisplay.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(76.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surfaceHigh),
            contentAlignment = Alignment.Center
        ) {
            VersionIcon("shield", TechBlue, Modifier.size(38.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text("执法链路", color = colors.text, fontSize = 28.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(6.dp))
        Text(
            "版本 v${uiState.versionUpdate.currentVersionName}",
            color = colors.text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(99.dp))
                .background(colors.control)
                .padding(horizontal = 18.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun VersionFooter(uiState: AppUiState) {
    val colors = PatrolDisplay.colors
    Column(
        Modifier.padding(top = 4.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("战术系统节点：${uiState.user.systemNode}", color = colors.textSubtle, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
        Text("© 2024 哨兵核心系统", color = colors.textMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            MiniSeal("认证")
            MiniSeal("安全")
        }
    }
}

@Composable
private fun AppUpgradeCard(updateState: VersionUpdateUiState, viewModel: PatrolViewModel) {
    val colors = PatrolDisplay.colors
    val accent = if (updateState.phase == VersionUpdatePhase.Available) Warning else TechBlue
    VersionUpgradeCard(
        icon = "appUpgrade",
        title = "App升级",
        subtitle = "当前版本 v${updateState.currentVersionName}",
        status = appUpgradeStatus(updateState),
        accent = accent,
        message = updateState.message ?: "检查执法链路 App 是否有新版本。",
        buttonText = if (updateState.phase == VersionUpdatePhase.Checking) "检查中" else "检查App更新",
        buttonEnabled = updateState.phase != VersionUpdatePhase.Checking,
        onClick = viewModel::checkVersionUpdate
    ) {
        if (updateState.phase == VersionUpdatePhase.Available && updateState.changelog.isNotEmpty()) {
            Text(updateState.changelog.take(2).joinToString(" / "), color = colors.text, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}

@Composable
private fun FirmwareUpgradeCard(uiState: AppUiState, viewModel: PatrolViewModel) {
    val colors = PatrolDisplay.colors
    val firmware = uiState.firmwareUpdate
    val device = uiState.device
    val hasDevice = device.online && device.id.isNotBlank()
    val accent = when (firmware.phase) {
        FirmwareUpdatePhase.Available -> Warning
        FirmwareUpdatePhase.Succeeded,
        FirmwareUpdatePhase.UpToDate -> Color(0xFF16A34A)
        FirmwareUpdatePhase.Failed -> Color(0xFFEF4444)
        else -> TechBlue
    }
    val busy = firmware.phase == FirmwareUpdatePhase.Checking ||
        firmware.phase == FirmwareUpdatePhase.Downloading ||
        firmware.phase == FirmwareUpdatePhase.Upgrading
    val currentFirmware = firmware.currentVersionName.ifBlank { device.firmware }.ifBlank { "未知" }
    VersionUpgradeCard(
        icon = "firmwareUpgrade",
        title = "耳机固件升级",
        subtitle = if (hasDevice) "${device.name} · 当前固件 $currentFirmware" else "未连接耳机",
        status = firmwareUpgradeStatus(firmware, hasDevice),
        accent = accent,
        message = firmware.message ?: if (hasDevice) "检查当前耳机是否有可用固件。" else "请先连接耳机后再检查固件。",
        buttonText = when (firmware.phase) {
            FirmwareUpdatePhase.Checking -> "检查中"
            FirmwareUpdatePhase.Downloading -> "下载中"
            FirmwareUpdatePhase.Upgrading -> "升级中"
            FirmwareUpdatePhase.Available -> "开始升级"
            else -> "检查固件更新"
        },
        buttonEnabled = !busy,
        onClick = {
            if (firmware.phase == FirmwareUpdatePhase.Available) {
                viewModel.startFirmwareUpgrade()
            } else {
                viewModel.checkFirmwareUpdate()
            }
        }
    ) {
        if (firmware.phase == FirmwareUpdatePhase.Downloading || firmware.phase == FirmwareUpdatePhase.Upgrading) {
            LinearProgressIndicator(
                progress = { firmware.progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = accent,
                trackColor = colors.border
            )
        }
        if (firmware.phase == FirmwareUpdatePhase.Available && firmware.changelog.isNotEmpty()) {
            Text(firmware.changelog.take(2).joinToString(" / "), color = colors.text, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}

@Composable
private fun VersionUpgradeCard(
    icon: String,
    title: String,
    subtitle: String,
    status: String,
    accent: Color,
    message: String,
    buttonText: String,
    buttonEnabled: Boolean,
    onClick: () -> Unit,
    extraContent: @Composable () -> Unit = {}
) {
    val colors = PatrolDisplay.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceHigh)
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                VersionIcon(icon, accent, Modifier.size(28.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = colors.text, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Text(subtitle, color = colors.textMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            StatusTag(status, accent, filled = status.contains("发现") || status.contains("可升级"))
        }
        Text(message, color = colors.textMuted, fontSize = 13.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium)
        extraContent()
        Button(
            onClick = onClick,
            enabled = buttonEnabled,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = accent)
        ) {
            Text(buttonText, fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
    }
}

private fun appUpgradeStatus(update: VersionUpdateUiState): String = when (update.phase) {
    VersionUpdatePhase.Checking -> "检查中"
    VersionUpdatePhase.Available -> "发现新版本"
    VersionUpdatePhase.Downloading -> "下载中"
    VersionUpdatePhase.Ready -> "待安装"
    VersionUpdatePhase.UpToDate -> "已是最新"
    VersionUpdatePhase.Failed -> "检查失败"
    VersionUpdatePhase.Idle -> "待检查"
}

private fun firmwareUpgradeStatus(firmware: FirmwareUpdateUiState, hasDevice: Boolean): String = when {
    !hasDevice -> "未连接"
    firmware.phase == FirmwareUpdatePhase.Checking -> "检查中"
    firmware.phase == FirmwareUpdatePhase.Available -> "可升级"
    firmware.phase == FirmwareUpdatePhase.Downloading -> "下载中"
    firmware.phase == FirmwareUpdatePhase.Upgrading -> "升级中"
    firmware.phase == FirmwareUpdatePhase.Succeeded -> "已启动"
    firmware.phase == FirmwareUpdatePhase.UpToDate -> "已是最新"
    firmware.phase == FirmwareUpdatePhase.Failed -> "检查失败"
    else -> "待检查"
}

private fun headsetFirmwareSubtitle(uiState: AppUiState): String {
    val device = uiState.device
    if (!device.online || device.id.isBlank()) return "未连接耳机"
    return "${device.name} · 固件 ${device.firmware.ifBlank { "未知" }}"
}

@Composable
private fun VersionTopBar(title: String, onBack: () -> Unit) {
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
        Text(title, color = TechBlue, fontSize = 22.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun VersionRowCard(icon: String, title: String, subtitle: String, onClick: () -> Unit) {
    val colors = PatrolDisplay.colors
    val iconTint = if (colors.dark) Color(0xFFC4D2FF) else TechBlue
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
            VersionIcon(icon, iconTint, Modifier.size(30.dp))
        }
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = colors.text, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Text(subtitle, color = colors.textMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = iconTint, modifier = Modifier.size(32.dp))
    }
}

@Composable
private fun VersionTile(icon: String, title: String, modifier: Modifier, onClick: () -> Unit) {
    val colors = PatrolDisplay.colors
    val iconTint = if (colors.dark) Color(0xFFC4D2FF) else TechBlue
    Column(
        modifier
            .height(108.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceHigh)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        VersionIcon(icon, iconTint, Modifier.size(30.dp))
        Text(title, color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.Black)
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
fun VersionUpdateDialog(uiState: AppUiState, onUpdate: () -> Unit, onLater: () -> Unit) {
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
            .padding(horizontal = 20.dp),
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
                    .height(104.dp)
                    .background(Brush.linearGradient(listOf(Color(0xFF0084FF), Color(0xFF0057FF)))),
                contentAlignment = Alignment.Center
            ) {
                CircuitPattern()
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        VersionIcon("upload", Color.White, Modifier.size(28.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("战术升级", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
                }
            }
            Column(
                Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(dialogTitle(update.phase, update.latestVersionName), color = colors.text, fontSize = PatrolTextSize.SectionTitle, fontWeight = FontWeight.Black)
                Text(
                    update.message ?: "任务稳定性与现场报告能力已完成增强，现在可以更新。",
                    color = colors.textMuted,
                    fontSize = PatrolTextSize.Body,
                    lineHeight = 21.sp
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    update.changelog.take(3).forEachIndexed { index, item ->
                        ChangeLog((index + 1).toString().padStart(2, '0'), item)
                    }
                    if (update.changelog.size > 3) {
                        Text("另有 ${update.changelog.size - 3} 项改进，可在版本日志中查看", color = colors.textMuted, fontSize = PatrolTextSize.BodySmall)
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
                    enabled = !isDownloading,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TechBlue)
                ) {
                    Text(if (isReady) "继续安装" else "立即更新", fontSize = PatrolTextSize.CardTitle, fontWeight = FontWeight.Black)
                }
                if ((!update.forceUpdate || !hasUpdate) && !isDownloading) {
                    Button(
                        onClick = onLater,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = secondaryButton)
                    ) {
                        Text(if (hasUpdate && !isReady) "稍后再说" else "关闭", color = colors.text, fontSize = PatrolTextSize.CardTitle, fontWeight = FontWeight.Black)
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(footer)
                    .border(0.5.dp, colors.border)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    VersionIcon("smallShield", TechBlue, Modifier.size(18.dp))
                    Text("下载后自动执行 SHA-256 与安装签名校验", color = colors.text, fontSize = PatrolTextSize.BodySmall, fontWeight = FontWeight.Black)
                }
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
        "appUpgrade" -> Icons.Filled.SystemUpdateAlt
        "firmwareUpgrade" -> Icons.Filled.Upload
        "upload" -> Icons.Filled.Upload
        "smallShield" -> Icons.Filled.VerifiedUser
        "shield" -> Icons.Filled.Security
        else -> Icons.Filled.SystemUpdateAlt
    }
    Icon(imageVector = imageVector, contentDescription = null, tint = color, modifier = modifier)
}
