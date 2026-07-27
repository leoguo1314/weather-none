package com.skypulse.weather.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 次级页面（城市管理 / 预警详情 / 设置）配色体系。
 *
 * 仅作用于这三个次级页面，主页天气沉浸主题与桌面小组件不受影响。
 * 浅色为 iOS 浅灰分组风（默认），深色为 iOS 深色分组风。
 *
 * 由 WeatherScreen 根据设置项 dark_mode 通过 [LocalSecondaryPageTheme] 下发。
 */
@Immutable
data class SecondaryPageColors(
    val isDark: Boolean,
    val background: Color,
    val cardBackground: Color,
    val divider: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val backArrow: Color,
    val accentBlue: Color,
    val accentGreen: Color,
    val switchOffTrack: Color
)

/** 浅色（默认）：iOS 浅灰分组风 */
val SecondaryPageLightColors = SecondaryPageColors(
    isDark = false,
    background = Color(0xFFF2F2F7),
    cardBackground = Color(0xFFFFFFFF),
    divider = Color(0xFFE5E5EA),
    textPrimary = Color(0xFF1C1C1E),
    textSecondary = Color(0xFF8E8E93),
    backArrow = Color(0xFF333333),
    accentBlue = Color(0xFF007AFF),
    accentGreen = Color(0xFF34C759),
    switchOffTrack = Color(0xFFE5E5EA)
)

/** 深色：iOS 深色分组风（纯黑底 + 深灰卡片） */
val SecondaryPageDarkColors = SecondaryPageColors(
    isDark = true,
    background = Color(0xFF000000),
    cardBackground = Color(0xFF1C1C1E),
    divider = Color(0xFF38383A),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFF8E8E93),
    backArrow = Color(0xFFFFFFFF),
    accentBlue = Color(0xFF0A84FF),
    accentGreen = Color(0xFF30D158),
    switchOffTrack = Color(0xFF39393D)
)

val LocalSecondaryPageTheme = staticCompositionLocalOf { SecondaryPageLightColors }
