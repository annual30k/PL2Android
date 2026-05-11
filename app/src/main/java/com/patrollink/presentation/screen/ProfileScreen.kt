package com.patrollink.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrollink.domain.AppUiState
import com.patrollink.presentation.PatrolViewModel
import com.patrollink.presentation.component.MetricTile
import com.patrollink.presentation.component.PatrolCard
import com.patrollink.presentation.component.PatrolTopBar
import com.patrollink.presentation.component.PrimaryAction
import com.patrollink.presentation.component.SectionTitle
import com.patrollink.presentation.component.StatusTag
import com.patrollink.presentation.theme.Danger
import com.patrollink.presentation.theme.Muted
import com.patrollink.presentation.theme.Success
import com.patrollink.presentation.theme.TechBlue
import com.patrollink.presentation.theme.Warning

@Composable
fun ProfileScreen(uiState: AppUiState, viewModel: PatrolViewModel, onSos: () -> Unit) {
    val user = uiState.user
    Column(Modifier.fillMaxSize()) {
        PatrolTopBar("个人中心", onSos)
        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                PatrolCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(user.name, fontSize = 26.sp, fontWeight = FontWeight.Black)
                        Text("${user.badgeNo} · ${user.department}", color = Muted, fontWeight = FontWeight.Bold)
                        StatusTag("执勤中", Success)
                    }
                }
            }
            item { MetricTile("当前班次时长", user.shiftDuration, TechBlue, 0.72f) }
            item {
                PatrolCard {
                    Column {
                        SectionTitle("执勤辖区")
                        Text(user.dutyArea, fontWeight = FontWeight.Bold)
                        Text("巡逻组 A-42 · 重点管控区域", color = Muted)
                    }
                }
            }
            item {
                PatrolCard {
                    Column {
                        SectionTitle("联络方式")
                        Text(user.phone, fontWeight = FontWeight.Bold)
                        Text(user.email, color = Muted)
                    }
                }
            }
            item {
                PatrolCard {
                    Column {
                        SectionTitle("系统状态")
                        Text("云端会话：已连接", color = Muted)
                        Text("App 版本：1.0.0", color = Muted)
                        Text("隐私存储：沙盒加密策略待接入 Keystore", color = Muted)
                    }
                }
            }
            item {
                PatrolCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusTag("装备提醒", Warning)
                        Text("个人装备电量低于 15% 时会触发前台通知和页面提醒。", color = Muted)
                    }
                }
            }
            item {
                PrimaryAction("退出登录", onClick = viewModel::logout, modifier = Modifier.fillMaxWidth(), danger = true)
            }
        }
    }
}
