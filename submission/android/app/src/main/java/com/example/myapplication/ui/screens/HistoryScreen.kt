package com.example.myapplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.HistoryRecord
import com.example.myapplication.ui.PrimaryText
import com.example.myapplication.ui.ScreenBackground
import com.example.myapplication.ui.SecondaryText
import com.example.myapplication.ui.WarmYellowDark
import com.example.myapplication.ui.components.HistoryRecordCard
import com.example.myapplication.ui.components.SecondaryWideButton
import com.example.myapplication.ui.components.TopCircleButton

@Composable
internal fun HistoryScreen(
    historyRecords: List<HistoryRecord>,
    historyQuery: String,
    onBack: () -> Unit,
    onOpenObstacle: () -> Unit,
    onVoiceInput: () -> Unit = {},
    onClearQuery: () -> Unit = {},
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TopCircleButton(
                    icon = Icons.Filled.Visibility,
                    contentDescription = "打开避障页面",
                    onClick = onOpenObstacle,
                )
                TopCircleButton(
                    icon = Icons.Filled.Mic,
                    contentDescription = "语音输入",
                    onClick = onVoiceInput,
                )
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector = Icons.Filled.RecordVoiceOver,
                    contentDescription = null,
                    tint = WarmYellowDark,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "如果想知道哪天吃了什么药直接问我哦~",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText,
                )
            }
        }
        if (historyQuery.isNotBlank()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "当前筛选：$historyQuery",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryText,
                    )
                    TopCircleButton(
                        icon = Icons.Filled.Close,
                        contentDescription = "清除筛选",
                        onClick = onClearQuery,
                    )
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (historyQuery.isBlank()) "识别历史" else "筛选结果",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText,
                )
                Text(
                    text = "共 ${historyRecords.size} 条",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = WarmYellowDark,
                )
            }
        }
        if (historyRecords.isEmpty()) {
            item {
                Text(
                    text = "没有找到与“$historyQuery”相关的历史记录",
                    style = MaterialTheme.typography.bodyLarge,
                    color = SecondaryText,
                )
            }
        } else {
            items(
                items = historyRecords,
                key = { record -> "${record.time}-${record.title}-${record.detail}" },
            ) { record ->
                HistoryRecordCard(record = record)
            }
        }
        item {
            SecondaryWideButton(
                title = if (historyQuery.isBlank()) "查看更多历史记录" else "重新语音查询",
                icon = if (historyQuery.isBlank()) Icons.Filled.ChevronRight else Icons.Filled.Mic,
                onClick = if (historyQuery.isBlank()) onOpenObstacle else onVoiceInput,
            )
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onBack),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = SecondaryText,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "返回我的",
                    color = SecondaryText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
