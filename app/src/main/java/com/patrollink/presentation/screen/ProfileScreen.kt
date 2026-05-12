package com.patrollink.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.patrollink.presentation.component.SectionTitle
import com.patrollink.presentation.component.SmallInfo
import com.patrollink.presentation.component.StatusTag
import com.patrollink.presentation.component.SystemBars
import com.patrollink.presentation.theme.Danger
import com.patrollink.presentation.theme.PatrolDisplay
import com.patrollink.presentation.theme.Success
import com.patrollink.presentation.theme.TechBlue
import com.patrollink.presentation.theme.Warning

@Composable
fun ProfileScreen(uiState: AppUiState, viewModel: PatrolViewModel, onSos: () -> Unit, onOpenVersionInfo: () -> Unit) {
    val colors = PatrolDisplay.colors
    SystemBars(statusBarColor = colors.topBar, navigationBarColor = colors.bottomBar, lightStatusBar = !colors.dark, lightNavigationBar = !colors.dark)
    val user = uiState.user
    Column(Modifier.fillMaxSize().background(colors.page)) {
        ForceTopBar(title = null, dark = true, onSos = onSos)
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp),
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
                                .background(Brush.linearGradient(listOf(Color(0xFF1D4ED8), Color(0xFF111827)))),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("警", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Black)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(user.name, color = colors.text, fontSize = 24.sp, fontWeight = FontWeight.Black)
                                StatusTag("已认证", TechBlue)
                            }
                            Text(user.badgeNo, color = TechBlue, fontSize = 14.sp, fontWeight = FontWeight.Black)
                            Text(user.department, color = colors.textSubtle, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            item {
                PatrolCard(radius = 12, dark = true) {
                    Column {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("勤务状态", color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.Black)
                                Text("当前状态：执勤中", color = colors.textSubtle, fontSize = 12.sp)
                            }
                            StatusTag("ACTIVE", Success)
                        }
                        androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
                        SmallInfo("当前班次时长", user.shiftDuration, dark = true)
                    }
                }
            }
            item {
                PatrolCard(radius = 12, dark = true) {
                    Column {
                        SectionTitle("执勤辖区")
                        Text(user.dutyArea, color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.Black)
                        Text("巡逻组 A-42 | 重点管控区域", color = colors.textSubtle, fontSize = 12.sp)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                                .aspectRatio(2.2f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Brush.linearGradient(listOf(Color(0xFF111827), Color(0xFF1E3A8A), Color(0xFF020617))))
                        ) {
                            Text("TACTICAL MAP", color = Color.White.copy(alpha = 0.18f), fontSize = 28.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.Center))
                            Icon(Icons.Filled.LocationOn, contentDescription = null, tint = TechBlue, modifier = Modifier.align(Alignment.Center))
                        }
                    }
                }
            }
            item {
                PatrolCard(radius = 12, dark = true) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        SectionTitle("联络方式")
                        SmallInfo("手机号码", user.phone, dark = true)
                        SmallInfo("警务邮箱", user.email, dark = true)
                    }
                }
            }
            item {
                PatrolCard(radius = 12, dark = true) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        SectionTitle("显示设置")
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
            item {
                PatrolCard(radius = 12, dark = true) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        Text("!", color = Danger, fontSize = 22.sp, fontWeight = FontWeight.Black)
                        Text("系统提醒：您的个人装备（单警执法记录仪）电量低于 15%，请及时充电。", color = if (colors.dark) Color(0xFFFFD2D2) else Color(0xFFB42318), fontSize = 12.sp, lineHeight = 18.sp)
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
                        Text("版本信息", color = colors.text, fontWeight = FontWeight.Black)
                        Text("执法链路 v1.2.4 · 加密通道已启用 · 核心服务已同步", color = colors.textSubtle, fontSize = 12.sp)
                        StatusTag("发现新版本 v1.3.0", Warning)
                    }
                }
            }
            item {
                PrimaryAction("退出登录", onClick = viewModel::logout, modifier = Modifier.fillMaxWidth(), danger = true)
            }
        }
    }
}

private data class DisplayOption<T>(val label: String, val value: T)

@Composable
private fun <T> DisplaySettingGroup(
    title: String,
    options: List<DisplayOption<T>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    val colors = PatrolDisplay.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = colors.textSubtle, fontSize = 11.sp, fontWeight = FontWeight.Black)
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
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) TechBlue else Color.Transparent)
                        .clickable { onSelect(option.value) }
                        .padding(top = 10.dp)
                )
            }
        }
    }
}
