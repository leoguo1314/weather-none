package com.skypulse.weather.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

/** 弧度转角度 */
private const val RadToDeg = 57.29578f

/** 雨丝最大倾斜角（弧度，约 20°），防止强风下角度失真 */
private const val MaxRainSlantRad = 0.349f

/** 闪电序列总时长（秒） */
private const val LightningDuration = 0.5f

// 太阳配色
private val SunOuterColor = Color(0xFFFFD54F)  // 外层暖金
private val SunMidColor = Color(0xFFFFE082)    // 中层浅金
private val SunCoreColor = Color(0xFFFFC107)   // 核心边缘琥珀

/**
 * 天气效果覆盖层
 * 在背景上叠加逼真的粒子动画
 *
 * 性能与效果设计：
 * 1. 分级帧率控制 - 雨/雷/雨夹雪 60fps；雪/风 30fps；晴/夜/云/雾 20fps
 * 2. 省电模式感知 - 系统省电模式下粒子数量减半、帧率下调（EffectQuality.Low）
 * 3. 生命周期感知 - 页面不可见（后台/锁屏）时暂停动画循环
 * 4. 粒子精灵化 - 渐变光晕/雨丝预烘焙为位图（见 ParticleSprites.kt），逐帧零 Brush/Shader 分配
 * 5. 按屏生成 - 粒子按画布实际尺寸分布，大屏不再只有左半屏有粒子
 * 6. 天气切换交叉淡入淡出（按效果类型，强度微调不触发）
 * 7. 风数据驱动 - 雨丝倾斜角、云层漂移方向/速度、风粒子速度跟随实时风
 * 8. 晴天太阳（暖色光晕+白心核心慢呼吸，无射灯式窄射线）；闪电双闪序列 + 分叉闪电路径；晴夜流星
 */
