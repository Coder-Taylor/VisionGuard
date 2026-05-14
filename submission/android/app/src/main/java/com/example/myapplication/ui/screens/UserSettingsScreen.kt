package com.example.myapplication.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.api.ChangePasswordReq
import com.example.myapplication.api.RetrofitClient
import com.example.myapplication.api.UpdateProfileReq
import com.example.myapplication.auth.AuthTokenHolder
import com.example.myapplication.ui.gradientBackground
import com.example.myapplication.ui.CardBackground
import com.example.myapplication.ui.OfflineGray
import com.example.myapplication.ui.PrimaryBlue
import com.example.myapplication.ui.SuccessGreen
import com.example.myapplication.ui.TextPrimary
import com.example.myapplication.ui.TextSecondary
import com.example.myapplication.ui.White
import com.example.myapplication.ui.components.AppPrimaryButton
import com.example.myapplication.ui.components.CompactTopBar
import com.example.myapplication.ui.components.StatusTag
import com.example.myapplication.util.AuthPreference
import com.example.myapplication.util.ErrorHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UserSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val phoneNumber = AuthTokenHolder.phone.ifEmpty { "未绑定" }

    var showChangePassword by remember { mutableStateOf(false) }
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var oldPwVisible by remember { mutableStateOf(false) }
    var newPwVisible by remember { mutableStateOf(false) }
    var confirmPwVisible by remember { mutableStateOf(false) }
    var pwError by remember { mutableStateOf<String?>(null) }
    var isChangingPw by remember { mutableStateOf(false) }

    // 换绑手机号
    var showPhoneDialog by remember { mutableStateOf(false) }
    var newPhone by remember { mutableStateOf("") }
    var phoneError by remember { mutableStateOf<String?>(null) }
    var isChangingPhone by remember { mutableStateOf(false) }

    // 换绑手机号弹窗
    if (showPhoneDialog) {
        AlertDialog(
            onDismissRequest = { if (!isChangingPhone) { showPhoneDialog = false; phoneError = null } },
            title = { Text("换绑手机号", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("请输入新手机号（暂无需验证码）", fontSize = 14.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newPhone,
                        onValueChange = { newPhone = it.trim(); phoneError = null },
                        label = { Text("新手机号") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )
                    if (phoneError != null) {
                        Text(text = phoneError!!, color = androidx.compose.ui.graphics.Color(0xFFF53F3F), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val phoneNum = newPhone.trim()
                        if (phoneNum.isBlank()) { phoneError = "请输入手机号"; return@TextButton }
                        if (!phoneNum.matches(Regex("^1[3-9]\\d{9}$"))) { phoneError = "手机号格式不正确"; return@TextButton }
                        isChangingPhone = true
                        scope.launch {
                            try {
                                val resp = withContext(Dispatchers.IO) {
                                    RetrofitClient.userProfileApi.updateUserProfile(
                                        UpdateProfileReq(AuthTokenHolder.displayName, phoneNum)
                                    )
                                }
                                val body = resp.body()
                                if (resp.isSuccessful && body?.code == 0) {
                                    AuthTokenHolder.phone = phoneNum
                                    withContext(Dispatchers.IO) {
                                        AuthPreference.saveUserInfo(context, AuthTokenHolder.displayName, phoneNum)
                                    }
                                    showPhoneDialog = false; phoneError = null
                                    android.widget.Toast.makeText(context, "手机号已更新", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    phoneError = body?.message ?: "换绑失败，请重试"
                                }
                            } catch (e: Exception) {
                                phoneError = ErrorHelper.userMessage(e, "updatePhone")
                            }
                            isChangingPhone = false
                        }
                    },
                    enabled = !isChangingPhone,
                ) { Text(if (isChangingPhone) "更换中…" else "确定", color = PrimaryBlue) }
            },
            dismissButton = {
                TextButton(onClick = { showPhoneDialog = false; phoneError = null }, enabled = !isChangingPhone) { Text("取消") }
            },
        )
    }

    Scaffold(
        modifier = modifier.gradientBackground(),
        topBar = { CompactTopBar(title = "用户设置", onBack = onBack) },
        containerColor = Color.Transparent,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 手机号
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "手机号", fontSize = 14.sp, color = TextSecondary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = phoneNumber, fontSize = 16.sp, color = TextPrimary)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            StatusTag(
                                text = if (phoneNumber != "未绑定") "已绑定" else "未绑定",
                                color = if (phoneNumber != "未绑定") SuccessGreen else OfflineGray,
                                backgroundColor = if (phoneNumber != "未绑定") SuccessGreen.copy(alpha = 0.1f) else OfflineGray.copy(alpha = 0.1f),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = {
                                newPhone = if (phoneNumber != "未绑定") phoneNumber else ""
                                showPhoneDialog = true
                            }) {
                                Text(if (phoneNumber != "未绑定") "换绑" else "绑定", color = PrimaryBlue, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            // 修改密码按钮
            item {
                if (!showChangePassword) {
                    AppPrimaryButton(
                        text = "修改密码",
                        onClick = { showChangePassword = true; pwError = null },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // 修改密码表单
            if (showChangePassword) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(text = "修改密码", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

                            OutlinedTextField(
                                value = oldPassword, onValueChange = { oldPassword = it; pwError = null },
                                label = { Text("原密码") }, singleLine = true,
                                visualTransformation = if (oldPwVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                trailingIcon = {
                                    IconButton(onClick = { oldPwVisible = !oldPwVisible }) {
                                        Icon(
                                            imageVector = if (oldPwVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                            contentDescription = if (oldPwVisible) "隐藏密码" else "显示密码",
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                            )
                            OutlinedTextField(
                                value = newPassword, onValueChange = { newPassword = it; pwError = null },
                                label = { Text("新密码（至少8位）") }, singleLine = true,
                                visualTransformation = if (newPwVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                trailingIcon = {
                                    IconButton(onClick = { newPwVisible = !newPwVisible }) {
                                        Icon(
                                            imageVector = if (newPwVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                            contentDescription = if (newPwVisible) "隐藏密码" else "显示密码",
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                            )
                            OutlinedTextField(
                                value = confirmPassword, onValueChange = { confirmPassword = it; pwError = null },
                                label = { Text("确认新密码") }, singleLine = true,
                                visualTransformation = if (confirmPwVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                trailingIcon = {
                                    IconButton(onClick = { confirmPwVisible = !confirmPwVisible }) {
                                        Icon(
                                            imageVector = if (confirmPwVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                            contentDescription = if (confirmPwVisible) "隐藏密码" else "显示密码",
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                            )
                            if (pwError != null) {
                                Text(text = pwError!!, color = androidx.compose.ui.graphics.Color(0xFFF53F3F), fontSize = 12.sp)
                            }
                            AppPrimaryButton(
                                text = if (isChangingPw) "提交中…" else "提交修改",
                                onClick = {
                                    when {
                                        oldPassword.isBlank() || newPassword.isBlank() || confirmPassword.isBlank() ->
                                            pwError = "请填写所有密码字段"
                                        newPassword.length < 8 ->
                                            pwError = "新密码至少8位"
                                        newPassword != confirmPassword ->
                                            pwError = "两次新密码不一致"
                                        else -> {
                                            pwError = null
                                            isChangingPw = true
                                            scope.launch {
                                                try {
                                                    val resp = withContext(Dispatchers.IO) {
                                                        RetrofitClient.authApi.changePassword(
                                                            ChangePasswordReq(oldPassword, newPassword)
                                                        )
                                                    }
                                                    val body = resp.body()
                                                    if (resp.isSuccessful && body?.code == 0) {
                                                        showChangePassword = false
                                                        oldPassword = ""; newPassword = ""; confirmPassword = ""
                                                        oldPwVisible = false; newPwVisible = false; confirmPwVisible = false
                                                        android.widget.Toast.makeText(context, "密码修改成功", android.widget.Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        pwError = body?.message ?: "修改失败，请重试"
                                                    }
                                                } catch (e: Exception) {
                                                    pwError = ErrorHelper.userMessage(e, "changePassword")
                                                }
                                                isChangingPw = false
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}
