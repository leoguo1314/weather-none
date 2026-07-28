package com.skypulse.weather.ui.components

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 粒子精灵（Sprite）缓存
 *
 * 背景：原实现每帧为每个粒子临时创建 Brush/Shader 对象
 * （暴雨天气 4 层约 240 个雨滴 + 云/雾/光晕，每帧数百次堆分配和 native Shader 创建），
 * 造成持续的 GC 压力与额外耗电。
 *
 * 方案：将各种径向/线性渐变按「归一化渐变剖面」离线烘焙成小尺寸位图
 * （柔和渐变 128px 已足够，放大绘制无可见失真），每帧通过 drawImage
 * 缩放 + alpha 调制绘制：
 *   原实现：gradient(color * pulseAlpha)  →  每帧新建 Shader
 *   现实现：sprite(归一化剖面) + drawImage(alpha = pulseAlpha)  →  零分配
 * 二者渲染结果在数学上等价，视觉效果保持一致。
 *
 * 所有访问均发生在主线程（组合/绘制阶段），普通 HashMap 即可。
 */
object ParticleSprites {

    private const val GlowSize = 128
    private const val StreakWidth = 32
    private const val StreakHeight = 256

    private val whiteCoreCache = HashMap<String, ImageBitmap>()
    private val tintCache = HashMap<Color, ColorFilter>()

    // ============ 预置白色光晕（归一化剖面：中心 alpha=1） ============

    /** 通用柔和光晕：(1, 0.5, 0)，用于雪/星/光束外层/云局部/风等 */
    val glowSoft: ImageBitmap by lazy {
        bakeRadial(
            0f to Color.White,
            0.5f to Color.White.copy(alpha = 0.5f),
            1f to Color.White.copy(alpha = 0f)
        )
    }

    /** 雨底水雾：(0.6, 0.3, 0.1, 0) 归一化 */
    val glowMist: ImageBitmap by lazy {
        bakeRadial(
            0f to Color.White,
            1f / 3f to Color.White.copy(alpha = 0.5f),
            2f / 3f to Color.White.copy(alpha = 1f / 6f),
            1f to Color.White.copy(alpha = 0f)
        )
    }

    /** 雾气团：(1, 0.7, 0.3, 0) 归一化 */
    val glowFog: ImageBitmap by lazy {
        bakeRadial(
            0f to Color.White,
            1f / 3f to Color.White.copy(alpha = 0.7f),
            2f / 3f to Color.White.copy(alpha = 0.3f),
            1f to Color.White.copy(alpha = 0f)
        )
    }

    /** 云朵主体：(0.7, 0.4, 0.15, 0) 归一化 */
    val glowCloudMain: ImageBitmap by lazy {
        bakeRadial(
            0f to Color.White,
            1f / 3f to Color.White.copy(alpha = 0.4f / 0.7f),
            2f / 3f to Color.White.copy(alpha = 0.15f / 0.7f),
            1f to Color.White.copy(alpha = 0f)
        )
    }

    /** 云朵左侧凸起：(0.65, 0.35, 0) 归一化 */
    val glowCloudLeft: ImageBitmap by lazy {
        bakeRadial(
            0f to Color.White,
            0.5f to Color.White.copy(alpha = 0.35f / 0.65f),
            1f to Color.White.copy(alpha = 0f)
        )
    }

    /** 云朵顶部凸起：(0.55, 0.25, 0) 归一化 */
    val glowCloudTop: ImageBitmap by lazy {
        bakeRadial(
            0f to Color.White,
            0.5f to Color.White.copy(alpha = 0.25f / 0.55f),
            1f to Color.White.copy(alpha = 0f)
        )
    }

    // ============ 雨丝精灵（垂直线性渐变，顶部透明 → 底部最亮） ============

    /** 柔和雨丝：(0.1, 0.5, 1.0)，用于远/中景雨层、雨夹雪 */
    val rainStreakSoft: ImageBitmap by lazy { bakeStreak(0.1f, 0.5f, 1.0f) }

    /** 高对比雨丝：(0.1, 0.6, 1.0)，用于近景雨层、雷阵雨 */
    val rainStreakHard: ImageBitmap by lazy { bakeStreak(0.1f, 0.6f, 1.0f) }

