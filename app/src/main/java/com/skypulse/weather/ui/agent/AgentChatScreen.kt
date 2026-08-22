package com.skypulse.weather.ui.agent

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AgentChatScreen(
    state: AgentUiState,
    onSend: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "AI天气助手",
            style = MaterialTheme.typography.headlineSmall
        )

        when (state) {
            is AgentUiState.Success -> {
                LazyColumn {
                    items(state.messages) { message ->
                        Text(
                            text = message.content,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }

            AgentUiState.Thinking -> {
                Text("正在分析天气...")
            }

            AgentUiState.Idle -> {
                Text("请输入天气问题")
            }

            is AgentUiState.Error -> {
                Text(state.message)
            }
        }

        AgentInputBar(onSend = onSend)
    }
}
