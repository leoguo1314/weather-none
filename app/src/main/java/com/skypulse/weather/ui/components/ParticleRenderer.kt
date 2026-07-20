package com.skypulse.weather.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.sin

/**
 * 粒子渲染器 - 使用Compose Canvas绘制逼真的天气粒子
 */
object ParticleRenderer {

    /**
     * 渲染所有粒子
     */
    fun DrawScope.renderParticles(particles: List<WeatherParticle>) {
        particles.forEach { particle ->
            when (particle) {
                is RainParticle -> renderRainDrop(particle)
                is SnowParticle -> renderSnowflake(particle)
                is StarParticle -> renderStar(particle)
                is LightSpotParticle -> renderLightSpot(particle)
                is FogParticle -> renderFog(particle)
                is LightningParticle -> renderLightning(particle)
            }
        }
    }

    /**
     * 渲染雨滴 - 带拖尾效果
     */
    private fun DrawScope.renderRainDrop(particle: RainParticle) {
        val startX = particle.x
        val startY = particle.y
        val endX = startX + particle.speedX * particle.length * 0.15f
        val endY = startY + particle.length

        // 主雨滴线
        drawLine(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0f),
                    Color.White.copy(alpha = particle.alpha * 0.5f),
                    Color.White.copy(alpha = particle.alpha)
                ),
                startY = startY,
                endY = endY
            ),
            start = Offset(startX, startY),
            end = Offset(endX, endY),
            strokeWidth = particle.thickness,
            cap = StrokeCap.Round
        )

        // 雨滴头部高光
        drawCircle(
            color = Color.White.copy(alpha = particle.alpha * 0.8f),
            radius = particle.thickness * 0.8f,
            center = Offset(endX, endY)
        )
    }

    /**
     * 渲染雪花 - 带旋转和摇摆
     */
    private fun DrawScope.renderSnowflake(particle: SnowParticle) {
        // 计算摇摆偏移
        val wobbleX = sin(particle.wobbleOffset.toDouble()).toFloat() * particle.wobbleAmplitude * 0.3f

        val centerX = particle.x + wobbleX
        val centerY = particle.y
        val radius = particle.size

        // 雪花主体 - 多层透明度营造立体感
        // 外层光晕
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = particle.alpha * 0.6f),
                    Color.White.copy(alpha = particle.alpha * 0.3f),
                    Color.White.copy(alpha = 0f)
                ),
                center = Offset(centerX, centerY),
                radius = radius * 2f
            ),
            radius = radius * 2f,
            center = Offset(centerX, centerY)
        )

        // 内层实心
        drawCircle(
            color = Color.White.copy(alpha = particle.alpha),
            radius = radius * 0.7f,
            center = Offset(centerX, centerY)
        )

        // 雪花晶体结构（6瓣）
        rotate(
            degrees = particle.rotation,
            pivot = Offset(centerX, centerY)
        ) {
            val armLength = radius * 1.2f
            for (i in 0 until 6) {
                val angle = Math.toRadians((i * 60).toDouble())
                val armEndX = centerX + cos(angle).toFloat() * armLength
                val armEndY = centerY + sin(angle).toFloat() * armLength

                drawLine(
                    color = Color.White.copy(alpha = particle.alpha * 0.7f),
                    start = Offset(centerX, centerY),
                    end = Offset(armEndX, armEndY),
                    strokeWidth = 0.5f,
                    cap = StrokeCap.Round
                )

                // 分支
                if (radius > 3f) {
                    val branchLength = armLength * 0.4f
                    val midX = centerX + cos(angle).toFloat() * armLength * 0.6f
                    val midY = centerY + sin(angle).toFloat() * armLength * 0.6f

                    for (j in listOf(-30, 30)) {
                        val branchAngle = angle + Math.toRadians(j.toDouble())
                        val branchEndX = midX + cos(branchAngle).toFloat() * branchLength
                        val branchEndY = midY + sin(branchAngle).toFloat() * branchLength

                        drawLine(
                            color = Color.White.copy(alpha = particle.alpha * 0.5f),
                            start = Offset(midX, midY),
                            end = Offset(branchEndX, branchEndY),
                            strokeWidth = 0.3f,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
        }
    }

    /**
     * 渲染星星 - 闪烁效果
     */
    private fun DrawScope.renderStar(particle: StarParticle) {
        val centerX = particle.x
        val centerY = particle.y
        val radius = particle.size

        // 星星光晕
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = particle.alpha * 0.8f),
                    Color.White.copy(alpha = particle.alpha * 0.4f),
                    Color.White.copy(alpha = 0f)
                ),
                center = Offset(centerX, centerY),
                radius = radius * 3f
            ),
            radius = radius * 3f,
            center = Offset(centerX, centerY)
        )

        // 星星核心
        drawCircle(
            color = Color.White.copy(alpha = particle.alpha),
            radius = radius * 0.5f,
            center = Offset(centerX, centerY)
        )

        // 十字光芒
        val rayLength = radius * 2f
        val rayAlpha = particle.alpha * 0.6f

        // 水平光芒
        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0f),
                    Color.White.copy(alpha = rayAlpha),
                    Color.White.copy(alpha = rayAlpha),
                    Color.White.copy(alpha = 0f)
                ),
                startX = centerX - rayLength,
                endX = centerX + rayLength
            ),
            start = Offset(centerX - rayLength, centerY),
            end = Offset(centerX + rayLength, centerY),
            strokeWidth = 0.8f
        )

        // 垂直光芒
        drawLine(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0f),
                    Color.White.copy(alpha = rayAlpha),
                    Color.White.copy(alpha = rayAlpha),
                    Color.White.copy(alpha = 0f)
                ),
                startY = centerY - rayLength,
                endY = centerY + rayLength
            ),
            start = Offset(centerX, centerY - rayLength),
            end = Offset(centerX, centerY + rayLength),
            strokeWidth = 0.8f
        )
    }

    /**
     * 渲染光斑 - 漂浮效果
     */
    private fun DrawScope.renderLightSpot(particle: LightSpotParticle) {
        val centerX = particle.x
        val centerY = particle.y
        val radius = particle.size

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    particle.color.copy(alpha = particle.alpha),
                    particle.color.copy(alpha = particle.alpha * 0.5f),
                    particle.color.copy(alpha = 0f)
                ),
                center = Offset(centerX, centerY),
                radius = radius
            ),
            radius = radius,
            center = Offset(centerX, centerY)
        )
    }

    /**
     * 渲染雾气 - 流动效果
     */
    private fun DrawScope.renderFog(particle: FogParticle) {
        val centerX = particle.x
        val centerY = particle.y
        val radiusX = particle.size * particle.scaleX
        val radiusY = particle.size * particle.scaleY

        // 使用椭圆形渐变模拟雾气
        val path = Path().apply {
            addOval(
                androidx.compose.ui.geometry.Rect(
                    centerX - radiusX,
                    centerY - radiusY,
                    centerX + radiusX,
                    centerY + radiusY
                )
            )
        }

        drawPath(
            path = path,
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = particle.alpha),
                    Color.White.copy(alpha = particle.alpha * 0.6f),
                    Color.White.copy(alpha = particle.alpha * 0.2f),
                    Color.White.copy(alpha = 0f)
                ),
                center = Offset(centerX, centerY),
                radius = radiusX.coerceAtLeast(radiusY)
            )
        )
    }

    /**
     * 渲染闪电 - 瞬时效果
     */
    private fun DrawScope.renderLightning(particle: LightningParticle) {
        if (particle.alpha <= 0.01f) return

        val startX = particle.x
        val startY = 0f
        val endY = size.height

        // 闪电主干
        val segments = 8
        val segmentHeight = (endY - startY) / segments
        var currentX = startX
        val path = Path().apply {
            moveTo(currentX, startY)
            for (i in 1..segments) {
                val nextX = currentX + (Math.random() * 80 - 40).toFloat()
                val nextY = startY + segmentHeight * i
                lineTo(nextX, nextY)
                currentX = nextX
            }
        }

        // 光晕效果
        drawPath(
            path = path,
            color = Color.White.copy(alpha = particle.alpha * 0.3f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 12f,
                cap = StrokeCap.Round
            )
        )

        // 主干
        drawPath(
            path = path,
            color = Color.White.copy(alpha = particle.alpha * 0.8f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 3f,
                cap = StrokeCap.Round
            )
        )

        // 核心高亮
        drawPath(
            path = path,
            color = Color.White.copy(alpha = particle.alpha),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 1f,
                cap = StrokeCap.Round
            )
        )
    }
}
