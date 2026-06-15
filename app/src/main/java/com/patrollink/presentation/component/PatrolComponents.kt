package com.patrollink.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrollink.domain.AlertLevel
import com.patrollink.presentation.theme.Danger
import com.patrollink.presentation.theme.Navy
import com.patrollink.presentation.theme.PatrolDisplay
import com.patrollink.presentation.theme.Success
import com.patrollink.presentation.theme.TechBlue
import com.patrollink.presentation.theme.Warning

enum class PatrolConfirmStyle {
    Normal,
    Warning,
    Danger
}

@Composable
fun PatrolConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissText: String = "取消",
    warningText: String? = null,
    style: PatrolConfirmStyle = PatrolConfirmStyle.Danger,
    loading: Boolean = false,
    loadingText: String = "正在处理",
    icon: ImageVector? = null
) {
    val colors = PatrolDisplay.colors
    val accent = when (style) {
        PatrolConfirmStyle.Normal -> TechBlue
        PatrolConfirmStyle.Warning -> Warning
        PatrolConfirmStyle.Danger -> Danger
    }
    AlertDialog(
        modifier = modifier,
        containerColor = colors.surface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(12.dp),
        onDismissRequest = { if (!loading) onDismiss() },
        title = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.08f))
                    .border(1.dp, accent.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon ?: Icons.Filled.Info, contentDescription = null, tint = accent, modifier = Modifier.size(21.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, color = colors.text, fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.Black)
                    Text(if (loading) loadingText else "请确认后继续", color = accent, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Black)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(message, color = colors.textMuted, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
                warningText?.takeIf { it.isNotBlank() }?.let { warning ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(accent.copy(alpha = 0.08f))
                            .border(1.dp, accent.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 9.dp)
                    ) {
                        Text(warning, color = accent, fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !loading,
                shape = RoundedCornerShape(9.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    disabledContainerColor = accent.copy(alpha = 0.62f),
                    contentColor = Color.White,
                    disabledContentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(loadingText, fontWeight = FontWeight.Black, fontSize = 13.sp, maxLines = 1)
                } else {
                    Text(confirmText, fontWeight = FontWeight.Black, fontSize = 13.sp, maxLines = 1)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !loading) {
                Text(dismissText, color = colors.textMuted.copy(alpha = if (loading) 0.48f else 1f), fontWeight = FontWeight.Black)
            }
        }
    )
}

@Composable
fun ForceTopBar(
    title: String? = null,
    dark: Boolean = true,
    onSos: () -> Unit,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    val colors = PatrolDisplay.colors
    val bg = if (dark) Navy else colors.topBar
    val main = if (dark) Color.White else colors.text
    val sub = if (dark) Color(0xFF94A3B8) else colors.textSubtle
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(bg)
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawLine(
                    color = colors.border.copy(alpha = 0.45f),
                    start = Offset(0f, size.height - stroke / 2f),
                    end = Offset(size.width, size.height - stroke / 2f),
                    strokeWidth = stroke
                )
            }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            leading?.invoke()
            Icon(Icons.Filled.Security, contentDescription = null, tint = Danger, modifier = Modifier.size(24.dp))
            Column {
                Text("ForceLink", color = main, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Black)
                if (title != null) Text(title, color = sub, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            trailing?.invoke()
            Text(
                "SOS",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFDC2626))
                    .clickable(onClick = onSos)
                    .padding(horizontal = 17.dp, vertical = 9.dp)
            )
        }
    }
}

@Composable
fun PatrolCard(
    modifier: Modifier = Modifier,
    radius: Int = 16,
    dark: Boolean = false,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit
) {
    val colors = PatrolDisplay.colors
    val container = if (dark) colors.surfaceHigh else colors.surface
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radius.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = if (dark) 0.dp else 2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
        content = { Box(Modifier.padding(padding)) { content() } }
    )
}

@Composable
fun StatusTag(text: String, color: Color, filled: Boolean = false) {
    Text(
        text = text,
        color = if (filled) Color.White else color,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        fontWeight = FontWeight.Black,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (filled) color else color.copy(alpha = 0.11f))
            .border(1.dp, color.copy(alpha = if (filled) 0f else 0.16f), RoundedCornerShape(4.dp))
            .padding(horizontal = 9.dp, vertical = 4.dp)
    )
}

@Composable
fun AlertLevelTag(level: AlertLevel) {
    val color = when (level) {
        AlertLevel.Critical -> Color(0xFFDC2626)
        AlertLevel.Warning -> Color(0xFFF97316)
        AlertLevel.Info -> TechBlue
    }
    val text = when (level) {
        AlertLevel.Critical -> "紧急"
        AlertLevel.Warning -> "预警"
        AlertLevel.Info -> "提示"
    }
    StatusTag(text, color, filled = true)
}

