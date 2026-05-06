package com.example.myapplication.ui

import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier

// ============================================================
// DOCX 设计规范色值 — VisionGuard 主色板
// ============================================================

// 主色
internal val PrimaryBlue = Color(0xFF165DFF)
internal val LightBlue = Color(0xFFE8F3FF)

// 状态色
internal val SuccessGreen = Color(0xFF00B42A)
internal val AlertRed = Color(0xFFF53F3F)
internal val WarningOrange = Color(0xFFFF7D00)
internal val OfflineGray = Color(0xFF909399)

// 柔和背景色
internal val CardBackground = Color(0xFFF5F7FA)
internal val SoftPink = Color(0xFFF5E8E8)
internal val SoftGreen = Color(0xFFE6F4EA)
internal val SoftOrange = Color(0xFFFFF0E6)

// 文字色
internal val TextPrimary = Color(0xFF333333)
internal val TextSecondary = Color(0xFF666666)

// 白色
internal val White = Color(0xFFFFFFFF)

// ============================================================
// 渐变背景 — 柔和毛玻璃感 (浅米黄泛粉 → 纯白)
// ============================================================
internal val GradientTop = Color(0xFFFFF5F0)
internal val GradientBottom = Color(0xFFFFFFFF)

// ============================================================
// 旧版颜色兼容别名 — 保证旧屏幕（Obstacle/Recognition/Device）
// 编译通过。这些屏幕已不在新导航中引用，但保留文件。
// 命名说明: 新代码用 TextPrimary/AlertRed（DOCX），旧代码用
// PrimaryText/DangerRed（旧版暖黄）。两者并存，值不同。
// ============================================================
internal val PrimaryText = Color(0xFF2E2621)          // 旧: 暖黑主文字
internal val SecondaryText = Color(0xFF4A3F36)        // 旧: 灰色副文字
internal val ScreenBackground = Color(0xFFF6F1E8)    // 旧: 暖黄底色
internal val WarmYellow = Color(0xFFF4C64E)           // 旧: 主色调
internal val WarmYellowDark = Color(0xFF7A5600)       // 旧: 深黄文字
internal val DangerRed = Color(0xFFE86862)             // 旧: 危险红(旧值)
internal val SuccessText = Color(0xFF275B2E)           // 旧: 成功文字(旧值)
internal val SurfaceMuted = Color(0xFFEFE4D7)          // 旧: 静音表面
internal val SurfaceSoft = Color(0xFFF5EADF)           // 旧: 柔和表面
internal val DarkPanel = Color(0xFF262D37)             // 旧: 暗色面板
internal val RadarLine = Color(0xFF76E5E2)             // 旧: 雷达线色
internal val ProfileBlue = Color(0xFF8BA5FF)           // 旧: 档案蓝

fun Modifier.gradientBackground(): Modifier = this.background(
    brush = Brush.verticalGradient(listOf(GradientTop, GradientBottom))
)
