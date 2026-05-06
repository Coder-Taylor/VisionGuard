package com.example.myapplication.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Battery6Bar
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.ui.graphics.Color
import com.example.myapplication.ConnectionState
import com.example.myapplication.FallAlertState
import com.example.myapplication.FallDetectionConfig
import com.example.myapplication.LocalVisionIssue
import com.example.myapplication.LocalVisionState
import com.example.myapplication.LocalVisionStatus
import com.example.myapplication.ui.DangerRed
import com.example.myapplication.ui.HistoryRecord
import com.example.myapplication.ui.StatMetric
import com.example.myapplication.ui.WarmYellowDark

internal fun obstacleMetrics(
    connectionState: ConnectionState,
    fallAlertState: FallAlertState,
    batteryPct: Int? = null,
    latencyMs: Int? = null,
): List<StatMetric> {
    val delayValue = if (connectionState == ConnectionState.CONNECTED) formatLatency(latencyMs) else "--"
    val computeValue = if (connectionState == ConnectionState.CONNECTED) "8.5 TFLOPS" else "待机"
    val batteryValue = when {
        connectionState == ConnectionState.CONNECTED && batteryPct != null -> "$batteryPct%"
        else -> "--"
    }
    return listOf(
        StatMetric("延迟", delayValue, "实时", Color(0xFF00A07E), Icons.Filled.Speed),
        StatMetric("算力", computeValue, "本地", WarmYellowDark, Icons.Filled.Memory),
        StatMetric("电量", batteryValue, "余量", DangerRed, Icons.Filled.Battery6Bar),
    )
}

internal fun deviceMetrics(
    connectionState: ConnectionState,
    batteryPct: Int? = null,
    latencyMs: Int? = null,
): List<StatMetric> {
    val batteryValue = when {
        connectionState == ConnectionState.CONNECTED && batteryPct != null -> "$batteryPct%"
        else -> "--"
    }
    return listOf(
        StatMetric(
            label = "响应延迟",
            value = if (connectionState == ConnectionState.CONNECTED) formatLatency(latencyMs) else "--",
            supporting = "链路",
            accent = WarmYellowDark,
            icon = Icons.Filled.Speed,
        ),
        StatMetric(
            label = "剩余电量",
            value = batteryValue,
            supporting = "设备",
            accent = Color(0xFF3C8B41),
            icon = Icons.Filled.Battery6Bar,
        ),
    )
}

internal fun obstacleDangerHeadline(
    connectionState: ConnectionState,
    fallAlertState: FallAlertState,
    radarDistance: Int? = null,
): String = when {
    fallAlertState == FallAlertState.FALL_CONFIRMED ||
        fallAlertState == FallAlertState.EMERGENCY_CALLING -> "检测到跌倒风险"
    connectionState == ConnectionState.CONNECTED && radarDistance != null ->
        "前方 ${formatRadarDistance(radarDistance)} 危险"
    connectionState == ConnectionState.CONNECTED -> "前方障碍预警"
    else -> "环境数据同步中"
}

private fun formatRadarDistance(radarDistance: Int): String {
    val meters = radarDistance / 100.0
    val formatted = if (radarDistance % 100 == 0) {
        meters.toInt().toString()
    } else {
        meters.toString().trimEnd('0').trimEnd('.')
    }
    return "$formatted 米"
}

private fun formatLatency(latencyMs: Int?): String = latencyMs?.let { "${it}ms" } ?: "--"

internal fun obstacleSensitivityLabel(config: FallDetectionConfig): String = when (config) {
    SensitivityPreference.LOW -> "低"
    SensitivityPreference.MEDIUM -> "中"
    SensitivityPreference.HIGH -> "高"
    else -> "自定义"
}

internal fun obstacleActionSubtitle(
    connectionState: ConnectionState,
    fallAlertState: FallAlertState,
): String = when {
    fallAlertState == FallAlertState.FALL_CONFIRMED ||
        fallAlertState == FallAlertState.EMERGENCY_CALLING -> "优先处理跌倒告警"
    connectionState == ConnectionState.CONNECTED -> "点击查看实时环境感知"
    else -> "点击开启环境感知"
}

internal fun recognitionActionSubtitle(state: LocalVisionState): String =
    if (state.status == LocalVisionStatus.FRAME_ANALYZED) {
        "最新结果已生成，可继续识别"
    } else {
        "智能扫描药盒信息"
    }