@Composable
fun MetricTile(label: String, value: String, accent: Color = TechBlue, progress: Float? = null) {
    val colors = PatrolDisplay.colors
    PatrolCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
                    Text(label, color = colors.textSubtle, fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
                Text(value, color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(99.dp)),
                    color = accent,
                    trackColor = colors.control
                )
            }
        }
    }
}

@Composable
fun PrimaryAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, danger: Boolean = false) {
    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (danger) Color(0xFFDC2626) else TechBlue),
        contentPadding = PaddingValues(horizontal = 12.dp)
    ) {
        Text(text, fontWeight = FontWeight.Black, fontSize = 16.sp, maxLines = 1)
    }
}

@Composable
fun OfflineBanner(online: Boolean) {
    if (!online) {
        val colors = PatrolDisplay.colors
        val container = if (colors.dark) Danger.copy(alpha = 0.18f) else Color(0xFFFFF1F2)
        val content = if (colors.dark) Color(0xFFFFB4B8) else Color(0xFFDC2626)
        Row(
            Modifier
                .fillMaxWidth()
                .background(container)
                .padding(9.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(content))
            Spacer(Modifier.width(8.dp))
            Text("网络已断开", color = content, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        }
    }
}

@Composable
fun SectionTitle(text: String, color: Color? = null) {
    Text(
        text,
        color = color ?: PatrolDisplay.colors.text,
        fontSize = 20.sp,
        lineHeight = 25.sp,
        fontWeight = FontWeight.Black
    )
}

@Composable
fun SegmentedTabs(
    left: String,
    right: String,
    leftSelected: Boolean,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = PatrolDisplay.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.control)
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SegmentButton(left, leftSelected, onLeft, Modifier.weight(1f))
        SegmentButton(right, !leftSelected, onRight, Modifier.weight(1f))
    }
}

@Composable
private fun SegmentButton(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = PatrolDisplay.colors
    Text(
        text,
        color = if (selected) colors.text else colors.textMuted,
        fontSize = 13.sp,
        fontWeight = FontWeight.Black,
        textAlign = TextAlign.Center,
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) colors.controlSelected else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(top = 9.dp)
    )
}

@Composable
fun ActionTile(label: String, glyph: String, active: Boolean = false, danger: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = PatrolDisplay.colors
    val accent = if (!enabled) colors.textSubtle else when {
        danger || active && label.contains("录像") -> Danger
        active -> Success
        else -> TechBlue
    }
    PatrolCard(
        modifier = Modifier
            .height(128.dp)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        padding = PaddingValues(0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().height(128.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                ActionGlyph(glyph = glyph, color = accent)
            }
            Spacer(Modifier.height(18.dp))
            Text(
                label,
                color = if (enabled) colors.textMuted else colors.textSubtle,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ActionGlyph(glyph: String, color: Color) {
    val imageVector = when (glyph) {
        "video" -> Icons.Filled.Videocam
        "stop" -> Icons.Filled.Stop
        "talk" -> Icons.Filled.Mic
        "wifi" -> Icons.Filled.Wifi
        "check" -> Icons.Filled.CheckCircle
        "info" -> Icons.Filled.Info
        else -> Icons.Filled.CameraAlt
    }
    Icon(
        imageVector = imageVector,
        contentDescription = null,
        tint = color,
        modifier = Modifier.size(28.dp)
    )
}

@Composable
fun MediaThumbBackground(kind: String, modifier: Modifier = Modifier) {
    val brush = when (kind) {
        "PHOTO" -> Brush.linearGradient(listOf(Color(0xFF7C3AED), Color(0xFF0891B2)))
        "AUDIO" -> Brush.linearGradient(listOf(Color(0xFF0F172A), Color(0xFF334155)))
        else -> Brush.linearGradient(listOf(Color(0xFF111827), Color(0xFF1D4ED8), Color(0xFFEA580C)))
    }
    Box(modifier.background(brush)) {
        Text(
            when (kind) {
                "PHOTO" -> "IMG"
                "AUDIO" -> "WAV"
                else -> "REC"
            },
            color = Color.White.copy(alpha = 0.24f),
            fontSize = 34.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
fun SmallInfo(label: String, value: String, modifier: Modifier = Modifier, dark: Boolean = false) {
    val colors = PatrolDisplay.colors
    Column(modifier) {
        Text(label, color = colors.textSubtle, fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(4.dp))
        Text(value, color = colors.text, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun DeviceStatPill(text: String, color: Color) {
    val colors = PatrolDisplay.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(text, color = colors.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Black)
    }
}