@Composable
fun WeatherEffectOverlay(
    skycon: String?,
    isDay: Boolean,
    wind: WindInfo? = null,
    modifier: Modifier = Modifier
) {
    // 优化1: 省电模式感知 -> 动画质量档位
    val quality = if (rememberPowerSaveMode()) EffectQuality.Low else EffectQuality.High

    // 优化2: 分级帧率控制（质量档位变化时重建，动画循环会跟随重启）
    val precipLimiter = remember(quality) { FrameRateLimiter(targetFps = quality.precipFps) }   // 雨/雷/雨夹雪
    val mediumLimiter = remember(quality) { FrameRateLimiter(targetFps = quality.mediumFps) }   // 雪/风
    val ambientLimiter = remember(quality) { FrameRateLimiter(targetFps = quality.ambientFps) } // 晴/夜/云/雾

    // 优化3: 解析天气强度（叠加省电模式的粒子缩放）
    val intensity = remember(skycon, quality) {
        val base = parseWeatherIntensity(skycon)
        if (quality.particleScale != 1f) {
            base.copy(particleCount = base.particleCount * quality.particleScale)
        } else {
            base
        }
    }

    // 优化4: 风驱动参数（-1..1；0 = 无风或无数据，此时所有效果与原实现完全一致）
    val windSlant = remember(wind) { computeWindSlant(wind) }

    // 优化5: 生命周期感知 - 仅在页面可见（STARTED 及以上）时驱动动画
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val animationsEnabled = lifecycleState.isAtLeast(Lifecycle.State.STARTED)

    // 优化6: 天气切换交叉淡入淡出（按效果类型切换，同类型的强度微调不触发）
    val effectType = remember(skycon, isDay) { effectTypeOf(skycon, isDay) }
    Crossfade(
        targetState = effectType,
        animationSpec = tween(durationMillis = 600),
        label = "weather_effect_crossfade",
        modifier = modifier
    ) { type ->
        WeatherEffectContent(
            type = type,
            intensity = intensity,
            windSlant = windSlant,
            precipLimiter = precipLimiter,
            mediumLimiter = mediumLimiter,
            ambientLimiter = ambientLimiter,
            animationsEnabled = animationsEnabled,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * 天气效果内容渲染（内部函数）
 * 保持原有效果选择逻辑不变（与 effectTypeOf 一一对应）
 */
@Composable
private fun WeatherEffectContent(
    type: String,
    intensity: WeatherIntensity,
    windSlant: Float,
    precipLimiter: FrameRateLimiter,
    mediumLimiter: FrameRateLimiter,
    ambientLimiter: FrameRateLimiter,
    animationsEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    when (type) {
        "SUNNY_DAY" -> SunnyDayEffect(modifier, ambientLimiter, animationsEnabled)
        "CLEAR_NIGHT" -> ClearNightEffect(modifier, ambientLimiter, animationsEnabled)
        "PARTLY_CLOUDY_DAY" -> PartlyCloudyDayEffect(modifier, windSlant, ambientLimiter, animationsEnabled)
        "CLOUDY" -> CloudyEffect(modifier, windSlant, ambientLimiter, animationsEnabled)
        "THUNDER_SHOWER" -> ThunderShowerEffect(modifier, intensity, windSlant, precipLimiter, animationsEnabled)
        "SNOW" -> SnowEffect(modifier, intensity, windSlant, mediumLimiter, animationsEnabled)
        "SLEET" -> SleetEffect(modifier, intensity, windSlant, precipLimiter, animationsEnabled)
        "RAIN" -> RainEffect(modifier, intensity, windSlant, precipLimiter, animationsEnabled)
        "FOG" -> FogEffect(modifier, ambientLimiter, animationsEnabled)
        "WIND" -> WindEffect(modifier, windSlant, mediumLimiter, animationsEnabled)
        // "NONE" -> 不渲染任何效果（与原实现行为一致）
    }
}

/**
 * 天气效果类型（用于切换过渡：同类型强度变化不触发 Crossfade）
 * 选择逻辑与原 when 分支完全一致
 */
private fun effectTypeOf(skycon: String?, isDay: Boolean): String {
    return when {
        // 无数据（启动加载中）时不渲染粒子效果，避免先闪出晴天特效再切换到实际天气
        skycon == null -> "NONE"
        skycon.contains("CLEAR") -> if (isDay) "SUNNY_DAY" else "CLEAR_NIGHT"
        skycon.contains("PARTLY_CLOUDY") -> if (isDay) "PARTLY_CLOUDY_DAY" else "CLEAR_NIGHT"
        skycon.contains("CLOUDY") -> "CLOUDY"
        // THUNDER_SHOWER: 雷阵雨 - 雨+闪电
        skycon == "THUNDER_SHOWER" -> "THUNDER_SHOWER"
        // STORM_SNOW: 暴雪 - 必须在 STORM_RAIN 之前判断
        skycon == "STORM_SNOW" -> "SNOW"
        // SLEET: 雨夹雪 - 雨+雪混合
        skycon == "SLEET" -> "SLEET"
        // RAIN 相关: LIGHT_RAIN, MODERATE_RAIN, HEAVY_RAIN, STORM_RAIN
        skycon.contains("RAIN") -> "RAIN"
        // SNOW 相关: LIGHT_SNOW, MODERATE_SNOW, HEAVY_SNOW
        skycon.contains("SNOW") -> "SNOW"
        // 霾/雾/浮尘/沙尘
        skycon.contains("HAZE") || skycon == "FOG" || skycon == "DUST" || skycon == "SAND" -> "FOG"
        skycon == "WIND" -> "WIND"
        else -> "NONE"
    }
}

/**
 * 计算风驱动系数（-1..1）
 * 风向为"来向"（0=北，90=东），屏幕水平速度分量 = -sin(direction)
 * 60km/h ≈ 7级风作为满强度
 */
private fun computeWindSlant(wind: WindInfo?): Float {
    if (wind == null || wind.speedKmh <= 0f) return 0f
    val strength = (wind.speedKmh / 60f).coerceIn(0f, 1f)
    val horizontal = (-sin(Math.toRadians(wind.directionDeg.toDouble()))).toFloat()
    return horizontal * strength
}

/**
 * 数学取模（结果恒为非负），修复原实现中负速度粒子随时间永久移出屏幕的问题
 */
private fun wrap(value: Float, modulo: Float): Float = ((value % modulo) + modulo) % modulo

/**
 * 雨丝倾斜角：由水平/垂直速度比决定，限制在 ±20° 内
 */
private fun slantAngle(vx: Float, vy: Float): Float =
    atan2(vx, vy).coerceIn(-MaxRainSlantRad, MaxRainSlantRad)

/**
 * 绘制雨丝（无风时走原路径零额外开销；有风时绕顶点旋转）
 */
private fun DrawScope.drawRainStreak(
    x: Float, y: Float, length: Float, width: Float, alpha: Float,
    angle: Float, hard: Boolean
) {
    val sprite = if (hard) ParticleSprites.rainStreakHard else ParticleSprites.rainStreakSoft
    if (angle == 0f) {
        drawStreak(sprite, x, y, width, length, alpha)
    } else {
        withTransform({
            rotate(degrees = angle * RadToDeg, pivot = Offset(x, y))
        }) {
            drawStreak(sprite, x, y, width, length, alpha)
        }
    }
}

/**
 * 绘制太阳（屏幕右上方）：外层暖色光晕 + 中层光晕 + 白心核心 + 微实感圆面，
 * 约 12.6s 慢呼吸。替代原窄条状“射灯”射线，可读性更贴近真实阳光。
 * @param dim 整体强度系数（多云时降低）
 */
private fun DrawScope.drawSun(animationTime: Float, dim: Float = 1f) {
    val sunX = size.width * 0.74f
    val sunY = size.height * 0.15f
    val breath = 0.93f + 0.07f * sin(animationTime * 0.5f)
    val base = size.width

    // 外层暖色大光晕
    drawGlow(
        sprite = ParticleSprites.glowSoft,
        centerX = sunX, centerY = sunY,
        radius = base * 0.30f * breath,
        alpha = 0.30f * dim,
        colorFilter = ParticleSprites.tint(SunOuterColor)
    )
    // 中层光晕
    drawGlow(
        sprite = ParticleSprites.glowSoft,
        centerX = sunX, centerY = sunY,
        radius = base * 0.17f * breath,
        alpha = 0.45f * dim,
        colorFilter = ParticleSprites.tint(SunMidColor)
    )
    // 核心（白心暖边）
    drawGlow(
        sprite = ParticleSprites.whiteCoreGlow(SunCoreColor, midAlpha = 0.85f),
        centerX = sunX, centerY = sunY,
        radius = base * 0.085f * breath,
        alpha = 0.95f * dim
    )
    // 太阳圆面（轻微实感，帮助识别为太阳）
    drawCircle(
        color = Color.White.copy(alpha = 0.85f * dim),
        radius = base * 0.038f * breath,
        center = Offset(sunX, sunY)
    )
}

/**
 * 省电模式监测（广播驱动，组合期间实时更新）
 */
@Composable
private fun rememberPowerSaveMode(): Boolean {
    val context = LocalContext.current
    val appContext = remember { context.applicationContext }
    val powerManager = remember { appContext.getSystemService(Context.POWER_SERVICE) as PowerManager }
    var powerSave by remember { mutableStateOf(powerManager.isPowerSaveMode) }
    DisposableEffect(appContext) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                powerSave = powerManager.isPowerSaveMode
            }
        }
        // 系统保护广播，无需 EXPORTED/NOT_EXPORTED 标志
        appContext.registerReceiver(receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        onDispose { appContext.unregisterReceiver(receiver) }
    }
    return powerSave
}

/**
 * 晴天效果 - 阳光光束
 */
@Composable
private fun SunnyDayEffect(
    modifier: Modifier,
    frameRateLimiter: FrameRateLimiter = FrameRateLimiter(),
    animationsEnabled: Boolean = true
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val areaWidth = constraints.maxWidth.toFloat()
        val areaHeight = constraints.maxHeight.toFloat()
        val sunBeams = remember(areaWidth, areaHeight) { generateSunBeams(areaWidth, areaHeight) }

        // 粒子位置动画（delta 时间累加，避免长时间运行后大浮点精度抖动）
        var animationTime by remember { mutableStateOf(0f) }
        var lastFrameTime by remember { mutableStateOf(0L) }
        LaunchedEffect(animationsEnabled, frameRateLimiter) {
            if (!animationsEnabled) return@LaunchedEffect
            lastFrameTime = 0L
            while (isActive) {
                withFrameMillis { frameTime ->
                    if (frameRateLimiter.shouldRender(frameTime)) {
                        if (lastFrameTime > 0) {
                            val delta = (frameTime - lastFrameTime) / 1000f
                            animationTime += delta
                        }
                        lastFrameTime = frameTime
                    }
                }
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            // 阳光光晕效果（精灵化：零逐帧分配，渲染结果与原径向渐变等价）
            sunBeams.forEach { beam ->
                val pulse = sin(animationTime * beam.pulseSpeed + beam.pulseOffset)
                val currentAlpha = (beam.alpha * (0.6f + 0.4f * pulse)).coerceIn(0f, 1f)
                val currentSize = beam.size * (0.95f + 0.05f * pulse)

                // 缓慢移动
                val x = wrap(beam.x + animationTime * beam.speedX * 15, size.width + beam.size * 2) - beam.size
                val y = beam.y + sin(animationTime * 0.3f + beam.pulseOffset) * 15f

                val beamTint = ParticleSprites.tint(beam.color)

                // 外层大光晕
                drawGlow(
                    sprite = ParticleSprites.glowSoft,
                    centerX = x, centerY = y,
                    radius = currentSize * 2.5f,
                    alpha = currentAlpha * 0.3f,
                    colorFilter = beamTint
                )

                // 中层光晕
                drawGlow(
                    sprite = ParticleSprites.glowSoft,
                    centerX = x, centerY = y,
                    radius = currentSize * 1.5f,
                    alpha = currentAlpha * 0.5f,
                    colorFilter = beamTint
                )

                // 核心光斑（白心彩色边缘）
                drawGlow(
                    sprite = ParticleSprites.whiteCoreGlow(beam.color, midAlpha = 0.75f),
                    centerX = x, centerY = y,
                    radius = currentSize * 0.6f,
                    alpha = currentAlpha * 0.8f
                )
            }


        }
    }
}

/**
 * 多云白天效果 - 阳光+云朵
 */
@Composable
private fun PartlyCloudyDayEffect(
    modifier: Modifier,
    windSlant: Float = 0f,
    frameRateLimiter: FrameRateLimiter = FrameRateLimiter(),
    animationsEnabled: Boolean = true
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val areaWidth = constraints.maxWidth.toFloat()
        val areaHeight = constraints.maxHeight.toFloat()
        val sunBeams = remember(areaWidth, areaHeight) { generateSunBeams(areaWidth, areaHeight) }
        val cloudsMid = remember(areaWidth, areaHeight) { generateCloudsMid(areaWidth, areaHeight) }
        val cloudsNear = remember(areaWidth, areaHeight) { generateCloudsNear(areaWidth, areaHeight) }

        var animationTime by remember { mutableStateOf(0f) }
        var lastFrameTime by remember { mutableStateOf(0L) }
        LaunchedEffect(animationsEnabled, frameRateLimiter) {
            if (!animationsEnabled) return@LaunchedEffect
            lastFrameTime = 0L
            while (isActive) {
                withFrameMillis { frameTime ->
                    if (frameRateLimiter.shouldRender(frameTime)) {
                        if (lastFrameTime > 0) {
                            val delta = (frameTime - lastFrameTime) / 1000f
                            animationTime += delta
                        }
                        lastFrameTime = frameTime
                    }
                }
            }
        }

        // 风对云层的影响：方向 + 速度倍率（无风时因子为 1，与原实现一致）
        val cloudWindFactor = if (windSlant != 0f) sign(windSlant) * (1f + abs(windSlant) * 1.5f) else 1f

        Canvas(modifier = Modifier.fillMaxSize()) {
            // 阳光光晕（较少，精灵化）
            sunBeams.take(6).forEach { beam ->
                val pulse = sin(animationTime * beam.pulseSpeed + beam.pulseOffset)
                val currentAlpha = (beam.alpha * 0.6f * (0.6f + 0.4f * pulse)).coerceIn(0f, 1f)
                val currentSize = beam.size * (0.95f + 0.05f * pulse)

                val x = wrap(beam.x + animationTime * beam.speedX * 15, size.width + beam.size * 2) - beam.size
                val y = beam.y + sin(animationTime * 0.3f + beam.pulseOffset) * 15f

                drawGlow(
                    sprite = ParticleSprites.glowSoft,
                    centerX = x, centerY = y,
                    radius = currentSize * 2f,
                    alpha = currentAlpha * 0.35f,
                    colorFilter = ParticleSprites.tint(beam.color)
                )
            }

            // 中景云层
            cloudsMid.forEach { cloud ->
                drawCloud(cloud, animationTime, speedFactor = 10f, alphaFactor = 0.8f, windFactor = cloudWindFactor)
            }

            // 近景云层
            cloudsNear.forEach { cloud ->
                drawCloud(cloud, animationTime, speedFactor = 16f, alphaFactor = 1f, windFactor = cloudWindFactor)
            }
        }
    }
}

