package com.skypulse.weather.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 天气效果覆盖层
 * 在背景上叠加逼真的粒子动画
 */
@Composable
fun WeatherEffectOverlay(
    skycon: String?,
    isDay: Boolean,
    modifier: Modifier = Modifier
) {
    // 根据天气条件选择效果
    when {
        skycon == null || skycon.contains("CLEAR") -> {
            if (isDay) {
                SunnyDayEffect(modifier)
            } else {
                ClearNightEffect(modifier)
            }
        }
        skycon.contains("PARTLY_CLOUDY") -> {
            if (isDay) {
                PartlyCloudyDayEffect(modifier)
            } else {
                ClearNightEffect(modifier)
            }
        }
        skycon.contains("CLOUDY") -> {
            CloudyEffect(modifier)
        }
        // THUNDER_SHOWER: 雷阵雨 - 雨+闪电
        skycon == "THUNDER_SHOWER" -> {
            ThunderShowerEffect(modifier)
        }
        // STORM_SNOW: 暴雪 - 必须在 STORM_RAIN 之前判断
        skycon == "STORM_SNOW" -> {
            SnowEffect(modifier)
        }
        // SLEET: 雨夹雪 - 雨+雪混合
        skycon == "SLEET" -> {
            SleetEffect(modifier)
        }
        // RAIN 相关: LIGHT_RAIN, MODERATE_RAIN, HEAVY_RAIN, STORM_RAIN
        skycon.contains("RAIN") -> {
            RainEffect(modifier)
        }
        // SNOW 相关: LIGHT_SNOW, MODERATE_SNOW, HEAVY_SNOW
        skycon.contains("SNOW") -> {
            SnowEffect(modifier)
        }
        // 霾/雾/浮尘/沙尘
        skycon.contains("HAZE") || skycon == "FOG" || skycon == "DUST" || skycon == "SAND" -> {
            FogEffect(modifier)
        }
        skycon == "WIND" -> {
            WindEffect(modifier)
        }
    }
}

/**
 * 晴天效果 - 阳光光束 + 光线射线 + 微小光点
 */
