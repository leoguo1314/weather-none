package com.skypulse.weather.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import com.caverock.androidsvg.SVG
import java.io.ByteArrayInputStream

object WeatherSvgRenderer {
    private const val DefaultRaindropStroke = "#0A5AD4"

    // SVG 文本读取 + 解析 + 光栅化成本较高；小时/每日预报列表滚动回收后会反复触发。
    // 全局 LRU 缓存（按字节数上限 4MB），同图标同尺寸同配色只解析一次。
    private val bitmapCache = object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun renderBitmap(
        context: Context,
        icon: String,
        sizePx: Int,
        precipitationColor: Int? = null
    ): Bitmap? {
        val cacheKey = "$icon|$sizePx|$precipitationColor"
        bitmapCache.get(cacheKey)?.let { return it }
        return try {
            val assetPath = "meteocons/fill/$icon.svg"
            val svg = context.assets.open(assetPath).use { input ->
                val svgText = input.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val tunedSvgText = precipitationColor?.let { color ->
                    svgText.replace(DefaultRaindropStroke, color.toSvgRgb())
                } ?: svgText
                SVG.getFromInputStream(ByteArrayInputStream(tunedSvgText.toByteArray(Charsets.UTF_8)))
            }
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            svg.documentWidth = sizePx.toFloat()
            svg.documentHeight = sizePx.toFloat()
            svg.renderToCanvas(canvas)

            bitmapCache.put(cacheKey, bitmap)
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    private fun Int.toSvgRgb(): String {
        val red = this shr 16 and 0xFF
        val green = this shr 8 and 0xFF
        val blue = this and 0xFF
        return "#%02X%02X%02X".format(red, green, blue)
    }
}
