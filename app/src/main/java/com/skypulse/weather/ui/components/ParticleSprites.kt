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
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
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

    // ============ 流星雨滴（头部亮核 + 长拖尾，近似流星但可区分） ============

    /**
     * 流星雨滴（近/中景）：底部头部亮核锐利清晰，向上拖尾拉长、随距离渐隐渐淡，
     * 透明度带轻微波动（清晰度变化），整体呈半透明质感。
     */
    val rainMeteorNear: ImageBitmap by lazy { bakeRainMeteor(head = 1f, trail = 0.85f, coreHalf = 0.20f) }

    /** 流星雨滴（远景）：更弥散、更暗，配合冷蓝大气透视 */
    val rainMeteorFar: ImageBitmap by lazy { bakeRainMeteor(head = 0.55f, trail = 0.5f, coreHalf = 0.26f) }

    // ============ 六角雪花（主臂 + 分支 + 中心亮核） ============

    /** 六角雪花精灵：6 根主臂等角分布，每根中段分叉两条小支臂，中心亮核，白色 */
    val snowflakeSprite: ImageBitmap by lazy { bakeSnowflake() }

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
     * 烘焙流星雨滴精灵：
     * - 底部头部（约占 20%）：亮核 + 高斯光晕，边缘高次幂衰减（锐利清晰）；
     * - 向上拖尾：随距离渐隐渐淡、宽度收窄，透明度带轻微波动（清晰度变化），
     *   边缘低次幂衰减（柔和虚化），形成「近清远虚」的半透明质感；
     * - 与夜空流星的区别：拖尾更长且透明度有波动，头部不带独立光晕层，整体连续。
     * @param head    头部峰值 alpha
     * @param trail   拖尾基础 alpha（尾部端渐隐至接近透明）
     * @param coreHalf 头部亮芯半宽（相对精灵半宽的比例）
     */
    private fun bakeRainMeteor(head: Float, trail: Float, coreHalf: Float): ImageBitmap {
        val w = StreakWidth
        val h = StreakHeight * 2 // 拖尾更长
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(w * h)
        val halfW = w / 2f
        val headStart = 0.80f
        val coreHalfPx = halfW * coreHalf
        for (y in 0 until h) {
            val t = y.toFloat() / h // 0=拖尾末端, 1=头部
            val isHead = t >= headStart
            val vertAlpha = if (isHead) {
                val hf = (t - headStart) / (1f - headStart)
                (head * (0.70f + 0.30f * hf)).coerceAtMost(1f)
            } else {
                val fade = t / headStart
                val wave = 0.82f + 0.18f * sin(t * 29f + 1.3f) // 拖尾清晰度波动
                (trail * (0.10f + 0.90f * fade)) * wave
            }
            // 头部锐利（高次幂），拖尾柔和（低次幂）
            val radiusAtY = halfW * (0.16f + 0.84f * t)
            val edgePow = if (isHead) 4f else 1.7f
            for (x in 0 until w) {
                val dxEdge = abs((x + 0.5f - halfW) / radiusAtY).coerceAtMost(1f)
                val edge = (1f - dxEdge.pow(edgePow)).coerceIn(0f, 1f)
                val dxCore = (x + 0.5f - halfW) / coreHalfPx
                val core = exp(-(dxCore * dxCore) / 0.12f)
                val horiz = if (isHead) {
                    (edge * 0.40f + core * 0.85f).coerceIn(0f, 1.25f)
                } else {
                    (edge * 0.75f + core * coreHalf * 0.20f).coerceIn(0f, 1f)
                }
                val a = (vertAlpha * horiz * 255).roundToInt().coerceIn(0, 255)
                pixels[y * w + x] = (a shl 24) or 0x00FFFFFF
            }
        }
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
        return bitmap.asImageBitmap()
    }

    /**
     * 烘焙六角雪花精灵（96px）：
     * - 6 根主臂等角分布（60°），从中心延伸到边缘，圆头笔触；
     * - 每根主臂中段分叉两条小支臂（±约 20°）；
     * - 中心亮核；整体带抗锯齿，缩放绘制仍清晰锐利。
     */
    private fun bakeSnowflake(): ImageBitmap {
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val c = size / 2f
        val white = Color.White.toArgb()
        val armPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = white
            strokeWidth = size * 0.05f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val branchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = white
            strokeWidth = size * 0.028f
            strokeCap = Paint.Cap.ROUND
        }
        for (i in 0 until 6) {
            val rad = Math.toRadians(i * 60.0)
            val cosA = cos(rad).toFloat()
            val sinA = sin(rad).toFloat()
            val armLen = size * 0.40f
            // 主臂
            canvas.drawLine(c, c, c + cosA * armLen, c + sinA * armLen, armPaint)
            // 主臂中段的两条分叉小支臂（±约 20°）
            val mx = c + cosA * armLen * 0.62f
            val my = c + sinA * armLen * 0.62f
            val bl = armLen * 0.42f
            for (sign in intArrayOf(-1, 1)) {
                val bRad = rad + sign * 0.35
                canvas.drawLine(
                    mx, my,
                    mx + (cos(bRad) * bl).toFloat(), my + (sin(bRad) * bl).toFloat(),
                    branchPaint
                )
            }
        }
        // 中心亮核
        canvas.drawCircle(c, c, size * 0.08f, armPaint)
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
