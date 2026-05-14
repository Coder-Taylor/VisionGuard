package com.example.myapplication.util

import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 将后端 RFC3339 时间字符串转为用户友好的 "yyyy-MM-dd HH:mm" 格式。
 * 后端 Go 使用 time.RFC3339 = "2006-01-02T15:04:05Z07:00"
 */
object TimeFormat {
    private val displayFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.of("Asia/Shanghai"))

    fun alertTime(isoStr: String?): String {
        if (isoStr.isNullOrBlank()) return "-"
        return try {
            val dt = OffsetDateTime.parse(isoStr)
            displayFormatter.format(dt)
        } catch (_: Exception) {
            // 兼容无时区的 ISO 格式 "2026-05-15T23:58:00"
            try {
                val dt = java.time.LocalDateTime.parse(isoStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                dt.atZone(ZoneId.of("Asia/Shanghai")).format(displayFormatter)
            } catch (_: Exception) {
                isoStr.take(16).replace("T", " ")
            }
        }
    }
}
