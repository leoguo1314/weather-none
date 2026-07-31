package com.skypulse.weather.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.skypulse.weather.ui.components.LucideIcon
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.skypulse.weather.model.AlertContent
import com.skypulse.weather.model.sortedByPublishTimeDescending
import com.skypulse.weather.ui.theme.LocalSecondaryPageTheme


@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun AlertDetailScreen(
    alerts: List<AlertContent>,
    initialSelectedIndex: Int = 0,
    onBack: () -> Unit = {}
) {
    val page = LocalSecondaryPageTheme.current
    val sortedAlerts = remember(alerts) {
        alerts.sortedByPublishTimeDescending()
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(page.background)
    ) {
        TopAppBar(
            title = { Text("预警详情", color = page.textPrimary) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    LucideIcon(
                        name = "arrow-left",
                        contentDescription = "返回",
                        tint = page.backArrow
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            )
        )

        if (sortedAlerts.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "暂无预警信息", color = page.textSecondary)
            }
        } else {
            val safeInitialIndex = remember(alerts, initialSelectedIndex) {
                initialSelectedIndex.coerceIn(sortedAlerts.indices)
            }
            LazyColumn(
                state = rememberLazyListState(
                    initialFirstVisibleItemIndex = safeInitialIndex
                ),
                // 底部留出 24dp contentPadding：卡片自身垂直内边距 8dp，合计 32dp，
                // 与台风路径页卡片底部间距保持一致（navigationBarsPadding 之上再抬 32dp）
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
            ) {
                itemsIndexed(sortedAlerts) { _, alert ->
                    val title = alert.title
                        ?.replace(Regex("\\[.*?\\]"), "")
                        ?.replace(Regex("^.*(?:发布|变更|解除|继续|更新)"), "")
                        ?.replace(Regex("预警.*$"), "预警")
                        ?.trim()
                        ?.ifBlank { null }

                    // 次级页面主题卡片（浅色 iOS 风 / 深色 iOS 风）
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(page.cardBackground)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            if (!title.isNullOrBlank()) {
                                val alertColor = remember(alert, page.isDark) {
                                    alertLevelColor(
                                        alert.level,
                                        alert.title,
                                        fallback = page.textPrimary,
                                        whiteAlert = if (page.isDark) Color.White else Color.Black
                                    )
                                }
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = alertColor
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            if (!alert.description.isNullOrBlank()) {
                                Text(
                                    text = alert.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = page.textPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
