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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.api.ApiResponse
import com.example.myapplication.api.ElderData
import com.example.myapplication.api.LocationData
import com.example.myapplication.api.RetrofitClient
import com.example.myapplication.ui.gradientBackground
import com.example.myapplication.ui.CardBackground
import com.example.myapplication.ui.LightBlue
import com.example.myapplication.ui.OfflineGray
import com.example.myapplication.ui.PrimaryBlue
import com.example.myapplication.ui.SoftGreen
import com.example.myapplication.ui.SuccessGreen
import com.example.myapplication.ui.TextPrimary
import com.example.myapplication.ui.TextSecondary
import com.example.myapplication.ui.White
import com.example.myapplication.ui.components.AppPrimaryButton
import com.example.myapplication.ui.components.CompactTopBar
import com.example.myapplication.ui.components.EmptyState
import com.example.myapplication.ui.components.StatusTag
import com.example.myapplication.util.ErrorHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
internal fun LocationScreen(
    onBack: () -> Unit,
    onNavigateToMap: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    var elders by remember { mutableStateOf<List<ElderData>>(emptyList()) }
    var selectedElderId by remember { mutableStateOf<String?>(null) }
    var latestLocation by remember { mutableStateOf<LocationData?>(null) }
    var trajectory by remember { mutableStateOf<List<LocationData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    fun loadData() {
        scope.launch {
            isLoading = true
            errorMsg = null
            try {
                // 先加载老人列表
                val elderResp = withContext(Dispatchers.IO) { RetrofitClient.elderApi.listMyElders() }
                if (elderResp.isSuccessful) {
                    elders = elderResp.body()?.data ?: emptyList()
                }
                // 选第一个有设备的老人
                val targetElder = elders.firstOrNull { !it.deviceId.isNullOrBlank() }
                if (targetElder != null) {
                    selectedElderId = targetElder.elderId
                    val locResp = withContext(Dispatchers.IO) {
                        RetrofitClient.locationApi.getLatestLocation(elderId = targetElder.elderId)
                    }
                    if (locResp.isSuccessful && locResp.body()?.data != null) {
                        latestLocation = locResp.body()!!.data
                    }
                    // 最近24h轨迹
                    val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                    val end = fmt.format(java.util.Date()) + "+08:00"
                    val start = fmt.format(java.util.Date(System.currentTimeMillis() - 86400000)) + "+08:00"
                    val rawBody = withContext(Dispatchers.IO) {
                        val resp = RetrofitClient.locationApi.getTrajectory(
                            elderId = targetElder.elderId, start = start, end = end,
                        )
                        if (resp.isSuccessful) resp.body()?.string() else null
                    }
                    if (rawBody != null) {
                        trajectory = parseTrajectoryData(rawBody) ?: emptyList()
                    }
                } else {
                    errorMsg = "暂无已绑定设备的老人"
                }
            } catch (e: Exception) {
                errorMsg = ErrorHelper.userMessage(e, "loadLocation")
            }
            isLoading = false
            kotlinx.coroutines.delay(400)
            isRefreshing = false
        }
    }

    LaunchedEffect(Unit) { loadData() }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            scope.launch { loadData() }
        },
    )

    Scaffold(
        modifier = modifier.gradientBackground(),
        topBar = {
            CompactTopBar(title = "实时定位", onBack = onBack) {
                IconButton(onClick = { loadData() }) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "刷新", tint = White)
                }
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
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("加载中…", fontSize = 16.sp, color = TextSecondary)
                }
            } else if (errorMsg != null) {
                EmptyState(
                    title = errorMsg!!,
                    message = "请先绑定设备后再查看定位",
                    onRetry = { loadData() },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                // 当前位置卡片
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.size(48.dp).clip(CircleShape).background(LightBlue),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(24.dp))
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("当前位置", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                    Text(latestLocation?.createdAt?.take(16)?.replace("T", " ") ?: "暂无数据", fontSize = 12.sp, color = OfflineGray)
                                }
                                StatusTag(
                                    text = if (latestLocation != null) "定位成功" else "无数据",
                                    color = if (latestLocation != null) SuccessGreen else OfflineGray,
                                    backgroundColor = if (latestLocation != null) SuccessGreen.copy(alpha = 0.1f)
                                    else OfflineGray.copy(alpha = 0.1f),
                                )
                            }

                            if (latestLocation != null) {
                                Spacer(modifier = Modifier.height(16.dp))
                                val loc = latestLocation!!
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    LocationStat("纬度", String.format("%.4f", loc.lat ?: 0.0))
                                    LocationStat("经度", String.format("%.4f", loc.lng ?: 0.0))
                                    if (loc.accuracy != null) {
                                        LocationStat("精度", "${String.format("%.1f", loc.accuracy)}m")
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                AppPrimaryButton(
                                    text = "在地图中查看",
                                    onClick = onNavigateToMap,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }

                // 轨迹卡片
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Route, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("24小时轨迹", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Spacer(modifier = Modifier.weight(1f))
                                Text("${trajectory.size} 个点", fontSize = 12.sp, color = OfflineGray)
                            }

                            if (trajectory.isEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("暂无轨迹数据", fontSize = 14.sp, color = TextSecondary)
                            } else {
                                Spacer(modifier = Modifier.height(12.dp))
                                trajectory.take(10).forEach { point ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Outlined.Timer, contentDescription = null, tint = OfflineGray, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = point.createdAt?.take(16)?.replace("T", " ") ?: "-",
                                                fontSize = 12.sp,
                                                color = OfflineGray,
                                            )
                                        }
                                        Text(
                                            text = "${String.format("%.4f", point.lat ?: 0.0)}, ${String.format("%.4f", point.lng ?: 0.0)}",
                                            fontSize = 12.sp,
                                            color = TextPrimary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 老人选择器
                if (elders.size > 1) {
                    item {
                        Text(
                            text = "可切换监护老人查看位置",
                            fontSize = 12.sp,
                            color = OfflineGray,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }

        PullRefreshIndicator(
            refreshing = isRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
    }
}

@Composable
private fun LocationStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(text = label, fontSize = 11.sp, color = TextSecondary)
    }
}

private fun parseTrajectoryData(rawBody: String): List<LocationData>? {
    return try {
        val type = object : com.google.gson.reflect.TypeToken<ApiResponse<List<LocationData>>>() {}.type
        val apiResp: ApiResponse<List<LocationData>> = com.google.gson.Gson().fromJson(rawBody, type)
        apiResp.data
    } catch (_: Exception) {
        null
    }
}