internal fun homeConnectionBannerTitle(connectionState: ConnectionState): String =
    when (connectionState) {
        ConnectionState.CONNECTED -> "已连接云端"
        ConnectionState.STOPPED -> "未连接"
        ConnectionState.STARTING -> "启动中"
        ConnectionState.ERROR -> "连接异常"
    }

internal fun homeConnectionBannerSubtitle(connectionState: ConnectionState): String =
    when (connectionState) {
        ConnectionState.CONNECTED -> "全 HTTP 通信已就绪"
        ConnectionState.STOPPED -> "请检查网络连接"
        ConnectionState.STARTING -> "正在初始化服务..."
        ConnectionState.ERROR -> "连接遇到问题，请检查网络"
    }

internal fun recognitionBannerTitle(state: LocalVisionState): String =
    when {
        state.issue == LocalVisionIssue.NO_NEW_FRAME -> "等待新图像帧"
        state.status == LocalVisionStatus.ERROR && state.issue == LocalVisionIssue.INPUT_FRAME -> "输入图像异常"
        state.status == LocalVisionStatus.ERROR && state.issue == LocalVisionIssue.MODEL_PIPELINE -> "模型推理异常"
        state.status == LocalVisionStatus.ERROR -> "识别链路需要检查"
        else -> "系统状态正常，随时可以识别"
    }

internal fun recognitionResultTitle(state: LocalVisionState): String =
    when (state.status) {
        LocalVisionStatus.FRAME_ANALYZED ->
            state.summary.substringBefore('，').substringBefore('。').ifBlank { "药品识别完成" }
        LocalVisionStatus.ERROR -> when (state.issue) {
            LocalVisionIssue.INPUT_FRAME -> "输入图像异常"
            LocalVisionIssue.MODEL_PIPELINE -> "模型推理异常"
            else -> "识别异常"
        }
        LocalVisionStatus.PROCESSING -> "识别进行中"
        LocalVisionStatus.IDLE -> "等待拍照识别"
    }

internal fun recognitionResultDosage(state: LocalVisionState): String =
    when (state.status) {
        LocalVisionStatus.FRAME_ANALYZED -> "请按标签说明服用"
        LocalVisionStatus.ERROR -> when (state.issue) {
            LocalVisionIssue.INPUT_FRAME -> "请重新拍摄或检查 JPEG 帧传输"
            LocalVisionIssue.MODEL_PIPELINE -> "请检查模型资源与标签配置"
            else -> "请检查识别链路"
        }
        LocalVisionStatus.PROCESSING -> "正在执行本地模型推理与摘要生成"
        LocalVisionStatus.IDLE -> if (state.issue == LocalVisionIssue.NO_NEW_FRAME) {
            "连接设备后将自动恢复识别"
        } else {
            "请拍摄药盒开始识别"
        }
    }

internal fun historyTitle(
    localVisionState: LocalVisionState,
    connectionState: ConnectionState,
): String = when {
    localVisionState.status == LocalVisionStatus.FRAME_ANALYZED -> recognitionResultTitle(localVisionState)
    connectionState == ConnectionState.CONNECTED -> "药品识别"
    else -> "识别历史"
}

internal fun historyDetail(localVisionState: LocalVisionState): String =
    when (localVisionState.status) {
        LocalVisionStatus.FRAME_ANALYZED -> localVisionState.summary
        LocalVisionStatus.PROCESSING -> "正在分析最新画面"
        LocalVisionStatus.ERROR -> localVisionState.summary
        LocalVisionStatus.IDLE -> localVisionState.summary
    }

internal fun filterHistoryRecords(
    historyRecords: List<HistoryRecord>,
    query: String,
): List<HistoryRecord> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isBlank()) {
        return historyRecords
    }
    return historyRecords.filter { record ->
        record.title.contains(normalizedQuery, ignoreCase = true) ||
            record.detail.contains(normalizedQuery, ignoreCase = true) ||
            record.time.contains(normalizedQuery, ignoreCase = true)
    }
}

internal fun deviceBannerTitle(connectionState: ConnectionState): String =
    if (connectionState == ConnectionState.CONNECTED) {
        "局域网运行状态良好"
    } else {
        "局域网等待设备接入"
    }