    // ============ 高级运动模糊雨丝（两端柔化 + 高光内芯） ============

    /**
     * 电影感雨丝（近景/中景）：高速下落被运动模糊拉长的细长条，
     * 顶部（尾迹）与底部（落点）皆柔化淡出，中部一条极细的高光内芯模拟「湿亮反光」。
     * 取代原水滴形 + 白点头部，消除虚线/彗尾的廉价感。
     */
    val rainStreakPro: ImageBitmap by lazy { bakeStreakPro(tail = 0f, body = 0.82f, head = 0.62f, core = 0.55f) }

    /** 电影感雨丝（远景）：更弥散、更暗，配合冷蓝大气透视 */
    val rainStreakProFar: ImageBitmap by lazy { bakeStreakPro(tail = 0f, body = 0.5f, head = 0.42f, core = 0.28f) }

    // ============ 冷色电影感配色（大气透视分级） ============

    /** 远景雨冷蓝（大气散射：越远越蓝越虚） */
    val RainTintFar = Color(0xFF9DC3E6)

    /** 近景雨近白冷（贴近镜头：更通透、几乎不着色） */
    val RainTintNear = Color(0xFFEAF4FF)

    /** 体积雾冷色（层间纵深雾） */
    val RainHazeCool = Color(0xFFAECBE6)

    // ============ 参数化精灵 ============

    /**
     * 白心彩色边缘光晕（用于阳光核心光斑、浮动光点）
     * @param edgeColor 边缘颜色
     * @param midAlpha 中间位置（0.5 半径处）颜色相对 alpha
     */
    fun whiteCoreGlow(edgeColor: Color, midAlpha: Float): ImageBitmap {
        val key = "${edgeColor.toArgb()}|$midAlpha"
        return whiteCoreCache.getOrPut(key) {
            bakeRadial(
                0f to Color.White,
                0.5f to edgeColor.copy(alpha = midAlpha),
                1f to edgeColor.copy(alpha = 0f)
            )
        }
    }

    /** 颜色滤镜缓存（SrcIn 模式：将白色光晕染色为目标颜色，alpha 由光晕剖面决定） */
    fun tint(color: Color): ColorFilter {
        return tintCache.getOrPut(color) { ColorFilter.tint(color) }
    }

    // ============ 烘焙实现 ============

    private fun bakeRadial(vararg stops: Pair<Float, Color>): ImageBitmap {
        val bitmap = Bitmap.createBitmap(GlowSize, GlowSize, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                GlowSize / 2f, GlowSize / 2f, GlowSize / 2f,
                stops.map { it.second.toArgb() }.toIntArray(),
                stops.map { it.first }.toFloatArray(),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, GlowSize.toFloat(), GlowSize.toFloat(), paint)
        return bitmap.asImageBitmap()
    }

    /**
     * 烘焙纺锤形雨丝精灵：垂直渐变 + 横向衰减，形成滴状轮廓
     * 底部（head）最宽最亮，顶部（tail）收窄变暗
     */
    private fun bakeStreak(topAlpha: Float, midAlpha: Float, bottomAlpha: Float): ImageBitmap {
        val w = StreakWidth
        val h = StreakHeight
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(w * h)
        val halfW = w / 2f
        for (y in 0 until h) {
            val t = y.toFloat() / h // 0=top(tail), 1=bottom(head)
            // 垂直 alpha 渐变
            val vertAlpha = when {
                t < 0.5f -> topAlpha + (midAlpha - topAlpha) * t * 2f
                else -> midAlpha + (bottomAlpha - midAlpha) * (t - 0.5f) * 2f
            }
            // 纺锤轮廓：顶部窄(30%) → 底部宽(100%)
            val radiusFrac = 0.30f + 0.70f * t
            val radiusAtY = halfW * radiusFrac
            for (x in 0 until w) {
                val dx = (x + 0.5f - halfW) / radiusAtY
                // 柔和横向衰减（幂次 <2 产生柔和光晕感）
                val horizAlpha = (1f - abs(dx).coerceAtMost(1f).let { it * it * it }).coerceIn(0f, 1f)
                val a = (vertAlpha * horizAlpha * 255).roundToInt().coerceIn(0, 255)
                pixels[y * w + x] = (a shl 24) or 0x00FFFFFF
            }
        }
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
        return bitmap.asImageBitmap()
    }

