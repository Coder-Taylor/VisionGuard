package com.example.myapplication.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.api.AlertData
import com.example.myapplication.api.RetrofitClient
import com.example.myapplication.ui.AlertRed
import com.example.myapplication.ui.CardBackground
import com.example.myapplication.ui.OfflineGray
import com.example.myapplication.ui.PrimaryBlue
import com.example.myapplication.ui.SoftGreen
import com.example.myapplication.ui.SoftOrange
import com.example.myapplication.ui.SoftPink
import com.example.myapplication.ui.SuccessGreen
import com.example.myapplication.ui.TextPrimary
import com.example.myapplication.ui.TextSecondary
import com.example.myapplication.ui.White
import com.example.myapplication.ui.components.AppPrimaryButton
import com.example.myapplication.ui.components.AppSecondaryButton
import com.example.myapplication.ui.components.EmptyState
import com.example.myapplication.ui.components.UnreadBadge
import com.example.myapplication.util.ErrorHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterialApi::class)
@Composable
internal fun HomeScreen(
    onAlertClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    // 独立作用域 — 切换 Tab 时不会被取消，确保忽视 API 能完成
    val dismissScope = remember { CoroutineScope(Dispatchers.IO + SupervisorJob()) }
    // 跨 Tab 记住已忽视的告警 ID，防止切回时重新出现
    var dismissedIds by rememberSaveable { mutableStateOf(setOf<String>()) }

    var alertCount24h by remember { mutableIntStateOf(0) }
    var onlineDeviceCount by remember { mutableIntStateOf(0) }
    var elderCount by remember { mutableIntStateOf(0) }
    var pendingAlerts by remember { mutableStateOf<List<AlertData>>(emptyList()) }
    var recentAlerts by remember { mutableStateOf<List<AlertData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val loadData: suspend () -> Unit = {
        try {
            // 仪表盘 — 设备/老人/告警汇总
            val dashResp = RetrofitClient.elderApi.getDashboard()
            if (dashResp.isSuccessful) {
                val d = dashResp.body()?.data
                alertCount24h = d?.alertCount24h ?: 0
                onlineDeviceCount = d?.onlineDeviceCount ?: 0
                elderCount = d?.elderCount ?: 0
            }
            // pending 告警（过滤已本地忽视的）
            val pendingResp = RetrofitClient.alertApi.listAlerts(status = "pending", pageSize = 5)
            if (pendingResp.isSuccessful) {
                pendingAlerts = (pendingResp.body()?.data?.list ?: emptyList())
                    .filter { it.alertId !in dismissedIds }
            }
            // 最近告警（过滤已本地忽视的）
            val recentResp = RetrofitClient.alertApi.listAlerts(page = 1, pageSize = 3)
            if (recentResp.isSuccessful) {
                recentAlerts = (recentResp.body()?.data?.list ?: emptyList())
                    .filter { it.alertId !in dismissedIds }
            }
            errorMessage = null
        } catch (e: Exception) {
            errorMessage = ErrorHelper.userMessage(e, "loadDashboard")
        }
        isLoading = false
        // 最小刷新动画时长，确保用户感知
        kotlinx.coroutines.delay(400)
        isRefreshing = false
    }

    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshKey) {
        scope.launch { loadData() }
    }

    val hasDevice = onlineDeviceCount > 0
    val isAlert = pendingAlerts.isNotEmpty()

    val infiniteTransition = rememberInfiniteTransition(label = "breath")
    val breathAlpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathAlpha",
    )

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            scope.launch { loadData() }
        },
    )

    Box(
        modifier = modifier.fillMaxSize().background(CardBackground).pullRefresh(pullRefreshState),
    ) {
        if (errorMessage != null) {
            EmptyState(
                title = errorMessage ?: "加载失败",
                onRetry = {
                    scope.launch {
                        isLoading = true
                        errorMessage = null
                        loadData()
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // === 设备连接状态 ===
                item {
                    DeviceStatusBar(
                        hasDevice = hasDevice,
                        onlineCount = onlineDeviceCount,
                        elderCount = elderCount,
                        isAlert = isAlert,
                        alertCount = alertCount24h,
                        breathAlpha = if (isAlert) breathAlpha else 1f,
                    )
                }

                // === 新发告警卡片 ===
                if (pendingAlerts.isNotEmpty()) {
                    item {
                        NewAlertCard(
                            alert = pendingAlerts.first(),
                            onViewDetail = { onAlertClick(pendingAlerts.first().alertId ?: "") },
                            onDismiss = {
                                val dismissed = pendingAlerts.first()
                                val id = dismissed.alertId ?: ""
                                dismissedIds = dismissedIds + id
                                dismissScope.launch {
                                    try {
                                        RetrofitClient.alertApi.updateAlertStatus(
                                            id,
                                            com.example.myapplication.api.UpdateAlertStatusReq("confirmed")
                                        )
                                    } catch (e: Exception) {
                                        android.util.Log.e("VisionGuard", "dismissAlert: ${e.message}", e)
                                    }
                                }
                                pendingAlerts = pendingAlerts.drop(1)
                                recentAlerts = recentAlerts.filter { it.alertId != dismissed.alertId }
                                alertCount24h = (alertCount24h - 1).coerceAtLeast(0)
                            },
                        )
                    }
                    if (pendingAlerts.size > 1) {
                        item {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                AppSecondaryButton(
                                    text = "一键忽视 (${pendingAlerts.size}条)",
                                    onClick = {
                                        val allPending = pendingAlerts.toList()
                                        val ids = allPending.mapNotNull { it.alertId }.toSet()
                                        dismissedIds = dismissedIds + ids
                                        dismissScope.launch {
                                            allPending.forEach { a ->
                                                try {
                                                    RetrofitClient.alertApi.updateAlertStatus(
                                                        a.alertId ?: "",
                                                        com.example.myapplication.api.UpdateAlertStatusReq("confirmed")
                                                    )
                                                } catch (e: Exception) {
                                                    android.util.Log.e("VisionGuard", "dismissAll: ${e.message}", e)
                                                }
                                            }
                                        }
                                        pendingAlerts = emptyList()
                                        recentAlerts = recentAlerts.filter { it.alertId !in ids }
                                        alertCount24h = 0
                                    },
                                )
                            }
                        }
                    }
                }

                // === 最近告警 ===
                if (recentAlerts.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("最近告警", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                    }
                    items(recentAlerts.size) { index ->
                        AlertHistoryCard(
                            alert = recentAlerts[index],
                            onClick = { onAlertClick(recentAlerts[index].alertId ?: "") },
                        )
                    }
                }

                // === 空状态 ===
                if (!isLoading && pendingAlerts.isEmpty() && recentAlerts.isEmpty()) {
                    item {
                        EmptyState(
                            title = if (hasDevice) "暂无告警" else "等待设备连接",
                            message = if (hasDevice) "设备运行正常，暂无告警记录"
                                else "请先绑定设备，告警信息将在此展示",
                            onRetry = null,
                        )
                    }
                }

                // 底部留白
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }
        }

        PullRefreshIndicator(
            refreshing = isRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

// ============================================================
// DeviceStatusBar — 设备连接 + 安全状态
// ============================================================

@Composable
private fun DeviceStatusBar(
    hasDevice: Boolean,
    onlineCount: Int,
    elderCount: Int,
    isAlert: Boolean,
    alertCount: Int,
    breathAlpha: Float,
) {
    val bgColor = when {
        isAlert -> SoftPink
        hasDevice -> SoftGreen
        else -> CardBackground
    }
    val accentColor = when {
        isAlert -> AlertRed
        hasDevice -> SuccessGreen
        else -> OfflineGray
    }
    val statusText = when {
        isAlert -> "有 $alertCount 条告警待处理"
        hasDevice -> "设备运行正常"
        else -> "尚未连接设备"
    }
    val indicatorDot = when {
        isAlert -> "●"
        hasDevice -> "●"
        else -> "○"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isAlert) Modifier.alpha(breathAlpha) else Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(10.dp).clip(CircleShape).background(accentColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = statusText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StatItem(label = "在线设备", value = "$onlineCount")
                StatItem(label = "监护老人", value = "$elderCount")
                StatItem(label = "24h告警", value = "$alertCount")
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(text = label, fontSize = 12.sp, color = TextSecondary)
    }
}

// ============================================================
// NewAlertCard — 新发告警卡片
// ============================================================

@Composable
private fun NewAlertCard(
    alert: AlertData,
    onViewDetail: () -> Unit,
    onDismiss: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(16.dp), spotColor = Color(0x33FF7D00)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SoftPink),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "⚠ 新发告警",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = AlertRed,
            )
            Spacer(modifier = Modifier.height(12.dp))

            alert.alertType?.let { type ->
                AlertInfoRow(label = "告警类型", value = mapAlertType(type))
            }
            alert.createdAt?.let { time ->
                AlertInfoRow(label = "告警时间", value = time)
            }
            alert.deviceId?.let { deviceId ->
                AlertInfoRow(label = "设备 ID", value = deviceId)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppPrimaryButton(
                    text = "查看详情",
                    onClick = onViewDetail,
                    modifier = Modifier.weight(1f),
                )
                AppSecondaryButton(
                    text = "忽视",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun AlertInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(text = "$label: ", fontSize = 14.sp, color = TextSecondary)
        Text(text = value, fontSize = 14.sp, color = TextPrimary)
    }
}

// ============================================================
// AlertHistoryCard — 最近告警列表项
// ============================================================

@Composable
private fun AlertHistoryCard(
    alert: AlertData,
    onClick: () -> Unit,
) {
    val timeStr = alert.createdAt?.takeLast(8)?.take(5) ?: alert.createdAt ?: ""
    val typeColor = when (alert.alertType) {
        "fall", "sos" -> AlertRed
        "device_offline", "low_battery" -> OfflineGray
        "geofence" -> Color(0xFFFF7D00)
        else -> PrimaryBlue
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (alert.status == "pending") AlertRed else OfflineGray)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mapAlertType(alert.alertType ?: "unknown"),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "设备: ${alert.deviceId ?: "-"}",
                    fontSize = 12.sp,
                    color = TextSecondary,
                )
            }
            Text(text = timeStr, fontSize = 12.sp, color = OfflineGray)
            Spacer(modifier = Modifier.width(8.dp))
            if (alert.status == "pending") {
                UnreadBadge(count = alert.duplicateCount ?: 1)
            }
        }
    }
}

// ============================================================
// Helpers
// ============================================================

private fun mapAlertType(type: String): String = when (type) {
    "fall" -> "摔倒告警"
    "obstacle" -> "避障危险"
    "sos" -> "紧急呼叫"
    "heart_rate_abnormal" -> "心率异常"
    "low_battery" -> "低电量"
    "device_offline" -> "设备离线"
    "geofence" -> "电子围栏"
    else -> type
}