internal fun guardianLocationText(connectionState: ConnectionState): String =
    if (connectionState == ConnectionState.CONNECTED) {
        "当前位置：北京市海淀区北三环西路"
    } else {
        "当前位置：等待设备同步位置信息"
    }

internal fun guardianLocationUpdateText(connectionState: ConnectionState): String =
    if (connectionState == ConnectionState.CONNECTED) {
        "最后更新：1分钟前"
    } else {
        "最后更新：设备连接后自动同步"
    }

internal fun finderSupportingText(fallAlertState: FallAlertState): String =
    if (fallAlertState == FallAlertState.FALL_CONFIRMED ||
        fallAlertState == FallAlertState.EMERGENCY_CALLING
    ) {
        "已切换为紧急提示模式，方便快速定位佩戴者"
    } else {
        "通过声光提示快速找到设备"
    }

internal fun connectionStatusText(state: ConnectionState): String {
    val label = when (state) {
        ConnectionState.STOPPED -> "已停止"
        ConnectionState.STARTING -> "启动中"
        ConnectionState.CONNECTED -> "已连接"
        ConnectionState.ERROR -> "连接异常"
    }
    return "连接状态：$label"
}

internal fun localVisionStatusText(state: LocalVisionState): String =
    "本地视觉：${state.summary}"

internal fun fallAlertTitle(state: FallAlertState): String =
    when (state) {
        FallAlertState.IDLE -> "监测中"
        FallAlertState.DETECTING -> "疑似跌倒"
        FallAlertState.FALL_CONFIRMED -> "已确认跌倒"
        FallAlertState.EMERGENCY_CALLING -> "正在呼叫紧急联系人"
    }

internal fun fallAlertDescription(state: FallAlertState): String =
    when (state) {
        FallAlertState.IDLE -> "系统正在持续监听跌倒信号。"
        FallAlertState.DETECTING -> "检测到异常运动，正在确认是否发生跌倒。"
        FallAlertState.FALL_CONFIRMED -> "已检测到跌倒，请立即查看佩戴者状态。"
        FallAlertState.EMERGENCY_CALLING -> "系统正在尝试发起紧急呼叫。"
    }

internal fun recognitionSupportingText(state: LocalVisionState): String =
    when (state.status) {
        LocalVisionStatus.IDLE -> if (state.issue == LocalVisionIssue.NO_NEW_FRAME) {
            "当前暂无新图像帧，收到新画面后会自动恢复"
        } else {
            "等待来自眼镜端的最新图像帧"
        }
        LocalVisionStatus.PROCESSING -> "正在执行本地模型推理与摘要生成"
        LocalVisionStatus.FRAME_ANALYZED -> "识别完成，可直接用于语音播报"
        LocalVisionStatus.ERROR -> when (state.issue) {
            LocalVisionIssue.INPUT_FRAME -> "检测到图像帧损坏或解码失败"
            LocalVisionIssue.MODEL_PIPELINE -> "本地模型或标签配置需要检查"
            else -> "请检查模型资源或图像输入格式"
        }
    }

internal fun obstacleHeadline(
    connectionState: ConnectionState,
    fallAlertState: FallAlertState,
): String = when {
    fallAlertState == FallAlertState.FALL_CONFIRMED ||
        fallAlertState == FallAlertState.EMERGENCY_CALLING -> "优先处理跌倒告警"
    connectionState == ConnectionState.CONNECTED -> "前方环境已同步"
    else -> "等待实时环境数据"
}

internal fun obstacleDescription(
    connectionState: ConnectionState,
    fallAlertState: FallAlertState,
): String = when {
    fallAlertState == FallAlertState.FALL_CONFIRMED ||
        fallAlertState == FallAlertState.EMERGENCY_CALLING ->
        "当前重点是联系监护人并确认佩戴者安全。"
    connectionState == ConnectionState.CONNECTED ->
        "可根据持续接入的数据播报障碍方向与可通行区域。"
    else ->
        "连接建立后，这里会展示更接近原型的雷达式反馈。"
}

internal fun obstacleGuidance(
    connectionState: ConnectionState,
    fallAlertState: FallAlertState,
): String = when {
    fallAlertState == FallAlertState.FALL_CONFIRMED ||
        fallAlertState == FallAlertState.EMERGENCY_CALLING -> "请原地停留并等待协助"
    connectionState == ConnectionState.CONNECTED -> "请向左避让"
    else -> "当前暂无实时引导，请先确认设备连接"
}
