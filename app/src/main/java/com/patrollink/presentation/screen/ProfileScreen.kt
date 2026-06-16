package com.patrollink.presentation.screen

import android.os.Bundle
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.CoordinateConverter
import com.amap.api.maps.MapView
import com.amap.api.maps.MapsInitializer
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.CircleOptions
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.LatLngBounds
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.PolygonOptions
import com.amap.api.maps.model.PolylineOptions
import com.patrollink.domain.GpsLocation
import com.patrollink.domain.DisplayThemeMode
import com.patrollink.domain.FontSizeMode
import com.patrollink.domain.AppUiState
import com.patrollink.domain.LocationFetchStatus
import com.patrollink.domain.PatrolArea
import com.patrollink.domain.PatrolGeoPoint
import com.patrollink.presentation.PatrolViewModel
import com.patrollink.presentation.component.PatrolCard
import com.patrollink.presentation.component.PrimaryAction
import com.patrollink.presentation.component.StatusTag
import com.patrollink.presentation.component.SystemBars
import com.patrollink.presentation.theme.PatrolDisplay
import com.patrollink.presentation.theme.PatrolTextStyle
import com.patrollink.presentation.theme.Success
import com.patrollink.presentation.theme.TechBlue
import com.patrollink.presentation.theme.Warning
import kotlin.math.abs
import java.util.Locale
import kotlinx.coroutines.delay
import android.graphics.Color as AndroidColor

private val ProfileIconSlotSize = 44.dp
private val ProfileMenuIconBoxSize = 32.dp
private val ProfileIconTextGap = 12.dp
private val ProfileTextStartPadding = ProfileIconSlotSize + ProfileIconTextGap

@Composable
fun ProfileScreen(
    uiState: AppUiState,
    viewModel: PatrolViewModel,
    onOpenVersionInfo: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onOpenCerebellumConfig: () -> Unit
) {
    val colors = PatrolDisplay.colors
    val titleColor = profileTitleColor()
    LaunchedEffect(Unit) {
        while (true) {
            viewModel.refreshCurrentLocation()
            delay(15_000L)
        }
    }
    SystemBars(statusBarColor = colors.topBar, navigationBarColor = colors.bottomBar, lightStatusBar = !colors.dark, lightNavigationBar = !colors.dark)
    val user = uiState.user
    Box(Modifier.fillMaxSize().background(colors.page)) {
        Column(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 10.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
            item {
                PatrolCard(radius = 12, dark = true) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(
                            Modifier
                                .weight(0.38f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Brush.linearGradient(listOf(Color(0xFF0B63F6), Color(0xFF0B1326)))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(42.dp))
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(user.name, color = titleColor, style = PatrolTextStyle.PageTitle.copy(fontSize = 22.sp, lineHeight = 27.sp))
                                StatusTag("已认证", TechBlue)
                            }
                            Text(user.badgeNo, color = TechBlue, style = PatrolTextStyle.BodyStrong.copy(fontSize = 14.sp, lineHeight = 19.sp))
                            Text(user.department, color = colors.textMuted, style = PatrolTextStyle.BodySmall.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
            item {
                PatrolCard(radius = 12, dark = true) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionHeading("执勤辖区", Icons.Filled.LocationOn, TechBlue)
                        Column(Modifier.padding(start = ProfileTextStartPadding), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(uiState.patrolArea.name, color = titleColor, style = PatrolTextStyle.BodyStrong.copy(fontSize = 15.sp, lineHeight = 21.sp))
                            Text("${uiState.patrolArea.teamName} | ${user.patrolGroup.substringAfter("| ", user.patrolGroup)}", color = colors.textMuted, style = PatrolTextStyle.BodySmall.copy(fontWeight = FontWeight.Bold))
                        }
                        PatrolAreaMap(
                            location = uiState.sosLocation,
                            locationStatus = uiState.locationFetchStatus,
                            dutyArea = uiState.patrolArea.name,
                            patrolArea = uiState.patrolArea
                        )
                    }
                }
            }
            item {
                PatrolCard(radius = 12, dark = true) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        SectionHeading("联络方式", Icons.Filled.ContactPhone, Color(0xFF22C55E))
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            ContactInfo(Icons.Filled.Phone, "手机号码", user.phone, Color(0xFF22C55E))
                            ContactInfo(Icons.Filled.Email, "警务邮箱", user.email, TechBlue)
                        }
                    }
                }
            }
            item {
                PatrolCard(radius = 12, dark = true) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeading("设置", Icons.Filled.Settings, Color(0xFF8B5CF6))
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SettingsNavRow(
                                icon = Icons.Filled.Tune,
                                title = "系统设置",
                                subtitle = "字体大小、主题模式",
                                accent = Color(0xFF8B5CF6),
                                onClick = onOpenSystemSettings
                            )
                            SettingsNavRow(
                                icon = Icons.Filled.Router,
                                title = "小脑配置",
                                subtitle = uiState.cerebellumSettings.baseUrl.ifBlank { "未配置服务地址" },
                                accent = TechBlue,
                                onClick = onOpenCerebellumConfig
                            )
                        }
                    }
                }
            }
            item {
                PatrolCard(
                    modifier = Modifier.clickable(onClick = onOpenVersionInfo),
                    radius = 12,
                    dark = true
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeading("版本信息", Icons.Filled.Info, Warning)
                        Column(Modifier.padding(start = ProfileTextStartPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("App v${uiState.versionUpdate.currentVersionName} · 耳机固件 ${uiState.device.firmware.ifBlank { "未连接设备" }}", color = colors.textMuted, style = PatrolTextStyle.BodySmall.copy(fontWeight = FontWeight.Bold))
                            StatusTag("App版本 / 耳机固件", Warning)
                        }
                    }
                }
            }
            item {
                PrimaryAction("退出登录", onClick = viewModel::logout, modifier = Modifier.fillMaxWidth(), danger = true)
            }
            }
        }
    }
}