/**
 * 晴夜效果 - 星星闪烁 + 流星
 */
@Composable
private fun ClearNightEffect(
    modifier: Modifier,
    frameRateLimiter: FrameRateLimiter = FrameRateLimiter(),
    animationsEnabled: Boolean = true
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val areaWidth = constraints.maxWidth.toFloat()
        val areaHeight = constraints.maxHeight.toFloat()
        val stars = remember(areaWidth, areaHeight) { generateStars(areaWidth, areaHeight) }
        var animationTime by remember { mutableStateOf(0f) }
        var lastFrameTime by remember { mutableStateOf(0L) }

        // 流星状态：间隔 8-15 秒随机划过一颗
        var meteor by remember { mutableStateOf<Meteor?>(null) }
        var meteorStartTime by remember { mutableStateOf(0f) }
        var nextMeteorTime by remember { mutableStateOf(5f + Random.nextFloat() * 6f) }

        LaunchedEffect(animationsEnabled, frameRateLimiter) {
            if (!animationsEnabled) return@LaunchedEffect
            lastFrameTime = 0L
            while (isActive) {
                withFrameMillis { frameTime ->
                    if (frameRateLimiter.shouldRender(frameTime)) {
                        if (lastFrameTime > 0) {
                            val delta = (frameTime - lastFrameTime) / 1000f
                            animationTime += delta

                            // 流星调度
                            val active = meteor
                            if (active == null) {
                                if (animationTime >= nextMeteorTime) {
                                    meteor = generateMeteor()
                                    meteorStartTime = animationTime
                                }
                            } else if (animationTime - meteorStartTime > active.duration) {
                                meteor = null
                                nextMeteorTime = animationTime + 8f + Random.nextFloat() * 7f
                            }
                        }
                        lastFrameTime = frameTime
                    }
                }
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            stars.forEach { star ->
                // 闪烁效果
                val twinkle = sin(animationTime * star.twinkleSpeed + star.twinkleOffset)
                val currentAlpha = (star.maxAlpha * (0.5f + 0.5f * twinkle)).coerceIn(0f, 1f)

                // 光晕（精灵化）
                drawGlow(
                    sprite = ParticleSprites.glowSoft,
                    centerX = star.x, centerY = star.y,
                    radius = star.size * 3f,
                    alpha = currentAlpha * 0.6f
                )

                // 核心
                drawCircle(
                    color = Color.White.copy(alpha = currentAlpha),
                    radius = star.size * 0.5f,
                    center = Offset(star.x, star.y)
                )

                // 十字光芒
                val rayLength = star.size * 2f * (0.8f + 0.2f * twinkle)
                val rayAlpha = currentAlpha * 0.5f

                drawLine(
                    color = Color.White.copy(alpha = rayAlpha),
                    start = Offset(star.x - rayLength, star.y),
                    end = Offset(star.x + rayLength, star.y),
                    strokeWidth = 0.5f
                )

                drawLine(
                    color = Color.White.copy(alpha = rayAlpha),
                    start = Offset(star.x, star.y - rayLength),
                    end = Offset(star.x, star.y + rayLength),
                    strokeWidth = 0.5f
                )
            }

            // 流星（彗尾渐变沿运动方向旋转，正弦包络淡入淡出）
            meteor?.let { m ->
                val elapsed = animationTime - meteorStartTime
                val progress = (elapsed / m.duration).coerceIn(0f, 1f)
                val fade = sin(progress * PI.toFloat())
                val headX = m.startX * size.width + m.dirX * m.speed * elapsed
                val headY = m.startY * size.height + m.dirY * m.speed * elapsed
                if (fade > 0f && headX >= -50f && headX <= size.width + 50f && headY <= size.height + 50f) {
                    val tailLength = m.speed * 0.16f
                    val angle = atan2(m.dirX, m.dirY)
                    withTransform({
                        rotate(degrees = angle * RadToDeg, pivot = Offset(headX, headY))
                    }) {
                        drawStreak(
                            sprite = ParticleSprites.rainStreakHard,
                            x = headX, top = headY - tailLength,
                            width = 2.5f, height = tailLength,
                            alpha = 0.85f * fade
                        )
                    }
                    drawGlow(
                        sprite = ParticleSprites.glowSoft,
                        centerX = headX, centerY = headY,
                        radius = 5f,
                        alpha = 0.7f * fade
                    )
                }
            }
        }
    }
}

/**
 * 雨天效果 - 4层雨效果 + 底部水雾
 * 支持天气强度调整（小雨、中雨、大雨、暴雨）
 * 支持风驱动倾斜（无风数据时与原实现完全一致）
 *
 * 4层结构：
 * 1. 远景层（far）：小、淡、慢，模拟远处的雨
 * 2. 中远景层（mid-far）：中等大小和透明度
 * 3. 中近景层（mid-near）：较大、较明显
 * 4. 近景层（near）：最大、最明显、最快
 */
