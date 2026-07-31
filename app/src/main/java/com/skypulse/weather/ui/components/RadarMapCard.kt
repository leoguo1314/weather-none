package com.skypulse.weather.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
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
            // 左侧：线性雷达图标
            LucideIcon(
                name = "radar",
                contentDescription = "台风雷达图",
                tint = Color.White,
                size = 48.dp
            )

            Spacer(modifier = Modifier.width(16.dp))

            // 右侧：文字说明
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "台风雷达图",
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


