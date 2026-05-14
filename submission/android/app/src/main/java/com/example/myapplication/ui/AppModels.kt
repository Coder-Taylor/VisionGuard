package com.example.myapplication.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

internal data class HistoryRecord(
    val title: String,
    val detail: String,
    val time: String,
    val accent: Color,
)

internal data class StatMetric(
    val label: String,
    val value: String,
    val supporting: String,
    val accent: Color,
    val icon: ImageVector? = null,
)