@Composable
private fun RainEffect(
    modifier: Modifier,
    intensity: WeatherIntensity = WeatherIntensity(),
    windSlant: Float = 0f,
    frameRateLimiter: FrameRateLimiter = FrameRateLimiter(),
    animationsEnabled: Boolean = true
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val areaWidth = constraints.maxWidth.toFloat()
        val areaHeight = constraints.maxHeight.toFloat()

        // 根据强度动态调整粒子数量（4层）
        val farCount = (30 * intensity.particleCount).toInt().coerceIn(10, 60)
        val midFarCount = (35 * intensity.particleCount).toInt().coerceIn(15, 70)
        val midNearCount = (30 * intensity.particleCount).toInt().coerceIn(10, 60)
        val nearCount = (25 * intensity.particleCount).toInt().coerceIn(10, 50)

        // 4层雨滴数据
        val raindropsFar = remember(areaWidth, areaHeight, intensity.particleCount) { generateRainLayer(farCount, areaWidth, areaHeight, isFar = true) }
        val raindropsMidFar = remember(areaWidth, areaHeight, intensity.particleCount) { generateRainLayer(midFarCount, areaWidth, areaHeight, isFar = false) }
        val raindropsMidNear = remember(areaWidth, areaHeight, intensity.particleCount) { generateRainLayer(midNearCount, areaWidth, areaHeight, isFar = false) }
        val raindropsNear = remember(areaWidth, areaHeight, intensity.particleCount) { generateRainLayer(nearCount, areaWidth, areaHeight, isFar = false) }
        val rainMist = remember(areaWidth) { generateRainMist(areaWidth) }

        var animationTime by remember { mutableStateOf(0f) }
        var lastFrameTime by remember { mutableStateOf(0L) }

        LaunchedEffect(animationsEnabled, frameRateLimiter) {
            if (!animationsEnabled) return@LaunchedEffect
            // 恢复可见时重置，避免时间差导致粒子瞬移
            lastFrameTime = 0L
            while (isActive) {
                withFrameMillis { frameTime ->
                    if (frameRateLimiter.shouldRender(frameTime)) {
                        if (lastFrameTime > 0) {
                            val delta = (frameTime - lastFrameTime) / 1000f
                            animationTime += delta
                        }
                        lastFrameTime = frameTime
                    }
                }
            }
        }

        // 风横向速度（px/s，满强度 220）
        val windVx = windSlant * 220f

        Canvas(modifier = Modifier.fillMaxSize()) {
            val speedMultiplier = intensity.speed
            val alphaMultiplier = intensity.alpha
            val thicknessMultiplier = intensity.thickness

            // ============ 1. 远景层（far）- 小、淡、慢 ============
            raindropsFar.forEach { drop ->
                val speedFactor = 80f * speedMultiplier
                val vx = drop.speedX * 15 + windVx
                val vy = drop.speedY * speedFactor
                val x = wrap(drop.x + animationTime * vx, size.width + 60f) - 30f
                val y = wrap(drop.y + animationTime * vy, size.height + drop.length) - drop.length
                val angle = if (windVx != 0f) slantAngle(vx, vy) else 0f

                drawRainStreak(
                    x = x, y = y, length = drop.length,
                    width = drop.thickness * thicknessMultiplier * 0.4f,
                    alpha = drop.alpha * alphaMultiplier * 0.3f,
                    angle = angle, hard = false
                )
            }

            // ============ 2. 中远景层（mid-far） ============
            raindropsMidFar.forEach { drop ->
                val speedFactor = 120f * speedMultiplier
                val vx = drop.speedX * 25 + windVx
                val vy = drop.speedY * speedFactor
                val x = wrap(drop.x + animationTime * vx, size.width + 80f) - 40f
                val y = wrap(drop.y + animationTime * vy, size.height + drop.length) - drop.length
                val angle = if (windVx != 0f) slantAngle(vx, vy) else 0f

                drawRainStreak(
                    x = x, y = y, length = drop.length,
                    width = drop.thickness * thicknessMultiplier * 0.6f,
                    alpha = drop.alpha * alphaMultiplier * 0.5f,
                    angle = angle, hard = false
                )
            }

            // ============ 3. 中近景层（mid-near） ============
            raindropsMidNear.forEach { drop ->
                val speedFactor = 170f * speedMultiplier
                val vx = drop.speedX * 35 + windVx
                val vy = drop.speedY * speedFactor
                val x = wrap(drop.x + animationTime * vx, size.width + 100f) - 50f
                val y = wrap(drop.y + animationTime * vy, size.height + drop.length) - drop.length
                val angle = if (windVx != 0f) slantAngle(vx, vy) else 0f

                drawRainStreak(
                    x = x, y = y, length = drop.length,
                    width = drop.thickness * thicknessMultiplier * 0.8f,
                    alpha = drop.alpha * alphaMultiplier * 0.7f,
                    angle = angle, hard = false
                )

                // 雨滴头部高光（跟随倾斜后的落点）
                drawCircle(
                    color = Color.White.copy(alpha = drop.alpha * alphaMultiplier * 0.5f),
                    radius = drop.thickness * thicknessMultiplier * 0.5f,
                    center = Offset(x + sin(angle) * drop.length, y + cos(angle) * drop.length)
                )
            }

            // ============ 4. 近景层（near）- 最大、最明显、最快 ============
            raindropsNear.forEach { drop ->
                val speedFactor = 230f * speedMultiplier
                val vx = drop.speedX * 45 + windVx
                val vy = drop.speedY * speedFactor
                val x = wrap(drop.x + animationTime * vx, size.width + 120f) - 60f
                val y = wrap(drop.y + animationTime * vy, size.height + drop.length) - drop.length
                val angle = if (windVx != 0f) slantAngle(vx, vy) else 0f

                // 雨滴主体
                drawRainStreak(
                    x = x, y = y, length = drop.length,
                    width = drop.thickness * thicknessMultiplier,
                    alpha = drop.alpha * alphaMultiplier,
                    angle = angle, hard = true
                )

                // 雨滴头部高光（更明显）
                drawCircle(
                    color = Color.White.copy(alpha = drop.alpha * alphaMultiplier * 0.8f),
                    radius = drop.thickness * thicknessMultiplier * 0.8f,
                    center = Offset(x + sin(angle) * drop.length, y + cos(angle) * drop.length)
                )
            }

            // ============ 5. 底部水雾效果（暴雨时更明显，精灵化） ============
            val mistAlphaBoost = if (intensity.particleCount > 1.5f) 1.5f else 1f
            rainMist.forEach { mist ->
                val x = wrap(mist.x + animationTime * mist.speedX * 15, size.width + mist.size * 2) - mist.size
                val y = size.height - mist.y - mist.size * 0.3f

                drawGlow(
                    sprite = ParticleSprites.glowMist,
                    centerX = x, centerY = y,
                    radius = mist.size,
                    alpha = mist.alpha * mistAlphaBoost * 0.6f
                )
            }
        }
    }
}

/**
 * 生成单层雨滴数据
 * @param count 粒子数量
 * @param isFar 是否为远景层（更小、更淡）
 */
private fun generateRainLayer(count: Int, width: Float, height: Float, isFar: Boolean): List<Raindrop> {
    return List(count) {
        val speed = if (isFar) {
            Random.nextFloat() * 4f + 6f  // 远景更慢
        } else {
            Random.nextFloat() * 8f + 12f
        }
        Raindrop(
            x = Random.nextFloat() * width,
            y = Random.nextFloat() * height,
            alpha = if (isFar) {
                Random.nextFloat() * 0.15f + 0.05f  // 远景更淡
            } else {
                Random.nextFloat() * 0.3f + 0.2f
            },
            size = if (isFar) {
                Random.nextFloat() * 1f + 0.3f  // 远景更小
            } else {
                Random.nextFloat() * 1.5f + 0.5f
            },
            speedX = Random.nextFloat() * 1.5f - 0.3f,
            speedY = speed,
            length = if (isFar) {
                Random.nextFloat() * 12f + 8f  // 远景更短
            } else {
                Random.nextFloat() * 22f + 15f
            },
            thickness = if (isFar) {
                Random.nextFloat() * 0.6f + 0.3f  // 远景更细
            } else {
                Random.nextFloat() * 1.2f + 0.6f
            }
        )
    }
}

/**
 * 雪天效果 - 雪花飘落
 * 支持天气强度调整（小雪、中雪、大雪、暴雪）
 * 支持风驱动侧移（无风数据时与原实现完全一致）
 */
