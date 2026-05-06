package com.example.myapplication.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.CardBackground
import com.example.myapplication.ui.SecondaryText
import com.example.myapplication.ui.StatMetric

@Composable
internal fun MetricCard(
    metric: StatMetric,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = metric.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = SecondaryText,
            )
            Spacer(modifier = Modifier.height(8.dp))
            metric.icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = metric.accent,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            Text(
                text = metric.value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = metric.accent,
                textAlign = TextAlign.Center,
            )
            Text(
                text = metric.supporting,
                style = MaterialTheme.typography.labelMedium,
                color = SecondaryText,
                textAlign = TextAlign.Center,
            )
        }
    }
}
