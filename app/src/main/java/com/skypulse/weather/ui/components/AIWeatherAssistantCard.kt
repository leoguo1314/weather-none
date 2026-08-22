package com.skypulse.weather.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
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
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "✨ AI天气助手",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "智能分析天气趋势、出行建议和户外活动风险",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
