package com.patrollink.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrollink.domain.AlertLevel
import com.patrollink.presentation.theme.Border
import com.patrollink.presentation.theme.Danger
import com.patrollink.presentation.theme.Ink
import com.patrollink.presentation.theme.Muted
import com.patrollink.presentation.theme.Navy
import com.patrollink.presentation.theme.Success
import com.patrollink.presentation.theme.SurfaceWhite
import com.patrollink.presentation.theme.TechBlue
import com.patrollink.presentation.theme.Warning

@Composable
fun PatrolTopBar(title: String, onSos: () -> Unit, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Navy)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text("PatrolLink", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Text(title, color = Color(0xFFC3C5D9), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            trailing?.invoke()
            Button(
                onClick = onSos,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Danger),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text("SOS", fontWeight = FontWeight.Black, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun PatrolCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        content = { Box(Modifier.padding(16.dp)) { content() } }
    )
}

@Composable
fun StatusTag(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.18f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
fun AlertLevelTag(level: AlertLevel) {
    val color = when (level) {
        AlertLevel.Critical -> Danger
        AlertLevel.Warning -> Warning
        AlertLevel.Info -> TechBlue
    }
    val text = when (level) {
        AlertLevel.Critical -> "高危"
        AlertLevel.Warning -> "预警"
        AlertLevel.Info -> "提示"
    }
    StatusTag(text, color)
}

@Composable
fun MetricTile(label: String, value: String, accent: Color = TechBlue, progress: Float? = null) {
    PatrolCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
                Text(label, color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1)
            }
            Text(value, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                    color = accent,
                    trackColor = Border
                )
            }
        }
    }
}

@Composable
fun PrimaryAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, danger: Boolean = false) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (danger) Danger else TechBlue)
    ) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun OfflineBanner(online: Boolean) {
    if (!online) {
        Row(
            Modifier.fillMaxWidth().background(Danger.copy(alpha = 0.1f)).padding(10.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text("网络已断开，处置记录将离线缓存", color = Danger, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Black)
    Spacer(Modifier.height(10.dp))
}
