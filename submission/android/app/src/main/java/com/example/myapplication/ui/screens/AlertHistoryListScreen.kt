package com.example.myapplication.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.api.AlertData
import com.example.myapplication.api.RetrofitClient
import com.example.myapplication.ui.gradientBackground
import com.example.myapplication.ui.CardBackground
import com.example.myapplication.ui.OfflineGray
import com.example.myapplication.ui.TextPrimary
import com.example.myapplication.ui.TextSecondary
import com.example.myapplication.ui.White
import com.example.myapplication.ui.components.AppSecondaryButton
import com.example.myapplication.ui.components.EmptyState
import com.example.myapplication.ui.components.SectionTitle
import com.example.myapplication.ui.components.UnreadBadge
import com.example.myapplication.util.ErrorHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterialApi::class)
@Composable
internal fun AlertHistoryListScreen(
    onAlertClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var alerts by remember { mutableStateOf<List<AlertData>>(emptyList()) }
    var currentPage by remember { mutableIntStateOf(1) }
    var hasMore by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun loadFirstPage() {
        scope.launch {
            try {
                val resp = RetrofitClient.alertApi.listAlerts(page = 1, pageSize = 10)
                if (resp.isSuccessful) {
                    val data = resp.body()?.data
                    alerts = data?.list ?: emptyList()
                    hasMore = (data?.list?.size ?: 0) >= 10
                    currentPage = 1
                }
            } catch (e: Exception) {
                errorMessage = ErrorHelper.userMessage(e, "loadAlertHistory")
            }
            isLoading = false
            kotlinx.coroutines.delay(400)
            isRefreshing = false
        }
    }

    LaunchedEffect(Unit) { loadFirstPage() }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            scope.launch { loadFirstPage() }
        },
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .gradientBackground()
            .pullRefresh(pullRefreshState),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SectionTitle(
                text = "告警历史",
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            if (errorMessage != null) {
                EmptyState(
                    title = errorMessage ?: "加载失败",
                    onRetry = { loadFirstPage() },
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (alerts.isEmpty()) {
                EmptyState(
                    title = "暂无告警记录",
                    message = "绑定设备后，告警记录将在此显示",
                    onRetry = { loadFirstPage() },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(alerts.size) { index ->
                        val alert = alerts[index]
                        val timeStr = alert.createdAt?.takeLast(8)?.take(5) ?: alert.createdAt ?: ""

                        Card(
                            onClick = { onAlertClick(alert.alertId ?: "") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = mapAlertType(alert.alertType ?: "unknown"),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "设备: ${alert.deviceId ?: "-"}",
                                        fontSize = 12.sp,
                                        color = TextSecondary,
                                    )
                                }
                                Text(
                                    text = timeStr,
                                    fontSize = 12.sp,
                                    color = OfflineGray,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                if (alert.status == "pending") {
                                    UnreadBadge(count = alert.duplicateCount ?: 1)
                                }
                            }
                        }
                    }

                    if (hasMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                AppSecondaryButton(
                                    text = if (isLoading) "加载中..." else "加载更多",
                                    onClick = {
                                        if (!isLoading) {
                                            scope.launch {
                                                isLoading = true
                                                try {
                                                    val nextPage = currentPage + 1
                                                    val resp = RetrofitClient.alertApi.listAlerts(
                                                        page = nextPage,
                                                        pageSize = 10
                                                    )
                                                    if (resp.isSuccessful) {
                                                        val data = resp.body()?.data
                                                        val newItems = data?.list ?: emptyList()
                                                        alerts = alerts + newItems
                                                        currentPage = nextPage
                                                        hasMore = newItems.size >= 10
                                                    }
                                                } catch (e: Exception) {
                                                    android.util.Log.e("VisionGuard", "loadMore: ${e.message}", e)
                                                }
                                                isLoading = false
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        PullRefreshIndicator(
            refreshing = isRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

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
