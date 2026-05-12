package com.patrollink.presentation.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrollink.domain.AppUiState
import com.patrollink.presentation.PatrolViewModel
import com.patrollink.presentation.component.SystemBars
import com.patrollink.service.PatrolForegroundService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SosScreen(uiState: AppUiState, viewModel: PatrolViewModel, onClose: () -> Unit) {
    SystemBars(statusBarColor = Color(0xFF111827), navigationBarColor = Color(0xFF0B0203), lightStatusBar = false, lightNavigationBar = false)
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
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF991B1B), Color(0xFF450A0A), Color(0xFF0B0203))))
    ) {
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFFEF4444).copy(alpha = 0.38f), Color.Transparent))))
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(Color(0xFF111827))
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Security, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Text("ForceLink", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.FiberManualRecord, contentDescription = null, tint = Color(0xFF22C55E), modifier = Modifier.size(10.dp))
                    Text("SECURE", color = Color(0xFF22C55E), fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 52.dp, start = 22.dp, end = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(if (activated) "紧急上报已激活" else "正在上报位置与录音...", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                Text("您的位置和环境音频正在被实时监控", color = Color(0xFFFFCDD2), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(42.dp))
                Box(Modifier.size(256.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(226.dp)
                            .clip(CircleShape)
                            .border(12.dp, Color(0xFF7F1D1D).copy(alpha = 0.55f), CircleShape)
                    )
                    Box(
                        Modifier
                            .size(204.dp)
                            .clip(CircleShape)
                            .border(9.dp, Color.White.copy(alpha = 0.88f), CircleShape)
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (activated) "SOS" else seconds.toString(), color = Color.White, fontSize = 76.sp, fontWeight = FontWeight.Black)
                        Text(if (activated) "ACTIVE" else "长按 3S 激活", color = Color.White.copy(alpha = 0.80f), fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                }
                Spacer(Modifier.height(38.dp))
                SlideToCancel(
                    onCancel = {
                        viewModel.cancelSos()
                        PatrolForegroundService.stop(context)
                        onClose()
                    }
                )
            }
            Spacer(Modifier.weight(1f))
            Column(Modifier.padding(horizontal = 28.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black.copy(alpha = 0.42f))
                        .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                        .padding(15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(Icons.Filled.Sync, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(22.dp))
                    Column {
                        Text("音频证据同步中: CH-01-SECURE...", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
                        Text("Cloud Secure · Backup ETA 4M", color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Column {
                        Text("当前位置 (GPS)", color = Color.White.copy(alpha = 0.60f), fontSize = 10.sp, fontWeight = FontWeight.Black)
                        Text("39.9087° N", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("116.3975° E", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("连接状态", color = Color.White.copy(alpha = 0.60f), fontSize = 10.sp, fontWeight = FontWeight.Black)
                        Text("高精度双频", color = Color(0xFF60A5FA), fontSize = 12.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
private fun SlideToCancel(onCancel: () -> Unit) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val thumbSize = 48.dp
    val horizontalPadding = 8.dp
    val dragOffset = remember { Animatable(0f) }
    var completed by remember { mutableStateOf(false) }

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(50.dp))
            .background(Color.Black.copy(alpha = 0.32f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50.dp))
            .padding(horizontalPadding),
        contentAlignment = Alignment.CenterStart
    ) {
        val maxDrag = with(density) { (maxWidth - thumbSize - horizontalPadding * 2).toPx() }.coerceAtLeast(0f)
        val threshold = maxDrag * 0.82f
        val progress = if (maxDrag == 0f) 0f else (dragOffset.value / maxDrag).coerceIn(0f, 1f)

        Box(
            Modifier
                .fillMaxWidth(progress)
                .height(48.dp)
                .clip(RoundedCornerShape(50.dp))
                .background(Color.White.copy(alpha = 0.10f))
        )
        Text(
            if (progress > 0.82f) "松手取消上报" else "右滑取消上报",
            color = Color.White.copy(alpha = 0.45f + progress * 0.35f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Box(
            Modifier
                .offset { IntOffset(dragOffset.value.roundToInt(), 0) }
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.White)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        if (!completed) {
                            scope.launch {
                                dragOffset.snapTo((dragOffset.value + delta).coerceIn(0f, maxDrag))
                            }
                        }
                    },
                    onDragStopped = {
                        if (dragOffset.value >= threshold && !completed) {
                            completed = true
                            scope.launch {
                                dragOffset.animateTo(maxDrag, tween(durationMillis = 120))
                                onCancel()
                            }
                        } else {
                            scope.launch {
                                dragOffset.animateTo(0f, tween(durationMillis = 180))
                            }
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = Color(0xFF991B1B), modifier = Modifier.size(30.dp))
        }
    }
}
