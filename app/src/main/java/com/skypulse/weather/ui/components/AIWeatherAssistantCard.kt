package com.skypulse.weather.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AIWeatherAssistantCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "AI 天气助手",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "基于实时天气进行趋势研判、穿衣建议和出行风险分析",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
