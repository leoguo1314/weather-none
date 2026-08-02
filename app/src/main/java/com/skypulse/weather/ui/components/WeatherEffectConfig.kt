package com.skypulse.weather.ui.components

/**
 * 天气强度配置
 * 用于根据天气类型调整粒子效果的强度参数
 */
data class WeatherIntensity(
    val particleCount: Float = 1f,   // 粒子数量倍率
    val speed: Float = 1f,           // 速度倍率
    val size: Float = 1f,            // 尺寸倍率
    val alpha: Float = 1f,           // 透明度倍率
    val thickness: Float = 1f        // 线条粗细倍率（仅对雨有效）
)

/**
 * 从skycon解析天气强度
 */
fun parseWeatherIntensity(skycon: String?): WeatherIntensity {
    if (skycon == null) return WeatherIntensity()

    return when (skycon) {
        // 雨天强度：四档雨量统一按「大雨」形态处理（尺寸/粗细一致），
        // 强度由粒子数量与速度共同体现：数量 0.4/0.7/1.1/1.5，速度 0.8/0.8/1.1/1.5；透明度统一 0.6。
        "LIGHT_RAIN" -> WeatherIntensity(
            particleCount = 0.4f,
            speed = 0.8f,
            size = 1.1f,
            alpha = 0.6f,
            thickness = 1.35f
        )
        "MODERATE_RAIN" -> WeatherIntensity(
            particleCount = 0.7f,
            speed = 0.8f,
            size = 1.1f,
            alpha = 0.6f,
            thickness = 1.35f
        )
        "HEAVY_RAIN" -> WeatherIntensity(
            particleCount = 1.1f,
            speed = 1.1f,
            size = 1.1f,
            alpha = 0.6f,
            thickness = 1.35f
        )
        "STORM_RAIN" -> WeatherIntensity(
            particleCount = 1.5f,
            speed = 1.5f,
            size = 1.1f,
            alpha = 0.6f,
            thickness = 1.35f
        )

        // 雪天强度
        "LIGHT_SNOW" -> WeatherIntensity(
            particleCount = 0.5f,
            speed = 0.7f,
            size = 0.7f,
            alpha = 0.5f
        )
        "MODERATE_SNOW" -> WeatherIntensity()  // 默认值
        "HEAVY_SNOW" -> WeatherIntensity(
            particleCount = 1.6f,
            speed = 1.2f,
            size = 1.3f,
            alpha = 0.9f
        )
        "STORM_SNOW" -> WeatherIntensity(
            particleCount = 2.2f,
            speed = 1.5f,
            size = 1.5f,
            alpha = 1f
        )

        // 雷阵雨
        "THUNDER_SHOWER" -> WeatherIntensity(
            particleCount = 1.8f,
            speed = 1.45f,
            size = 1.15f,
            alpha = 0.95f,
            thickness = 1.3f
        )

        // 其他天气类型保持默认
        else -> WeatherIntensity()
    }
}

/**
 * 帧率控制器
 * 用于限制动画帧率，减少性能消耗
 */
class FrameRateLimiter(private val targetFps: Int = 60) {
    private var lastFrameTime = 0L
    private val frameIntervalMs = 1000L / targetFps

    /**
     * 检查是否应该渲染下一帧
     * @return true表示应该渲染，false表示跳过
     */
    fun shouldRender(currentTimeMillis: Long): Boolean {
        if (currentTimeMillis - lastFrameTime >= frameIntervalMs) {
            lastFrameTime = currentTimeMillis
            return true
        }
        return false
    }
}

/**
 * 动画质量档位
 * 用于省电模式降级：粒子数量减半、各级效果帧率下调
 */
data class EffectQuality(
    val particleScale: Float,   // 粒子数量倍率
    val precipFps: Int,         // 雨/雷/雨夹雪帧率
    val mediumFps: Int,         // 雪/风帧率
    val ambientFps: Int         // 晴/夜/云/雾帧率
) {
    companion object {
        val High = EffectQuality(1f, 60, 30, 20)
        val Low = EffectQuality(0.5f, 30, 20, 10)
    }
}

/**
 * 实时风信息（用于驱动雨丝倾斜、云层漂移方向、风粒子速度）
 * @param speedKmh 风速（km/h）
 * @param directionDeg 风向（度，气象惯例：风的来向，0=北，90=东）
 */
data class WindInfo(
    val speedKmh: Float,
    val directionDeg: Float
)