@Composable
private fun SunnyDayEffect(modifier: Modifier) {
    val sunBeams = remember { generateSunBeams() }
    val lightRays = remember { generateLightRays() }
    val floatingMotes = remember { generateFloatingMotes() }

    // 粒子位置动画
    var animationTime by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameMillis { frameTime ->
                animationTime = frameTime / 1000f
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        // 1. 光线射线效果（从上方射入）
        lightRays.forEach { ray ->
            val pulse = sin(animationTime * ray.pulseSpeed + ray.pulseOffset)
            val currentAlpha = (ray.alpha * (0.5f + 0.5f * pulse)).coerceIn(0f, 1f)

            // 射线起点（屏幕上方）
            val startX = ray.x + animationTime * ray.speedX * 10
            val startY = -50f
            // 射线终点
            val endX = startX + ray.angleX * ray.length
            val endY = startY + ray.length

            drawLine(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0f),
                        ray.color.copy(alpha = currentAlpha * 0.3f),
                        ray.color.copy(alpha = currentAlpha),
                        ray.color.copy(alpha = currentAlpha * 0.3f),
                        Color.White.copy(alpha = 0f)
                    ),
                    startY = startY,
                    endY = endY
                ),
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = ray.width,
                cap = StrokeCap.Round
            )
        }

        // 2. 阳光光晕效果
        sunBeams.forEach { beam ->
            val pulse = sin(animationTime * beam.pulseSpeed + beam.pulseOffset)
            val currentAlpha = (beam.alpha * (0.6f + 0.4f * pulse)).coerceIn(0f, 1f)
            val currentSize = beam.size * (0.95f + 0.05f * pulse)

            // 缓慢移动
            val x = (beam.x + animationTime * beam.speedX * 15) % (size.width + beam.size * 2) - beam.size
            val y = beam.y + sin(animationTime * 0.3f + beam.pulseOffset) * 15f

            // 外层大光晕
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        beam.color.copy(alpha = currentAlpha * 0.3f),
                        beam.color.copy(alpha = currentAlpha * 0.15f),
                        beam.color.copy(alpha = 0f)
                    ),
                    center = Offset(x, y),
                    radius = currentSize * 2.5f
                ),
                radius = currentSize * 2.5f,
                center = Offset(x, y)
            )

            // 中层光晕
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        beam.color.copy(alpha = currentAlpha * 0.5f),
                        beam.color.copy(alpha = currentAlpha * 0.25f),
                        beam.color.copy(alpha = 0f)
                    ),
                    center = Offset(x, y),
                    radius = currentSize * 1.5f
                ),
                radius = currentSize * 1.5f,
                center = Offset(x, y)
            )

            // 核心光斑
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = currentAlpha * 0.8f),
                        beam.color.copy(alpha = currentAlpha * 0.6f),
                        beam.color.copy(alpha = 0f)
                    ),
                    center = Offset(x, y),
                    radius = currentSize * 0.6f
                ),
                radius = currentSize * 0.6f,
                center = Offset(x, y)
            )
        }

        // 3. 微小光点飘浮
        floatingMotes.forEach { mote ->
            val pulse = sin(animationTime * mote.pulseSpeed + mote.pulseOffset)
            val currentAlpha = (mote.alpha * (0.4f + 0.6f * pulse)).coerceIn(0f, 1f)
            val currentSize = mote.size * (0.8f + 0.2f * pulse)

            // 随机漂浮轨迹
            val x = (mote.x + animationTime * mote.speedX * 8 + sin(animationTime * 0.7f + mote.pulseOffset) * 20f) %
                    (size.width + mote.size * 2) - mote.size
            val y = (mote.y + animationTime * mote.speedY * 5 + cos(animationTime * 0.5f + mote.pulseOffset) * 15f) %
                    (size.height + mote.size * 2) - mote.size

            // 光点光晕
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = currentAlpha),
                        mote.color.copy(alpha = currentAlpha * 0.6f),
                        mote.color.copy(alpha = 0f)
                    ),
                    center = Offset(x, y),
                    radius = currentSize * 2f
                ),
                radius = currentSize * 2f,
                center = Offset(x, y)
            )

            // 光点核心
            drawCircle(
                color = Color.White.copy(alpha = currentAlpha * 0.9f),
                radius = currentSize * 0.3f,
                center = Offset(x, y)
            )
        }
    }
}

/**
 * 多云白天效果 - 阳光+云朵
 */
@Composable
private fun PartlyCloudyDayEffect(modifier: Modifier) {
    val sunBeams = remember { generateSunBeams() }
    val lightRays = remember { generateLightRays() }
    val cloudsMid = remember { generateCloudsMid() }
    val cloudsNear = remember { generateCloudsNear() }

    var animationTime by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameMillis { frameTime ->
                animationTime = frameTime / 1000f
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        // 光线射线（较少）
        lightRays.take(4).forEach { ray ->
            val pulse = sin(animationTime * ray.pulseSpeed + ray.pulseOffset)
            val currentAlpha = (ray.alpha * 0.6f * (0.5f + 0.5f * pulse)).coerceIn(0f, 1f)

            val startX = ray.x + animationTime * ray.speedX * 10
            val startY = -50f
            val endX = startX + ray.angleX * ray.length
            val endY = startY + ray.length

            drawLine(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0f),
                        ray.color.copy(alpha = currentAlpha * 0.2f),
                        ray.color.copy(alpha = currentAlpha),
                        ray.color.copy(alpha = currentAlpha * 0.2f),
                        Color.White.copy(alpha = 0f)
                    ),
                    startY = startY,
                    endY = endY
                ),
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = ray.width * 0.8f,
                cap = StrokeCap.Round
            )
        }

        // 阳光光晕（较少）
        sunBeams.take(6).forEach { beam ->
            val pulse = sin(animationTime * beam.pulseSpeed + beam.pulseOffset)
            val currentAlpha = (beam.alpha * 0.6f * (0.6f + 0.4f * pulse)).coerceIn(0f, 1f)
            val currentSize = beam.size * (0.95f + 0.05f * pulse)

            val x = (beam.x + animationTime * beam.speedX * 15) % (size.width + beam.size * 2) - beam.size
            val y = beam.y + sin(animationTime * 0.3f + beam.pulseOffset) * 15f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        beam.color.copy(alpha = currentAlpha * 0.35f),
                        beam.color.copy(alpha = currentAlpha * 0.18f),
                        beam.color.copy(alpha = 0f)
                    ),
                    center = Offset(x, y),
                    radius = currentSize * 2f
                ),
                radius = currentSize * 2f,
                center = Offset(x, y)
            )
        }

        // 中景云层
        cloudsMid.forEach { cloud ->
            drawCloud(cloud, animationTime, speedFactor = 10f, alphaFactor = 0.8f)
        }

        // 近景云层
        cloudsNear.forEach { cloud ->
            drawCloud(cloud, animationTime, speedFactor = 16f, alphaFactor = 1f)
        }
    }
}

