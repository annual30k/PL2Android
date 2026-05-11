package com.patrollink.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrollink.domain.AppUiState
import com.patrollink.presentation.PatrolViewModel
import com.patrollink.presentation.component.PatrolCard
import com.patrollink.presentation.component.PrimaryAction
import com.patrollink.presentation.component.StatusTag
import com.patrollink.presentation.theme.Danger
import com.patrollink.presentation.theme.Success
import com.patrollink.service.PatrolForegroundService
import kotlinx.coroutines.delay

@Composable
fun SosScreen(uiState: AppUiState, viewModel: PatrolViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    var seconds by remember { mutableIntStateOf(3) }

    LaunchedEffect(Unit) {
        PatrolForegroundService.start(context, "SOS 上报与音频采集中")
        viewModel.activateSos()
        while (seconds > 0) {
            delay(1000)
            seconds -= 1
        }
    }

    val activated = seconds == 0 || uiState.sosActive
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF5B080A), Color(0xFF120305))))
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(52.dp))
        Text(if (activated) "紧急上报已激活" else "SOS 确认中", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
        Text("位置、环境音频和设备状态将实时同步至指挥中心", color = Color(0xFFFFCDD2), fontSize = 13.sp)
        Spacer(Modifier.height(36.dp))
        Box(
            Modifier
                .height(210.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.clip(CircleShape).background(Danger).padding(42.dp), contentAlignment = Alignment.Center) {
                Text(if (activated) "SOS" else seconds.toString(), color = Color.White, fontSize = 54.sp, fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.height(18.dp))
        PatrolCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("当前位置 (GPS)", fontWeight = FontWeight.Black)
                    StatusTag("高精度双频", Success)
                }
                Text("39.9087 N, 116.3975 E · 核心商务区 CBD-North", color = Color(0xFF5F6673), fontSize = 13.sp)
                Text("音频证据同步中：CH-01-SECURE", color = Color(0xFF5F6673), fontSize = 13.sp)
                Text("预计支援到达：4 分钟", color = Color(0xFF5F6673), fontSize = 13.sp)
            }
        }
        Spacer(Modifier.weight(1f))
        PrimaryAction(
            text = "右滑取消上报",
            onClick = {
                viewModel.cancelSos()
                PatrolForegroundService.stop(context)
                onClose()
            },
            modifier = Modifier.fillMaxWidth(),
            danger = false
        )
        Spacer(Modifier.height(16.dp))
    }
}
