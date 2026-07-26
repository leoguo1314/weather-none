package com.skypulse.weather.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.caverock.androidsvg.SVG
import java.io.ByteArrayInputStream

object LucideSvgRenderer {

    // SVG 文本读取 + 解析 + 光栅化成本较高；列表滚动回收/重组时会反复触发。
    // 全局 LRU 缓存（按字节数上限 4MB），同名同尺寸同配色的图标只解析一次。
    private val bitmapCache = object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun renderBitmap(
        context: Context,
        name: String,
        sizePx: Int,
        strokeColor: String? = null,
        fillColor: String? = null
    ): Bitmap? {
        val cacheKey = "$name|$sizePx|$strokeColor|$fillColor"
        bitmapCache.get(cacheKey)?.let { return it }
        return try {
            val assetPath = "lucide/$name.svg"
            val svg = context.assets.open(assetPath).use { input ->
                var svgText = input.bufferedReader(Charsets.UTF_8).use { it.readText() }
                if (strokeColor != null) {
                    svgText = svgText.replace("currentColor", strokeColor)
                }
                if (fillColor != null) {
                    svgText = svgText.replaceFirst(
                        Regex("""fill="none""""),
                        "fill=\"$fillColor\""
                    )
                }
                SVG.getFromInputStream(ByteArrayInputStream(svgText.toByteArray(Charsets.UTF_8)))
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
}

@Composable
fun LucideIcon(
    name: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = Color.Unspecified,
    fillColor: Color = Color.Unspecified
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val sizePx = remember(size, density) {
        with(density) { size.roundToPx() }.coerceAtLeast(1)
    }
    val strokeColor = remember(tint) {
        if (tint != Color.Unspecified) {
            val argb = tint.toArgb()
            val r = (argb shr 16) and 0xFF
            val g = (argb shr 8) and 0xFF
            val b = argb and 0xFF
            "#%02X%02X%02X".format(r, g, b)
        } else null
    }
    val fillColorHex = remember(fillColor) {
        if (fillColor != Color.Unspecified && fillColor != Color.Transparent) {
            val argb = fillColor.toArgb()
            val r = (argb shr 16) and 0xFF
            val g = (argb shr 8) and 0xFF
            val b = argb and 0xFF
            "#%02X%02X%02X".format(r, g, b)
        } else null
    }
    val bitmap = remember(context, name, sizePx, strokeColor, fillColorHex) {
        LucideSvgRenderer.renderBitmap(context, name, sizePx, strokeColor, fillColorHex)
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = modifier.size(size)
        )
    }
}