    /**
     * 烘焙电影感运动模糊雨丝：
     * - 垂直 alpha 在尾迹(顶)与落点(底)皆柔化淡出，中部恒定，模拟高速运动模糊的长条；
     * - 横向为纺锤轮廓，叠加一条按固定半宽计算的高斯亮芯（不随轮廓缩放，保持极细反光）。
     * @param tail  顶部(尾迹)alpha
     * @param body  中部主体alpha
     * @param head  底部(落点)alpha
     * @param core  高光内芯强度
     */
    private fun bakeStreakPro(tail: Float, body: Float, head: Float, core: Float): ImageBitmap {
        val w = StreakWidth
        val h = StreakHeight
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(w * h)
        val halfW = w / 2f
        val coreHalf = halfW * 0.42f // 高光内芯固定半宽，保证缩放后仍是细线
        for (y in 0 until h) {
            val t = y.toFloat() / h // 0=尾迹, 1=落点
            val vertAlpha = when {
                t < 0.16f -> tail + (body - tail) * (t / 0.16f)
                t > 0.84f -> body + (head - body) * ((t - 0.84f) / 0.16f)
                else -> body
            }
            // 纺锤宽度轮廓：两端收窄、中部饱满
            val radiusAtY = halfW * (0.18f + 0.82f * sin(t * PI.toFloat()))
            for (x in 0 until w) {
                val dxEdge = ((x + 0.5f - halfW) / radiusAtY).coerceIn(-1f, 1f)
                val edge = (1f - dxEdge * dxEdge * dxEdge).coerceIn(0f, 1f) // 柔和纺锤
                val dxCore = (x + 0.5f - halfW) / coreHalf
                val coreG = exp(-(dxCore * dxCore) / 0.12f) // 极细高斯亮芯
                val horiz = (edge * 0.55f + coreG * core).coerceIn(0f, 1.4f)
                val a = (vertAlpha * horiz * 255).roundToInt().coerceIn(0, 255)
                pixels[y * w + x] = (a shl 24) or 0x00FFFFFF
            }
        }
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
        return bitmap.asImageBitmap()
    }

}

/**
 * 绘制径向光晕精灵（等价于原 drawCircle + radialGradient，但无任何逐帧分配）
 * @param radius 光晕半径（像素）
 * @param alpha 整体 alpha（即原渐变中心点的 alpha）
 */
fun DrawScope.drawGlow(
    sprite: ImageBitmap,
    centerX: Float,
    centerY: Float,
    radius: Float,
    alpha: Float,
    colorFilter: ColorFilter? = null
) {
    if (radius <= 0f || alpha <= 0f) return
    val diameter = (radius * 2f).roundToInt().coerceAtLeast(1)
    drawImage(
        image = sprite,
        dstOffset = IntOffset(
            (centerX - radius).roundToInt(),
            (centerY - radius).roundToInt()
        ),
        dstSize = IntSize(diameter, diameter),
        alpha = alpha.coerceAtMost(1f),
        colorFilter = colorFilter
    )
}

/**
 * 绘制雨丝精灵（等价于原 drawLine + verticalGradient，但无任何逐帧分配）
 * @param x 雨丝中心 x
 * @param top 雨丝顶部 y
 */
fun DrawScope.drawStreak(
    sprite: ImageBitmap,
    x: Float,
    top: Float,
    width: Float,
    height: Float,
    alpha: Float,
    colorFilter: ColorFilter? = null
) {
    if (alpha <= 0f || width <= 0f || height <= 0f) return
    drawImage(
        image = sprite,
        dstOffset = IntOffset(
            (x - width / 2f).roundToInt(),
            top.roundToInt()
        ),
        dstSize = IntSize(
            width.roundToInt().coerceAtLeast(1),
            height.roundToInt().coerceAtLeast(1)
        ),
        alpha = alpha.coerceAtMost(1f),
        colorFilter = colorFilter
    )
}
