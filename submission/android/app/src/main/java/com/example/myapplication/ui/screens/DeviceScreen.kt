package com.example.myapplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication.VisionDataHub
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.myapplication.ConnectionState
import com.example.myapplication.FallAlertState
import com.example.myapplication.ui.ScreenBackground
import com.example.myapplication.ui.SuccessGreen
import com.example.myapplication.ui.SuccessText
import com.example.myapplication.ui.components.AppWordmark
import com.example.myapplication.ui.components.ConnectionConfigCard
import com.example.myapplication.ui.components.DeviceBindingCard
import com.example.myapplication.ui.components.DeviceCoreCard
import com.example.myapplication.ui.components.FinderCard
import com.example.myapplication.ui.components.GuardianLocationCard
import com.example.myapplication.ui.components.StatusBanner
import com.example.myapplication.util.deviceBannerTitle
import com.example.myapplication.util.deviceMetrics

@Composable
internal fun DeviceScreen(
    connectionState: ConnectionState,
    fallAlertState: FallAlertState,
    onBuzzer: () -> Unit = {},
    onFlashlight: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val batteryPct by VisionDataHub.deviceBattery.collectAsStateWithLifecycle()
    val latencyMs by VisionDataHub.networkLatencyMs.collectAsStateWithLifecycle()
    val remoteDeviceIp by VisionDataHub.remoteDeviceIp.collectAsStateWithLifecycle()
    val latitude by VisionDataHub.deviceLatitude.collectAsStateWithLifecycle()
    val longitude by VisionDataHub.deviceLongitude.collectAsStateWithLifecycle()
    val metrics = remember(connectionState, batteryPct, latencyMs) {
        deviceMetrics(connectionState, batteryPct, latencyMs)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ScreenBackground)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            AppWordmark(
                title = "设备管理",
                subtitle = "暖阳语音助手已就绪",
            )
        }
        item {
            StatusBanner(
                title = deviceBannerTitle(connectionState),
                subtitle = "所有核心通信模块正在稳定运行中",
                icon = Icons.Filled.Shield,
                background = SuccessGreen,
                foreground = SuccessText,
            )
        }
        item {
            ConnectionConfigCard(port = 8080, remoteDeviceIp = remoteDeviceIp)
        }
        item {
            DeviceBindingCard()
        }
        item {
            DeviceCoreCard(connectionState = connectionState, metrics = metrics)
        }
        item {
            GuardianLocationCard(
                connectionState = connectionState,
                latitude = latitude,
                longitude = longitude,
            )
        }
        item {
            FinderCard(
                fallAlertState = fallAlertState,
                connectionState = connectionState,
                onBuzzer = onBuzzer,
                onFlashlight = onFlashlight,
            )
        }
    }
}
