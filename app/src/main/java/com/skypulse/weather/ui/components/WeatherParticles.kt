package com.skypulse.weather.ui.components

import androidx.compose.ui.graphics.Color
import kotlin.random.Random

/**
 * 天气粒子效果系统
 * 支持多种天气条件的逼真粒子动画
 */

// ============ 粒子数据模型 ============

/**
 * 粒子基础接口
 */
sealed class WeatherParticle {
    abstract var x: Float
    abstract var y: Float
    abstract var alpha: Float
    abstract val size: Float
    abstract val speedX: Float
    abstract val speedY: Float
}

/**
 * 雨滴粒子 - 带拖尾效果
 */
data class RainParticle(
    override var x: Float,
    override var y: Float,
    override var alpha: Float,
    override val size: Float,
    override val speedX: Float,
    override val speedY: Float,
    val length: Float,         // 雨滴长度
    val thickness: Float,      // 雨滴粗细
    val windOffset: Float      // 风偏移
) : WeatherParticle()

/**
 * 雪花粒子 - 带飘落摇摆
 */
data class SnowParticle(
    override var x: Float,
    override var y: Float,
    override var alpha: Float,
    override val size: Float,
    override val speedX: Float,
    override val speedY: Float,
    val wobbleAmplitude: Float, // 摇摆幅度
    val wobbleSpeed: Float,     // 摇摆速度
    val wobbleOffset: Float,    // 摇摆初始偏移
    val rotation: Float,        // 旋转角度
    val rotationSpeed: Float    // 旋转速度
) : WeatherParticle()

/**
 * 星星粒子 - 闪烁效果
 */
data class StarParticle(
    override var x: Float,
    override var y: Float,
    override var alpha: Float,
    override val size: Float,
    override val speedX: Float = 0f,
    override val speedY: Float = 0f,
    val twinkleSpeed: Float,    // 闪烁速度
    val twinkleOffset: Float,   // 闪烁初始偏移
    val maxAlpha: Float         // 最大亮度
) : WeatherParticle()

/**
 * 光斑粒子 - 漂浮效果（晴天）
 */
data class LightSpotParticle(
    override var x: Float,
    override var y: Float,
    override var alpha: Float,
    override val size: Float,
    override val speedX: Float,
    override val speedY: Float,
    val pulseSpeed: Float,      // 脉动速度
    val pulseOffset: Float,     // 脉动初始偏移
    val color: Color            // 光斑颜色
) : WeatherParticle()

/**
 * 雾气粒子 - 流动效果
 */
data class FogParticle(
    override var x: Float,
    override var y: Float,
    override var alpha: Float,
    override val size: Float,
    override val speedX: Float,
    override val speedY: Float,
    val scaleX: Float,          // X轴缩放
    val scaleY: Float           // Y轴缩放
) : WeatherParticle()

/**
 * 闪电粒子 - 瞬时效果
 */
data class LightningParticle(
    override var x: Float,
    override var y: Float,
    override var alpha: Float,
    override val size: Float,
    override val speedX: Float = 0f,
    override val speedY: Float = 0f,
    val flashDuration: Long,    // 闪烁持续时间
    val startTime: Long         // 开始时间
) : WeatherParticle()

// ============ 粒子生成器 ============

object ParticleGenerator {

    /**
     * 根据天气条件生成粒子列表
     */
    fun generateParticles(
        skycon: String?,
        isDay: Boolean,
        width: Float,
        height: Float,
        particleCount: Int = DEFAULT_PARTICLE_COUNT
    ): List<WeatherParticle> {
        return when {
            skycon == null || skycon.contains("CLEAR") -> {
                if (isDay) generateLightSpots(width, height, particleCount)
                else generateStars(width, height, particleCount)
            }
            skycon.contains("PARTLY_CLOUDY") -> {
                if (isDay) generateLightSpots(width, height, particleCount / 2)
                else generateStars(width, height, particleCount / 2)
            }
            skycon.contains("CLOUDY") -> {
                generateFogParticles(width, height, particleCount / 3)
            }
            skycon.contains("RAIN") || skycon.contains("STORM") -> {
                generateRainDrops(width, height, particleCount)
            }
            skycon.contains("SNOW") -> {
                generateSnowflakes(width, height, particleCount)
            }
            skycon.contains("HAZE") || skycon == "FOG" || skycon == "DUST" || skycon == "SAND" -> {
                generateFogParticles(width, height, particleCount)
            }
            skycon == "WIND" -> {
                generateWindParticles(width, height, particleCount / 2)
            }
            else -> emptyList()
        }
    }

