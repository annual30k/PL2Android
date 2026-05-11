package com.patrollink.presentation.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrollink.domain.AlertStatus
import com.patrollink.domain.AppUiState
import com.patrollink.presentation.PatrolViewModel
import com.patrollink.presentation.component.AlertLevelTag
import com.patrollink.presentation.component.OfflineBanner
import com.patrollink.presentation.component.PatrolCard
import com.patrollink.presentation.component.PatrolTopBar
import com.patrollink.presentation.component.PrimaryAction
import com.patrollink.presentation.component.SectionTitle
import com.patrollink.presentation.component.StatusTag
import com.patrollink.presentation.theme.Muted
import com.patrollink.presentation.theme.Success
import com.patrollink.presentation.theme.TechBlue

@Composable
fun AlertListScreen(
    uiState: AppUiState,
    viewModel: PatrolViewModel,
    onSos: () -> Unit,
    onOpenDetail: (String) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        PatrolTopBar("预警处置", onSos)
        OfflineBanner(uiState.networkOnline)
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AlertTab("待处理", uiState.selectedAlertTab == AlertStatus.Pending, Modifier.weight(1f)) { viewModel.setAlertTab(AlertStatus.Pending) }
                AlertTab("已处理", uiState.selectedAlertTab == AlertStatus.Closed, Modifier.weight(1f)) { viewModel.setAlertTab(AlertStatus.Closed) }
            }
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(uiState.alerts.filter { it.status == uiState.selectedAlertTab }) { alert ->
                PatrolCard(Modifier.clickable { onOpenDetail(alert.id) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Text(alert.title, fontSize = 18.sp, fontWeight = FontWeight.Black)
                                Text(alert.id, color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            AlertLevelTag(alert.level)
                        }
                        Text("${alert.location} · ${alert.source}", color = Muted, fontSize = 12.sp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(alert.time, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            StatusTag(if (alert.status == AlertStatus.Closed) "已闭环" else "待处置", if (alert.status == AlertStatus.Closed) Success else TechBlue)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertTab(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(42.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (selected) TechBlue else Color.White, contentColor = if (selected) Color.White else Muted)
    ) {
        Text(text, fontWeight = FontWeight.Bold)
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
    val alert = uiState.alerts.firstOrNull { it.id == alertId } ?: uiState.alerts.first()
    Column(Modifier.fillMaxSize()) {
        PatrolTopBar("预警详情", onSos, trailing = {
            Text("返回", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onBack() })
        })
        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                PatrolCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(alert.id, fontSize = 20.sp, fontWeight = FontWeight.Black)
                            AlertLevelTag(alert.level)
                        }
                        StatusTag(if (alert.status == AlertStatus.Closed) "已闭环" else "待处置", if (alert.status == AlertStatus.Closed) Success else TechBlue)
                    }
                }
            }
            item {
                PatrolCard {
                    Column {
                        SectionTitle("事件基本信息")
                        Text("类型：${alert.title}", fontWeight = FontWeight.Bold)
                        Text("时间：${alert.time}", color = Muted)
                        Text("位置：${alert.location}", color = Muted)
                        Text("来源：${alert.source}", color = Muted)
                        Text("可信度：${alert.confidence}", color = Muted)
                    }
                }
            }
            item {
                PatrolCard {
                    Column {
                        SectionTitle("现场多媒体")
                        Text("已关联耳机视频片段、环境音频与现场照片。当前版本使用占位预览，接入文件服务后展示真实封面。", color = Muted, lineHeight = 20.sp)
                    }
                }
            }
            item {
                PatrolCard {
                    Column {
                        SectionTitle("事件描述")
                        Text(alert.description, color = Muted, lineHeight = 20.sp)
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryAction("误报", onClick = { viewModel.closeAlert(alert.id); onBack() }, modifier = Modifier.weight(1f))
                    PrimaryAction("已处理并上报", onClick = { viewModel.closeAlert(alert.id); onBack() }, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                PrimaryAction("请求支援", onClick = {}, modifier = Modifier.fillMaxWidth(), danger = true)
            }
        }
    }
}
