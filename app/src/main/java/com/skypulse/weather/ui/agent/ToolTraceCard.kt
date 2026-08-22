package com.skypulse.weather.ui.agent

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun ToolTraceCard(
    traces: List<ToolTraceUi>
) {
    Card {
        Column {
            Text(text = "分析过程")
            traces.forEach { trace ->
                Text(
                    text = "✓ ${trace.toolName}: ${trace.status}"
                )
            }
        }
    }
}

data class ToolTraceUi(
    val toolName: String,
    val status: String
)
