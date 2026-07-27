package com.skypulse.weather.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class WeatherTheme(
    val isDay: Boolean,
    val backgroundGradient: List<Color>,
    val glass: GlassColors,
    val chartColors: WeatherChartColors,
    val precipitationIconColor: Color = Color.White,
    val textPrimary: Color = Color.White,
    val textSecondary: Color = TextSecondary,
    val textTertiary: Color = TextTertiary
)

/**
 * 玻璃卡片配色体系。
 *
 * 设计要点：
 * - 卡片保留天气色彩个性：取天气渐变中段色为「天气色相」，
 *   白天非雨雪向白色提亮（亮玻璃），雨雪与夜晚向黑色压暗（深玻璃），
 *   派生公式见 WeatherUtils.getWeatherTheme，无需逐天气手工调色，文字恒白；
 * - 模糊开启（Android 12+ 非省电）时使用低不透明度的 [tint]，
 *   让雨丝、闪电、星空透过卡片隐约可见；
 * - 降级场景使用 [tintFallback]（更高不透明度，保证对比度）；
 * - 卡片无描边，依靠模糊与罩色的明暗差自然分层。
 *
 * @param tint 实时模糊开启时的罩色（叠加于模糊之上）
 * @param tintFallback 无模糊降级底色
 * @param isLight 玻璃是否为亮色（供卡内标签对比度自适应）
 */
@Immutable
data class GlassColors(
    val tint: Color,
    val tintFallback: Color,
    val isLight: Boolean
)

@Immutable
data class WeatherChartColors(
    val clear: Pair<Color, Color>,
    val partlyCloudy: Pair<Color, Color>,
    val cloudy: Pair<Color, Color>,
    val rain: Pair<Color, Color>,
    val snow: Pair<Color, Color>,
    val wind: Pair<Color, Color>,
    val haze: Pair<Color, Color>,
    val storm: Pair<Color, Color>
)

val LocalWeatherTheme = staticCompositionLocalOf {
    WeatherTheme(
        isDay = true,
        backgroundGradient = SunnyGradient,
        glass = GlassColors(
            tint = Color(0xFFA5C9E6).copy(alpha = 0.32f),
            tintFallback = Color(0xFFA5C9E6).copy(alpha = 0.45f),
            isLight = true
        ),
        chartColors = WeatherChartColors(
            clear = Color(0xFFFFF8E1) to Color(0xFFFFD54F),
            partlyCloudy = Color(0xFFFFF8E1) to Color(0xFFFFECB3),
            cloudy = Color(0xFF9DB5D0) to Color(0xFF7A9ABB),
            rain = Color(0xFF5090E0) to Color(0xFF3570C0),
            snow = Color(0xFF80B8F0) to Color(0xFF5090E0),
            wind = Color(0xFF60C0D0) to Color(0xFF40A0B0),
            haze = Color(0xFFAA9A86) to Color(0xFF8A7A66),
            storm = Color(0xFF2A50A0) to Color(0xFF103070)
        )
    )
}
