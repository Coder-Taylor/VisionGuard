package com.example.myapplication.ui.screens

import android.content.Context
import android.graphics.Color
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Refresh
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapsInitializer
import com.amap.api.maps.TextureMapView
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.LatLngBounds
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.MyLocationStyle
import com.amap.api.maps.model.PolylineOptions
import com.example.myapplication.api.ElderData
import com.example.myapplication.api.LocationData
import com.example.myapplication.api.RetrofitClient
import com.example.myapplication.ui.gradientBackground
import com.example.myapplication.ui.CardBackground
import com.example.myapplication.ui.OfflineGray
import com.example.myapplication.ui.PrimaryBlue
import com.example.myapplication.ui.TextPrimary
import com.example.myapplication.ui.TextSecondary
import com.example.myapplication.ui.White
import com.example.myapplication.ui.components.EmptyState
import com.example.myapplication.util.ErrorHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MapScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var mapView by remember { mutableStateOf<TextureMapView?>(null) }
    var aMap by remember { mutableStateOf<AMap?>(null) }
    var elders by remember { mutableStateOf<List<ElderData>>(emptyList()) }
    var selectedElderIdx by remember { mutableStateOf(0) }
    var locationCache by remember { mutableStateOf<Map<String, LocationData>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    fun loadAndDisplay() {
        scope.launch {
            isLoading = true
            errorMsg = null
            try {
                val elderResp = withContext(Dispatchers.IO) { RetrofitClient.elderApi.listMyElders() }
                if (elderResp.isSuccessful) {
                    elders = elderResp.body()?.data ?: emptyList()
                }
                // 为每个绑定设备的老人获取位置
                val newCache = mutableMapOf<String, LocationData>()
                elders.forEach { e ->
                    if (!e.deviceId.isNullOrBlank()) {
                        try {
                            val locResp = withContext(Dispatchers.IO) {
                                RetrofitClient.locationApi.getLatestLocation(elderId = e.elderId)
                            }
                            if (locResp.isSuccessful && locResp.body()?.data != null) {
                                newCache[e.elderId ?: ""] = locResp.body()!!.data!!
                            }
                        } catch (_: Exception) {}
                    }
                }
                locationCache = newCache
                // 渲染标记到地图
                val map = aMap
                if (map != null) {
                    map.clear()
                    val boundsBuilder = LatLngBounds.Builder()
                    var hasPoint = false

                    newCache.forEach { (elderId, loc) ->
                        val lat = loc.lat ?: return@forEach
                        val lng = loc.lng ?: return@forEach
                        val elder = elders.find { it.elderId == elderId }
                        val title = elder?.name ?: "设备位置"
                        val snippet = loc.createdAt?.take(16)?.replace("T", " ") ?: ""

                        map.addMarker(
                            MarkerOptions()
                                .position(LatLng(lat, lng))
                                .title(title)
                                .snippet(snippet)
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                        )
                        boundsBuilder.include(LatLng(lat, lng))
                        hasPoint = true
                    }

                    // 加载告警标记
                    try {
                        val alertResp = withContext(Dispatchers.IO) {
                            RetrofitClient.locationApi.getAlertMarkers()
                        }
                        if (alertResp.isSuccessful) {
                            alertResp.body()?.data?.forEach { marker ->
                                val lat = marker.lat ?: return@forEach
                                val lng = marker.lng ?: return@forEach
                                map.addMarker(
                                    MarkerOptions()
                                        .position(LatLng(lat, lng))
                                        .title("告警: ${mapAlertType(marker.alertType ?: "")}")
                                        .snippet(marker.createdAt?.take(16)?.replace("T", " ") ?: "")
                                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                                )
                                boundsBuilder.include(LatLng(lat, lng))
                                hasPoint = true
                            }
                        }
                    } catch (_: Exception) {}

                    // 加载当前选中老人的轨迹
                    val selElder = elders.getOrNull(selectedElderIdx)
                    if (selElder != null && !selElder.deviceId.isNullOrBlank()) {
                        try {
                            val end = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                                .format(java.util.Date())
                            val start = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                                .format(java.util.Date(System.currentTimeMillis() - 86400000))
                            val trajResp = withContext(Dispatchers.IO) {
                                RetrofitClient.locationApi.getTrajectory(
                                    elderId = selElder.elderId, start = start, end = end,
                                )
                            }
                            if (trajResp.isSuccessful) {
                                val points = trajResp.body()?.data?.list?.mapNotNull { pt ->
                                    val lat = pt.lat ?: return@mapNotNull null
                                    val lng = pt.lng ?: return@mapNotNull null
                                    LatLng(lat, lng)
                                } ?: emptyList()
                                if (points.size >= 2) {
                                    map.addPolyline(
                                        PolylineOptions()
                                            .addAll(points)
                                            .color(Color.parseColor("#165DFF"))
                                            .width(8f)
                                    )
                                }
                            }
                        } catch (_: Exception) {}
                    }

                    if (hasPoint) {
                        map.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 80))
                    }
                }
            } catch (e: Exception) {
                errorMsg = ErrorHelper.userMessage(e, "loadMap")
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { loadAndDisplay() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("位置地图", fontWeight = FontWeight.Bold, color = White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = White)
                    }
                },
                actions = {
                    IconButton(onClick = { loadAndDisplay() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新", tint = White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PrimaryBlue, titleContentColor = White),
            )
        },
        containerColor = White,
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            // 老人选择器
            if (elders.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .gradientBackground()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    elders.forEachIndexed { idx, elder ->
                        val selected = idx == selectedElderIdx
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (selected) PrimaryBlue else White)
                                .clickable {
                                    selectedElderIdx = idx
                                    loadAndDisplay()
                                }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = elder.name ?: "老人${idx + 1}",
                                fontSize = 13.sp,
                                color = if (selected) White else TextPrimary,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }

            // 地图主体
            if (errorMsg != null) {
                EmptyState(
                    title = errorMsg!!,
                    message = "请检查网络连接后重试",
                    onRetry = { loadAndDisplay() },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(modifier = Modifier.weight(1f)) {
                    AndroidView(
                        factory = { ctx ->
                            MapsInitializer.updatePrivacyShow(ctx, true, true)
                            MapsInitializer.updatePrivacyAgree(ctx, true)
                            TextureMapView(ctx).apply {
                                onCreate(null)
                                mapView = this
                                aMap = this.map
                                this.map.apply {
                                    uiSettings.isMyLocationButtonEnabled = false  // 不显示手机定位按钮，用自定义"定位老人"
                                    uiSettings.isZoomControlsEnabled = true
                                    uiSettings.isZoomGesturesEnabled = true
                                    uiSettings.isScaleControlsEnabled = true
                                    isMyLocationEnabled = false  // 不显示手机蓝色定位点
                                    mapType = AMap.MAP_TYPE_NORMAL
                                    moveCamera(CameraUpdateFactory.zoomTo(12f))
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )

                    // 图例（放左上，避免挡住右下角原生缩放按钮）
                    Card(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            LegendItem(Color.parseColor("#165DFF"), "设备位置")
                            LegendItem(Color.RED, "告警位置")
                            LegendItem(Color.parseColor("#165DFF"), "24h 轨迹", isLine = true)
                        }
                    }

                    // 定位老人按钮（右上角，替代原生我的位置按钮）
                    Card(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .size(40.dp)
                            .clickable {
                                val selIdx = selectedElderIdx
                                if (selIdx in elders.indices) {
                                    val elder = elders[selIdx]
                                    val loc = locationCache[elder.elderId ?: ""]
                                    if (loc != null && loc.lat != null && loc.lng != null) {
                                        aMap?.animateCamera(
                                            CameraUpdateFactory.newLatLngZoom(
                                                LatLng(loc.lat!!, loc.lng!!), 16f
                                            )
                                        )
                                    }
                                }
                            },
                        shape = CircleShape,
                        colors = CardDefaults.cardColors(containerColor = White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Outlined.MyLocation,
                                contentDescription = "定位老人",
                                tint = PrimaryBlue,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mapView?.onDestroy()
        }
    }
}

@Composable
private fun LegendItem(color: Int, label: String, isLine: Boolean = false) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isLine) {
            Box(
                modifier = Modifier
                    .width(14.dp)
                    .height(3.dp)
                    .background(androidx.compose.ui.graphics.Color(color))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(androidx.compose.ui.graphics.Color(color))
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, fontSize = 11.sp, color = TextSecondary)
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