@Composable
private fun SnowEffect(
    modifier: Modifier,
    intensity: WeatherIntensity = WeatherIntensity(),
    windSlant: Float = 0f,
    frameRateLimiter: FrameRateLimiter = FrameRateLimiter(),
    animationsEnabled: Boolean = true
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val areaWidth = constraints.maxWidth.toFloat()
        val areaHeight = constraints.maxHeight.toFloat()

        // 根据强度动态调整粒子数量
        val snowCount = (50 * intensity.particleCount).toInt().coerceIn(20, 120)
        val snowflakes = remember(areaWidth, areaHeight, intensity.particleCount) { generateSnowflakes(snowCount, areaWidth, areaHeight) }
        var animationTime by remember { mutableStateOf(0f) }
        var lastFrameTime by remember { mutableStateOf(0L) }

        LaunchedEffect(animationsEnabled, frameRateLimiter) {
            if (!animationsEnabled) return@LaunchedEffect
            lastFrameTime = 0L
            while (isActive) {
                withFrameMillis { frameTime ->
                    if (frameRateLimiter.shouldRender(frameTime)) {
                        if (lastFrameTime > 0) {
                            val delta = (frameTime - lastFrameTime) / 1000f
                            animationTime += delta
                        }
                        lastFrameTime = frameTime
                    }
                }
            }
        }

        // 雪花受风影响较弱（横向漂移系数 0.25）
        val windDrift = windSlant * 220f * 0.25f

        Canvas(modifier = Modifier.fillMaxSize()) {
            // 优化: 根据强度调整参数
            val speedMultiplier = intensity.speed
            val sizeMultiplier = intensity.size
            val alphaMultiplier = intensity.alpha

            snowflakes.forEach { flake ->
                // 摇摆效果
                val wobble = sin(animationTime * flake.wobbleSpeed + flake.wobbleOffset)
                val rawX = flake.x + wobble * flake.wobbleAmplitude + animationTime * windDrift
                val x = if (windDrift != 0f) {
                    wrap(rawX, size.width + flake.size * 4f) - flake.size * 2f
                } else {
                    rawX
                }
                val y = ((flake.y + animationTime * flake.speedY * speedMultiplier * 30) % (size.height + flake.size * 4)) - flake.size * 2

                val adjustedSize = flake.size * sizeMultiplier
                val adjustedAlpha = flake.alpha * alphaMultiplier

                // 外层光晕（精灵化）
                drawGlow(
                    sprite = ParticleSprites.glowSoft,
                    centerX = x, centerY = y,
                    radius = adjustedSize * 2.5f,
                    alpha = adjustedAlpha * 0.4f
                )

                // 内层实心
                drawCircle(
                    color = Color.White.copy(alpha = adjustedAlpha),
                    radius = adjustedSize * 0.6f,
                    center = Offset(x, y)
                )
            }
        }
    }
}

/**
 * 多云效果 - 远近云层视差漂浮
 * 支持风驱动方向/速度（无风数据时与原实现完全一致）
 */
@Composable
private fun CloudyEffect(
    modifier: Modifier,
    windSlant: Float = 0f,
    frameRateLimiter: FrameRateLimiter = FrameRateLimiter(),
    animationsEnabled: Boolean = true
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val areaWidth = constraints.maxWidth.toFloat()
        val areaHeight = constraints.maxHeight.toFloat()
        val cloudsFar = remember(areaWidth, areaHeight) { generateCloudsFar(areaWidth, areaHeight) }
        val cloudsMid = remember(areaWidth, areaHeight) { generateCloudsMid(areaWidth, areaHeight) }
        val cloudsNear = remember(areaWidth, areaHeight) { generateCloudsNear(areaWidth, areaHeight) }
        var animationTime by remember { mutableStateOf(0f) }
        var lastFrameTime by remember { mutableStateOf(0L) }

        LaunchedEffect(animationsEnabled, frameRateLimiter) {
            if (!animationsEnabled) return@LaunchedEffect
            lastFrameTime = 0L
            while (isActive) {
                withFrameMillis { frameTime ->
                    if (frameRateLimiter.shouldRender(frameTime)) {
                        if (lastFrameTime > 0) {
                            val delta = (frameTime - lastFrameTime) / 1000f
                            animationTime += delta
                        }
                        lastFrameTime = frameTime
                    }
                }
            }
        }

        // 风对云层的影响：方向 + 速度倍率（无风时因子为 1，与原实现一致）
        val cloudWindFactor = if (windSlant != 0f) sign(windSlant) * (1f + abs(windSlant) * 1.5f) else 1f

        Canvas(modifier = Modifier.fillMaxSize()) {
            // 远景云层（更小、更淡、更慢）
            cloudsFar.forEach { cloud ->
                drawCloud(cloud, animationTime, speedFactor = 5f, alphaFactor = 0.4f, windFactor = cloudWindFactor)
            }

            // 中景云层
            cloudsMid.forEach { cloud ->
                drawCloud(cloud, animationTime, speedFactor = 10f, alphaFactor = 0.7f, windFactor = cloudWindFactor)
            }

            // 近景云层（更大、更明显、更快）
            cloudsNear.forEach { cloud ->
                drawCloud(cloud, animationTime, speedFactor = 18f, alphaFactor = 1f, windFactor = cloudWindFactor)
            }
        }
    }
}

/**
 * 绘制单朵云（精灵化：原 5 个径向渐变圆 → 5 次位图绘制，渐变剖面保持一致）
 * @param windFactor 风因子（方向+速度倍率），为 1 时与原实现完全一致
 */
private fun DrawScope.drawCloud(
    cloud: Cloud,
    animationTime: Float,
    speedFactor: Float,
    alphaFactor: Float,
    windFactor: Float = 1f
) {
    val x = wrap(cloud.x + animationTime * cloud.speedX * speedFactor * windFactor, size.width + cloud.width * 2) - cloud.width
    val y = cloud.y + sin(animationTime * 0.12f + cloud.y * 0.003f) * 6f

    val centerX = x + cloud.width / 2
    val centerY = y + cloud.height / 2
    val alpha = cloud.alpha * alphaFactor

    // 云朵主体（大椭圆）
    drawGlow(
        sprite = ParticleSprites.glowCloudMain,
        centerX = centerX, centerY = centerY,
        radius = cloud.width * 0.55f,
        alpha = alpha * 0.7f
    )

    // 左侧凸起
    drawGlow(
        sprite = ParticleSprites.glowCloudLeft,
        centerX = x + cloud.width * 0.2f,
        centerY = centerY - cloud.height * 0.1f,
        radius = cloud.width * 0.38f,
        alpha = alpha * 0.65f
    )

    // 右侧凸起
    drawGlow(
        sprite = ParticleSprites.glowSoft,
        centerX = x + cloud.width * 0.8f,
        centerY = centerY + cloud.height * 0.05f,
        radius = cloud.width * 0.32f,
        alpha = alpha * 0.6f
    )

    // 顶部凸起
    drawGlow(
        sprite = ParticleSprites.glowCloudTop,
        centerX = centerX + cloud.width * 0.15f,
        centerY = centerY - cloud.height * 0.35f,
        radius = cloud.width * 0.28f,
        alpha = alpha * 0.55f
    )

    // 底部阴影
    drawGlow(
        sprite = ParticleSprites.glowSoft,
        centerX = centerX,
        centerY = centerY + cloud.height * 0.25f,
        radius = cloud.width * 0.45f,
        alpha = alpha * 0.3f
    )
}

/**
 * 雾天效果 - 浓雾弥漫
 */
@Composable
private fun FogEffect(
    modifier: Modifier,
    frameRateLimiter: FrameRateLimiter = FrameRateLimiter(),
    animationsEnabled: Boolean = true
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val areaWidth = constraints.maxWidth.toFloat()
        val areaHeight = constraints.maxHeight.toFloat()
        val fogLayers = remember(areaWidth, areaHeight) { generateFogLayers(areaWidth, areaHeight) }
        var animationTime by remember { mutableStateOf(0f) }
        var lastFrameTime by remember { mutableStateOf(0L) }

        LaunchedEffect(animationsEnabled, frameRateLimiter) {
            if (!animationsEnabled) return@LaunchedEffect
            lastFrameTime = 0L
            while (isActive) {
                withFrameMillis { frameTime ->
                    if (frameRateLimiter.shouldRender(frameTime)) {
                        if (lastFrameTime > 0) {
                            val delta = (frameTime - lastFrameTime) / 1000f
                            animationTime += delta
                        }
                        lastFrameTime = frameTime
                    }
                }
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            fogLayers.forEach { fog ->
                // 缓慢漂浮
                val x = wrap(fog.x + animationTime * fog.speedX * 8, size.width + fog.size * 2) - fog.size
                val y = fog.y + sin(animationTime * 0.2f + fog.y * 0.008f) * 12f

                // 雾气团（精灵化）
                drawGlow(
                    sprite = ParticleSprites.glowFog,
                    centerX = x, centerY = y,
                    radius = fog.size,
                    alpha = fog.alpha
                )
            }
        }
    }
}

