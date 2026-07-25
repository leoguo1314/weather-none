package com.skypulse.weather.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.skypulse.weather.ui.components.LucideIcon
import com.skypulse.weather.ui.theme.TextSecondary
import kotlinx.coroutines.delay

internal data class AlertItem(val title: String, val level: String?)

@Composable
internal fun AlertBannerSlot(
    alerts: List<AlertItem>,
    modifier: Modifier = Modifier,
    onClick: (Int) -> Unit = {},
    showBookmark: Boolean = false,
    isBookmarked: Boolean = false,
    onBookmarkClick: () -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (alerts.isNotEmpty()) {
                Box(modifier = Modifier.padding(top = 8.dp)) {
                    AlertBanner(alerts = alerts, onClick = onClick)
                }
            }
        }
        if (showBookmark) {
            BookmarkBanner(
                isBookmarked = isBookmarked,
                onClick = onBookmarkClick
            )
        }
    }
}

@Composable
internal fun AlertBanner(alerts: List<AlertItem>, onClick: (Int) -> Unit = {}) {
    if (alerts.isEmpty()) return

    var currentAlertIndex by remember { mutableIntStateOf(0) }
    val safeAlertIndex = currentAlertIndex.coerceIn(alerts.indices)
    val currentAlert = alerts[safeAlertIndex]
    val itemHeightDp = 20.dp

    LaunchedEffect(alerts.size) {
        currentAlertIndex = safeAlertIndex
    }

    Surface(
        onClick = { onClick(if (alerts.size == 1) 0 else safeAlertIndex) },
        modifier = Modifier.padding(start = 20.dp).offset(y = (-4).dp),
        shape = RoundedCornerShape(6.dp),
        color = Color.White.copy(alpha = 0.08f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)
        ) {
            Box(
                modifier = Modifier.size(itemHeightDp),
                contentAlignment = Alignment.Center
            ) {
                LucideIcon(
                    name = "triangle-alert",
                    contentDescription = "预警",
                    tint = TextSecondary,
                    size = 17.dp
                )
            }
            Spacer(modifier = Modifier.width(4.dp))

            if (alerts.size == 1) {
                Text(
                    text = alerts[0].title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.clickable { onClick(0) }
                )
            } else {
                LaunchedEffect(alerts) {
                    while (true) {
                        delay(3500)
                        currentAlertIndex = if (currentAlertIndex < alerts.size - 1) currentAlertIndex + 1 else 0
                    }
                }

                AnimatedContent(
                    targetState = safeAlertIndex,
                    transitionSpec = {
                        slideInVertically(
                            animationSpec = tween(400, easing = FastOutSlowInEasing)
                        ) { height -> height } + fadeIn(
                            animationSpec = tween(300)
                        ) togetherWith slideOutVertically(
                            animationSpec = tween(400, easing = FastOutSlowInEasing)
                        ) { height -> -height } + fadeOut(
                            animationSpec = tween(250)
                        )
                    },
                    contentKey = { it },
                    modifier = Modifier.height(itemHeightDp).clipToBounds()
                ) { index ->
                    val alert = alerts.getOrNull(index) ?: currentAlert
                    Text(
                        text = alert.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.wrapContentHeight(Alignment.CenterVertically)
                    )
                }
            }
        }
    }
}
