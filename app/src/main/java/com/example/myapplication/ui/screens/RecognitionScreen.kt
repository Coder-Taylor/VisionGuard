package com.example.myapplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapplication.LocalVisionState
import com.example.myapplication.ui.PrimaryText
import com.example.myapplication.ui.ScreenBackground
import com.example.myapplication.ui.SuccessGreen
import com.example.myapplication.ui.SuccessText
import com.example.myapplication.ui.components.AppWordmark
import com.example.myapplication.ui.components.CaptureCard
import com.example.myapplication.ui.components.RecognitionResultCard
import com.example.myapplication.ui.components.SectionLabel
import com.example.myapplication.ui.components.StatusBanner
import com.example.myapplication.util.recognitionBannerTitle
import com.example.myapplication.util.recognitionResultDosage
import com.example.myapplication.util.recognitionResultTitle
import com.example.myapplication.util.recognitionSupportingText

@Composable
internal fun RecognitionScreen(
    localVisionState: LocalVisionState,
    onOpenHistory: () -> Unit,
    onCaptureClick: () -> Unit = {},
    onSpeakResult: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
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
                title = "暖阳语音助手已就绪",
                subtitle = "系统状态正常，随时可以识别",
            )
        }
        item {
            StatusBanner(
                title = recognitionBannerTitle(localVisionState),
                subtitle = recognitionSupportingText(localVisionState),
                icon = Icons.Filled.CheckCircle,
                background = SuccessGreen,
                foreground = SuccessText,
            )
        }
        item {
            SectionLabel(text = "当前模式")
        }
        item {
            Text(
                text = "药品识别",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Black,
                color = PrimaryText,
            )
        }
        item {
            CaptureCard(onClick = onCaptureClick)
        }
        item {
            RecognitionResultCard(
                title = recognitionResultTitle(localVisionState),
                dosage = recognitionResultDosage(localVisionState),
                summary = localVisionState.summary,
                onOpenHistory = onOpenHistory,
                onSpeakResult = onSpeakResult,
            )
        }
    }
}
