package com.example.myapplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication.ConnectionState
import com.example.myapplication.FallAlertState
import com.example.myapplication.VisionDataHub
import com.example.myapplication.ui.DangerRed
import com.example.myapplication.ui.PrimaryText
import com.example.myapplication.ui.ScreenBackground
import com.example.myapplication.ui.WarmYellow
import com.example.myapplication.ui.components.AppWordmark
import com.example.myapplication.ui.components.MetricCard
import com.example.myapplication.ui.components.PillActionButton
import com.example.myapplication.ui.components.RadarPanel
import com.example.myapplication.ui.components.SettingPill
import com.example.myapplication.ui.components.StatusBanner
import com.example.myapplication.ui.components.VolumeCard
import com.example.myapplication.util.obstacleDangerHeadline
import com.example.myapplication.util.obstacleDescription
import com.example.myapplication.util.obstacleGuidance
import com.example.myapplication.util.obstacleHeadline
import com.example.myapplication.util.obstacleMetrics
import com.example.myapplication.util.obstacleSensitivityLabel

@Composable
internal fun ObstacleScreen(
    connectionState: ConnectionState,
    fallAlertState: FallAlertState,
    obstacleEnabled: Boolean = true,
    volume: Float = 0.66f,
    onToggleObstacle: () -> Unit = {},
    onVolumeChange: (Float) -> Unit = {},
    onEmergencyHelp: () -> Unit = {},
    onSensitivityClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val batteryPct by VisionDataHub.deviceBattery.collectAsStateWithLifecycle()
    val radarDistance by VisionDataHub.radarDistance.collectAsStateWithLifecycle()
    val latencyMs by VisionDataHub.networkLatencyMs.collectAsStateWithLifecycle()
    val fallConfig by VisionDataHub.fallConfig.collectAsStateWithLifecycle()
    val metrics = remember(connectionState, fallAlertState, batteryPct, latencyMs) {
        obstacleMetrics(connectionState, fallAlertState, batteryPct, latencyMs)
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
                title = "实时避障",
                subtitle = if (obstacleEnabled) "避障检测已开启" else "避障检测已关闭",
            )
        }
        item {
            StatusBanner(
                title = obstacleDangerHeadline(connectionState, fallAlertState, radarDistance),
                subtitle = obstacleGuidance(connectionState, fallAlertState),
                icon = Icons.Filled.Warning,
                background = DangerRed,
                foreground = Color.White,
            )
        }
        item {
            RadarPanel(
                headline = obstacleHeadline(connectionState, fallAlertState),
                description = obstacleDescription(connectionState, fallAlertState),
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                metrics.forEach { metric ->
                    MetricCard(
                        metric = metric,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PillActionButton(
                    label = if (obstacleEnabled) "避障开启中" else "避障已关闭",
                    icon = Icons.Filled.Cameraswitch,
                    containerColor = if (obstacleEnabled) WarmYellow else DangerRed,
                    contentColor = if (obstacleEnabled) PrimaryText else Color.White,
                    modifier = Modifier.weight(1f),
                    onClick = onToggleObstacle,
                )
                VolumeCard(
                    value = volume,
                    onValueChange = onVolumeChange,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            SettingPill(
                title = "跌倒检测灵敏度",
                value = obstacleSensitivityLabel(fallConfig),
                icon = Icons.Filled.Tune,
                onClick = onSensitivityClick,
            )
        }
        item {
            PillActionButton(
                label = "紧急求助",
                icon = Icons.Filled.Emergency,
                containerColor = DangerRed,
                contentColor = Color.White,
                modifier = Modifier.fillMaxWidth(),
                height = 88.dp,
                textStyle = MaterialTheme.typography.headlineSmall,
                onClick = onEmergencyHelp,
            )
        }
    }
}
