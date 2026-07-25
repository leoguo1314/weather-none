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
        // 雨天强度
        "LIGHT_RAIN" -> WeatherIntensity(
            particleCount = 0.5f,
            speed = 0.7f,
            size = 0.8f,
            alpha = 0.6f,
            thickness = 0.7f
        )
        "MODERATE_RAIN" -> WeatherIntensity()  // 默认值
        "HEAVY_RAIN" -> WeatherIntensity(
            particleCount = 1.5f,
            speed = 1.3f,
            size = 1.2f,
            alpha = 0.9f,
            thickness = 1.3f
        )
        "STORM_RAIN" -> WeatherIntensity(
            particleCount = 2f,
            speed = 1.6f,
            size = 1.4f,
            alpha = 1f,
            thickness = 1.5f
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
            particleCount = 1.5f,
            speed = 1.4f,
            size = 1.1f,
            alpha = 0.9f,
            thickness = 1.2f
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

    /**
     * 获取帧间隔（秒）
     */
    fun getFrameInterval(): Float = frameIntervalMs / 1000f
}

/**
 * 天气过渡状态
 * 用于实现天气切换时的平滑过渡
 */
data class WeatherTransitionState(
    val previousSkycon: String? = null,
    val currentSkycon: String? = null,
    val transitionProgress: Float = 1f,  // 0 = 显示旧效果, 1 = 显示新效果
    val isTransitioning: Boolean = false
)

/**
 * 雨滴溅射效果
 * 当雨滴落到卡片顶部时产生的水花效果
 */
data class RainSplash(
    val x: Float,           // 溅射位置 x
    val y: Float,           // 溅射位置 y
    val startTime: Float,   // 开始时间
    val lifetime: Float = 0.4f,  // 生命周期（秒）
    val size: Float = 8f    // 溅射大小
)

/**
 * 4层雨效果配置
 */
data class RainLayerConfig(
    val count: Int,         // 粒子数量
    val speedFactor: Float, // 速度系数
    val alphaFactor: Float, // 透明度系数
    val sizeFactor: Float,  // 尺寸系数
    val thicknessFactor: Float // 线条粗细系数
)
