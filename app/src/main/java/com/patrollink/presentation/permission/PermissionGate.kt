package com.patrollink.presentation.permission

import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.patrollink.data.AndroidPermissionPlanner
import com.patrollink.presentation.component.PatrolCard
import com.patrollink.presentation.component.PrimaryAction
import com.patrollink.presentation.component.SystemBars
import com.patrollink.presentation.theme.PatrolDisplay

@Composable
fun PermissionGate(content: @Composable () -> Unit) {
    val colors = PatrolDisplay.colors
    val context = LocalContext.current
    val planner = remember { AndroidPermissionPlanner() }
    val permissions = remember {
        planner.requiredPermissions(Build.VERSION.SDK_INT)
            .mapNotNull { it.toAndroidPermission() }
            .distinct()
            .toTypedArray()
    }
    var grantedMap by remember {
        mutableStateOf(permissions.associateWith { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        })
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        grantedMap = permissions.associateWith { result[it] == true || ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    }
    val missing = grantedMap.filterValues { !it }.keys.toTypedArray()

    LaunchedEffect(Unit) {
        if (missing.isNotEmpty()) launcher.launch(missing)
    }

    if (missing.isEmpty()) {
        content()
    } else {
        SystemBars(statusBarColor = colors.page, navigationBarColor = colors.page, lightStatusBar = !colors.dark, lightNavigationBar = !colors.dark)
        Column(
            Modifier.fillMaxSize().background(colors.page).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PatrolCard {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("需要授权", fontWeight = FontWeight.Black)
                    Text("执法耳机接入、录像、对讲、定位和通知需要系统权限。未授权时，相关硬件能力会保持不可用。", color = colors.textMuted)
                    PrimaryAction("继续授权", onClick = { launcher.launch(missing) }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}