/**
 * 晴夜效果 - 星星闪烁
 */
@Composable
private fun ClearNightEffect(modifier: Modifier) {
    val stars = remember { generateStars() }
    var animationTime by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameMillis { frameTime ->
                animationTime = frameTime / 1000f
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        stars.forEach { star ->
            // 闪烁效果
            val twinkle = sin(animationTime * star.twinkleSpeed + star.twinkleOffset)
            val currentAlpha = (star.maxAlpha * (0.5f + 0.5f * twinkle)).coerceIn(0f, 1f)

            // 光晕
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = currentAlpha * 0.6f),
                        Color.White.copy(alpha = currentAlpha * 0.3f),
                        Color.White.copy(alpha = 0f)
                    ),
                    center = Offset(star.x, star.y),
                    radius = star.size * 3f
                ),
                radius = star.size * 3f,
                center = Offset(star.x, star.y)
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
    }
}

/**
 * 雨天效果 - 前景/背景雨层 + 底部水雾
 */
@Composable
private fun RainEffect(modifier: Modifier) {
    val raindropsBg = remember { generateRaindropsBg() }
    val raindropsFg = remember { generateRaindropsFg() }
    val rainMist = remember { generateRainMist() }
    var animationTime by remember { mutableStateOf(0f) }
    var lastFrameTime by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameMillis { frameTime ->
                if (lastFrameTime > 0) {
                    val delta = (frameTime - lastFrameTime) / 1000f
                    animationTime += delta
                }
                lastFrameTime = frameTime
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        // 1. 背景雨层（小、淡、慢）
        raindropsBg.forEach { drop ->
            val speedFactor = 120f
            val x = ((drop.x + animationTime * drop.speedX * 25) % (size.width + 80f)) - 40f
            val y = ((drop.y + animationTime * drop.speedY * speedFactor) % (size.height + drop.length)) - drop.length

            val endX = x + drop.speedX * drop.length * 0.12f
            val endY = y + drop.length

            drawLine(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = drop.alpha * 0.05f),
                        Color.White.copy(alpha = drop.alpha * 0.3f),
                        Color.White.copy(alpha = drop.alpha * 0.6f)
                    ),
                    startY = y,
                    endY = endY
                ),
                start = Offset(x, y),
                end = Offset(endX, endY),
                strokeWidth = drop.thickness * 0.6f,
                cap = StrokeCap.Round
            )
        }

        // 2. 前景雨层（大、明显、快）
        raindropsFg.forEach { drop ->
            val speedFactor = 200f
            val x = ((drop.x + animationTime * drop.speedX * 40) % (size.width + 120f)) - 60f
            val y = ((drop.y + animationTime * drop.speedY * speedFactor) % (size.height + drop.length)) - drop.length

            val endX = x + drop.speedX * drop.length * 0.18f
            val endY = y + drop.length

            // 雨滴主体
            drawLine(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = drop.alpha * 0.08f),
                        Color.White.copy(alpha = drop.alpha * 0.5f),
                        Color.White.copy(alpha = drop.alpha)
                    ),
                    startY = y,
                    endY = endY
                ),
                start = Offset(x, y),
                end = Offset(endX, endY),
                strokeWidth = drop.thickness,
                cap = StrokeCap.Round
            )

            // 雨滴头部高光
            drawCircle(
                color = Color.White.copy(alpha = drop.alpha * 0.7f),
                radius = drop.thickness * 0.7f,
                center = Offset(endX, endY)
            )
        }

        // 3. 底部水雾效果
        rainMist.forEach { mist ->
            val x = ((mist.x + animationTime * mist.speedX * 15) % (size.width + mist.size * 2)) - mist.size
            val y = size.height - mist.y - mist.size * 0.3f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = mist.alpha * 0.6f),
                        Color.White.copy(alpha = mist.alpha * 0.3f),
                        Color.White.copy(alpha = mist.alpha * 0.1f),
                        Color.White.copy(alpha = 0f)
                    ),
                    center = Offset(x, y),
                    radius = mist.size
                ),
                radius = mist.size,
                center = Offset(x, y)
            )
        }
    }
}

