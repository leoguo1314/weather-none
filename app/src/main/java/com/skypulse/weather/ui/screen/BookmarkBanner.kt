package com.skypulse.weather.ui.screen

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.skypulse.weather.ui.components.LucideIcon
import com.skypulse.weather.ui.theme.TextSecondary

@Composable
internal fun BookmarkBanner(
    isBookmarked: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.padding(end = 20.dp),
        shape = RoundedCornerShape(6.dp),
        color = Color.White.copy(alpha = 0.08f)
    ) {
        LucideIcon(
            name = "bookmark",
            contentDescription = if (isBookmarked) "已收藏" else "收藏",
            tint = TextSecondary,
            fillColor = if (isBookmarked) TextSecondary else Color.Unspecified,
            size = 17.dp,
            modifier = Modifier
                .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)
        )
    }
}
