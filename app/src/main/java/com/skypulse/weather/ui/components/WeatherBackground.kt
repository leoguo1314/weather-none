package com.skypulse.weather.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import com.skypulse.weather.model.DailyForecast
import com.skypulse.weather.util.skyGradientColorStops
import com.skypulse.weather.util.WeatherUtils
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

/**
 * 天气背景容器：5 色渐变 + 粒子特效 + 实时模糊源。
 *
 * 结构分两层（这是玻璃卡片能正确模糊的关键）：
 * 1. 模糊源层（haze）：仅天气渐变 + 粒子特效，被录入离屏图层；
 * 2. 内容层：卡片等 UI，通过 [LocalGlassHazeState] 拿到状态后
 *    以 hazeChild 采样模糊源——内容层自身不会被录入，避免自我模糊反馈。
 *
 * 省电模式下不提供模糊状态，卡片自动降级为增强底色（见 GlassCard）。
 */
@Composable
fun WeatherBackground(
    skycon: String?,
    daily: DailyForecast? = null,
    wind: WindInfo? = null,
    modifier: Modifier = Modifier,
    showParticles: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val isDay = WeatherUtils.isCurrentlyDay(daily)
    val gradientColors = WeatherUtils.getWeatherGradient(skycon, isDay)

    // 背景色平滑过渡：天气/昼夜切换时逐色插值（渐变列表恒为 5 色），避免硬切。
    // 首次组合时 animateColorAsState 直接取目标值，不会出现入场闪变。
    val animatedColors = gradientColors.mapIndexed { index, color ->
        animateColorAsState(
            targetValue = color,
            animationSpec = tween(durationMillis = 600),
            label = "bg_color_$index"
        ).value
    }

    // 省电模式下关闭实时模糊（haze 在 API<31 等平台会自动使用罩色降级）
    val powerSave = rememberPowerSaveMode()
    val hazeState = remember { HazeState() }

    Box(modifier = modifier.fillMaxSize()) {
        // 第一层：模糊源（仅渐变 + 粒子）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .haze(state = hazeState)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = *skyGradientColorStops(animatedColors),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY
                    )
                )
            )
            // 粒子效果叠加层（不影响内容展示）
            if (showParticles) {
                WeatherEffectOverlay(
                    skycon = skycon,
                    isDay = isDay,
                    wind = wind,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // 第二层：内容（卡片经 LocalGlassHazeState 采样上面的模糊源）
        CompositionLocalProvider(
            LocalGlassHazeState provides (if (powerSave) null else hazeState)
        ) {
            content()
        }
    }
}
