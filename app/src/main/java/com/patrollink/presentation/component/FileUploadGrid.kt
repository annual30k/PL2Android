package com.patrollink.presentation.component

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.patrollink.presentation.theme.PatrolDisplay
import com.patrollink.presentation.theme.TechBlue

enum class UploadFileState { Uploaded, Verifying, Pending }

data class UploadFileItem(
    val id: String,
    val name: String,
    val uri: Uri?,
    val mimeType: String,
    val sizeBytes: Long?,
    val source: String,
    val state: UploadFileState,
    val deletable: Boolean
)

@Composable
fun FileUploadGrid(
    files: List<UploadFileItem>,
    onAdd: () -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var previewFile by remember { mutableStateOf<UploadFileItem?>(null) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        (files + null).chunked(3).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowItems.forEach { item ->
                    Box(Modifier.weight(1f)) {
                        if (item == null) {
                            AddUploadTile(onClick = onAdd)
                        } else {
                            UploadFileTile(
                                item = item,
                                onPreview = { previewFile = item },
                                onDelete = onDelete
                            )
                        }
                    }
                }
                repeat(3 - rowItems.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
    previewFile?.let { item ->
        UploadPreviewDialog(item = item, onDismiss = { previewFile = null })
    }
}

@Composable
private fun AddUploadTile(onClick: () -> Unit) {
    val colors = PatrolDisplay.colors
    Column(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceHigh)
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.AddAPhoto, contentDescription = null, tint = colors.textSubtle, modifier = Modifier.size(34.dp))
        Spacer(Modifier.height(9.dp))
        Text("添加文件", color = colors.textSubtle, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun UploadFileTile(item: UploadFileItem, onPreview: () -> Unit, onDelete: (String) -> Unit) {
    val colors = PatrolDisplay.colors
    val bitmap = rememberUploadBitmap(item.uri)
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(if (item.uri == null) Color(0xFF111827) else colors.surfaceHigh)
            .border(1.dp, if (item.state == UploadFileState.Pending) TechBlue else colors.border, RoundedCornerShape(12.dp))
            .clickable(onClick = onPreview)
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            UploadPlaceholder(item.state, Modifier.fillMaxSize())
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)))))
        StatusBadge(item.state, Modifier.align(Alignment.BottomStart).padding(8.dp))
        if (item.deletable) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(7.dp)
                    .size(26.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color.Black.copy(alpha = 0.58f))
                    .clickable { onDelete(item.id) },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun StatusBadge(state: UploadFileState, modifier: Modifier) {
    val (bg, icon, fg) = when (state) {
        UploadFileState.Uploaded -> Triple(Color(0xFF22C55E), Icons.Filled.Check, Color.White)
        UploadFileState.Pending -> Triple(Color(0xFF22C55E), Icons.Filled.Check, Color.White)
        UploadFileState.Verifying -> Triple(TechBlue, Icons.Filled.VerifiedUser, Color.White)
    }
    Box(
        modifier
            .size(28.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun UploadPreviewDialog(item: UploadFileItem, onDismiss: () -> Unit) {
    val colors = PatrolDisplay.colors
    val bitmap = rememberUploadBitmap(item.uri)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.88f)
                .clip(RoundedCornerShape(22.dp))
                .background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(22.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surfaceHigh),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    UploadPlaceholder(item.state, Modifier.fillMaxSize())
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(item.name, color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.Black)
                    Text(item.mimeType, color = colors.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(colors.surfaceHigh)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(19.dp))
                }
            }
        }
    }
}

@Composable
private fun rememberUploadBitmap(uri: Uri?): ImageBitmap? {
    val context = LocalContext.current
    return remember(uri) {
        uri?.let {
            runCatching {
                // Keep this component dependency-free. Large production uploads should thumbnail off the UI path.
                context.contentResolver.openInputStream(it)?.use { input ->
                    BitmapFactory.decodeStream(input)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
}

@Composable
private fun UploadPlaceholder(state: UploadFileState, modifier: Modifier) {
    Box(modifier.background(Color(0xFF111827))) {
        val brush = if (state == UploadFileState.Uploaded) {
            Brush.linearGradient(listOf(Color(0xFFFF6A00), Color(0xFF8A12E8), Color(0xFFE6B64A)))
        } else {
            Brush.linearGradient(listOf(Color(0xFF4B5563), Color(0xFF020617)))
        }
        Box(Modifier.fillMaxSize().background(brush))
    }
}
