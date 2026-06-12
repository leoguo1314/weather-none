package com.skypulse.weather.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.skypulse.weather.ui.theme.TextPrimary

@Composable
fun CityDotIndicator(
    cityCount: Int,
    currentIndex: Int,
    modifier: Modifier = Modifier
) {
    val dotColor = TextPrimary.copy(alpha = 0.5f)
    val activeDotColor = TextPrimary.copy(alpha = 1f)
    val dotSpacing = 10.dp
    val dotRadius = 2.5.dp
    val activeDotWidth = 12.dp

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(dotSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until cityCount) {
            val isActive = i == currentIndex
            Canvas(
                modifier = Modifier.height(dotRadius * 2)
            ) {
                val radius = dotRadius.toPx()
                if (isActive) {
                    drawCircle(
                        color = activeDotColor,
                        radius = radius,
                        center = Offset(size.width / 2, size.height / 2)
                    )
                } else {
                    drawCircle(
                        color = dotColor,
                        radius = radius,
                        center = Offset(size.width / 2, size.height / 2)
                    )
                }
            }
        }
    }
}

@Composable
fun CitySwitchBar(
    cityCount: Int,
    currentIndex: Int,
    isScrolled: Boolean,
    modifier: Modifier = Modifier
) {
    val dividerAlpha = animateFloatAsState(
        targetValue = if (isScrolled) 0.35f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "dividerAlpha"
    )
    val dotsAlpha = animateFloatAsState(
        targetValue = if (isScrolled) 0f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "dotsAlpha"
    )

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        // Divider line (visible when scrolled)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .alpha(dividerAlpha.value)
        ) {
            drawLine(
                color = TextPrimary,
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = size.height
            )
        }

        // City dots (visible when at top)
        if (cityCount > 1) {
            CityDotIndicator(
                cityCount = cityCount,
                currentIndex = currentIndex,
                modifier = Modifier.alpha(dotsAlpha.value)
            )
        }
    }
}