    private fun generateRainDrops(width: Float, height: Float, count: Int): List<RainParticle> {
        return List(count) {
            val speed = Random.nextFloat() * 8f + 12f  // 12-20
            RainParticle(
                x = Random.nextFloat() * width * 1.2f - width * 0.1f,
                y = Random.nextFloat() * height * 1.2f - height * 0.1f,
                alpha = Random.nextFloat() * 0.3f + 0.15f,  // 0.15-0.45
                size = Random.nextFloat() * 1.5f + 0.5f,
                speedX = Random.nextFloat() * 2f - 0.5f,  // -0.5 to 1.5 (微风)
                speedY = speed,
                length = Random.nextFloat() * 20f + 15f,  // 15-35
                thickness = Random.nextFloat() * 1f + 0.5f,  // 0.5-1.5
                windOffset = Random.nextFloat() * 0.3f - 0.15f
            )
        }
    }

    private fun generateSnowflakes(width: Float, height: Float, count: Int): List<SnowParticle> {
        return List(count) {
            SnowParticle(
                x = Random.nextFloat() * width * 1.2f - width * 0.1f,
                y = Random.nextFloat() * height * 1.2f - height * 0.1f,
                alpha = Random.nextFloat() * 0.4f + 0.3f,  // 0.3-0.7
                size = Random.nextFloat() * 4f + 2f,  // 2-6
                speedX = Random.nextFloat() * 1.5f - 0.75f,  // -0.75 to 0.75
                speedY = Random.nextFloat() * 2f + 1f,  // 1-3
                wobbleAmplitude = Random.nextFloat() * 30f + 10f,  // 10-40
                wobbleSpeed = Random.nextFloat() * 2f + 1f,  // 1-3
                wobbleOffset = Random.nextFloat() * Math.PI.toFloat() * 2f,
                rotation = Random.nextFloat() * 360f,
                rotationSpeed = Random.nextFloat() * 2f - 1f  // -1 to 1
            )
        }
    }

    private fun generateStars(width: Float, height: Float, count: Int): List<StarParticle> {
        return List(count) {
            StarParticle(
                x = Random.nextFloat() * width,
                y = Random.nextFloat() * height * 0.7f,  // 主要在上半部分
                alpha = 0f,
                size = Random.nextFloat() * 2f + 1f,  // 1-3
                twinkleSpeed = Random.nextFloat() * 3f + 1f,  // 1-4
                twinkleOffset = Random.nextFloat() * Math.PI.toFloat() * 2f,
                maxAlpha = Random.nextFloat() * 0.5f + 0.3f  // 0.3-0.8
            )
        }
    }

    private fun generateLightSpots(width: Float, height: Float, count: Int): List<LightSpotParticle> {
        val colors = listOf(
            Color(0xFFFFF9C4),  // 暖黄
            Color(0xFFFFECB3),  // 浅橙
            Color(0xFFFFF8E1),  // 奶白
            Color(0xFFFFD54F)   // 金色
        )
        return List(count) {
            LightSpotParticle(
                x = Random.nextFloat() * width,
                y = Random.nextFloat() * height,
                alpha = Random.nextFloat() * 0.15f + 0.05f,  // 0.05-0.2 (很淡)
                size = Random.nextFloat() * 60f + 30f,  // 30-90
                speedX = Random.nextFloat() * 0.5f - 0.25f,
                speedY = Random.nextFloat() * 0.3f - 0.15f,
                pulseSpeed = Random.nextFloat() * 1.5f + 0.5f,
                pulseOffset = Random.nextFloat() * Math.PI.toFloat() * 2f,
                color = colors[Random.nextInt(colors.size)]
            )
        }
    }

    private fun generateFogParticles(width: Float, height: Float, count: Int): List<FogParticle> {
        return List(count) {
            FogParticle(
                x = Random.nextFloat() * width * 1.5f - width * 0.25f,
                y = Random.nextFloat() * height,
                alpha = Random.nextFloat() * 0.15f + 0.05f,  // 0.05-0.2
                size = Random.nextFloat() * 200f + 100f,  // 100-300
                speedX = Random.nextFloat() * 1.5f + 0.5f,  // 0.5-2 (向右飘)
                speedY = Random.nextFloat() * 0.3f - 0.15f,
                scaleX = Random.nextFloat() * 0.5f + 0.8f,  // 0.8-1.3
                scaleY = Random.nextFloat() * 0.3f + 0.4f   // 0.4-0.7 (扁平)
            )
        }
    }

    private fun generateWindParticles(width: Float, height: Float, count: Int): List<FogParticle> {
        return List(count) {
            FogParticle(
                x = Random.nextFloat() * width * 1.5f - width * 0.25f,
                y = Random.nextFloat() * height,
                alpha = Random.nextFloat() * 0.1f + 0.03f,  // 0.03-0.13
                size = Random.nextFloat() * 150f + 50f,
                speedX = Random.nextFloat() * 4f + 3f,  // 3-7 (快速移动)
                speedY = Random.nextFloat() * 0.5f - 0.25f,
                scaleX = Random.nextFloat() * 0.8f + 1.2f,  // 1.2-2 (拉长)
                scaleY = Random.nextFloat() * 0.2f + 0.2f   // 0.2-0.4 (很扁)
            )
        }
    }

    private const val DEFAULT_PARTICLE_COUNT = 60
}