/**
 * 雪天效果 - 雪花飘落
 */
@Composable
private fun SnowEffect(modifier: Modifier) {
    val snowflakes = remember { generateSnowflakes() }
    var animationTime by remember { mutableStateOf(0f) }
    var lastFrameTime by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameMillis { frameTime ->
                if (lastFrameTime > 0) {
                    val delta = (frameTime - lastFrameTime) / 1000f
                    animationTime += delta
                }
                lastFrameTime = frameTime
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        snowflakes.forEach { flake ->
            // 摇摆效果
            val wobble = sin(animationTime * flake.wobbleSpeed + flake.wobbleOffset)
            val x = flake.x + wobble * flake.wobbleAmplitude
            val y = ((flake.y + animationTime * flake.speedY * 30) % (size.height + flake.size * 4)) - flake.size * 2

            // 外层光晕
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = flake.alpha * 0.4f),
                        Color.White.copy(alpha = flake.alpha * 0.2f),
                        Color.White.copy(alpha = 0f)
                    ),
                    center = Offset(x, y),
                    radius = flake.size * 2.5f
                ),
                radius = flake.size * 2.5f,
                center = Offset(x, y)
            )

            // 内层实心
            drawCircle(
                color = Color.White.copy(alpha = flake.alpha),
                radius = flake.size * 0.6f,
                center = Offset(x, y)
            )
        }
    }
}

/**
 * 多云效果 - 远近云层视差漂浮
 */
@Composable
private fun CloudyEffect(modifier: Modifier) {
    val cloudsFar = remember { generateCloudsFar() }
    val cloudsMid = remember { generateCloudsMid() }
    val cloudsNear = remember { generateCloudsNear() }
    var animationTime by remember { mutableStateOf(0f) }
    var lastFrameTime by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameMillis { frameTime ->
                if (lastFrameTime > 0) {
                    val delta = (frameTime - lastFrameTime) / 1000f
                    animationTime += delta
                }
                lastFrameTime = frameTime
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        // 远景云层（更小、更淡、更慢）
        cloudsFar.forEach { cloud ->
            drawCloud(cloud, animationTime, speedFactor = 5f, alphaFactor = 0.4f)
        }

        // 中景云层
        cloudsMid.forEach { cloud ->
            drawCloud(cloud, animationTime, speedFactor = 10f, alphaFactor = 0.7f)
        }

        // 近景云层（更大、更明显、更快）
        cloudsNear.forEach { cloud ->
            drawCloud(cloud, animationTime, speedFactor = 18f, alphaFactor = 1f)
        }
    }
}

/**
 * 绘制单朵云（由多个柔和圆形组成）
 */
