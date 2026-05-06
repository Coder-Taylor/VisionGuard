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
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.api.NotificationData
import com.example.myapplication.api.RetrofitClient
import com.example.myapplication.ui.gradientBackground
import com.example.myapplication.ui.AlertRed
import com.example.myapplication.ui.CardBackground
import com.example.myapplication.ui.OfflineGray
import com.example.myapplication.ui.PrimaryBlue
import com.example.myapplication.ui.TextPrimary
import com.example.myapplication.ui.TextSecondary
import com.example.myapplication.ui.White
import com.example.myapplication.ui.components.CompactTopBar
import com.example.myapplication.ui.components.EmptyState
import com.example.myapplication.ui.components.UnreadBadge
import com.example.myapplication.util.ErrorHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
internal fun NotificationListScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var notifications by remember { mutableStateOf<List<NotificationData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    fun loadNotifications() {
        scope.launch {
            isLoading = true
            try {
                val resp = withContext(Dispatchers.IO) {
                    RetrofitClient.notificationApi.listNotifications()
                }
                if (resp.isSuccessful) {
                    notifications = resp.body()?.data?.list ?: emptyList()
                }
            } catch (e: Exception) {
                errorMsg = ErrorHelper.userMessage(e, "loadNotifications")
            }
            isLoading = false
            kotlinx.coroutines.delay(400)
            isRefreshing = false
        }
    }

    LaunchedEffect(Unit) { loadNotifications() }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            scope.launch { loadNotifications() }
        },
    )

    Scaffold(
        modifier = modifier.gradientBackground(),
        topBar = {
            CompactTopBar(title = "消息通知", onBack = onBack) {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                RetrofitClient.notificationApi.markAllRead()
                            }
                            loadNotifications()
                        } catch (_: Exception) {}
                    }
                }) { Text("全部已读", color = White, fontSize = 13.sp) }
            }
        },
        containerColor = Color.Transparent,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .pullRefresh(pullRefreshState),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (isLoading && notifications.isEmpty()) {
                    item { EmptyState(title = "加载中…", message = "正在获取消息", onRetry = null) }
                } else if (!isLoading && notifications.isEmpty()) {
                    item {
                        EmptyState(
                            title = "暂无消息",
                            message = "设备告警和通知会在这里显示",
                            onRetry = { loadNotifications() },
                        )
                    }
                } else {
                    items(count = notifications.size) { index ->
                        val n = notifications[index]
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (n.read == true) White else White.copy(alpha = 0.95f),
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (n.read != true) {
                                            UnreadBadge(count = 1, modifier = Modifier.padding(end = 6.dp))
                                        }
                                        Text(
                                            text = n.title ?: "通知",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = n.body ?: "",
                                        fontSize = 13.sp,
                                        color = TextSecondary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        n.priority?.let {
                                            val pColor = when (it) {
                                                "P0" -> AlertRed
                                                "P1" -> AlertRed.copy(alpha = 0.7f)
                                                else -> OfflineGray
                                            }
                                            Text(it, fontSize = 10.sp, color = pColor, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }
                                        n.createdAt?.let {
                                            Text(it.take(16).replace("T", " "), fontSize = 10.sp, color = OfflineGray)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }

            PullRefreshIndicator(
                refreshing = isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}
