package com.patrollink.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrollink.domain.AppUiState
import com.patrollink.presentation.component.SystemBars
import com.patrollink.presentation.theme.Muted
import com.patrollink.presentation.theme.PatrolDisplay
import com.patrollink.presentation.theme.TechBlue

@Composable
fun LoginScreen(uiState: AppUiState, onLogin: (String, String, Boolean) -> Unit) {
    val colors = PatrolDisplay.colors
    SystemBars(
        statusBarColor = colors.page,
        navigationBarColor = colors.page,
        lightStatusBar = !colors.dark,
        lightNavigationBar = !colors.dark
    )
    var account by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var agreed by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        colors.page,
                        if (colors.dark) Color(0xFF0B1326) else Color(0xFFEAF1FB),
                        colors.page
                    )
                )
            )
    ) {
        Box(
            Modifier
                .size(360.dp)
                .align(Alignment.TopEnd)
                .background(
                    Brush.radialGradient(
                        listOf(TechBlue.copy(alpha = 0.08f), Color.Transparent)
                    )
                )
        )
        Box(
            Modifier
                .size(420.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.72f), Color.Transparent)
                    )
                )
        )
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(113.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .size(80.dp)
                        .shadow(18.dp, RoundedCornerShape(16.dp), ambientColor = TechBlue.copy(alpha = 0.22f), spotColor = TechBlue.copy(alpha = 0.24f))
                        .clip(RoundedCornerShape(16.dp))
                        .background(TechBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Security,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(44.dp)
                    )
                }
                Spacer(Modifier.height(21.dp))
                Text("智能执法协同平台", fontSize = 26.sp, color = colors.text, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(10.dp))
                Text(
                    "I N T E L L I G E N T   L A W   E N F O R C E M E N T",
                    color = colors.textSubtle,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.height(30.dp))
            Box(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
                    .shadow(18.dp, RoundedCornerShape(16.dp), ambientColor = Color(0xFF94A3B8).copy(alpha = 0.10f), spotColor = Color(0xFF94A3B8).copy(alpha = 0.13f))
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surface)
                    .padding(horizontal = 26.dp, vertical = 27.dp)
            ) {
                Column {
                    LoginLabel("账号")
                    Spacer(Modifier.height(14.dp))
                    LoginInput(
                        value = account,
                        onValueChange = { account = it },
                        placeholder = "请输入警员编号或手机号",
                        leadingIcon = { UserIcon() }
                    )
                    Spacer(Modifier.height(24.dp))
                    LoginLabel("密码")
                    Spacer(Modifier.height(14.dp))
                    LoginInput(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = "请输入登录密码",
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        leadingIcon = { LockIcon() },
                        trailingIcon = {
                            EyeOffIcon(
                                modifier = Modifier.clickable { passwordVisible = !passwordVisible },
                                visible = passwordVisible
                            )
                        }
                    )
                    Spacer(Modifier.height(21.dp))
                    Text(
                        "忘记密码？",
                        color = TechBlue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(32.dp))
                    LoginButton(uiState.loginLoading) { onLogin(account, password, agreed) }
                    if (uiState.loginLoading) {
                        Spacer(Modifier.height(16.dp))
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(22.dp)
                                .align(Alignment.CenterHorizontally),
                            color = TechBlue,
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                verticalAlignment = Alignment.Top
            ) {
                ConsentCheck(checked = agreed, onClick = { agreed = !agreed })
                Spacer(Modifier.width(13.dp))
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = colors.textMuted)) { append("我已阅读并同意 ") }
                        withStyle(SpanStyle(color = TechBlue, fontWeight = FontWeight.Black)) { append("《服务协议》") }
                        withStyle(SpanStyle(color = colors.textMuted)) { append(" 与 ") }
                        withStyle(SpanStyle(color = TechBlue, fontWeight = FontWeight.Black)) { append("《隐私政策》") }
                        withStyle(SpanStyle(color = colors.textMuted)) { append("，并授权\n系统获取必要的执法权限。") }
                    },
                    fontSize = 13.sp,
                    lineHeight = 22.sp,
                    modifier = Modifier
                        .clickable { agreed = !agreed }
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp, top = 27.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(width = 34.dp, height = 1.dp).background(Muted.copy(alpha = 0.38f)))
                Text("  SECURED BY ENCRYPTION  ", color = Muted.copy(alpha = 0.45f), fontSize = 10.sp, fontWeight = FontWeight.Black)
                Box(Modifier.size(width = 34.dp, height = 1.dp).background(Muted.copy(alpha = 0.38f)))
            }
        }
    }
}

@Composable
private fun LoginLabel(text: String) {
    Text(text, color = PatrolDisplay.colors.text, fontSize = 17.sp, fontWeight = FontWeight.Black)
}

@Composable
private fun LoginInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    val colors = PatrolDisplay.colors
    Row(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.control)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leadingIcon()
        Spacer(Modifier.width(14.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(placeholder, color = colors.textSubtle, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                visualTransformation = visualTransformation,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = colors.text,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (trailingIcon != null) {
            Spacer(Modifier.width(10.dp))
            trailingIcon()
        }
    }
}

@Composable
private fun LoginButton(loading: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .shadow(14.dp, RoundedCornerShape(12.dp), ambientColor = TechBlue.copy(alpha = 0.25f), spotColor = TechBlue.copy(alpha = 0.35f))
            .clip(RoundedCornerShape(12.dp))
            .background(TechBlue)
            .clickable(enabled = !loading, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (loading) "登录中..." else "安全登录", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
        if (!loading) {
            Spacer(Modifier.width(10.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun ConsentCheck(checked: Boolean, onClick: () -> Unit) {
    val colors = PatrolDisplay.colors
    Box(
        Modifier
            .padding(top = 3.dp)
            .size(16.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (checked) TechBlue else colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

@Composable
private fun UserIcon() {
    val colors = PatrolDisplay.colors
    Icon(
        imageVector = Icons.Filled.Person,
        contentDescription = null,
        tint = colors.textMuted,
        modifier = Modifier.size(20.dp)
    )
}

@Composable
private fun LockIcon() {
    val colors = PatrolDisplay.colors
    Icon(
        imageVector = Icons.Filled.Lock,
        contentDescription = null,
        tint = colors.textMuted,
        modifier = Modifier.size(20.dp)
    )
}

@Composable
private fun EyeOffIcon(modifier: Modifier = Modifier, visible: Boolean = false) {
    val colors = PatrolDisplay.colors
    Icon(
        imageVector = if (visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
        contentDescription = null,
        tint = colors.textMuted,
        modifier = modifier.size(22.dp)
    )
}
