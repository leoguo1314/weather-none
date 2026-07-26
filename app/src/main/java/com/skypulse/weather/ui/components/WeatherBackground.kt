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
import com.skypulse.weather.util.WeatherUtils

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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = animatedColors,
                    startY = 0f,
                    endY = Float.POSITIVE_INFINITY
                )
            )
    ) {
        // 粒子效果叠加层（不影响内容展示）
        if (showParticles) {
            WeatherEffectOverlay(
                skycon = skycon,
                isDay = isDay,
                wind = wind,
                modifier = Modifier.fillMaxSize()
            )
        }
        content()
    }
}
