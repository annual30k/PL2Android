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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrollink.domain.DisplayThemeMode
import com.patrollink.domain.FontSizeMode
import com.patrollink.domain.AppUiState
import com.patrollink.presentation.PatrolViewModel
import com.patrollink.presentation.component.ForceTopBar
import com.patrollink.presentation.component.PatrolCard
import com.patrollink.presentation.component.PrimaryAction
import com.patrollink.presentation.component.StatusTag
import com.patrollink.presentation.component.SystemBars
import com.patrollink.presentation.theme.PatrolDisplay
import com.patrollink.presentation.theme.PatrolTextStyle
import com.patrollink.presentation.theme.TechBlue
import com.patrollink.presentation.theme.Warning

@Composable
fun ProfileScreen(uiState: AppUiState, viewModel: PatrolViewModel, onSos: () -> Unit, onOpenVersionInfo: () -> Unit) {
    val colors = PatrolDisplay.colors
    val titleColor = profileTitleColor()
    SystemBars(statusBarColor = colors.topBar, navigationBarColor = colors.bottomBar, lightStatusBar = !colors.dark, lightNavigationBar = !colors.dark)
    val user = uiState.user
    Box(Modifier.fillMaxSize().background(colors.page)) {
        Column(Modifier.fillMaxSize()) {
            ForceTopBar(title = null, dark = colors.dark, onSos = onSos)
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
                    Column {
                        SectionHeading("执勤辖区", Icons.Filled.LocationOn, TechBlue)
                        Spacer(Modifier.height(10.dp))
                        Text(user.dutyArea, color = titleColor, style = PatrolTextStyle.BodyStrong.copy(fontSize = 15.sp, lineHeight = 21.sp))
                        Spacer(Modifier.height(3.dp))
                        Text(user.patrolGroup, color = colors.textMuted, style = PatrolTextStyle.BodySmall.copy(fontWeight = FontWeight.Bold))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                                .aspectRatio(2.2f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Brush.linearGradient(listOf(Color(0xFF111827), Color(0xFF1E3A8A), Color(0xFF020617))))
                        ) {
                            Text("TACTICAL MAP", color = Color.White.copy(alpha = 0.16f), fontSize = 24.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.Center))
                            Icon(Icons.Filled.LocationOn, contentDescription = null, tint = TechBlue, modifier = Modifier.align(Alignment.Center))
                        }
                    }
                }
            }
            item {
                PatrolCard(radius = 12, dark = true) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            item {
                PatrolCard(
                    modifier = Modifier.clickable(onClick = onOpenVersionInfo),
                    radius = 12,
                    dark = true
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeading("版本信息", Icons.Filled.Info, Warning)
                        Text("执法链路 v${uiState.versionUpdate.currentVersionName} · 加密通道已启用 · 核心服务已同步", color = colors.textMuted, style = PatrolTextStyle.BodySmall.copy(fontWeight = FontWeight.Bold))
                        StatusTag(uiState.versionUpdate.latestVersionName?.let { "发现新版本 v$it" } ?: "点击检查更新", Warning)
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
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(accent.copy(alpha = if (colors.dark) 0.18f else 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(17.dp))
        }
        Text(title, color = titleColor, style = PatrolTextStyle.CardTitle.copy(fontSize = 16.sp, lineHeight = 21.sp))
    }
}

@Composable
private fun ContactInfo(icon: ImageVector, label: String, value: String, accent: Color) {
    val colors = PatrolDisplay.colors
    val titleColor = profileTitleColor()
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
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
