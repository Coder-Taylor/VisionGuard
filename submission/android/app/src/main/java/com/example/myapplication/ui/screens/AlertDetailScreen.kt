package com.example.myapplication.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.api.AlertDetailData
import com.example.myapplication.api.RetrofitClient
import com.example.myapplication.api.UpdateAlertStatusReq
import com.example.myapplication.ui.gradientBackground
import com.example.myapplication.ui.OfflineGray
import com.example.myapplication.ui.PrimaryBlue
import com.example.myapplication.ui.TextPrimary
import com.example.myapplication.ui.TextSecondary
import com.example.myapplication.ui.White
import com.example.myapplication.ui.components.AppPrimaryButton
import com.example.myapplication.util.ErrorHelper
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AlertDetailScreen(
    alertId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var alert by remember { mutableStateOf<AlertDetailData?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(alertId) {
        scope.launch {
            try {
                val resp = RetrofitClient.alertApi.getAlertDetail(alertId)
                if (resp.isSuccessful && resp.body()?.data != null) {
                    alert = resp.body()!!.data
                } else {
                    errorMessage = "加载失败"
                }
            } catch (e: Exception) {
                errorMessage = ErrorHelper.userMessage(e, "loadAlertDetail")
            }
        }
    }

    Scaffold(
        modifier = modifier.gradientBackground(),
        topBar = {
            TopAppBar(
                title = { Text("告警详情", fontWeight = FontWeight.Bold, color = White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = White,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PrimaryBlue,
                    titleContentColor = White,
                ),
            )
        },
        containerColor = Color.Transparent,
    ) { innerPadding ->
        val detail = alert
        if (detail != null) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 告警基本信息
                item {
                    DetailCard {
                        DetailRow("告警类型", mapAlertType(detail.alertType ?: "-"))
                        DetailRow("告警等级", mapAlertLevel(detail.alertLevel ?: "-"))
                        DetailRow("处理状态", mapAlertStatus(detail.status ?: "-"))
                    }
                }

                // 详细信息
                item {
                    DetailCard {
                        DetailRow("告警时间", detail.createdAt ?: "-")
                        DetailRow("设备 ID", detail.deviceId ?: "-")
                        DetailRow("老人 ID", detail.elderId ?: "-")
                        if (!detail.description.isNullOrBlank()) {
                            DetailRow("描述", detail.description ?: "")
                        }
                        if (!detail.resolution.isNullOrBlank()) {
                            DetailRow("处理说明", detail.resolution ?: "")
                        }
                    }
                }

                // 时间线 — 后端返回 at(action time)/action/by 三个字段
                item {
                    val timeline = detail.timeline
                    if (timeline != null && timeline.isNotEmpty()) {
                        Text(
                            text = "处理时间线",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        )
                        timeline.forEach { entry ->
                            val time = entry["at"]?.toString() ?: ""
                            val action = entry["action"]?.toString() ?: ""
                            val by = entry["by"]?.toString() ?: ""
                            TimelineItem(
                                time = time,
                                action = mapTimelineAction(action),
                                by = by,
                            )
                        }
                    }
                }

                // 操作按钮区
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // 待处理 → 显示"确认"
                        if (detail.status == "pending") {
                            AppPrimaryButton(
                                text = "确认告警",
                                onClick = {
                                    scope.launch {
                                        try {
                                            RetrofitClient.alertApi.updateAlertStatus(
                                                alertId, UpdateAlertStatusReq("confirmed")
                                            )
                                            onBack()
                                        } catch (e: Exception) {
                                            android.util.Log.e("VisionGuard", "confirm: ${e.message}", e)
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        // 已确认 → 显示"解决"
                        if (detail.status == "confirmed") {
                            AppPrimaryButton(
                                text = "标记解决",
                                onClick = {
                                    scope.launch {
                                        try {
                                            RetrofitClient.alertApi.resolveAlert(
                                                alertId,
                                                com.example.myapplication.api.ResolveAlertReq("用户手动解决")
                                            )
                                            onBack()
                                        } catch (e: Exception) {
                                            android.util.Log.e("VisionGuard", "resolve: ${e.message}", e)
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        // 未关闭 → 显示"关闭"
                        if (detail.status != "closed") {
                            com.example.myapplication.ui.components.AppSecondaryButton(
                                text = "关闭告警",
                                onClick = {
                                    scope.launch {
                                        try {
                                            RetrofitClient.alertApi.updateAlertStatus(
                                                alertId, UpdateAlertStatusReq("closed")
                                            )
                                            onBack()
                                        } catch (e: Exception) {
                                            android.util.Log.e("VisionGuard", "close: ${e.message}", e)
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        } else if (errorMessage != null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = errorMessage ?: "", fontSize = 16.sp, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun DetailCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, fontSize = 14.sp, color = TextSecondary)
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
    }
}

@Composable
private fun TimelineItem(time: String, action: String, by: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row {
            Text(text = time, fontSize = 12.sp, color = OfflineGray)
            Spacer(modifier = Modifier.weight(1f))
            Text(text = action, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }
        if (by.isNotEmpty()) {
            Text(text = "操作者: $by", fontSize = 12.sp, color = TextSecondary)
        }
    }
}

// 对齐后端: fall/obstacle/sos/heart_rate_abnormal/low_battery/device_offline/geofence
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

// 对齐后端: critical/warning/info/notice
private fun mapAlertLevel(level: String): String = when (level) {
    "critical" -> "紧急"
    "warning" -> "严重"
    "info" -> "一般"
    "notice" -> "提示"
    else -> level
}

// 对齐后端: pending/confirmed/resolved/closed
private fun mapAlertStatus(status: String): String = when (status) {
    "pending" -> "待处理"
    "confirmed" -> "已确认"
    "resolved" -> "已解决"
    "closed" -> "已关闭"
    else -> status
}

// 对齐后端 timeline action: created/confirmed/resolved/closed
private fun mapTimelineAction(action: String): String = when (action) {
    "created" -> "创建告警"
    "confirmed" -> "确认告警"
    "resolved" -> "已解决"
    "closed" -> "已关闭"
    else -> action
}
