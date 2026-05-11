package com.patrollink.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrollink.domain.AppUiState
import com.patrollink.presentation.component.PatrolCard
import com.patrollink.presentation.component.PrimaryAction
import com.patrollink.presentation.theme.Muted
import com.patrollink.presentation.theme.PageBg
import com.patrollink.presentation.theme.TechBlue

@Composable
fun LoginScreen(uiState: AppUiState, onLogin: (String, String, Boolean) -> Unit) {
    var account by remember { mutableStateOf("POLICE_9527") }
    var password by remember { mutableStateOf("123456") }
    var agreed by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier.fillMaxSize().background(PageBg).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(54.dp))
        Text("智能执法协同平台", fontSize = 26.sp, fontWeight = FontWeight.Black)
        Text("INTELLIGENT LAW ENFORCEMENT", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(26.dp))
        Text(
            "生产环境",
            color = TechBlue,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(androidx.compose.ui.graphics.Color.White).padding(horizontal = 14.dp, vertical = 7.dp)
        )
        Spacer(Modifier.height(26.dp))
        PatrolCard {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = account,
                    onValueChange = { account = it },
                    label = { Text("账号") },
                    placeholder = { Text("请输入警员编号或手机号") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码") },
                    placeholder = { Text("请输入登录密码") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                PrimaryAction(
                    text = if (uiState.loginLoading) "登录中..." else "安全登录",
                    onClick = { onLogin(account, password, agreed) },
                    modifier = Modifier.fillMaxWidth()
                )
                if (uiState.loginLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally), color = TechBlue)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.Top) {
            Checkbox(checked = agreed, onCheckedChange = { agreed = it })
            Text("我已阅读并同意《服务协议》与《隐私政策》，并授权系统获取必要的执法权限。", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}