private fun DrawScope.drawCloud(cloud: Cloud, animationTime: Float, speedFactor: Float, alphaFactor: Float) {
    val x = ((cloud.x + animationTime * cloud.speedX * speedFactor) % (size.width + cloud.width * 2)) - cloud.width
    val y = cloud.y + sin(animationTime * 0.12f + cloud.y * 0.003f) * 6f

    val centerX = x + cloud.width / 2
    val centerY = y + cloud.height / 2
    val alpha = cloud.alpha * alphaFactor

    // 云朵主体（大椭圆）
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = alpha * 0.7f),
                Color.White.copy(alpha = alpha * 0.4f),
                Color.White.copy(alpha = alpha * 0.15f),
                Color.White.copy(alpha = 0f)
            ),
            center = Offset(centerX, centerY),
            radius = cloud.width * 0.55f
        ),
        radius = cloud.width * 0.55f,
        center = Offset(centerX, centerY)
    )

    // 左侧凸起
    val leftX = x + cloud.width * 0.2f
    val leftY = centerY - cloud.height * 0.1f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = alpha * 0.65f),
                Color.White.copy(alpha = alpha * 0.35f),
                Color.White.copy(alpha = 0f)
            ),
            center = Offset(leftX, leftY),
            radius = cloud.width * 0.38f
        ),
        radius = cloud.width * 0.38f,
        center = Offset(leftX, leftY)
    )

    // 右侧凸起
    val rightX = x + cloud.width * 0.8f
    val rightY = centerY + cloud.height * 0.05f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = alpha * 0.6f),
                Color.White.copy(alpha = alpha * 0.3f),
                Color.White.copy(alpha = 0f)
            ),
            center = Offset(rightX, rightY),
            radius = cloud.width * 0.32f
        ),
        radius = cloud.width * 0.32f,
        center = Offset(rightX, rightY)
    )

    // 顶部凸起
    val topX = centerX + cloud.width * 0.15f
    val topY = centerY - cloud.height * 0.35f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = alpha * 0.55f),
                Color.White.copy(alpha = alpha * 0.25f),
                Color.White.copy(alpha = 0f)
            ),
            center = Offset(topX, topY),
            radius = cloud.width * 0.28f
        ),
        radius = cloud.width * 0.28f,
        center = Offset(topX, topY)
    )

    // 底部阴影
    val bottomX = centerX
    val bottomY = centerY + cloud.height * 0.25f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = alpha * 0.3f),
                Color.White.copy(alpha = alpha * 0.15f),
                Color.White.copy(alpha = 0f)
            ),
            center = Offset(bottomX, bottomY),
            radius = cloud.width * 0.45f
        ),
        radius = cloud.width * 0.45f,
        center = Offset(bottomX, bottomY)
    )
}

/**
 * 雾天效果 - 浓雾弥漫
 */
@Composable
private fun FogEffect(modifier: Modifier) {
    val fogLayers = remember { generateFogLayers() }
    var animationTime by remember { mutableStateOf(0f) }
    var lastFrameTime by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameMillis { frameTime ->
                if (lastFrameTime > 0) {
                    val delta = (frameTime - lastFrameTime) / 1000f
                    animationTime += delta
                }
                lastFrameTime = frameTime
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        fogLayers.forEach { fog ->
            // 缓慢漂浮
            val x = ((fog.x + animationTime * fog.speedX * 8) % (size.width + fog.size * 2)) - fog.size
            val y = fog.y + sin(animationTime * 0.2f + fog.y * 0.008f) * 12f

            // 多层雾气叠加
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = fog.alpha),
                        Color.White.copy(alpha = fog.alpha * 0.7f),
                        Color.White.copy(alpha = fog.alpha * 0.3f),
                        Color.White.copy(alpha = 0f)
                    ),
                    center = Offset(x, y),
                    radius = fog.size
                ),
                radius = fog.size,
                center = Offset(x, y)
            )
        }
    }
}

/**
 * 大风效果
 */
@Composable
private fun WindEffect(modifier: Modifier) {
    val windParticles = remember { generateWindParticles() }
    var animationTime by remember { mutableStateOf(0f) }
    var lastFrameTime by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameMillis { frameTime ->
                if (lastFrameTime > 0) {
                    val delta = (frameTime - lastFrameTime) / 1000f
                    animationTime += delta
                }
                lastFrameTime = frameTime
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        windParticles.forEach { wind ->
            // 快速水平移动
            val x = ((wind.x + animationTime * wind.speedX * 50) % (size.width + wind.size * 3)) - wind.size * 1.5f
            val y = wind.y + sin(animationTime * 0.5f + wind.y * 0.01f) * 15f

            val radiusX = wind.size * wind.scaleX
            val radiusY = wind.size * wind.scaleY

            // 拉长的椭圆形
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = wind.alpha),
                        Color.White.copy(alpha = wind.alpha * 0.5f),
                        Color.White.copy(alpha = 0f)
                    ),
                    center = Offset(x, y),
                    radius = radiusX.coerceAtLeast(radiusY)
                ),
                radius = radiusX.coerceAtLeast(radiusY),
                center = Offset(x, y)
            )
        }
    }
}

/**
 * 雷阵雨效果 - 雨滴 + 偶尔闪电
 */
