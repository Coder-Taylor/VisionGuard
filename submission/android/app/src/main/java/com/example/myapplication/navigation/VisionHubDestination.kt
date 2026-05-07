package com.example.myapplication.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.graphics.vector.ImageVector

internal enum class VisionHubDestination(
    val label: String,
    val icon: ImageVector,
    val showInBottomBar: Boolean = true,
) {
    HOME("首页", Icons.Outlined.Home),
    POSITION_MEDICINE("定位用药", Icons.Outlined.AccessTime),
    ALERT_HISTORY("告警历史", Icons.Outlined.Notifications),
    PROFILE("个人中心", Icons.Outlined.Person),
}
