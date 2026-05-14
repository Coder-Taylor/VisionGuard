package com.example.myapplication

import androidx.compose.ui.graphics.Color
import com.example.myapplication.ui.HistoryRecord
import com.example.myapplication.util.connectionStatusText
import com.example.myapplication.util.fallAlertDescription
import com.example.myapplication.util.fallAlertTitle
import com.example.myapplication.util.filterHistoryRecords
import com.example.myapplication.util.localVisionStatusText
import com.example.myapplication.util.obstacleDangerHeadline
import com.example.myapplication.util.obstacleMetrics
import com.example.myapplication.util.deviceMetrics
import com.example.myapplication.util.obstacleSensitivityLabel
import com.example.myapplication.util.recognitionBannerTitle
import com.example.myapplication.util.recognitionResultDosage
import com.example.myapplication.util.recognitionResultTitle
import com.example.myapplication.util.recognitionSupportingText
import com.example.myapplication.util.SensitivityPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun `obstacleDangerHeadline uses radar distance when connected`() {
        assertEquals(
            "前方 0.8 米 危险",
            obstacleDangerHeadline(
                connectionState = ConnectionState.CONNECTED,
                fallAlertState = FallAlertState.IDLE,
                radarDistance = 80,
            ),
        )
    }

    @Test
    fun `obstacleDangerHeadline keeps fall alert priority over radar distance`() {
        assertEquals(
            "检测到跌倒风险",
            obstacleDangerHeadline(
                connectionState = ConnectionState.CONNECTED,
                fallAlertState = FallAlertState.FALL_CONFIRMED,
                radarDistance = 80,
            ),
        )
    }

    @Test
    fun `obstacleMetrics uses real latency value`() {
        val metrics = obstacleMetrics(
            connectionState = ConnectionState.CONNECTED,
            fallAlertState = FallAlertState.IDLE,
            batteryPct = 62,
            latencyMs = 37,
        )

        assertEquals("37ms", metrics.first { it.label == "延迟" }.value)
        assertEquals("62%", metrics.first { it.label == "电量" }.value)
    }

    @Test
    fun `deviceMetrics uses placeholder when latency missing`() {
        val metrics = deviceMetrics(
            connectionState = ConnectionState.CONNECTED,
            batteryPct = 51,
            latencyMs = null,
        )

        assertEquals("--", metrics.first { it.label == "响应延迟" }.value)
        assertEquals("51%", metrics.first { it.label == "剩余电量" }.value)
    }

    @Test
    fun `obstacleSensitivityLabel maps low medium high and custom configs`() {
        assertEquals("低", obstacleSensitivityLabel(SensitivityPreference.LOW))
        assertEquals("中", obstacleSensitivityLabel(SensitivityPreference.MEDIUM))
        assertEquals("高", obstacleSensitivityLabel(SensitivityPreference.HIGH))
        assertEquals(
            "自定义",
            obstacleSensitivityLabel(
                FallDetectionConfig(
                    freeFallThreshold = 2.3,
                    impactThreshold = 16.0,
                    impactWindowMillis = 1_100L,
                    cooldownMillis = 9_000L,
                ),
            ),
        )
    }

    @Test
    fun `recognition copy reflects input frame issue`() {
        val state = LocalVisionState(
            status = LocalVisionStatus.ERROR,
            summary = "输入图像无法解码",
            issue = LocalVisionIssue.INPUT_FRAME,
        )

        assertEquals("输入图像异常", recognitionBannerTitle(state))
        assertEquals("输入图像异常", recognitionResultTitle(state))
        assertEquals("请重新拍摄或检查 JPEG 帧传输", recognitionResultDosage(state))
        assertEquals("检测到图像帧损坏或解码失败", recognitionSupportingText(state))
    }

    @Test
    fun `recognition copy reflects model pipeline issue`() {
        val state = LocalVisionState(
            status = LocalVisionStatus.ERROR,
            summary = "模型缺少类别标签元数据",
            issue = LocalVisionIssue.MODEL_PIPELINE,
        )

        assertEquals("模型推理异常", recognitionBannerTitle(state))
        assertEquals("模型推理异常", recognitionResultTitle(state))
        assertEquals("请检查模型资源与标签配置", recognitionResultDosage(state))
        assertEquals("本地模型或标签配置需要检查", recognitionSupportingText(state))
    }

    @Test
    fun `recognition copy reflects waiting for new frame issue`() {
        val state = LocalVisionState(
            status = LocalVisionStatus.IDLE,
            summary = "等待新图像帧",
            issue = LocalVisionIssue.NO_NEW_FRAME,
        )

        assertEquals("等待新图像帧", recognitionBannerTitle(state))
        assertEquals("等待拍照识别", recognitionResultTitle(state))
        assertEquals("连接设备后将自动恢复识别", recognitionResultDosage(state))
        assertEquals("当前暂无新图像帧，收到新画面后会自动恢复", recognitionSupportingText(state))
    }

    @Test
    fun `localVisionState waitingForNextFrame preserves model pipeline issue`() {
        val state = LocalVisionState(
            status = LocalVisionStatus.ERROR,
            summary = "模型标签不包含药盒或文本区域类别",
            issue = LocalVisionIssue.MODEL_PIPELINE,
        )

        val next = state.waitingForNextFrame()

        assertEquals(LocalVisionStatus.ERROR, next.status)
        assertEquals(LocalVisionIssue.MODEL_PIPELINE, next.issue)
        assertEquals("模型标签不包含药盒或文本区域类别", next.summary)
    }

    @Test
    fun `connectionStatusText maps stopped state`() {
        assertEquals("连接状态：已停止", connectionStatusText(ConnectionState.STOPPED))
    }

    @Test
    fun `connectionStatusText maps error state`() {
        assertEquals("连接状态：连接异常", connectionStatusText(ConnectionState.ERROR))
    }

    @Test
    fun `localVisionStatusText uses idle summary`() {
        assertEquals("本地视觉：等待图像帧", localVisionStatusText(LocalVisionState.IDLE))
    }

    @Test
    fun `localVisionStatusText uses state summary`() {
        val state = LocalVisionState(
            status = LocalVisionStatus.FRAME_ANALYZED,
            summary = "最近帧大小 7 字节",
        )

        assertEquals("本地视觉：最近帧大小 7 字节", localVisionStatusText(state))
    }

    @Test
    fun `fallAlertTitle maps emergency state`() {
        assertEquals("正在呼叫紧急联系人", fallAlertTitle(FallAlertState.EMERGENCY_CALLING))
    }

    @Test
    fun `fallAlertTitle maps confirmed state`() {
        assertEquals("已确认跌倒", fallAlertTitle(FallAlertState.FALL_CONFIRMED))
    }

    @Test
    fun `fallAlertDescription maps detecting state`() {
        assertEquals(
            "检测到异常运动，正在确认是否发生跌倒。",
            fallAlertDescription(FallAlertState.DETECTING),
        )
    }

    @Test
    fun `fallAlertDescription maps confirmed state`() {
        assertEquals(
            "已检测到跌倒，请立即查看佩戴者状态。",
            fallAlertDescription(FallAlertState.FALL_CONFIRMED),
        )
    }

    @Test
    fun `filterHistoryRecords returns all records for blank query`() {
        val records = sampleHistoryRecords()

        assertEquals(records, filterHistoryRecords(records, "   "))
    }

    @Test
    fun `filterHistoryRecords matches title detail and time`() {
        val records = sampleHistoryRecords()

        val titleMatches = filterHistoryRecords(records, "药品")
        val detailMatches = filterHistoryRecords(records, "跌倒")
        val timeMatches = filterHistoryRecords(records, "10月24日")

        assertEquals(listOf(records[0]), titleMatches)
        assertEquals(listOf(records[1]), detailMatches)
        assertEquals(listOf(records[1]), timeMatches)
    }

    @Test
    fun `filterHistoryRecords ignores case`() {
        val records = listOf(
            HistoryRecord(
                title = "Medicine Box",
                detail = "Vitamin C",
                time = "Today 10:30",
                accent = Color.Yellow,
            ),
        )

        val result = filterHistoryRecords(records, "medicine")

        assertEquals(records, result)
    }

    @Test
    fun `filterHistoryRecords returns empty list when nothing matches`() {
        val result = filterHistoryRecords(sampleHistoryRecords(), "语音设置")

        assertTrue(result.isEmpty())
    }

    private fun sampleHistoryRecords(): List<HistoryRecord> {
        return listOf(
            HistoryRecord(
                title = "药品识别",
                detail = "识别摘要：阿司匹林",
                time = "今天 10:30",
                accent = Color.Yellow,
            ),
            HistoryRecord(
                title = "跌倒提醒",
                detail = "已检测到跌倒，请立即查看佩戴者状态。",
                time = "10月24日 08:20",
                accent = Color.Red,
            ),
        )
    }
}
