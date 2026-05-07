package com.example.myapplication

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.myapplication.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test

class ExampleInstrumentedTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun visionHubScreen_shows_home_design_cards() {
        composeTestRule.setContent {
            MyApplicationTheme {
                VisionHubScreen(
                    fallAlertState = FallAlertState.IDLE,
                    connectionState = ConnectionState.LISTENING,
                    localVisionState = LocalVisionState.IDLE,
                )
            }
        }

        composeTestRule.onNodeWithText("智视胸牌").assertIsDisplayed()
        composeTestRule.onNodeWithText("实时避障").assertIsDisplayed()
        composeTestRule.onNodeWithText("药品识别").assertIsDisplayed()
        composeTestRule.onNodeWithText("设备已连接").assertIsDisplayed()
        composeTestRule.onNodeWithText("视觉引导系统运行中").assertIsDisplayed()
    }

    @Test
    fun visionHubScreen_switches_to_recognition_tab() {
        composeTestRule.setContent {
            MyApplicationTheme {
                VisionHubScreen(
                    fallAlertState = FallAlertState.EMERGENCY_CALLING,
                    connectionState = ConnectionState.CONNECTED,
                    localVisionState = LocalVisionState.PROCESSING,
                )
            }
        }

        composeTestRule.onNodeWithText("识别").performClick()

        composeTestRule.onNodeWithText("药品识别").assertIsDisplayed()
        composeTestRule.onNodeWithText("点击拍照识别").assertIsDisplayed()
        composeTestRule.onNodeWithText("最新识别结果").assertIsDisplayed()
        composeTestRule.onNodeWithText("正在执行本地模型推理与摘要生成").assertIsDisplayed()
    }

    @Test
    fun visionHubScreen_opens_history_from_profile() {
        composeTestRule.setContent {
            MyApplicationTheme {
                VisionHubScreen(
                    fallAlertState = FallAlertState.FALL_CONFIRMED,
                    connectionState = ConnectionState.STOPPED,
                    localVisionState = LocalVisionState(
                        status = LocalVisionStatus.ERROR,
                        summary = "图像帧格式无效",
                    ),
                )
            }
        }

        composeTestRule.onNodeWithText("我的").performClick()
        composeTestRule.onNodeWithText("查看历史记录").performClick()

        composeTestRule.onNodeWithText("识别历史").assertIsDisplayed()
        composeTestRule.onNodeWithText("共 6 条").assertIsDisplayed()
        composeTestRule.onNodeWithText("图像帧格式无效").assertIsDisplayed()
        composeTestRule.onNodeWithText("返回我的").assertIsDisplayed()
    }
}
