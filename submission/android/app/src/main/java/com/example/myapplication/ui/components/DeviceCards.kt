package com.example.myapplication.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication.ConnectionState
import com.example.myapplication.FallAlertState
import com.example.myapplication.VisionDataHub
import com.example.myapplication.api.OnlineDevice
import com.example.myapplication.api.BindDeviceRequest
import com.example.myapplication.api.UnbindDeviceRequest
import com.example.myapplication.api.RetrofitClient
import com.example.myapplication.ui.CardBackground
import com.example.myapplication.ui.PrimaryText
import com.example.myapplication.ui.SecondaryText
import com.example.myapplication.ui.StatMetric
import com.example.myapplication.ui.SuccessGreen
import com.example.myapplication.ui.SuccessText
import com.example.myapplication.ui.SurfaceSoft
import com.example.myapplication.ui.WarmYellow
import com.example.myapplication.ui.WarmYellowDark
import com.example.myapplication.util.finderSupportingText
import com.example.myapplication.util.guardianLocationText
import com.example.myapplication.util.guardianLocationUpdateText
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun DeviceCoreCard(
    connectionState: ConnectionState,
    metrics: List<StatMetric>,
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "主控制器",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        color = WarmYellowDark,
                    )
                    Text(
                        text = "ESP32-S3 核心",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                        color = PrimaryText,
                    )
                }
                Surface(
                    color = SuccessGreen,
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (connectionState == ConnectionState.CONNECTED)
                                        Color(0xFF54B35D) else WarmYellowDark
                                ),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (connectionState == ConnectionState.CONNECTED) "已连接" else "待同步",
                            color = SuccessText,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                metrics.forEach { metric ->
                    MetricCard(metric = metric, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
internal fun GuardianLocationCard(
    connectionState: ConnectionState,
    latitude: Double? = null,
    longitude: Double? = null,
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = WarmYellow),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = PrimaryText,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "设备定位",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = PrimaryText,
                )
            }
            Card(
                modifier = Modifier.padding(top = 16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.55f)),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (latitude != null && longitude != null) {
                        Text(
                            text = "纬度 ${"%.4f".format(latitude)}  经度 ${"%.4f".format(longitude)}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = PrimaryText,
                        )
                        Text(
                            text = "已接收到设备 GPS 坐标",
                            modifier = Modifier.padding(top = 6.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = PrimaryText.copy(alpha = 0.72f),
                        )
                    } else {
                        Text(
                            text = guardianLocationText(connectionState),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = PrimaryText,
                        )
                        Text(
                            text = guardianLocationUpdateText(connectionState),
                            modifier = Modifier.padding(top = 6.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = PrimaryText.copy(alpha = 0.72f),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SecondaryWideButton(
                    title = "地图查看",
                    icon = Icons.Filled.Map,
                    modifier = Modifier.weight(1f),
                    containerColor = PrimaryText,
                    contentColor = Color.White,
                )
                SecondaryWideButton(
                    title = "发送位置",
                    icon = Icons.Filled.LocationOn,
                    modifier = Modifier.weight(1f),
                    containerColor = Color.White,
                    contentColor = PrimaryText,
                )
            }
        }
    }
}

@Composable
internal fun FinderCard(
    fallAlertState: FallAlertState,
    connectionState: ConnectionState,
    onBuzzer: () -> Unit = {},
    onFlashlight: () -> Unit = {},
) {
    var showNotConnectedDialog by remember { mutableStateOf(false) }

    if (showNotConnectedDialog) {
        AlertDialog(
            onDismissRequest = { showNotConnectedDialog = false },
            title = { Text("设备未连接") },
            text = { Text("请先连接 ESP32 设备后再使用此功能。") },
            confirmButton = {
                TextButton(onClick = { showNotConnectedDialog = false }) {
                    Text("知道了")
                }
            },
        )
    }

    val isConnected = connectionState == ConnectionState.CONNECTED

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceSoft),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "一键找寻",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = PrimaryText,
            )
            Text(
                text = finderSupportingText(fallAlertState),
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = SecondaryText,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FinderOptionCard(
                    title = "蜂鸣器",
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    onClick = {
                        if (isConnected) {
                            onBuzzer()
                        } else {
                            showNotConnectedDialog = true
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                FinderOptionCard(
                    title = "灯光闪烁",
                    icon = Icons.Filled.FlashlightOn,
                    onClick = {
                        if (isConnected) {
                            onFlashlight()
                        } else {
                            showNotConnectedDialog = true
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun FinderOptionCard(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = WarmYellowDark,
                modifier = Modifier.size(40.dp),
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = PrimaryText,
            )
        }
    }
}

@Composable
internal fun ConnectionConfigCard(
    port: Int = 8080,
    remoteDeviceIp: String? = null,
) {
    var localIp by remember { mutableStateOf("获取中…") }
    LaunchedEffect(Unit) {
        localIp = withContext(Dispatchers.IO) { getLocalIpAddress() ?: "未知" }
    }
    var ipOverride by remember { mutableStateOf("") }
    var showEditDialog by remember { mutableStateOf(false) }
    val displayIp = ipOverride.ifBlank { localIp }

    if (showEditDialog) {
        var dialogText by remember(showEditDialog) { mutableStateOf(displayIp) }
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("修改 IP 地址") },
            text = {
                OutlinedTextField(
                    value = dialogText,
                    onValueChange = { dialogText = it },
                    label = { Text("IP 地址") },
                    placeholder = { Text(localIp) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    ipOverride = dialogText.trim()
                    showEditDialog = false
                }) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text("取消") }
            },
        )
    }

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "连接配置",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    color = WarmYellowDark,
                )
                Text(
                    text = "ESP32 设备连接信息",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = PrimaryText,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            ConfigItem(
                label = "手机 IP 地址",
                value = displayIp,
                hint = "请在 ESP32 上配置此地址",
                trailingContent = {
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "修改 IP",
                            tint = SecondaryText,
                        )
                    }
                },
            )
            Spacer(modifier = Modifier.height(12.dp))
            ConfigItem(
                label = "云端地址",
                value = remoteDeviceIp ?: "未连接",
                hint = if (remoteDeviceIp == null) "请检查网络" else "已连接云端",
            )
            Spacer(modifier = Modifier.height(12.dp))
            ConfigItem(
                label = "连接协议",
                value = "HTTP",
                hint = "全 HTTP 通信 / JSON 数据交互",
            )
        }
    }
}

