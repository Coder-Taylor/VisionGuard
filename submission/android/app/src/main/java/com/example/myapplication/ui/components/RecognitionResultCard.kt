package com.example.myapplication.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.CardBackground
import com.example.myapplication.ui.PrimaryText
import com.example.myapplication.ui.SecondaryText
import com.example.myapplication.ui.SuccessGreen
import com.example.myapplication.ui.SuccessText
import com.example.myapplication.ui.SurfaceMuted
import com.example.myapplication.ui.WarmYellowDark

@Composable
internal fun RecognitionResultCard(
    title: String,
    dosage: String,
    summary: String,
    onOpenHistory: () -> Unit,
    onSpeakResult: () -> Unit = {},
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Memory,
                    contentDescription = null,
                    tint = WarmYellowDark,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "最新识别结果",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = WarmYellowDark,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            FieldBlock(label = "药品名称", value = title)
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 14.dp),
                color = SurfaceMuted,
            )
            FieldBlock(label = "用法用量", value = dosage)
            Text(
                text = summary,
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = SecondaryText,
            )
            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SecondaryWideButton(
                    title = "语音播报",
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    modifier = Modifier.weight(1f),
                    containerColor = SuccessGreen,
                    contentColor = SuccessText,
                    onClick = onSpeakResult,
                )
                SecondaryWideButton(
                    title = "历史",
                    icon = Icons.Filled.History,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenHistory,
                )
            }
        }
    }
}

@Composable
private fun FieldBlock(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = SecondaryText,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
            color = PrimaryText,
        )
    }
}