@Composable
fun SystemSettingsScreen(uiState: AppUiState, viewModel: PatrolViewModel, onBack: () -> Unit) {
    val colors = PatrolDisplay.colors
    SystemBars(statusBarColor = colors.topBar, navigationBarColor = colors.bottomBar, lightStatusBar = !colors.dark, lightNavigationBar = !colors.dark)
    SettingsPageScaffold(title = "系统设置", onBack = onBack) {
        item {
            PatrolCard(radius = 12, dark = true) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeading("显示设置", Icons.Filled.Settings, Color(0xFF8B5CF6))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DisplaySettingGroup(
                            title = "字体大小",
                            options = listOf(
                                DisplayOption("紧凑", FontSizeMode.Compact),
                                DisplayOption("标准", FontSizeMode.Standard),
                                DisplayOption("大号", FontSizeMode.Large)
                            ),
                            selected = uiState.fontSizeMode,
                            onSelect = viewModel::setFontSizeMode
                        )
                        DisplaySettingGroup(
                            title = "主题模式",
                            options = listOf(
                                DisplayOption("跟随系统", DisplayThemeMode.System),
                                DisplayOption("浅色", DisplayThemeMode.Light),
                                DisplayOption("深色", DisplayThemeMode.Dark)
                            ),
                            selected = uiState.displayThemeMode,
                            onSelect = viewModel::setDisplayThemeMode
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CerebellumConfigScreen(uiState: AppUiState, viewModel: PatrolViewModel, onBack: () -> Unit) {
    val colors = PatrolDisplay.colors
    var activePage by remember { mutableStateOf<CerebellumConfigPage?>(null) }
    val page = activePage
    BackHandler(enabled = page != null) {
        activePage = null
    }
    SystemBars(statusBarColor = colors.topBar, navigationBarColor = colors.bottomBar, lightStatusBar = !colors.dark, lightNavigationBar = !colors.dark)
    SettingsPageScaffold(
        title = page?.title ?: "小脑配置",
        onBack = {
            if (page == null) onBack() else activePage = null
        }
    ) {
        when (page) {
            null -> {
                item { CerebellumOverviewCard(uiState = uiState) }
                item {
                    PatrolCard(radius = 12, dark = true, padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            CerebellumMenuRow(
                                icon = Icons.Filled.Router,
                                title = "连接设置",
                                accent = TechBlue,
                                status = if (uiState.cerebellumSettings.baseUrl.isBlank()) "待配置" else "已配置",
                                onClick = { activePage = CerebellumConfigPage.Connection }
                            )
                            CerebellumMenuRow(
                                icon = Icons.Filled.Folder,
                                title = "文件管理",
                                accent = Color(0xFF0EA5E9),
                                status = uiState.cerebellumSettings.lastFileCount?.let { "$it 个" } ?: "未读取",
                                onClick = { activePage = CerebellumConfigPage.Files }
                            )
                            CerebellumMenuRow(
                                icon = Icons.Filled.HealthAndSafety,
                                title = "健康诊断",
                                accent = Success,
                                status = if (uiState.cerebellumSettings.healthStatus.isBlank()) "未检查" else "正常",
                                onClick = { activePage = CerebellumConfigPage.Health }
                            )
                            CerebellumMenuRow(
                                icon = Icons.Filled.Face,
                                title = "人脸库同步",
                                accent = Color(0xFF8B5CF6),
                                status = if (uiState.cerebellumSettings.lastFaceLibrarySyncResult.isNotBlank()) "已同步" else "未同步",
                                onClick = { activePage = CerebellumConfigPage.FaceLibrary }
                            )
                        }
                    }
                }
            }
            CerebellumConfigPage.Connection -> {
                item {
                    PatrolCard(radius = 12, dark = true) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionHeading("连接设置", Icons.Filled.Router, TechBlue)
                            RuntimeTextField(
                                label = "小脑服务地址",
                                value = uiState.cerebellumSettings.baseUrl,
                                placeholder = "http://192.168.4.1:8088",
                                keyboardType = KeyboardType.Uri,
                                onValueChange = viewModel::updateCerebellumBaseUrl
                            )
                            RuntimeTextField(
                                label = "API Key",
                                value = uiState.cerebellumSettings.apiKey,
                                placeholder = "未启用鉴权时可留空",
                                keyboardType = KeyboardType.Text,
                                onValueChange = viewModel::updateCerebellumApiKey
                            )
                            PrimaryAction(
                                text = if (uiState.cerebellumSettings.saving) "保存中" else "保存连接设置",
                                onClick = viewModel::saveCerebellumSettings,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
            CerebellumConfigPage.Files -> {
                item {
                    PatrolCard(radius = 12, dark = true) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionHeading("文件管理", Icons.Filled.Folder, Color(0xFF0EA5E9))
                            CerebellumActionRow(
                                icon = Icons.Filled.Folder,
                                title = "读取小脑文件",
                                accent = TechBlue,
                                onClick = viewModel::refreshCerebellumFiles
                            )
                            CerebellumActionRow(
                                icon = Icons.Filled.Refresh,
                                title = "刷新文件索引",
                                accent = Color(0xFF0EA5E9),
                                onClick = { viewModel.sendCerebellumCommand("refresh_files") }
                            )
                            CerebellumFileResult(uiState)
                            CerebellumCommandResult(
                                result = uiState.cerebellumSettings.lastFileCommandResult
                            )
                        }
                    }
                }
            }
            CerebellumConfigPage.Health -> {
                item {
                    PatrolCard(radius = 12, dark = true) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionHeading("健康诊断", Icons.Filled.HealthAndSafety, Success)
                            CerebellumActionRow(
                                icon = Icons.Filled.HealthAndSafety,
                                title = "开始健康检查",
                                accent = Success,
                                onClick = viewModel::checkCerebellumHealth
                            )
                            CerebellumHealthResult(uiState)
                        }
                    }
                }
            }
            CerebellumConfigPage.FaceLibrary -> {
                item {
                    PatrolCard(radius = 12, dark = true) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionHeading("人脸库同步", Icons.Filled.Face, Color(0xFF8B5CF6))
                            CerebellumActionRow(
                                icon = Icons.Filled.Sync,
                                title = "同步人脸库",
                                accent = Color(0xFF8B5CF6),
                                onClick = { viewModel.sendCerebellumCommand("sync_face_library") }
                            )
                            CerebellumCommandResult(
                                result = uiState.cerebellumSettings.lastFaceLibrarySyncResult
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class CerebellumConfigPage(val title: String) {
    Connection("连接设置"),
    Files("文件管理"),
    Health("健康诊断"),
    FaceLibrary("人脸库同步")
}

@Composable
private fun CerebellumOverviewCard(uiState: AppUiState) {
    val colors = PatrolDisplay.colors
    val configured = uiState.cerebellumSettings.baseUrl.isNotBlank()
    PatrolCard(radius = 12, dark = true) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                SectionHeading("小脑中心", Icons.Filled.Router, TechBlue)
                StatusTag(if (configured) "已配置" else "待配置", if (configured) Success else Warning, filled = true)
            }
            Text(
                uiState.cerebellumSettings.baseUrl.ifBlank { "未配置服务地址" },
                color = colors.text,
                style = PatrolTextStyle.BodyStrong.copy(fontSize = 15.sp, lineHeight = 21.sp),
                maxLines = 1
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CerebellumSmallPill(if (uiState.cerebellumSettings.apiKey.isBlank()) "无 Key" else "Key 已填", TechBlue)
                CerebellumSmallPill(uiState.cerebellumSettings.lastFileCount?.let { "文件 $it" } ?: "文件未读取", Color(0xFF0EA5E9))
            }
        }
    }
}

@Composable
private fun CerebellumMenuRow(
    icon: ImageVector,
    title: String,
    accent: Color,
    status: String,
    onClick: () -> Unit
) {
    val colors = PatrolDisplay.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accent.copy(alpha = if (colors.dark) 0.2f else 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
        }
        Text(title, color = colors.text, style = PatrolTextStyle.BodyStrong.copy(fontSize = 16.sp, lineHeight = 21.sp), maxLines = 1, modifier = Modifier.weight(1f))
        Text(status, color = colors.textMuted, style = PatrolTextStyle.BodySmall.copy(fontWeight = FontWeight.Bold), maxLines = 1)
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colors.textSubtle, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun CerebellumActionRow(icon: ImageVector, title: String, accent: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(accent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Text(title, color = Color.White, style = PatrolTextStyle.BodyStrong.copy(fontSize = 16.sp, lineHeight = 21.sp), modifier = Modifier.weight(1f))
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.82f), modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun CerebellumFileResult(uiState: AppUiState) {
    val count = uiState.cerebellumSettings.lastFileCount ?: return
    val names = uiState.cerebellumSettings.lastFileNames
    CerebellumResultPanel(
        title = "已读取 $count 个小脑文件",
        subtitle = names.takeIf { it.isNotEmpty() }?.joinToString(" / ").orEmpty(),
        accent = Color(0xFF0EA5E9)
    )
}

@Composable
private fun CerebellumCommandResult(result: String) {
    if (result.isBlank()) return
    CerebellumResultPanel(
        title = result,
        subtitle = "",
        accent = Success
    )
}

@Composable
private fun CerebellumResultPanel(title: String, subtitle: String, accent: Color) {
    val colors = PatrolDisplay.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = if (colors.dark) 0.16f else 0.1f))
            .border(1.dp, accent.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(title, color = colors.text, style = PatrolTextStyle.BodyStrong.copy(fontSize = 15.sp, lineHeight = 20.sp))
        if (subtitle.isNotBlank()) {
            Text(subtitle, color = colors.textMuted, style = PatrolTextStyle.BodySmall.copy(fontWeight = FontWeight.Bold), maxLines = 1)
        }
    }
}

@Composable
private fun CerebellumHealthResult(uiState: AppUiState) {
    val status = uiState.cerebellumSettings.healthStatus
    if (status.isBlank()) return
    CerebellumResultPanel(
        title = status,
        subtitle = uiState.cerebellumSettings.healthDetail,
        accent = Success
    )
}

@Composable
private fun CerebellumSmallPill(text: String, accent: Color) {
    Text(
        text,
        color = accent,
        style = PatrolTextStyle.Caption,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(accent.copy(alpha = 0.1f))
            .border(1.dp, accent.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp)
    )
}

@Composable
private fun SettingsPageScaffold(
    title: String,
    onBack: () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    val colors = PatrolDisplay.colors
    Box(Modifier.fillMaxSize().background(colors.page)) {
        Column(Modifier.fillMaxSize()) {
            SettingsTopBar(title = title, onBack = onBack)
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 14.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = content
            )
        }
    }
}

@Composable
private fun SettingsTopBar(title: String, onBack: () -> Unit) {
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
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = TechBlue, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(title, color = TechBlue, fontSize = 22.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit
) {
    val colors = PatrolDisplay.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ProfileIconTextGap)
    ) {
        Box(
            Modifier.size(ProfileIconSlotSize),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size(ProfileMenuIconBoxSize)
                    .clip(RoundedCornerShape(9.dp))
                    .background(accent.copy(alpha = if (colors.dark) 0.18f else 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = colors.text, style = PatrolTextStyle.BodyStrong.copy(fontSize = 16.sp, lineHeight = 21.sp))
            Text(subtitle, color = colors.textMuted, style = PatrolTextStyle.BodySmall.copy(fontWeight = FontWeight.Bold), maxLines = 1)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colors.textSubtle, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun RuntimeTextField(
    label: String,
    value: String,
    placeholder: String,
    keyboardType: KeyboardType,
    onValueChange: (String) -> Unit
) {
    val colors = PatrolDisplay.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label, fontWeight = FontWeight.Bold) },
        placeholder = { Text(placeholder, color = colors.textSubtle) },
        singleLine = true,
        shape = RoundedCornerShape(10.dp),
        textStyle = PatrolTextStyle.Body.copy(color = colors.text),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
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

private data class DisplayOption<T>(val label: String, val value: T)

@Composable
private fun profileTitleColor(): Color {
    val colors = PatrolDisplay.colors
    return if (colors.dark) colors.text else Color(0xFF1E293B)
}

@Composable
private fun SectionHeading(title: String, icon: ImageVector, accent: Color) {
    val colors = PatrolDisplay.colors
    val titleColor = profileTitleColor()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ProfileIconTextGap)) {
        Box(
            Modifier
                .size(ProfileIconSlotSize)
                .clip(RoundedCornerShape(12.dp))
                .background(accent.copy(alpha = if (colors.dark) 0.18f else 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(25.dp))
        }
        Text(title, color = titleColor, style = PatrolTextStyle.CardTitle.copy(fontSize = 16.sp, lineHeight = 21.sp))
    }
}

@Composable
private fun PatrolAreaMap(
    location: GpsLocation,
    locationStatus: LocationFetchStatus,
    dutyArea: String,
    patrolArea: PatrolArea
) {
    var expanded by remember { mutableStateOf(false) }

    DutyMapView(
        location = location,
        locationStatus = locationStatus,
        dutyArea = dutyArea,
        patrolArea = patrolArea,
        expanded = false,
        onMapClick = { expanded = true },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .aspectRatio(2.2f)
    )

    if (expanded) {
        Dialog(
            onDismissRequest = { expanded = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                DutyMapView(
                    location = location,
                    locationStatus = locationStatus,
                    dutyArea = dutyArea,
                    patrolArea = patrolArea,
                    expanded = true,
                    onMapClick = {},
                    modifier = Modifier.fillMaxSize()
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(18.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.58f))
                        .clickable { expanded = false }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Text("关闭", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun DutyMapView(
    location: GpsLocation,
    locationStatus: LocationFetchStatus,
    dutyArea: String,
    patrolArea: PatrolArea,
    expanded: Boolean,
    onMapClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasLocation = location.hasUsableCoordinate()
    val currentLabel = if (hasLocation) {
        "${"%.4f".format(Locale.CHINA, location.latitude)}, ${"%.4f".format(Locale.CHINA, location.longitude)}"
    } else {
        when (locationStatus) {
            LocationFetchStatus.Loading -> "定位中"
            else -> "暂无定位"
        }
    }
    val accuracyLabel = if (hasLocation && location.accuracyMeters > 0f) {
        "${"%.1f".format(Locale.CHINA, location.accuracyMeters)}m"
    } else {
        "--"
    }
    val locationStatusLabel = when {
        hasLocation -> "已定位"
        locationStatus == LocationFetchStatus.Loading -> "定位中"
        else -> "暂无有效定位"
    }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val dark = PatrolDisplay.colors.dark
    val shape = if (expanded) RoundedCornerShape(0.dp) else RoundedCornerShape(10.dp)
    val mapView = remember {
        MapsInitializer.updatePrivacyShow(context, true, true)
        MapsInitializer.updatePrivacyAgree(context, true)
        MapView(context).apply {
            onCreate(Bundle())
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    Box(
        modifier
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF07111F), Color(0xFF123C8A), Color(0xFF020617))))
    ) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { view -> view.configureDutyMap(location, patrolArea, expanded, dark, onMapClick) }
        )

        val topOverlayModifier = if (expanded) {
            Modifier.statusBarsPadding()
        } else {
            Modifier
        }
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .then(topOverlayModifier)
                .padding(10.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Color.Black.copy(alpha = 0.42f))
                .padding(horizontal = 9.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("当前辖区", color = Color.White.copy(alpha = 0.72f), fontSize = 9.sp, fontWeight = FontWeight.Black)
            Text(dutyArea, color = Color.White, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Black)
            if (expanded) {
                Text(
                    "精度 $accuracyLabel",
                    color = Color(0xFFBFDBFE),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
        if (!expanded) {
            Text(
                "精度 $accuracyLabel",
                color = Color(0xFFBFDBFE),
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color.Black.copy(alpha = 0.34f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        if (!expanded) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(10.dp)
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.42f))
                    .clickable(onClick = onMapClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.OpenInFull, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
        if (expanded) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 18.dp, bottom = if (expanded) 76.dp else 18.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.52f))
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable { mapView.map?.animateCamera(CameraUpdateFactory.zoomIn()) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable { mapView.map?.animateCamera(CameraUpdateFactory.zoomOut()) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .then(if (expanded) Modifier.navigationBarsPadding() else Modifier)
                .padding(10.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Color.Black.copy(alpha = 0.48f))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(Icons.Filled.LocationOn, contentDescription = null, tint = TechBlue, modifier = Modifier.size(14.dp))
            Text(
                "当前位置 $currentLabel · $locationStatusLabel",
                color = Color.White,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

private fun MapView.configureDutyMap(location: GpsLocation, patrolArea: PatrolArea, expanded: Boolean, dark: Boolean, onMapClick: () -> Unit) {
    val aMap = map ?: return
    val point = if (location.hasUsableCoordinate()) location.toAmapLatLng(this) else null
    val route = patrolArea.route.filter { it.hasUsableCoordinate() }.map { it.toAmapLatLng(this) }
    val boundary = patrolArea.boundary.filter { it.hasUsableCoordinate() }.map { it.toAmapLatLng(this) }

    aMap.clear()
    aMap.mapType = if (dark) AMap.MAP_TYPE_NIGHT else AMap.MAP_TYPE_NORMAL
    aMap.uiSettings.apply {
        isZoomControlsEnabled = false
        isScaleControlsEnabled = expanded
        isCompassEnabled = expanded
        isMyLocationButtonEnabled = false
        setAllGesturesEnabled(expanded)
    }
    aMap.setOnMapClickListener { onMapClick() }
    if (boundary.size >= 3) {
        aMap.addPolygon(
            PolygonOptions()
                .addAll(boundary)
                .strokeColor(if (dark) AndroidColor.argb(225, 96, 165, 250) else AndroidColor.argb(210, 37, 99, 235))
                .fillColor(if (dark) AndroidColor.argb(42, 96, 165, 250) else AndroidColor.argb(34, 37, 99, 235))
                .strokeWidth(4f)
        )
    }
    if (route.size >= 2) {
        aMap.addPolyline(
            PolylineOptions()
                .addAll(route)
                .width(9f)
                .color(if (dark) AndroidColor.argb(235, 34, 211, 238) else AndroidColor.argb(230, 8, 145, 178))
        )
    }
    if (point != null) {
        aMap.addCircle(
            CircleOptions()
                .center(point)
                .radius(location.accuracyMeters.coerceAtLeast(30f).toDouble())
                .strokeColor(if (dark) AndroidColor.argb(210, 96, 165, 250) else AndroidColor.argb(190, 11, 99, 246))
                .fillColor(if (dark) AndroidColor.argb(48, 96, 165, 250) else AndroidColor.argb(42, 11, 99, 246))
                .strokeWidth(3f)
        )
        aMap.addMarker(
            MarkerOptions()
                .position(point)
                .title("当前位置")
                .snippet(location.address)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
        )
    }
    moveCameraToDutyArea(aMap, boundary, route, point, expanded)
}

private fun moveCameraToDutyArea(aMap: AMap, boundary: List<LatLng>, route: List<LatLng>, point: LatLng?, expanded: Boolean) {
    val dutyPoints = boundary.ifEmpty { route }
    when {
        dutyPoints.size >= 2 -> {
            val boundsBuilder = LatLngBounds.builder()
            dutyPoints.forEach { boundsBuilder.include(it) }
            val padding = if (expanded) 96 else 56
            runCatching {
                aMap.moveCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), padding))
            }.onFailure {
                aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(dutyPoints.first(), if (expanded) 15.8f else 14.8f))
            }
        }
        dutyPoints.size == 1 -> aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(dutyPoints.first(), if (expanded) 16.2f else 15.2f))
        point != null -> aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(point, if (expanded) 16.8f else 15.8f))
    }
}

private fun GpsLocation.hasUsableCoordinate(): Boolean {
    return latitude.isFinite() &&
        longitude.isFinite() &&
        latitude in -90.0..90.0 &&
        longitude in -180.0..180.0 &&
        !(abs(latitude) < 0.000001 && abs(longitude) < 0.000001)
}

private fun PatrolGeoPoint.hasUsableCoordinate(): Boolean {
    return latitude.isFinite() &&
        longitude.isFinite() &&
        latitude in -90.0..90.0 &&
        longitude in -180.0..180.0 &&
        !(abs(latitude) < 0.000001 && abs(longitude) < 0.000001)
}

private fun GpsLocation.toAmapLatLng(mapView: MapView): LatLng {
    return toConvertedAmapLatLng(latitude, longitude, mapView)
}

private fun PatrolGeoPoint.toAmapLatLng(mapView: MapView): LatLng {
    return toConvertedAmapLatLng(latitude, longitude, mapView)
}

private fun toConvertedAmapLatLng(latitude: Double, longitude: Double, mapView: MapView): LatLng {
    val gpsPoint = LatLng(latitude, longitude)
    return runCatching {
        CoordinateConverter(mapView.context.applicationContext)
            .from(CoordinateConverter.CoordType.GPS)
            .coord(gpsPoint)
            .convert()
    }.getOrDefault(gpsPoint)
}

@Composable
private fun ContactInfo(icon: ImageVector, label: String, value: String, accent: Color) {
    val colors = PatrolDisplay.colors
    val titleColor = profileTitleColor()
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ProfileIconTextGap)
    ) {
        Box(
            Modifier.size(ProfileIconSlotSize),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size(ProfileMenuIconBoxSize)
                    .clip(RoundedCornerShape(9.dp))
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(label, color = colors.textMuted, style = PatrolTextStyle.BodySmall.copy(fontWeight = FontWeight.Black))
            Text(value, color = titleColor, style = PatrolTextStyle.BodyStrong.copy(fontSize = 15.sp, lineHeight = 21.sp))
        }
    }
}

@Composable
private fun <T> DisplaySettingGroup(
    title: String,
    options: List<DisplayOption<T>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    val colors = PatrolDisplay.colors
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, color = colors.textMuted, style = PatrolTextStyle.BodySmall.copy(fontWeight = FontWeight.Black))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(colors.control)
                .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEach { option ->
                val active = option.value == selected
                Text(
                    text = option.label,
                    color = if (active) Color.White else colors.textMuted,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) TechBlue else Color.Transparent)
                        .clickable { onSelect(option.value) }
                        .padding(top = 8.dp)
                )
            }
        }
    }
}