@Composable
private fun ThunderShowerEffect(modifier: Modifier) {
    val raindrops = remember { generateRaindrops() }
    var animationTime by remember { mutableStateOf(0f) }
    var lastFrameTime by remember { mutableStateOf(0L) }
    var lightningFlash by remember { mutableStateOf(0f) }
    var nextLightningTime by remember { mutableStateOf(Random.nextFloat() * 5f + 3f) }

    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameMillis { frameTime ->
                if (lastFrameTime > 0) {
                    val delta = (frameTime - lastFrameTime) / 1000f
                    animationTime += delta

                    // 闪电效果
                    if (animationTime >= nextLightningTime) {
                        lightningFlash = 1f
                        nextLightningTime = animationTime + Random.nextFloat() * 6f + 4f
                    }
                    // 闪电衰减
                    if (lightningFlash > 0f) {
                        lightningFlash = (lightningFlash - delta * 4f).coerceAtLeast(0f)
                    }
                }
                lastFrameTime = frameTime
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        // 绘制雨滴
        raindrops.forEach { drop ->
            val speedFactor = 180f
            val x = ((drop.x + animationTime * drop.speedX * 35) % (size.width + 100f)) - 50f
            val y = ((drop.y + animationTime * drop.speedY * speedFactor) % (size.height + drop.length)) - drop.length

            val endX = x + drop.speedX * drop.length * 0.15f
            val endY = y + drop.length

            drawLine(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = drop.alpha * 0.1f),
                        Color.White.copy(alpha = drop.alpha * 0.6f),
                        Color.White.copy(alpha = drop.alpha)
                    ),
                    startY = y,
                    endY = endY
                ),
                start = Offset(x, y),
                end = Offset(endX, endY),
                strokeWidth = drop.thickness,
                cap = StrokeCap.Round
            )

            drawCircle(
                color = Color.White.copy(alpha = drop.alpha * 0.8f),
                radius = drop.thickness * 0.8f,
                center = Offset(endX, endY)
            )
        }

        // 绘制闪电闪光效果（整体屏幕闪烁）
        if (lightningFlash > 0f) {
            drawRect(
                color = Color.White.copy(alpha = lightningFlash * 0.15f),
                topLeft = Offset.Zero,
                size = size
            )
        }
    }
}

/**
 * 雨夹雪效果 - 雨滴 + 雪花混合
 */
@Composable
private fun SleetEffect(modifier: Modifier) {
    val raindrops = remember { generateRaindrops() }
    val snowflakes = remember { generateSnowflakes() }
    var animationTime by remember { mutableStateOf(0f) }
    var lastFrameTime by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameMillis { frameTime ->
                if (lastFrameTime > 0) {
                    val delta = (frameTime - lastFrameTime) / 1000f
                    animationTime += delta
                }
                lastFrameTime = frameTime
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        // 绘制雨滴（数量减半）
        raindrops.take(50).forEach { drop ->
            val speedFactor = 160f
            val x = ((drop.x + animationTime * drop.speedX * 30) % (size.width + 100f)) - 50f
            val y = ((drop.y + animationTime * drop.speedY * speedFactor) % (size.height + drop.length)) - drop.length

            val endX = x + drop.speedX * drop.length * 0.15f
            val endY = y + drop.length

            drawLine(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = drop.alpha * 0.1f),
                        Color.White.copy(alpha = drop.alpha * 0.5f),
                        Color.White.copy(alpha = drop.alpha * 0.8f)
                    ),
                    startY = y,
                    endY = endY
                ),
                start = Offset(x, y),
                end = Offset(endX, endY),
                strokeWidth = drop.thickness * 0.8f,
                cap = StrokeCap.Round
            )
        }

        // 绘制雪花（数量减半）
        snowflakes.take(25).forEach { flake ->
            val wobble = sin(animationTime * flake.wobbleSpeed + flake.wobbleOffset)
            val x = flake.x + wobble * flake.wobbleAmplitude
            val y = ((flake.y + animationTime * flake.speedY * 25) % (size.height + flake.size * 4)) - flake.size * 2

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = flake.alpha * 0.4f),
                        Color.White.copy(alpha = flake.alpha * 0.2f),
                        Color.White.copy(alpha = 0f)
                    ),
                    center = Offset(x, y),
                    radius = flake.size * 2f
                ),
                radius = flake.size * 2f,
                center = Offset(x, y)
            )

            drawCircle(
                color = Color.White.copy(alpha = flake.alpha * 0.8f),
                radius = flake.size * 0.5f,
                center = Offset(x, y)
            )
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