/**
 * 大风效果
 * 支持风驱动方向/速度（无风数据时与原实现完全一致）
 */
@Composable
private fun WindEffect(
    modifier: Modifier,
    windSlant: Float = 0f,
    frameRateLimiter: FrameRateLimiter = FrameRateLimiter(),
    animationsEnabled: Boolean = true
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val areaWidth = constraints.maxWidth.toFloat()
        val areaHeight = constraints.maxHeight.toFloat()
        val windParticles = remember(areaWidth, areaHeight) { generateWindParticles(areaWidth, areaHeight) }
        var animationTime by remember { mutableStateOf(0f) }
        var lastFrameTime by remember { mutableStateOf(0L) }

        LaunchedEffect(animationsEnabled, frameRateLimiter) {
            if (!animationsEnabled) return@LaunchedEffect
            lastFrameTime = 0L
            while (isActive) {
                withFrameMillis { frameTime ->
                    if (frameRateLimiter.shouldRender(frameTime)) {
                        if (lastFrameTime > 0) {
                            val delta = (frameTime - lastFrameTime) / 1000f
                            animationTime += delta
                        }
                        lastFrameTime = frameTime
                    }
                }
            }
        }

        // 风因子：方向 + 速度倍率（无风数据时因子为 1，与原实现一致）
        val windFactor = if (windSlant != 0f) sign(windSlant) * (0.7f + abs(windSlant) * 1.3f) else 1f

        Canvas(modifier = Modifier.fillMaxSize()) {
            windParticles.forEach { wind ->
                // 快速水平移动
                val x = wrap(wind.x + animationTime * wind.speedX * 50 * windFactor, size.width + wind.size * 3) - wind.size * 1.5f
                val y = wind.y + sin(animationTime * 0.5f + wind.y * 0.01f) * 15f

                val radiusX = wind.size * wind.scaleX
                val radiusY = wind.size * wind.scaleY

                // 拉长的椭圆形（保持原实现行为：按最大半径绘制圆形光晕）
                drawGlow(
                    sprite = ParticleSprites.glowSoft,
                    centerX = x, centerY = y,
                    radius = radiusX.coerceAtLeast(radiusY),
                    alpha = wind.alpha
                )
            }
        }
    }
}

/**
 * 雷阵雨效果 - 雨滴 + 双闪闪电序列 + 分叉闪电路径
 * 支持天气强度调整、风驱动倾斜
 */