@Composable
private fun ConfigItem(
    label: String,
    value: String,
    hint: String,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceSoft),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = SecondaryText,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = PrimaryText,
                )
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryText.copy(alpha = 0.7f),
                )
            }
            trailingContent?.invoke()
        }
    }
}

@Composable
internal fun DeviceBindingCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val boundDevice by VisionDataHub.boundDevice.collectAsStateWithLifecycle()
    val userProfile by VisionDataHub.userProfile.collectAsStateWithLifecycle()

    var onlineDevices by remember { mutableStateOf<List<OnlineDevice>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("在线设备") },
            text = {
                if (isSearching) {
                    Text("正在搜索…")
                } else if (onlineDevices.isEmpty()) {
                    Text(searchError ?: "未找到在线设备")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        onlineDevices.forEach { device ->
                            val isBound = boundDevice?.deviceId == device.deviceId
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isBound) SuccessGreen else SurfaceSoft,
                                ),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = device.deviceName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = PrimaryText,
                                        )
                                        Text(
                                            text = device.ipAddress,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = SecondaryText,
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                try {
                                                    if (isBound) {
                                                        RetrofitClient.deviceApi.unbindDevice(
                                                            userId = userProfile.userId,
                                                            req = UnbindDeviceRequest(device.deviceId),
                                                        )
                                                        VisionDataHub.updateBoundDevice(null)
                                                    } else {
                                                        RetrofitClient.deviceApi.bindDevice(
                                                            userId = userProfile.userId,
                                                            req = BindDeviceRequest(device.deviceId),
                                                        )
                                                        VisionDataHub.updateBoundDevice(device)
                                                    }
                                                    showDialog = false
                                                } catch (e: Exception) {
                                                    withContext(Dispatchers.Main) {
                                                        Toast.makeText(context, "操作失败，请稍后重试", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        },
                                    ) {
                                        Icon(
                                            imageVector = if (isBound) Icons.Filled.LinkOff else Icons.Filled.Link,
                                            contentDescription = if (isBound) "解绑" else "绑定",
                                            tint = if (isBound) PrimaryText else WarmYellowDark,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) { Text("关闭") }
            },
        )
    }

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "设备绑定",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        color = WarmYellowDark,
                    )
                    Text(
                        text = if (boundDevice != null) boundDevice!!.deviceName else "未绑定设备",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = PrimaryText,
                    )
                    if (boundDevice != null) {
                        Text(
                            text = "ID: ${boundDevice!!.deviceId}",
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondaryText,
                        )
                    }
                }
                Surface(
                    color = if (boundDevice != null) SuccessGreen else SurfaceSoft,
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text(
                        text = if (boundDevice != null) "已绑定" else "未绑定",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        color = if (boundDevice != null) SuccessText else SecondaryText,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = {
                    showDialog = true
                    isSearching = true
                    searchError = null
                    onlineDevices = emptyList()
                    scope.launch {
                        try {
                            val response = withContext(Dispatchers.IO) {
                                RetrofitClient.deviceApi.listOnlineDevices()
                            }
                            onlineDevices = response.body()?.devices ?: emptyList()
                            if (onlineDevices.isEmpty()) searchError = "未找到在线设备"
                        } catch (e: Exception) {
                            searchError = "搜索失败：${e.message}"
                        } finally {
                            isSearching = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.BluetoothSearching, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("搜索并绑定设备")
            }
        }
    }
}

private fun getLocalIpAddress(): String? {
    return try {
        NetworkInterface.getNetworkInterfaces()?.toList()
            ?.flatMap { it.inetAddresses?.toList() ?: emptyList() }
            ?.filterIsInstance<Inet4Address>()
            ?.firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }
            ?.hostAddress
    } catch (e: Exception) {
        null
    }
}