private data class LightRay(
    val x: Float,
    val alpha: Float,
    val length: Float,
    val width: Float,
    val angleX: Float,
    val speedX: Float,
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

private fun generateSunBeams(): List<SunBeam> {
    val colors = listOf(
        Color(0xFFFFF9C4),  // 暖黄
        Color(0xFFFFECB3),  // 浅橙
        Color(0xFFFFF8E1),  // 奶白
        Color(0xFFFFD54F),  // 金色
        Color(0xFFFFF176)   // 亮黄
    )
    return List(12) {
        SunBeam(
            x = Random.nextFloat() * 1200f,
            y = Random.nextFloat() * 1600f,
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

private fun generateLightRays(): List<LightRay> {
    val colors = listOf(
        Color(0xFFFFF9C4),  // 暖黄
        Color(0xFFFFECB3),  // 浅橙
        Color(0xFFFFF8E1)   // 奶白
    )
    return List(8) {
        LightRay(
            x = Random.nextFloat() * 1200f,
            alpha = Random.nextFloat() * 0.08f + 0.04f,  // 0.04-0.12
            length = Random.nextFloat() * 600f + 400f,  // 400-1000
            width = Random.nextFloat() * 30f + 15f,  // 15-45
            angleX = Random.nextFloat() * 0.3f - 0.15f,  // 轻微倾斜
            speedX = Random.nextFloat() * 0.2f - 0.1f,
            pulseSpeed = Random.nextFloat() * 0.5f + 0.2f,
            pulseOffset = Random.nextFloat() * PI.toFloat() * 2f,
            color = colors[Random.nextInt(colors.size)]
        )
    }
}

private fun generateFloatingMotes(): List<FloatingMote> {
    val colors = listOf(
        Color(0xFFFFF9C4),  // 暖黄
        Color(0xFFFFECB3),  // 浅橙
        Color(0xFFFFF8E1),  // 奶白
        Color(0xFFFFD54F),  // 金色
        Color(0xFFFFFFE0)   // 浅黄
    )
    return List(30) {
        FloatingMote(
            x = Random.nextFloat() * 1200f,
            y = Random.nextFloat() * 2000f,
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

private fun generateStars(): List<Star> {
    return List(25) {
        Star(
            x = Random.nextFloat() * 1200f,
            y = Random.nextFloat() * 1400f,
            size = Random.nextFloat() * 2f + 0.8f,
            twinkleSpeed = Random.nextFloat() * 2.5f + 0.8f,
            twinkleOffset = Random.nextFloat() * PI.toFloat() * 2f,
            maxAlpha = Random.nextFloat() * 0.4f + 0.25f
        )
    }
}

private fun generateRaindrops(): List<Raindrop> {
    return List(100) {
        val speed = Random.nextFloat() * 8f + 12f
        Raindrop(
            x = Random.nextFloat() * 1200f,
            y = Random.nextFloat() * 2000f,
            alpha = Random.nextFloat() * 0.3f + 0.2f,  // 0.2-0.5（更明显）
            size = Random.nextFloat() * 1.5f + 0.5f,
            speedX = Random.nextFloat() * 1.8f - 0.4f,
            speedY = speed,
            length = Random.nextFloat() * 22f + 15f,  // 15-37（更长）
            thickness = Random.nextFloat() * 1.2f + 0.6f  // 0.6-1.8（更粗）
        )
    }
}

// 背景雨层（小、淡、慢）
private fun generateRaindropsBg(): List<Raindrop> {
    return List(60) {
        val speed = Random.nextFloat() * 6f + 8f
        Raindrop(
            x = Random.nextFloat() * 1200f,
            y = Random.nextFloat() * 2000f,
            alpha = Random.nextFloat() * 0.2f + 0.1f,  // 0.1-0.3
            size = Random.nextFloat() * 1f + 0.3f,
            speedX = Random.nextFloat() * 1.2f - 0.3f,
            speedY = speed,
            length = Random.nextFloat() * 15f + 10f,  // 10-25
            thickness = Random.nextFloat() * 0.8f + 0.4f  // 0.4-1.2
        )
    }
}

// 前景雨层（大、明显、快）
private fun generateRaindropsFg(): List<Raindrop> {
    return List(40) {
        val speed = Random.nextFloat() * 10f + 15f
        Raindrop(
            x = Random.nextFloat() * 1200f,
            y = Random.nextFloat() * 2000f,
            alpha = Random.nextFloat() * 0.35f + 0.25f,  // 0.25-0.6
            size = Random.nextFloat() * 2f + 0.8f,
            speedX = Random.nextFloat() * 2f - 0.5f,
            speedY = speed,
            length = Random.nextFloat() * 28f + 18f,  // 18-46
            thickness = Random.nextFloat() * 1.5f + 0.8f  // 0.8-2.3
        )
    }
}

// 底部水雾
private fun generateRainMist(): List<RainMist> {
    return List(10) {
        RainMist(
            x = Random.nextFloat() * 1200f,
            y = Random.nextFloat() * 150f + 50f,  // 底部区域
            alpha = Random.nextFloat() * 0.12f + 0.05f,  // 0.05-0.17
            size = Random.nextFloat() * 200f + 100f,  // 100-300
            speedX = Random.nextFloat() * 1f + 0.3f  // 0.3-1.3
        )
    }
}

private fun generateSnowflakes(): List<Snowflake> {
    return List(50) {
        Snowflake(
            x = Random.nextFloat() * 1200f,
            y = Random.nextFloat() * 2000f,
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
private fun generateCloudsFar(): List<Cloud> {
    return List(4) {
        Cloud(
            x = Random.nextFloat() * 1200f,
            y = Random.nextFloat() * 400f + 50f,
            alpha = Random.nextFloat() * 0.12f + 0.06f,  // 0.06-0.18
            width = Random.nextFloat() * 120f + 80f,  // 80-200
            height = Random.nextFloat() * 35f + 20f,  // 20-55
            speedX = Random.nextFloat() * 0.4f + 0.2f  // 0.2-0.6
        )
    }
}

// 中景云层
private fun generateCloudsMid(): List<Cloud> {
    return List(5) {
        Cloud(
            x = Random.nextFloat() * 1200f,
            y = Random.nextFloat() * 600f + 100f,
            alpha = Random.nextFloat() * 0.15f + 0.1f,  // 0.1-0.25
            width = Random.nextFloat() * 180f + 120f,  // 120-300
            height = Random.nextFloat() * 50f + 30f,  // 30-80
            speedX = Random.nextFloat() * 0.6f + 0.3f  // 0.3-0.9
        )
    }
}

// 近景云层（大、明显、快）
private fun generateCloudsNear(): List<Cloud> {
    return List(3) {
        Cloud(
            x = Random.nextFloat() * 1200f,
            y = Random.nextFloat() * 500f + 200f,
            alpha = Random.nextFloat() * 0.18f + 0.12f,  // 0.12-0.3
            width = Random.nextFloat() * 250f + 180f,  // 180-430
            height = Random.nextFloat() * 70f + 45f,  // 45-115
            speedX = Random.nextFloat() * 0.8f + 0.5f  // 0.5-1.3
        )
    }
}

private fun generateWindParticles(): List<WindP> {
    return List(20) {
        WindP(
            x = Random.nextFloat() * 1200f,
            y = Random.nextFloat() * 2000f,
            alpha = Random.nextFloat() * 0.08f + 0.02f,
            size = Random.nextFloat() * 120f + 40f,
            speedX = Random.nextFloat() * 3.5f + 2.5f,
            speedY = Random.nextFloat() * 0.4f - 0.2f,
            scaleX = Random.nextFloat() * 0.6f + 1.0f,
            scaleY = Random.nextFloat() * 0.15f + 0.15f
        )
    }
}

private fun generateFogLayers(): List<FogLayer> {
    return List(15) {
        FogLayer(
            x = Random.nextFloat() * 1200f,
            y = Random.nextFloat() * 1800f,
            alpha = Random.nextFloat() * 0.18f + 0.1f,  // 0.1-0.28
            size = Random.nextFloat() * 250f + 120f,  // 120-370
            speedX = Random.nextFloat() * 0.6f + 0.2f  // 0.2-0.8
        )
    }
}
