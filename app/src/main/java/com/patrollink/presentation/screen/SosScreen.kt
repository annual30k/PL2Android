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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
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
    SystemBars(statusBarColor = Color(0xFF991B1B), navigationBarColor = Color(0xFF0B0203), lightStatusBar = false, lightNavigationBar = false)
    val context = LocalContext.current
    var seconds by remember { mutableIntStateOf(5) }
    var activationStarted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (seconds > 0) {
            delay(1000)
            seconds -= 1
        }
        activationStarted = true
        PatrolForegroundService.start(context, "SOS 上报与音频采集中")
        viewModel.activateSos()
    }

    val activated = activationStarted && uiState.sosActive
    val activating = activationStarted && !activated
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF991B1B), Color(0xFF450A0A), Color(0xFF0B0203))))
    ) {
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFFEF4444).copy(alpha = 0.38f), Color.Transparent))))
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 96.dp, start = 22.dp, end = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    when {
                        activated -> "紧急上报已激活"
                        activating -> "正在发起紧急上报"
                        else -> "即将发起紧急上报"
                    },
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
                Text(
                    if (activated || activating) "您的位置和环境音频正在被实时监控" else "倒计时结束前可右滑取消",
                    color = Color(0xFFFFCDD2),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
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
                        Text(if (activated) "ACTIVE" else "5秒后自动上报", color = Color.White.copy(alpha = 0.80f), fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                }
                Spacer(Modifier.height(38.dp))
                SlideToCancel(
                    onCancel = {
                        onClose()
                        if (activationStarted || uiState.sosActive) {
                            viewModel.cancelSos()
                        }
                        PatrolForegroundService.stop(context)
                    }
                )
            }
            Spacer(Modifier.weight(1f))
            Column(
                Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 28.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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
