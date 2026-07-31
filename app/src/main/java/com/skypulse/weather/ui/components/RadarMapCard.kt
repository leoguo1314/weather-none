package com.skypulse.weather.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skypulse.weather.ui.screen.LocalSkipCardAnimation
import com.skypulse.weather.ui.theme.*

/**
 * 雷达云图预览卡片。
 *
 * 显示在主页滚动内容中，点击后导航到全屏雷达图页面。
 * 卡片内绘制了一个抽象的雷达示意图形（非真实数据），
 * 以视觉提示用户此处可查看实时雷达云图。
 */
@Composable
fun RadarMapCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val skipAnimation = LocalSkipCardAnimation.current
    var visible by remember { mutableStateOf(false) }
    val cardAlpha by animateFloatAsState(
        targetValue = if (skipAnimation || visible) 1f else 0f,
        animationSpec = if (skipAnimation) tween(0) else tween(
            SkyPulseDesignSystem.Motion.cardEnterMillis,
            delayMillis = SkyPulseDesignSystem.Motion.cardEnterDelayMillis
        ),
        label = "radar_card_fade"
    )
    LaunchedEffect(Unit) { visible = true }
    LaunchedEffect(skipAnimation) { if (skipAnimation) visible = true }

    val accentColor = Color(0xFF4FC3F7)

    GlassCard(
        modifier = modifier
            .alpha(cardAlpha)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：雷达示意图形
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                RadarThumbnail(accentColor = accentColor)
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 右侧：文字说明
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "台风路径",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "实时台风路径与雷达云图，跟踪热带气旋动态",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = TextSecondary,
                    maxLines = 2
                )
            }

            // 向右箭头
            LucideIcon(
                name = "chevron-right",
                contentDescription = "查看",
                tint = TextSecondary.copy(alpha = 0.5f),
                size = 20.dp
            )
        }
    }
}

/**
 * 雷达缩略图绘制。
 * 用 Canvas 画一个类似雷达扫描的圆形图案，带有半透明色块模拟降水区域。
 */
@Composable
private fun RadarThumbnail(
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val bgColor = Color(0xFF1A2A3A)
    Canvas(modifier = modifier.fillMaxSize()) {
        val canvasW = size.width
        val canvasH = size.height
        val cx = canvasW / 2f
        val cy = canvasH / 2f
        val radius = minOf(canvasW, canvasH) * 0.42f

        // 暗色底圆
        drawCircle(
            color = bgColor,
            radius = radius * 1.15f
        )

        // 雷达扫描环
        val ringCount = 3
        for (i in 1..ringCount) {
            val r = radius * (i.toFloat() / ringCount)
            drawCircle(
                color = Color.White.copy(alpha = 0.08f),
                radius = r,
                style = Stroke(width = 1.dp.toPx())
            )
        }

        // 十字线
        drawLine(
            color = Color.White.copy(alpha = 0.06f),
            start = Offset(cx - radius, cy),
            end = Offset(cx + radius, cy),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = Color.White.copy(alpha = 0.06f),
            start = Offset(cx, cy - radius),
            end = Offset(cx, cy + radius),
            strokeWidth = 1.dp.toPx()
        )

        // 模拟降水区域（绿色/蓝色半透明色块）
        val precipColors = listOf(
            Color(0xFF4FC3F7).copy(alpha = 0.3f),
            Color(0xFF29B6F6).copy(alpha = 0.2f),
            Color(0xFF81C784).copy(alpha = 0.25f),
            Color(0xFF4DD0E1).copy(alpha = 0.15f)
        )

        // 绘制几块模拟降水区域
        val blobs = listOf(
            Offset(cx - radius * 0.35f, cy - radius * 0.15f) to 0.35f * radius,
            Offset(cx + radius * 0.2f, cy - radius * 0.3f) to 0.25f * radius,
            Offset(cx + radius * 0.3f, cy + radius * 0.2f) to 0.2f * radius,
            Offset(cx - radius * 0.15f, cy + radius * 0.35f) to 0.15f * radius
        )

        blobs.forEachIndexed { index, (center, r) ->
            val path = Path().apply {
                val segments = 8
                moveTo(
                    center.x + r * kotlin.math.cos(0.0f),
                    center.y + r * kotlin.math.sin(0.0f)
                )
                for (i in 1..segments) {
                    val angle = (2.0f * kotlin.math.PI.toFloat() * i) / segments
                    val variance = 0.7f + 0.3f * kotlin.math.sin(i * 3.7f + index)
                    val px = center.x + r * variance * kotlin.math.cos(angle)
                    val py = center.y + r * variance * kotlin.math.sin(angle)
                    lineTo(px, py)
                }
                close()
            }
            drawPath(
                path = path,
                brush = Brush.radialGradient(
                    colors = listOf(
                        precipColors[index % precipColors.size].copy(alpha = 0.5f),
                        precipColors[index % precipColors.size].copy(alpha = 0.0f)
                    ),
                    center = center,
                    radius = r
                )
            )
        }

        // 中心点
        drawCircle(
            color = accentColor,
            radius = 3.dp.toPx()
        )
        drawCircle(
            color = accentColor.copy(alpha = 0.2f),
            radius = 6.dp.toPx()
        )
    }
}