@Composable
private fun ThunderShowerEffect(
    modifier: Modifier,
    intensity: WeatherIntensity = WeatherIntensity(),
    windSlant: Float = 0f,
    frameRateLimiter: FrameRateLimiter = FrameRateLimiter(),
    animationsEnabled: Boolean = true
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val areaWidth = constraints.maxWidth.toFloat()
        val areaHeight = constraints.maxHeight.toFloat()

        // 根据强度动态调整粒子数量
        val rainCount = (100 * intensity.particleCount).toInt().coerceIn(50, 200)
        val raindrops = remember(areaWidth, areaHeight, intensity.particleCount) { generateRaindrops(rainCount, areaWidth, areaHeight) }
        val bolts = remember { List(3) { generateLightningBolt() } }
        var animationTime by remember { mutableStateOf(0f) }
        var lastFrameTime by remember { mutableStateOf(0L) }
        var lightningTime by remember { mutableStateOf(-1f) }
        var nextLightningTime by remember { mutableStateOf(Random.nextFloat() * 5f + 3f) }
        var boltIndex by remember { mutableStateOf(0) }

        LaunchedEffect(animationsEnabled, frameRateLimiter) {
            if (!animationsEnabled) return@LaunchedEffect
            lastFrameTime = 0L
            while (isActive) {
                withFrameMillis { frameTime ->
                    if (frameRateLimiter.shouldRender(frameTime)) {
                        if (lastFrameTime > 0) {
                            val delta = (frameTime - lastFrameTime) / 1000f
                            animationTime += delta

                            // 闪电触发
                            if (animationTime >= nextLightningTime) {
                                lightningTime = 0f
                                boltIndex = Random.nextInt(bolts.size)
                                nextLightningTime = animationTime + Random.nextFloat() * 6f + 4f
                            }
                            // 闪电序列推进
                            if (lightningTime >= 0f) {
                                lightningTime += delta
                                if (lightningTime > LightningDuration) lightningTime = -1f
                            }
                        }
                        lastFrameTime = frameTime
                    }
                }
            }
        }

        // 风横向速度（px/s）
        val windVx = windSlant * 220f

        Canvas(modifier = Modifier.fillMaxSize()) {
            // 优化: 根据强度调整参数
            val speedMultiplier = intensity.speed
            val alphaMultiplier = intensity.alpha
            val thicknessMultiplier = intensity.thickness

            // 绘制雨滴
            raindrops.forEach { drop ->
                val speedFactor = 180f * speedMultiplier
                val vx = drop.speedX * 35 + windVx
                val vy = drop.speedY * speedFactor
                val x = wrap(drop.x + animationTime * vx, size.width + 100f) - 50f
                val y = wrap(drop.y + animationTime * vy, size.height + drop.length) - drop.length
                val angle = if (windVx != 0f) slantAngle(vx, vy) else 0f

                // 雨滴垂直下落（有风时按倾斜角绘制）
                drawRainStreak(
                    x = x, y = y, length = drop.length,
                    width = drop.thickness * thicknessMultiplier,
                    alpha = drop.alpha * alphaMultiplier,
                    angle = angle, hard = true
                )

                drawCircle(
                    color = Color.White.copy(alpha = drop.alpha * alphaMultiplier * 0.8f),
                    radius = drop.thickness * thicknessMultiplier * 0.8f,
                    center = Offset(x + sin(angle) * drop.length, y + cos(angle) * drop.length)
                )
            }

            // 闪电：双闪包络强度
            val flash = if (lightningTime >= 0f) lightningIntensity(lightningTime) else 0f
            if (flash > 0f) {
                // 分叉闪电路径（归一化坐标，按画布缩放）
                val bolt = bolts[boltIndex]
                val path = Path()
                bolt.forEachIndexed { index, point ->
                    val px = point.first * size.width
                    val py = point.second * size.height
                    if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                // 外层辉光
                drawPath(
                    path = path,
                    color = Color(0xFFEAF2FF).copy(alpha = 0.3f * flash),
                    style = Stroke(width = 7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                // 核心亮线
                drawPath(
                    path = path,
                    color = Color.White.copy(alpha = 0.9f * flash),
                    style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                // 整体屏幕闪烁
                drawRect(
                    color = Color.White.copy(alpha = flash * 0.15f),
                    topLeft = Offset.Zero,
                    size = size
                )
            }
        }
    }
}

/**
 * 闪电双闪包络：主闪（快速上升→衰减）→ 回闪（较弱二次闪光）→ 缓慢熄灭
 */
private fun lightningIntensity(t: Float): Float = when {
    t < 0.05f -> t / 0.05f
    t < 0.16f -> 1f - (t - 0.05f) / 0.11f * 0.85f
    t < 0.22f -> 0.15f + (t - 0.16f) / 0.06f * 0.55f
    t < LightningDuration -> 0.7f * (1f - (t - 0.22f) / (LightningDuration - 0.22f))
    else -> 0f
}

/**
 * 生成分叉闪电路径（归一化坐标 0..1，中点位移算法）
 */
private fun generateLightningBolt(): List<Pair<Float, Float>> {
    val startX = 0.25f + Random.nextFloat() * 0.5f
    val endY = 0.35f + Random.nextFloat() * 0.2f
    var points = mutableListOf(
        startX to -0.02f,
        (startX + (Random.nextFloat() - 0.5f) * 0.15f) to endY
    )
    repeat(4) {
        val displaced = mutableListOf<Pair<Float, Float>>()
        for (i in 0 until points.size - 1) {
            val a = points[i]
            val b = points[i + 1]
            displaced.add(a)
            val midX = (a.first + b.first) * 0.5f + (Random.nextFloat() - 0.5f) * 0.05f
            val midY = (a.second + b.second) * 0.5f
            displaced.add(midX to midY)
        }
        displaced.add(points.last())
        points = displaced
    }
    return points
}

/**
 * 雨夹雪效果 - 雨滴 + 雪花混合
 * 支持天气强度调整、风驱动
 */
@Composable
private fun SleetEffect(
    modifier: Modifier,
    intensity: WeatherIntensity = WeatherIntensity(),
    windSlant: Float = 0f,
    frameRateLimiter: FrameRateLimiter = FrameRateLimiter(),
    animationsEnabled: Boolean = true
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val areaWidth = constraints.maxWidth.toFloat()
        val areaHeight = constraints.maxHeight.toFloat()

        // 根据强度动态调整粒子数量
        val rainCount = (50 * intensity.particleCount).toInt().coerceIn(25, 100)
        val snowCount = (25 * intensity.particleCount).toInt().coerceIn(10, 60)

        val raindrops = remember(areaWidth, areaHeight, intensity.particleCount) { generateRaindrops(rainCount, areaWidth, areaHeight) }
        val snowflakes = remember(areaWidth, areaHeight, intensity.particleCount) { generateSnowflakes(snowCount, areaWidth, areaHeight) }
        var animationTime by remember { mutableStateOf(0f) }
        var lastFrameTime by remember { mutableStateOf(0L) }

        LaunchedEffect(animationsEnabled, frameRateLimiter) {
            if (!animationsEnabled) return@LaunchedEffect
            lastFrameTime = 0L
            while (isActive) {
                withFrameMillis { frameTime ->
                    if (frameRateLimiter.shouldRender(frameTime)) {
                        if (lastFrameTime > 0) {
                            val delta = (frameTime - lastFrameTime) / 1000f
                            animationTime += delta
                        }
                        lastFrameTime = frameTime
                    }
                }
            }
        }

        // 风横向速度（雨部分全量，雪部分弱化）
        val windVx = windSlant * 220f
        val windDrift = windVx * 0.25f

        Canvas(modifier = Modifier.fillMaxSize()) {
            // 优化: 根据强度调整参数
            val speedMultiplier = intensity.speed
            val sizeMultiplier = intensity.size
            val alphaMultiplier = intensity.alpha
            val thicknessMultiplier = intensity.thickness

            // 绘制雨滴
            raindrops.forEach { drop ->
                val speedFactor = 160f * speedMultiplier
                val vx = drop.speedX * 30 + windVx
                val vy = drop.speedY * speedFactor
                val x = wrap(drop.x + animationTime * vx, size.width + 100f) - 50f
                val y = wrap(drop.y + animationTime * vy, size.height + drop.length) - drop.length
                val angle = if (windVx != 0f) slantAngle(vx, vy) else 0f

                // 雨滴垂直下落
                drawRainStreak(
                    x = x, y = y, length = drop.length,
                    width = drop.thickness * thicknessMultiplier * 0.8f,
                    alpha = drop.alpha * alphaMultiplier * 0.8f,
                    angle = angle, hard = false
                )
            }

            // 绘制雪花
            snowflakes.forEach { flake ->
                val wobble = sin(animationTime * flake.wobbleSpeed + flake.wobbleOffset)
                val rawX = flake.x + wobble * flake.wobbleAmplitude + animationTime * windDrift
                val x = if (windDrift != 0f) {
                    wrap(rawX, size.width + flake.size * 4f) - flake.size * 2f
                } else {
                    rawX
                }
                val y = ((flake.y + animationTime * flake.speedY * speedMultiplier * 25) % (size.height + flake.size * 4)) - flake.size * 2

                val adjustedSize = flake.size * sizeMultiplier
                val adjustedAlpha = flake.alpha * alphaMultiplier

                drawGlow(
                    sprite = ParticleSprites.glowSoft,
                    centerX = x, centerY = y,
                    radius = adjustedSize * 2f,
                    alpha = adjustedAlpha * 0.4f
                )

                drawCircle(
                    color = Color.White.copy(alpha = adjustedAlpha * 0.8f),
                    radius = adjustedSize * 0.5f,
                    center = Offset(x, y)
                )
            }
        }
    }
}

// ============ 粒子生成函数 ============

private data class SunBeam(
    val x: Float,
    val y: Float,
    val alpha: Float,
    val size: Float,
    val speedX: Float,
    val speedY: Float,
    val pulseSpeed: Float,
    val pulseOffset: Float,
    val color: Color
)

private data class FloatingMote(
    val x: Float,
    val y: Float,
    val alpha: Float,
    val size: Float,
    val speedX: Float,
    val speedY: Float,
    val pulseSpeed: Float,
    val pulseOffset: Float,
    val color: Color
)

private data class Cloud(
    val x: Float,
    val y: Float,
    val alpha: Float,
    val width: Float,
    val height: Float,
    val speedX: Float
)

private data class Star(
    val x: Float,
    val y: Float,
    val size: Float,
    val twinkleSpeed: Float,
    val twinkleOffset: Float,
    val maxAlpha: Float
)

private data class Raindrop(
    val x: Float,
    val y: Float,
    val alpha: Float,
    val size: Float,
    val speedX: Float,
    val speedY: Float,
    val length: Float,
    val thickness: Float
)

private data class RainMist(
    val x: Float,
    val y: Float,
    val alpha: Float,
    val size: Float,
    val speedX: Float
)

private data class Snowflake(
    val x: Float,
    val y: Float,
    val alpha: Float,
    val size: Float,
    val speedX: Float,
    val speedY: Float,
    val wobbleAmplitude: Float,
    val wobbleSpeed: Float,
    val wobbleOffset: Float
)

private data class WindP(
    val x: Float,
    val y: Float,
    val alpha: Float,
    val size: Float,
    val speedX: Float,
    val speedY: Float,
    val scaleX: Float,
    val scaleY: Float
)

private data class FogLayer(
    val x: Float,
    val y: Float,
    val alpha: Float,
    val size: Float,
    val speedX: Float
)

/**
 * 流星数据（startX/startY 为归一化坐标 0..1，dirX/dirY 为运动方向单位向量）
 */
private data class Meteor(
    val startX: Float,
    val startY: Float,
    val dirX: Float,
    val dirY: Float,
    val speed: Float,
    val duration: Float
)

private fun generateSunBeams(width: Float, height: Float): List<SunBeam> {
    val colors = listOf(
        Color(0xFFFFF9C4),  // 暖黄
        Color(0xFFFFECB3),  // 浅橙
        Color(0xFFFFF8E1),  // 奶白
        Color(0xFFFFD54F),  // 金色
        Color(0xFFFFF176)   // 亮黄
    )
    return List(12) {
        SunBeam(
            x = Random.nextFloat() * width,
            y = Random.nextFloat() * height * 0.8f,
            alpha = Random.nextFloat() * 0.15f + 0.1f,  // 0.1-0.25（更明显）
            size = Random.nextFloat() * 80f + 60f,  // 60-140（更大）
            speedX = Random.nextFloat() * 0.3f - 0.15f,
            speedY = Random.nextFloat() * 0.15f - 0.075f,
            pulseSpeed = Random.nextFloat() * 0.8f + 0.3f,  // 更慢的脉动
            pulseOffset = Random.nextFloat() * PI.toFloat() * 2f,
            color = colors[Random.nextInt(colors.size)]
        )
    }
}

private fun generateFloatingMotes(width: Float, height: Float): List<FloatingMote> {
    val colors = listOf(
        Color(0xFFFFF9C4),  // 暖黄
        Color(0xFFFFECB3),  // 浅橙
        Color(0xFFFFF8E1),  // 奶白
        Color(0xFFFFD54F),  // 金色
        Color(0xFFFFFFE0)   // 浅黄
    )
    return List(30) {
        FloatingMote(
            x = Random.nextFloat() * width,
            y = Random.nextFloat() * height,
            alpha = Random.nextFloat() * 0.2f + 0.1f,  // 0.1-0.3
            size = Random.nextFloat() * 3f + 1f,  // 1-4
            speedX = Random.nextFloat() * 0.6f - 0.3f,
            speedY = Random.nextFloat() * 0.4f - 0.2f,
            pulseSpeed = Random.nextFloat() * 2f + 1f,
            pulseOffset = Random.nextFloat() * PI.toFloat() * 2f,
            color = colors[Random.nextInt(colors.size)]
        )
    }
}

private fun generateStars(width: Float, height: Float): List<Star> {
    return List(25) {
        Star(
            x = Random.nextFloat() * width,
            y = Random.nextFloat() * height * 0.7f,
            size = Random.nextFloat() * 2f + 0.8f,
            twinkleSpeed = Random.nextFloat() * 2.5f + 0.8f,
            twinkleOffset = Random.nextFloat() * PI.toFloat() * 2f,
            maxAlpha = Random.nextFloat() * 0.4f + 0.25f
        )
    }
}

private fun generateRaindrops(count: Int, width: Float, height: Float): List<Raindrop> {
    return List(count) {
        val speed = Random.nextFloat() * 8f + 12f
        Raindrop(
            x = Random.nextFloat() * width,
            y = Random.nextFloat() * height,
            alpha = Random.nextFloat() * 0.3f + 0.2f,  // 0.2-0.5（更明显）
            size = Random.nextFloat() * 1.5f + 0.5f,
            speedX = Random.nextFloat() * 1.8f - 0.4f,
            speedY = speed,
            length = Random.nextFloat() * 22f + 15f,  // 15-37（更长）
            thickness = Random.nextFloat() * 1.2f + 0.6f  // 0.6-1.8（更粗）
        )
    }
}

// 底部水雾
private fun generateRainMist(width: Float): List<RainMist> {
    return List(10) {
        RainMist(
            x = Random.nextFloat() * width,
            y = Random.nextFloat() * 150f + 50f,  // 底部区域
            alpha = Random.nextFloat() * 0.12f + 0.05f,  // 0.05-0.17
            size = Random.nextFloat() * 200f + 100f,  // 100-300
            speedX = Random.nextFloat() * 1f + 0.3f  // 0.3-1.3
        )
    }
}

private fun generateSnowflakes(count: Int, width: Float, height: Float): List<Snowflake> {
    return List(count) {
        Snowflake(
            x = Random.nextFloat() * width,
            y = Random.nextFloat() * height,
            alpha = Random.nextFloat() * 0.35f + 0.25f,
            size = Random.nextFloat() * 3.5f + 1.5f,
            speedX = Random.nextFloat() * 1.2f - 0.6f,
            speedY = Random.nextFloat() * 1.8f + 0.8f,
            wobbleAmplitude = Random.nextFloat() * 25f + 8f,
            wobbleSpeed = Random.nextFloat() * 1.8f + 0.8f,
            wobbleOffset = Random.nextFloat() * PI.toFloat() * 2f
        )
    }
}

// 远景云层（小、淡、慢）
private fun generateCloudsFar(width: Float, height: Float): List<Cloud> {
    return List(4) {
        Cloud(
            x = Random.nextFloat() * width,
            y = Random.nextFloat() * height * 0.2f + height * 0.025f,
            alpha = Random.nextFloat() * 0.12f + 0.06f,  // 0.06-0.18
            width = Random.nextFloat() * 120f + 80f,  // 80-200
            height = Random.nextFloat() * 35f + 20f,  // 20-55
            speedX = Random.nextFloat() * 0.4f + 0.2f  // 0.2-0.6
        )
    }
}

// 中景云层
private fun generateCloudsMid(width: Float, height: Float): List<Cloud> {
    return List(5) {
        Cloud(
            x = Random.nextFloat() * width,
            y = Random.nextFloat() * height * 0.3f + height * 0.05f,
            alpha = Random.nextFloat() * 0.15f + 0.1f,  // 0.1-0.25
            width = Random.nextFloat() * 180f + 120f,  // 120-300
            height = Random.nextFloat() * 50f + 30f,  // 30-80
            speedX = Random.nextFloat() * 0.6f + 0.3f  // 0.3-0.9
        )
    }
}

// 近景云层（大、明显、快）
private fun generateCloudsNear(width: Float, height: Float): List<Cloud> {
    return List(3) {
        Cloud(
            x = Random.nextFloat() * width,
            y = Random.nextFloat() * height * 0.25f + height * 0.1f,
            alpha = Random.nextFloat() * 0.18f + 0.12f,  // 0.12-0.3
            width = Random.nextFloat() * 250f + 180f,  // 180-430
            height = Random.nextFloat() * 70f + 45f,  // 45-115
            speedX = Random.nextFloat() * 0.8f + 0.5f  // 0.5-1.3
        )
    }
}

private fun generateWindParticles(width: Float, height: Float): List<WindP> {
    return List(20) {
        WindP(
            x = Random.nextFloat() * width,
            y = Random.nextFloat() * height,
            alpha = Random.nextFloat() * 0.08f + 0.02f,
            size = Random.nextFloat() * 120f + 40f,
            speedX = Random.nextFloat() * 3.5f + 2.5f,
            speedY = Random.nextFloat() * 0.4f - 0.2f,
            scaleX = Random.nextFloat() * 0.6f + 1.0f,
            scaleY = Random.nextFloat() * 0.15f + 0.15f
        )
    }
}

private fun generateFogLayers(width: Float, height: Float): List<FogLayer> {
    return List(15) {
        FogLayer(
            x = Random.nextFloat() * width,
            y = Random.nextFloat() * height * 0.9f,
            alpha = Random.nextFloat() * 0.18f + 0.1f,  // 0.1-0.28
            size = Random.nextFloat() * 250f + 120f,  // 120-370
            speedX = Random.nextFloat() * 0.6f + 0.2f  // 0.2-0.8
        )
    }
}

/**
 * 生成一颗流星（从顶部区域斜向划过）
 */
private fun generateMeteor(): Meteor {
    val angleDeg = Random.nextFloat() * 20f + 30f  // 与水平夹角 30°-50°
    val toRight = Random.nextBoolean()
    val rad = Math.toRadians(angleDeg.toDouble())
    return Meteor(
        startX = if (toRight) Random.nextFloat() * 0.45f + 0.15f else Random.nextFloat() * 0.45f + 0.4f,
        startY = Random.nextFloat() * 0.2f + 0.02f,
        dirX = (cos(rad) * if (toRight) 1.0 else -1.0).toFloat(),
        dirY = sin(rad).toFloat(),
        speed = Random.nextFloat() * 400f + 700f,   // 700-1100 px/s
        duration = Random.nextFloat() * 0.4f + 0.7f // 0.7-1.1s
    )
}
