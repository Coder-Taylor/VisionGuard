package com.example.myapplication.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.api.RetrofitClient
import com.example.myapplication.api.UpdateProfileReq
import com.example.myapplication.auth.AuthTokenHolder
import com.example.myapplication.ui.gradientBackground
import com.example.myapplication.ui.AlertRed
import com.example.myapplication.ui.CardBackground
import com.example.myapplication.ui.OfflineGray
import com.example.myapplication.ui.PrimaryBlue
import com.example.myapplication.ui.SuccessGreen
import com.example.myapplication.ui.TextPrimary
import com.example.myapplication.ui.TextSecondary
import com.example.myapplication.ui.White
import com.example.myapplication.ui.components.AppConfirmDialog
import com.example.myapplication.ui.components.StatusTag
import com.example.myapplication.ui.components.UnreadBadge
import com.example.myapplication.util.AuthPreference
import com.example.myapplication.util.ErrorHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ProfileScreen(
    onNavigateToDeviceManagement: () -> Unit,
    onNavigateToUserSettings: () -> Unit,
    onNavigateToElderManagement: () -> Unit,
    onNavigateToNotificationCenter: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf("") }
    var editError by remember { mutableStateOf<String?>(null) }
    var isEditSubmitting by remember { mutableStateOf(false) }
    val nickname = AuthTokenHolder.displayName.ifEmpty { "监护人" }
    val phone = AuthTokenHolder.phone.ifEmpty { "未绑定" }
    val isOnline = AuthTokenHolder.token != null
    var unreadCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        try {
            val resp = withContext(Dispatchers.IO) {
                RetrofitClient.notificationApi.listNotifications(pageSize = 50)
            }
            if (resp.isSuccessful) {
                val list = resp.body()?.data?.list ?: emptyList()
                unreadCount = list.count { it.read != true }
            }
        } catch (_: Exception) {}
    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { if (!isEditSubmitting) { showEditDialog = false; editError = null } },
            title = { Text("编辑昵称", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it; editError = null },
                    label = { Text("监护人昵称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )
                if (editError != null) {
                    Text(
                        text = editError!!,
                        color = AlertRed,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newName = editName.trim()
                        if (newName.isBlank()) {
                            editError = "昵称不能为空"
                            return@TextButton
                        }
                        isEditSubmitting = true
                        scope.launch {
                            try {
                                val resp = withContext(Dispatchers.IO) {
                                    RetrofitClient.userProfileApi.updateUserProfile(UpdateProfileReq(newName))
                                }
                                val body = resp.body()
                                if (resp.isSuccessful && body?.code == 0) {
                                    AuthTokenHolder.displayName = newName
                                    withContext(Dispatchers.IO) {
                                        AuthPreference.saveUserInfo(context, newName, AuthTokenHolder.phone)
                                    }
                                    showEditDialog = false
                                    editError = null
                                } else {
                                    editError = body?.message ?: "修改失败，请重试"
                                }
                            } catch (e: Exception) {
                                editError = ErrorHelper.userMessage(e, "updateProfile")
                            }
                            isEditSubmitting = false
                        }
                    },
                    enabled = !isEditSubmitting,
                ) {
                    Text(if (isEditSubmitting) "保存中…" else "保存", color = PrimaryBlue)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showEditDialog = false; editError = null },
                    enabled = !isEditSubmitting,
                ) { Text("取消") }
            },
        )
    }

    if (showLogoutDialog) {
        AppConfirmDialog(
            title = "退出登录",
            message = "确定退出当前账号？",
            confirmText = "确定退出",
            cancelText = "取消",
            confirmColor = AlertRed,
            onConfirm = {
                showLogoutDialog = false
                onLogout()
            },
            onDismiss = { showLogoutDialog = false },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .gradientBackground(),
    ) {
        // === 个人信息区 ===
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 头像
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(PrimaryBlue),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = nickname.take(1),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = White,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 昵称 + 在线标签
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = nickname,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                StatusTag(
                    text = if (isOnline) "在线" else "离线",
                    color = if (isOnline) SuccessGreen else OfflineGray,
                    backgroundColor = if (isOnline) SuccessGreen.copy(alpha = 0.1f)
                    else OfflineGray.copy(alpha = 0.1f),
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = phone, fontSize = 14.sp, color = TextSecondary)
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = {
                editName = nickname
                showEditDialog = true
            }) {
                Text("编辑资料", color = PrimaryBlue, fontSize = 14.sp)
            }
        }

        // === 功能列表 ===
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 我的老人
            ProfileMenuItem(
                icon = { Icon(Icons.Outlined.People, contentDescription = null, tint = PrimaryBlue) },
                text = "我的老人",
                onClick = onNavigateToElderManagement,
            )

            // 设备绑定与管理
            ProfileMenuItem(
                icon = { Icon(Icons.Outlined.Devices, contentDescription = null, tint = PrimaryBlue) },
                text = "设备绑定与管理",
                onClick = onNavigateToDeviceManagement,
            )

            // 消息通知
            ProfileMenuItem(
                icon = {
                    Icon(Icons.Outlined.Notifications, contentDescription = null, tint = PrimaryBlue)
                    if (unreadCount > 0) {
                        UnreadBadge(count = unreadCount)
                    }
                },
                text = "消息通知",
                onClick = onNavigateToNotificationCenter,
            )

            // 用户设置
            ProfileMenuItem(
                icon = { Icon(Icons.Outlined.Settings, contentDescription = null, tint = PrimaryBlue) },
                text = "用户设置",
                onClick = onNavigateToUserSettings,
            )

            // 退出登录（红色）
            ProfileMenuItem(
                icon = {
                    Icon(
                        Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = null,
                        tint = AlertRed,
                    )
                },
                text = "退出登录",
                textColor = AlertRed,
                onClick = { showLogoutDialog = true },
            )
        }
    }
}

@Composable
private fun ProfileMenuItem(
    icon: @Composable () -> Unit,
    text: String,
    onClick: () -> Unit,
    textColor: androidx.compose.ui.graphics.Color = TextPrimary,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                fontSize = 18.sp,
                color = textColor,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = OfflineGray,
            )
        }
    }
}
